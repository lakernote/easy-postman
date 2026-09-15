package com.laker.postman.mcp.server;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.time.Duration;
import java.util.Objects;

public final class EasyPostmanMcpServer {
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(10);

    public int serve(EasyPostmanMcpBackend backend,
                     String version,
                     InputStream input,
                     OutputStream output,
                     PrintStream err) {
        Objects.requireNonNull(backend, "backend");
        LifecycleAwareStdioServerTransportProvider transport = new LifecycleAwareStdioServerTransportProvider(
                McpJsonDefaults.getMapper(),
                input,
                output
        );
        McpSyncServer server = null;
        try {
            server = McpServer.sync(transport)
                    .serverInfo("easy-postman", normalizedVersion(version))
                    .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                    .tools(McpToolSpecifications.create(backend))
                    .requestTimeout(REQUEST_TIMEOUT)
                    .build();
            transport.awaitTermination();
            return 0;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            err.println("EasyPostman MCP server was interrupted.");
            return 130;
        } finally {
            if (server != null) {
                try {
                    server.closeGracefully();
                } catch (RuntimeException exception) {
                    err.println("EasyPostman MCP shutdown warning: " + backend.sanitizeError(exception));
                }
            }
        }
    }

    private static String normalizedVersion(String version) {
        if (version == null || version.isBlank()) {
            return "0.0.0";
        }
        return version.startsWith("v") ? version.substring(1) : version;
    }
}
