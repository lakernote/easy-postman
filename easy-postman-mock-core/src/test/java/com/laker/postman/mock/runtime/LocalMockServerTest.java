package com.laker.postman.mock.runtime;

import com.laker.postman.mock.model.MockResponse;
import com.laker.postman.mock.model.MockRoute;
import com.laker.postman.mock.model.MockServerDefinition;
import org.testng.annotations.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class LocalMockServerTest {

    @Test
    public void shouldListenOnAllInterfacesByDefault() {
        assertEquals(new MockServerDefinition().getHost(), MockServerDefinition.ALL_INTERFACES_HOST);
    }

    @Test
    public void shouldServeExampleRunScriptKeepSessionStateAndRecordLogs() throws Exception {
        MockServerDefinition definition = new MockServerDefinition();
        definition.setName("test");
        definition.setPort(0);
        definition.setCorsEnabled(true);
        definition.setScript("");
        MockRoute route = new MockRoute(
                "route-1", "request-1", "Counter", "example-1", "counter",
                "GET", "/counter/{id}", Map.of(), Map.of(), "",
                new MockResponse(200, Map.of("Content-Type", "application/json"), "{}"),
                "increment"
        );

        try (LocalMockServer server = new LocalMockServer(definition, List.of(route), (script, context) -> {
            Object existing = context.getState().get("count");
            int next = existing instanceof Number number ? number.intValue() + 1 : 1;
            context.getState().set("count", next);
            context.getResponse().setBody("{\"count\":" + next
                    + ",\"id\":\"" + context.getRequest().pathVariable("id") + "\"}");
            context.getResponse().setHeader("X-Mock-Script", "true");
        })) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> first = client.send(request(server, "/counter/7"),
                    HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> second = client.send(request(server, "/counter/8"),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(first.statusCode(), 200);
            assertEquals(first.body(), "{\"count\":1,\"id\":\"7\"}");
            assertEquals(first.headers().firstValue("X-Mock-Script").orElseThrow(), "true");
            assertEquals(first.headers().firstValue("Access-Control-Allow-Origin").orElseThrow(), "*");
            assertEquals(second.body(), "{\"count\":2,\"id\":\"8\"}");
            assertEquals(server.stateSnapshot().get("session-a").get("count"), 2);
            assertEquals(server.logs().size(), 2);
            assertEquals(server.logs().get(0).exampleName(), "counter");
        }
    }

    @Test
    public void shouldReturnHelpful404WhenNoExampleMatches() throws Exception {
        MockServerDefinition definition = new MockServerDefinition();
        definition.setPort(0);

        try (LocalMockServer server = new LocalMockServer(definition, List.of(), null)) {
            server.start();
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(server.baseUrl() + "/missing")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(response.statusCode(), 404);
            assertTrue(response.body().contains("no matching example"));
            assertFalse(server.logs().isEmpty());
        }
    }

    @Test
    public void shouldAllowCallLoggingToBeDisabledForLoadTests() throws Exception {
        MockServerDefinition definition = new MockServerDefinition();
        definition.setPort(0);
        definition.setRecordCallLogs(false);
        MockRoute route = new MockRoute(
                "route-1", "request-1", "Health", "example-1", "healthy",
                "GET", "/health", Map.of(), Map.of(), "",
                new MockResponse(200, Map.of(), "ok"), ""
        );

        try (LocalMockServer server = new LocalMockServer(definition, List.of(route), null)) {
            server.start();
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    request(server, "/health"), HttpResponse.BodyHandlers.ofString());

            assertEquals(response.statusCode(), 200);
            assertTrue(server.logs().isEmpty());
        }
    }

    @Test
    public void shouldServeConcurrentStaticRequests() throws Exception {
        MockServerDefinition definition = new MockServerDefinition();
        definition.setPort(0);
        definition.setRecordCallLogs(false);
        MockRoute route = new MockRoute(
                "route-1", "request-1", "Ping", "example-1", "pong",
                "GET", "/ping", Map.of(), Map.of(), "",
                new MockResponse(200, Map.of(), "pong"), ""
        );

        try (LocalMockServer server = new LocalMockServer(definition, List.of(route), null)) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            List<CompletableFuture<HttpResponse<String>>> requests = IntStream.range(0, 32)
                    .mapToObj(ignored -> client.sendAsync(
                            request(server, "/ping"), HttpResponse.BodyHandlers.ofString()))
                    .toList();
            CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).join();

            for (CompletableFuture<HttpResponse<String>> request : requests) {
                assertEquals(request.join().statusCode(), 200);
                assertEquals(request.join().body(), "pong");
            }
        }
    }

    @Test
    public void shouldBindAllInterfacesRequireAccessKeyAndHonorDelayOverride() throws Exception {
        MockServerDefinition definition = new MockServerDefinition();
        definition.setHost(MockServerDefinition.ALL_INTERFACES_HOST);
        definition.setPort(0);
        definition.setAccessKey("team-secret");
        definition.setFixedDelayMs(500);
        MockRoute route = new MockRoute(
                "route-1", "request-1", "Shared", "example-1", "success",
                "GET", "/shared", Map.of(), Map.of(), "",
                new MockResponse(200, Map.of("Content-Type", "application/json"), "{\"ok\":true}"),
                ""
        );

        try (LocalMockServer server = new LocalMockServer(definition, List.of(route), null)) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            String loopbackUrl = "http://127.0.0.1:" + server.port() + "/shared";

            HttpResponse<String> unauthorized = client.send(
                    HttpRequest.newBuilder(URI.create(loopbackUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            long started = System.nanoTime();
            HttpResponse<String> authorized = client.send(
                    HttpRequest.newBuilder(URI.create(loopbackUrl))
                            .header(LocalMockServer.ACCESS_KEY_HEADER, "team-secret")
                            .header(LocalMockServer.RESPONSE_DELAY_HEADER, "1")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;

            assertEquals(unauthorized.statusCode(), 401);
            assertEquals(authorized.statusCode(), 200);
            assertEquals(authorized.body(), "{\"ok\":true}");
            assertTrue(elapsedMs < 400, "request-level delay should override the 500 ms server delay");
        }
    }

    private HttpRequest request(LocalMockServer server, String path) {
        return HttpRequest.newBuilder(URI.create(server.baseUrl() + path))
                .header("x-mock-session-id", "session-a")
                .GET()
                .build();
    }
}
