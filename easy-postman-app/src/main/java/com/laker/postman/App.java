package com.laker.postman;

import com.laker.postman.startup.AppLauncher;

/**
 * 应用主入口。
 */
public final class App {
    private static final String MCP_COMMAND = "mcp";

    public static void main(String[] args) {
        configureMcpLogging(args);
        int exitCode = AppLauncher.launch(args);
        if (exitCode != AppLauncher.GUI_STARTED) {
            System.exit(exitCode);
        }
    }

    /**
     * MCP communicates over stdio, so routine host/plugin logging is disabled by default.
     * Explicit JVM properties still win when diagnostics are intentionally enabled.
     */
    static void configureMcpLogging(String[] args) {
        if (args == null || args.length == 0 || !MCP_COMMAND.equals(args[0])) {
            return;
        }
        setDefaultSystemProperty("LOG_LEVEL", "OFF");
        setDefaultSystemProperty("CONSOLE_LOG_LEVEL", "OFF");
        setDefaultSystemProperty("HTTP_LOG_LEVEL", "OFF");
    }

    private static void setDefaultSystemProperty(String name, String value) {
        if (System.getProperty(name) == null) {
            System.setProperty(name, value);
        }
    }
}
