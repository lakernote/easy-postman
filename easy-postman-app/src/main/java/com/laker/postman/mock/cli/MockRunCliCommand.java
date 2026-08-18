package com.laker.postman.mock.cli;

import com.laker.postman.mock.app.MockCollectionRouteProvider;
import com.laker.postman.mock.app.MockNetworkAddressResolver;
import com.laker.postman.mock.app.MockScriptExecutorAdapter;
import com.laker.postman.mock.model.MockRoute;
import com.laker.postman.mock.model.MockServerDefinition;
import com.laker.postman.mock.runtime.LocalMockServer;

import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class MockRunCliCommand {
    private final MockRunWorkspaceLoader workspaceLoader = new MockRunWorkspaceLoader();

    public int run(String[] args, PrintStream out, PrintStream err) {
        try {
            MockRunCliOptions options = MockRunCliOptions.parse(args);
            if (options.isHelp()) {
                printUsage(out);
                return 0;
            }
            MockRunWorkspace workspace = workspaceLoader.load(options.getWorkspace());
            MockServerDefinition definition = workspaceLoader.select(workspace, options.getServerSelector());
            applyOverrides(definition, options);
            List<MockRoute> routes = new java.util.ArrayList<>(definition.getStandaloneRoutes() == null
                    ? List.of() : definition.getStandaloneRoutes());
            routes.addAll(new MockCollectionRouteProvider()
                    .buildRoutes(workspace.collections(), definition.collectionSourceIds()));
            LocalMockServer server = new LocalMockServer(definition, routes, new MockScriptExecutorAdapter());
            CountDownLatch shutdown = new CountDownLatch(1);
            Thread shutdownHook = new Thread(() -> {
                server.close();
                shutdown.countDown();
            }, "MockServerCliShutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            try {
                server.start();
                printStarted(out, workspace, definition, server, routes.size());
                shutdown.await();
                return 0;
            } finally {
                server.close();
                removeShutdownHook(shutdownHook);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return 0;
        } catch (IllegalArgumentException ex) {
            err.println(ex.getMessage());
            printUsage(err);
            return 2;
        } catch (Exception ex) {
            err.println("Mock Server failed: " + describe(ex));
            return 1;
        }
    }

    private void applyOverrides(MockServerDefinition definition, MockRunCliOptions options) {
        if (options.getHost() != null) definition.setHost(options.getHost());
        if (options.getPort() != null) definition.setPort(options.getPort());
        String accessKey = options.getAccessKey();
        if ((accessKey == null || accessKey.isBlank()) && options.getAccessKeyEnvironment() != null) {
            accessKey = System.getenv(options.getAccessKeyEnvironment());
        }
        if (accessKey != null && !accessKey.isBlank()) {
            definition.setAccessKey(accessKey);
        }
    }

    private void printStarted(PrintStream out,
                              MockRunWorkspace workspace,
                              MockServerDefinition definition,
                              LocalMockServer server,
                              int routeCount) {
        if (out == null) return;
        out.printf("Mock Server: %s%n", definition.getName());
        out.printf("Workspace: %s%n", workspace.directory());
        out.printf("Listening: %s%n", server.baseUrl());
        out.printf("Access URL: %s%n", MockNetworkAddressResolver.accessUrl(definition, server.port()));
        out.printf("Routes: %d | Access key: %s%n", routeCount,
                definition.getAccessKey() == null || definition.getAccessKey().isBlank() ? "not required" : "required");
        out.println("Press Ctrl+C to stop.");
    }

    private void removeShutdownHook(Thread hook) {
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException ignored) {
            // JVM shutdown is already in progress.
        }
    }

    private String describe(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    static void printUsage(PrintStream out) {
        out.println("Usage: mock run <workspace-directory> [options]");
        out.println("Starts a saved EasyPostman Mock Server without the desktop UI.");
        out.println("Options:");
        out.println("  -w, --workspace <directory>  Alternative to the positional workspace directory");
        out.println("  -s, --server <name-or-id>     Required when the workspace has multiple Mock Servers");
        out.println("      --host <address>          Override listen host, for example 0.0.0.0");
        out.println("      --port <port>             Override configured port");
        out.println("      --api-key <key>           Override the configured x-api-key value");
        out.println("      --api-key-env <name>      Read key from an environment variable");
        out.println("                               Defaults to EASY_POSTMAN_MOCK_API_KEY");
        out.println("  -h, --help                    Show this help");
    }
}
