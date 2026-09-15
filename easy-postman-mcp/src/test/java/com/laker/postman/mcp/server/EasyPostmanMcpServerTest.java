package com.laker.postman.mcp.server;

import com.laker.postman.util.JsonUtil;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;

public class EasyPostmanMcpServerTest {

    @Test(timeOut = 15_000)
    public void shouldCompleteStdioHandshakeAndToolCalls() throws Exception {
        PipedInputStream serverInput = new PipedInputStream();
        PipedOutputStream clientOutput = new PipedOutputStream(serverInput);
        PipedInputStream clientInput = new PipedInputStream();
        PipedOutputStream serverOutput = new PipedOutputStream(clientInput);
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        CompletableFuture<Integer> server = CompletableFuture.supplyAsync(() -> new EasyPostmanMcpServer().serve(
                new FakeBackend(),
                "v1.2.3",
                serverInput,
                serverOutput,
                new PrintStream(stderr)
        ));

        try (PrintWriter writer = new PrintWriter(clientOutput, true, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(new InputStreamReader(clientInput, StandardCharsets.UTF_8))) {
            writer.println("""
                    {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}
                    """.trim());
            JsonNode initialize = readJsonLine(reader);
            assertEquals(initialize.get("result").get("serverInfo").get("version").asText(), "1.2.3");

            writer.println("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
            writer.println("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}");
            JsonNode tools = readJsonLine(reader);
            assertEquals(tools.get("result").get("tools").size(), McpToolSpecifications.TOOL_NAMES.size());

            writer.println("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"list_workspaces\",\"arguments\":{}}}");
            JsonNode workspaces = readJsonLine(reader);
            assertFalse(workspaces.get("result").get("isError").asBoolean());
            assertEquals(workspaces.get("result").get("structuredContent").get("count").asInt(), 1);

            writer.println("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"list_collections\",\"arguments\":{\"workspaceId\":\"workspace-1\"}}}");
            JsonNode collections = readJsonLine(reader);
            assertFalse(collections.get("result").get("isError").asBoolean());
            assertEquals(collections.get("result").get("structuredContent").get("count").asInt(), 1);
        }

        assertEquals(server.get(5, TimeUnit.SECONDS), 0, stderr.toString(StandardCharsets.UTF_8));
    }

    @Test(timeOut = 10_000)
    public void shouldStopWhenMalformedInputTerminatesTheSdkReader() throws Exception {
        PipedInputStream serverInput = new PipedInputStream();
        PipedOutputStream clientOutput = new PipedOutputStream(serverInput);
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        CompletableFuture<Integer> server = CompletableFuture.supplyAsync(() -> new EasyPostmanMcpServer().serve(
                new FakeBackend(),
                "1.2.3",
                serverInput,
                new ByteArrayOutputStream(),
                new PrintStream(stderr)
        ));

        try {
            clientOutput.write("not-json\n".getBytes(StandardCharsets.UTF_8));
            clientOutput.flush();
            assertEquals(server.get(5, TimeUnit.SECONDS), 0);
        } finally {
            clientOutput.close();
        }
    }

    private static JsonNode readJsonLine(BufferedReader reader) throws Exception {
        String line = CompletableFuture.supplyAsync(() -> {
            try {
                return reader.readLine();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }).get(5, TimeUnit.SECONDS);
        assertNotNull(line);
        return JsonUtil.readTree(line);
    }

    private static final class FakeBackend implements EasyPostmanMcpBackend {
        @Override
        public Map<String, Object> listWorkspaces() {
            return Map.of(
                    "workspaces", List.of(Map.of("id", "workspace-1", "name", "Test")),
                    "count", 1
            );
        }

        @Override
        public Map<String, Object> listCollections(Map<String, Object> arguments) {
            return Map.of(
                    "workspaceId", arguments.get("workspaceId"),
                    "collections", List.of(Map.of("id", "collection-1", "name", "Test API")),
                    "count", 1
            );
        }

        @Override
        public Map<String, Object> listEnvironments(Map<String, Object> arguments) {
            return Map.of();
        }

        @Override
        public Map<String, Object> listRequests(Map<String, Object> arguments) {
            return Map.of();
        }

        @Override
        public Map<String, Object> runRequest(Map<String, Object> arguments) {
            return Map.of();
        }

        @Override
        public Map<String, Object> runCollection(Map<String, Object> arguments) {
            return Map.of();
        }
    }
}
