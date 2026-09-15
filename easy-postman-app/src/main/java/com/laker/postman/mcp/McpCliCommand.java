package com.laker.postman.mcp;

import com.laker.postman.mcp.server.EasyPostmanMcpServer;
import com.laker.postman.startup.HeadlessStartupBootstrap;
import com.laker.postman.util.SystemUtil;

import java.io.PrintStream;

public class McpCliCommand {
    private final RuntimeBootstrap runtimeBootstrap;
    private final EasyPostmanMcpServer server;

    public McpCliCommand() {
        this(HeadlessStartupBootstrap::initRuntime, new EasyPostmanMcpServer());
    }

    McpCliCommand(RuntimeBootstrap runtimeBootstrap, EasyPostmanMcpServer server) {
        this.runtimeBootstrap = runtimeBootstrap == null ? () -> {
        } : runtimeBootstrap;
        this.server = server == null ? new EasyPostmanMcpServer() : server;
    }

    public static boolean matches(String[] args) {
        return args != null && args.length > 0 && "mcp".equals(args[0]);
    }

    public int run(String[] args, PrintStream out, PrintStream err) {
        if (args == null || args.length < 2 || isHelp(args[1])) {
            printUsage(out);
            return 0;
        }
        try {
            return switch (args[1]) {
                case "serve" -> serve(args, out, err);
                default -> {
                    err.println("Unknown MCP command: " + args[1]);
                    printUsage(err);
                    yield 2;
                }
            };
        } catch (IllegalArgumentException exception) {
            err.println(McpSensitiveDataRedactor.message(exception.getMessage()));
            printUsage(err);
            return 2;
        } catch (Exception exception) {
            err.println("EasyPostman MCP command failed: "
                    + McpSensitiveDataRedactor.message(describe(exception)));
            return 1;
        }
    }

    private int serve(String[] args, PrintStream out, PrintStream err) throws Exception {
        if (args.length > 2 && isHelp(args[2])) {
            printServeUsage(out);
            return 0;
        }
        McpServeOptions options = McpServeOptions.parse(args, 2);
        McpWorkspaceToolService backend = new McpWorkspaceToolService(options);
        backend.validate();
        PrintStream originalSystemOut = System.out;
        System.setOut(err);
        try {
            runtimeBootstrap.init();
            return server.serve(backend, SystemUtil.getCurrentVersion(), System.in, out, err);
        } finally {
            System.setOut(originalSystemOut);
        }
    }

    static void printUsage(PrintStream out) {
        out.println("Usage: mcp serve [workspace-directory] [options]");
        out.println("Start the built-in MCP server over stdio.");
        out.println("Run 'mcp serve --help' for options.");
    }

    private static void printServeUsage(PrintStream out) {
        out.println("Usage: mcp serve [workspace-directory] [options]");
        out.println("Options:");
        out.println("      --max-response-bytes <n>    Total response-body bytes per tool call (default 65536)");
        out.println("  -h, --help                      Show this help");
        out.println("Without a directory, all workspaces registered in EasyPostman are authorized.");
        out.println("Only newline-delimited MCP JSON-RPC is written to stdout; diagnostics use stderr.");
    }

    private static boolean isHelp(String value) {
        return "--help".equals(value) || "-h".equals(value) || "help".equals(value);
    }

    private static String describe(Throwable failure) {
        String message = failure == null ? null : failure.getMessage();
        return message == null || message.isBlank()
                ? failure == null ? "unknown error" : failure.getClass().getSimpleName()
                : message;
    }

    @FunctionalInterface
    interface RuntimeBootstrap {
        void init() throws Exception;
    }
}
