package com.laker.postman.mock.runtime;

import com.laker.postman.mock.model.MockCallLog;
import com.laker.postman.mock.model.MockRequest;
import com.laker.postman.mock.model.MockResponse;
import com.laker.postman.mock.model.MockRoute;
import com.laker.postman.mock.model.MockServerDefinition;
import com.laker.postman.mock.script.MockScriptContext;
import com.laker.postman.mock.script.MockScriptExecutor;
import com.laker.postman.mock.script.MockState;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight LAN-capable HTTP mock runtime backed by the JDK HTTP server.
 */
@Slf4j
public final class LocalMockServer implements AutoCloseable {
    private static final int MAX_REQUEST_BODY_BYTES = 1024 * 1024;
    private static final int MAX_LOG_BODY_CHARS = 64 * 1024;
    private static final int MAX_LOG_ENTRIES = 500;
    private static final int MAX_DELAY_MS = 60_000;
    private static final int HTTP_BACKLOG = 256;
    private static final int WORKER_THREADS = Math.max(
            8, Math.min(64, Runtime.getRuntime().availableProcessors() * 4));
    private static final String DEFAULT_SESSION = "default";
    private static final String SESSION_HEADER = "x-mock-session-id";
    public static final String ACCESS_KEY_HEADER = "x-api-key";
    public static final String RESPONSE_DELAY_HEADER = "x-mock-response-delay";

    private final MockServerDefinition definition;
    private final Map<String, List<MockRoute>> routesByMethod;
    private final MockScriptExecutor scriptExecutor;
    private final Map<String, ConcurrentHashMap<String, Object>> sessionStates = new ConcurrentHashMap<>();
    private final Deque<MockCallLog> logs = new ArrayDeque<>();

    private volatile HttpServer server;
    private volatile ExecutorService executor;
    private volatile String script;

    public LocalMockServer(MockServerDefinition definition,
                           List<MockRoute> routes,
                           MockScriptExecutor scriptExecutor) {
        this.definition = definition.copy();
        this.routesByMethod = indexRoutesByMethod(routes == null ? List.of() : routes);
        this.scriptExecutor = scriptExecutor == null ? MockScriptExecutor.NO_OP : scriptExecutor;
        this.script = definition.getScript();
    }

    public synchronized void start() throws IOException {
        if (server != null) {
            return;
        }
        validateLocalDefinition();
        InetSocketAddress address = new InetSocketAddress(normalizedHost(), definition.getPort());
        HttpServer created = HttpServer.create(address, HTTP_BACKLOG);
        ExecutorService createdExecutor = Executors.newFixedThreadPool(
                WORKER_THREADS,
                new DaemonThreadFactory("mock-server-" + definition.getPort())
        );
        created.setExecutor(createdExecutor);
        created.createContext("/", this::handle);
        try {
            created.start();
            server = created;
            executor = createdExecutor;
            log.info("Local mock server '{}' started at {}", definition.getName(), baseUrl());
        } catch (RuntimeException ex) {
            created.stop(0);
            createdExecutor.shutdownNow();
            throw ex;
        }
    }

    public synchronized void stop() {
        HttpServer current = server;
        server = null;
        if (current != null) {
            current.stop(0);
        }
        ExecutorService currentExecutor = executor;
        executor = null;
        if (currentExecutor != null) {
            currentExecutor.shutdownNow();
        }
        if (current != null) {
            log.info("Local mock server '{}' stopped", definition.getName());
        }
    }

    public boolean isRunning() {
        return server != null;
    }

    public int port() {
        HttpServer current = server;
        return current == null ? definition.getPort() : current.getAddress().getPort();
    }

    public String baseUrl() {
        return "http://" + normalizedHost() + ":" + port();
    }

    public void updateScript(String script) {
        this.script = script == null ? "" : script;
    }

    public List<MockCallLog> logs() {
        synchronized (logs) {
            return List.copyOf(logs);
        }
    }

    public void clearLogs() {
        synchronized (logs) {
            logs.clear();
        }
    }

    public Map<String, Map<String, Object>> stateSnapshot() {
        Map<String, Map<String, Object>> snapshot = new LinkedHashMap<>();
        sessionStates.forEach((session, values) -> snapshot.put(session, new LinkedHashMap<>(values)));
        return snapshot;
    }

    public void clearState() {
        sessionStates.clear();
    }

    private void validateLocalDefinition() throws IOException {
        if (definition.getPort() < 0 || definition.getPort() > 65_535) {
            throw new IllegalArgumentException("Mock server port must be between 0 and 65535");
        }
        InetAddress configuredAddress = InetAddress.getByName(normalizedHost());
        if (!configuredAddress.isAnyLocalAddress()
                && !configuredAddress.isLoopbackAddress()
                && NetworkInterface.getByInetAddress(configuredAddress) == null) {
            throw new IllegalArgumentException("Mock server host must be a local interface address");
        }
    }

    private void handle(HttpExchange exchange) {
        long startedNanos = System.nanoTime();
        String requestBody = "";
        String responseBody = "";
        int statusCode = 500;
        MockRoute matchedRoute = null;
        String error = null;
        try {
            if (definition.isCorsEnabled() && "OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                applyCors(exchange.getResponseHeaders());
                exchange.sendResponseHeaders(204, -1);
                statusCode = 204;
                return;
            }
            if (!isAuthorized(exchange)) {
                statusCode = 401;
                error = "invalid or missing mock access key";
                responseBody = "{\"error\":\"invalid or missing x-api-key\"}";
                writeError(exchange, statusCode, responseBody);
                return;
            }

            byte[] requestBytes = readLimited(exchange);
            requestBody = new String(requestBytes, StandardCharsets.UTF_8);
            MockRequest initialRequest = new MockRequest(
                    exchange.getRequestMethod(),
                    decode(exchange.getRequestURI().getRawPath()),
                    parseQuery(exchange.getRequestURI().getRawQuery()),
                    copyHeaders(exchange.getRequestHeaders()),
                    requestBody,
                    Map.of()
            );

            List<MockRoute> candidates = routesByMethod.getOrDefault(
                    initialRequest.method().toUpperCase(Locale.ROOT), List.of());
            Optional<MockRouteMatcher.Match> matched = MockRouteMatcher.match(candidates, initialRequest, definition);
            if (matched.isEmpty()) {
                MockResponse notFound = notFoundResponse(initialRequest);
                statusCode = notFound.getStatusCode();
                responseBody = notFound.getBody();
                writeResponse(exchange, notFound);
                return;
            }

            MockRouteMatcher.Match match = matched.get();
            matchedRoute = match.route();
            MockRequest request = new MockRequest(
                    initialRequest.method(), initialRequest.path(), initialRequest.queryParameters(),
                    initialRequest.headers(), initialRequest.body(), match.pathVariables()
            );
            MockResponse response = matchedRoute.response().copy();
            String sessionId = Optional.ofNullable(request.header(SESSION_HEADER))
                    .filter(value -> !value.isBlank())
                    .orElse(DEFAULT_SESSION);
            ConcurrentHashMap<String, Object> stateValues = sessionStates.computeIfAbsent(
                    sessionId, ignored -> new ConcurrentHashMap<>());
            if (script != null && !script.isBlank()) {
                scriptExecutor.execute(script, new MockScriptContext(request, response, new MockState(stateValues)));
            }
            if (!matchedRoute.script().isBlank()) {
                scriptExecutor.execute(matchedRoute.script(),
                        new MockScriptContext(request, response, new MockState(stateValues)));
            }
            delay(resolveDelay(request, response));
            statusCode = sanitizeStatusCode(response.getStatusCode());
            response.setStatusCode(statusCode);
            responseBody = response.getBody();
            writeResponse(exchange, response);
        } catch (RequestTooLargeException ex) {
            statusCode = 413;
            error = ex.getMessage();
            responseBody = "{\"error\":\"request body exceeds 1 MiB\"}";
            writeError(exchange, statusCode, responseBody);
        } catch (Exception ex) {
            statusCode = 500;
            error = ex.getMessage();
            responseBody = "{\"error\":\"mock script or server execution failed\"}";
            log.warn("Mock request failed: {} {}", exchange.getRequestMethod(), exchange.getRequestURI(), ex);
            writeError(exchange, statusCode, responseBody);
        } finally {
            exchange.close();
            if (definition.isRecordCallLogs()) {
                recordLog(new MockCallLog(
                        Instant.now(),
                        exchange.getRequestMethod(),
                        exchange.getRequestURI().toString(),
                        statusCode,
                        (System.nanoTime() - startedNanos) / 1_000_000,
                        matchedRoute == null ? "" : matchedRoute.requestName(),
                        matchedRoute == null ? "" : matchedRoute.exampleName(),
                        truncate(requestBody),
                        truncate(responseBody),
                        error
                ));
            }
        }
    }

    private byte[] readLimited(HttpExchange exchange) throws IOException, RequestTooLargeException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(initialBodyCapacity(exchange))) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = exchange.getRequestBody().read(buffer)) != -1) {
                total += read;
                if (total > MAX_REQUEST_BODY_BYTES) {
                    throw new RequestTooLargeException("Request body exceeds 1 MiB");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private MockResponse notFoundResponse(MockRequest request) {
        MockResponse response = new MockResponse();
        response.setStatusCode(404);
        response.setHeader("Content-Type", "application/json; charset=UTF-8");
        response.setBody("{\"error\":\"no matching example\",\"method\":\""
                + jsonEscape(request.method()) + "\",\"path\":\"" + jsonEscape(request.path()) + "\"}");
        return response;
    }

    private void writeError(HttpExchange exchange, int statusCode, String body) {
        try {
            MockResponse response = new MockResponse();
            response.setStatusCode(statusCode);
            response.setHeader("Content-Type", "application/json; charset=UTF-8");
            response.setBody(body);
            writeResponse(exchange, response);
        } catch (IOException ignored) {
            // The peer may already be gone.
        }
    }

    private void writeResponse(HttpExchange exchange, MockResponse response) throws IOException {
        Headers responseHeaders = exchange.getResponseHeaders();
        response.getHeaders().forEach((name, value) -> {
            if (!name.equalsIgnoreCase("Content-Length") && !name.equalsIgnoreCase("Transfer-Encoding")) {
                responseHeaders.set(name, value);
            }
        });
        if (!containsHeader(responseHeaders, "Content-Type")) {
            responseHeaders.set("Content-Type", "text/plain; charset=UTF-8");
        }
        if (definition.isCorsEnabled()) {
            applyCors(responseHeaders);
        }
        byte[] bytes = Optional.ofNullable(response.getBody()).orElse("").getBytes(StandardCharsets.UTF_8);
        int status = sanitizeStatusCode(response.getStatusCode());
        boolean noBody = "HEAD".equalsIgnoreCase(exchange.getRequestMethod()) || status == 204 || status == 304;
        exchange.sendResponseHeaders(status, noBody ? -1 : bytes.length);
        if (!noBody) {
            exchange.getResponseBody().write(bytes);
        }
    }

    private void applyCors(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Headers", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS");
    }

    private void delay(long requestedDelayMs) throws InterruptedException {
        int safeDelay = (int) Math.max(0, Math.min(MAX_DELAY_MS, requestedDelayMs));
        if (safeDelay > 0) {
            Thread.sleep(safeDelay);
        }
    }

    private long resolveDelay(MockRequest request, MockResponse response) {
        String override = request.header(RESPONSE_DELAY_HEADER);
        if (override != null && !override.isBlank()) {
            try {
                return Long.parseLong(override.trim());
            } catch (NumberFormatException ignored) {
                // Invalid overrides fall back to the configured route/server delay.
            }
        }
        return (long) response.getDelayMs() + definition.getFixedDelayMs();
    }

    private boolean isAuthorized(HttpExchange exchange) {
        String expected = definition.getAccessKey();
        if (expected == null || expected.isBlank()) {
            return true;
        }
        String actual = exchange.getRequestHeaders().getFirst(ACCESS_KEY_HEADER);
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String normalizedHost() {
        String configured = definition.getHost();
        return configured == null || configured.isBlank()
                ? MockServerDefinition.LOOPBACK_HOST
                : configured.trim();
    }

    private void recordLog(MockCallLog entry) {
        synchronized (logs) {
            logs.addFirst(entry);
            while (logs.size() > MAX_LOG_ENTRIES) {
                logs.removeLast();
            }
        }
    }

    private static Map<String, List<MockRoute>> indexRoutesByMethod(List<MockRoute> routes) {
        Map<String, List<MockRoute>> mutable = new LinkedHashMap<>();
        for (MockRoute route : routes) {
            if (route == null) continue;
            String method = route.method() == null ? "" : route.method().toUpperCase(Locale.ROOT);
            mutable.computeIfAbsent(method, ignored -> new ArrayList<>()).add(route);
        }
        Map<String, List<MockRoute>> immutable = new LinkedHashMap<>();
        mutable.forEach((method, methodRoutes) -> immutable.put(method, List.copyOf(methodRoutes)));
        return Map.copyOf(immutable);
    }

    private static int initialBodyCapacity(HttpExchange exchange) {
        String contentLength = exchange.getRequestHeaders().getFirst("Content-Length");
        if (contentLength == null) return 32;
        try {
            return Math.max(32, Math.min(MAX_REQUEST_BODY_BYTES, Integer.parseInt(contentLength)));
        } catch (NumberFormatException ignored) {
            return 32;
        }
    }

    private static Map<String, List<String>> copyHeaders(Headers headers) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        headers.forEach((key, values) -> copy.put(key, List.copyOf(values)));
        return Map.copyOf(copy);
    }

    static Map<String, List<String>> parseQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return Map.of();
        }
        Map<String, List<String>> values = new LinkedHashMap<>();
        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            String key = decode(separator < 0 ? pair : pair.substring(0, separator));
            String value = decode(separator < 0 ? "" : pair.substring(separator + 1));
            values.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
        }
        Map<String, List<String>> immutable = new LinkedHashMap<>();
        values.forEach((key, items) -> immutable.put(key, List.copyOf(items)));
        return Map.copyOf(immutable);
    }

    private static String decode(String value) {
        return value == null ? "" : URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static boolean containsHeader(Headers headers, String name) {
        return headers.keySet().stream().anyMatch(key -> key.equalsIgnoreCase(name));
    }

    private static int sanitizeStatusCode(int statusCode) {
        return statusCode >= 200 && statusCode <= 599 ? statusCode : 200;
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_LOG_BODY_CHARS) {
            return value == null ? "" : value;
        }
        return value.substring(0, MAX_LOG_BODY_CHARS) + "\n…<truncated>";
    }

    private static String jsonEscape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public void close() {
        stop();
    }

    private static final class RequestTooLargeException extends Exception {
        private RequestTooLargeException(String message) {
            super(message);
        }
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();
        private final String prefix;

        private DaemonThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, prefix + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
