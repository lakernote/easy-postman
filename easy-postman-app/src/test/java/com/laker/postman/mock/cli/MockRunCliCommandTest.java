package com.laker.postman.mock.cli;

import cn.hutool.json.JSONUtil;
import com.laker.postman.collection.model.CollectionDocument;
import com.laker.postman.collection.model.CollectionNode;
import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.mock.model.MockServerDefinition;
import com.laker.postman.mock.model.MockCollectionSource;
import com.laker.postman.mock.model.MockResponse;
import com.laker.postman.mock.model.MockRoute;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.SavedResponse;
import com.laker.postman.service.collections.CollectionDocumentJsonCodec;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class MockRunCliCommandTest {

    @Test
    public void shouldRunSavedMockHeadlesslyUntilInterrupted() throws Exception {
        int port = availablePort();
        Path workspace = createWorkspace(port);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        AtomicInteger exitCode = new AtomicInteger(-1);
        Thread commandThread = new Thread(() -> exitCode.set(new MockRunCliCommand().run(
                new String[]{"mock", "run", workspace.toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        )), "mock-cli-test");
        commandThread.setDaemon(true);

        HttpResponse<String> response;
        try {
            commandThread.start();
            waitUntilStarted(stdout, commandThread);
            response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health"))
                            .header("x-api-key", "test-key")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
        } finally {
            commandThread.interrupt();
            commandThread.join(5_000);
        }

        assertEquals(response.statusCode(), 200);
        assertEquals(response.body(), "{\"status\":\"ok\"}");
        assertEquals(exitCode.get(), 0, stderr.toString(StandardCharsets.UTF_8));
        assertTrue(!commandThread.isAlive(), "Mock CLI should stop after interruption");
    }

    @Test
    public void shouldRunStandaloneRouteWithoutACollectionSource() throws Exception {
        int port = availablePort();
        Path workspace = createStandaloneWorkspace(port);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        AtomicInteger exitCode = new AtomicInteger(-1);
        Thread commandThread = new Thread(() -> exitCode.set(new MockRunCliCommand().run(
                new String[]{"mock", "run", workspace.toString()},
                new PrintStream(stdout),
                new PrintStream(stderr)
        )), "standalone-mock-cli-test");
        commandThread.setDaemon(true);

        HttpResponse<String> response;
        try {
            commandThread.start();
            waitUntilStarted(stdout, commandThread);
            response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/standalone"))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
        } finally {
            commandThread.interrupt();
            commandThread.join(5_000);
        }

        assertEquals(response.statusCode(), 200);
        assertEquals(response.body(), "standalone");
        assertEquals(exitCode.get(), 0, stderr.toString(StandardCharsets.UTF_8));
    }

    private Path createWorkspace(int port) throws Exception {
        Path directory = Files.createTempDirectory("easy-postman-mock-run-");
        RequestGroup collection = new RequestGroup("Health API");
        collection.setId("collection-health");
        HttpRequestItem request = new HttpRequestItem();
        request.setId("request-health");
        request.setName("Health");
        request.setMethod("GET");
        request.setUrl("http://127.0.0.1:" + port + "/health");
        SavedResponse response = new SavedResponse();
        response.setId("response-health");
        response.setName("healthy");
        response.setCode(200);
        response.setBody("{\"status\":\"ok\"}");
        request.setResponse(List.of(response));
        CollectionNode root = CollectionNode.group(collection);
        root.addChild(CollectionNode.request(request));
        CollectionDocumentJsonCodec.write(
                directory.resolve("collections.json").toFile(),
                new CollectionDocument(List.of(root))
        );

        MockServerDefinition server = new MockServerDefinition();
        server.setName("Health Mock");
        server.setCollectionSources(List.of(new MockCollectionSource("collection-health", "Health")));
        server.setPort(port);
        server.setAccessKey("test-key");
        Files.writeString(
                directory.resolve("mock_servers.json"),
                JSONUtil.toJsonPrettyStr(List.of(server)),
                StandardCharsets.UTF_8
        );
        return directory;
    }

    private Path createStandaloneWorkspace(int port) throws Exception {
        Path directory = Files.createTempDirectory("easy-postman-standalone-mock-run-");
        CollectionDocumentJsonCodec.write(
                directory.resolve("collections.json").toFile(), CollectionDocument.empty());
        MockServerDefinition server = new MockServerDefinition();
        server.setName("Standalone Mock");
        server.setPort(port);
        server.setStandaloneRoutes(List.of(new MockRoute(
                "route-standalone", "request-standalone", "Standalone", "response-standalone", "OK",
                "GET", "/standalone", Map.of(), Map.of(), "",
                new MockResponse(200, Map.of("Content-Type", "text/plain"), "standalone"), ""
        )));
        Files.writeString(
                directory.resolve("mock_servers.json"),
                JSONUtil.toJsonPrettyStr(List.of(server)),
                StandardCharsets.UTF_8
        );
        return directory;
    }

    private int availablePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private void waitUntilStarted(ByteArrayOutputStream output, Thread commandThread) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (output.toString(StandardCharsets.UTF_8).contains("Press Ctrl+C to stop.")) return;
            if (!commandThread.isAlive()) break;
            Thread.sleep(20);
        }
        throw new AssertionError("Mock CLI did not start: " + output.toString(StandardCharsets.UTF_8));
    }
}
