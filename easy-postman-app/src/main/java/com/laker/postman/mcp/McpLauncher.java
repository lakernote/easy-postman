package com.laker.postman.mcp;

import com.laker.postman.App;

/**
 * Native-package entry point that always routes additional launcher arguments to {@code mcp serve}.
 */
public final class McpLauncher {
    private static final String[] COMMAND_PREFIX = {"mcp", "serve"};

    private McpLauncher() {
    }

    public static void main(String[] args) {
        App.main(toAppArguments(args));
    }

    static String[] toAppArguments(String[] args) {
        int argumentCount = args == null ? 0 : args.length;
        String[] appArguments = new String[COMMAND_PREFIX.length + argumentCount];
        System.arraycopy(COMMAND_PREFIX, 0, appArguments, 0, COMMAND_PREFIX.length);
        if (argumentCount > 0) {
            System.arraycopy(args, 0, appArguments, COMMAND_PREFIX.length, argumentCount);
        }
        return appArguments;
    }
}
