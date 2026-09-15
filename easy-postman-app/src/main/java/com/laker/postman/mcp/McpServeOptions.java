package com.laker.postman.mcp;

record McpServeOptions(String workspace, int maxResponseBytes) {
    static final int DEFAULT_MAX_RESPONSE_BYTES = 64 * 1024;
    static final int MIN_MAX_RESPONSE_BYTES = 1024;
    static final int MAX_MAX_RESPONSE_BYTES = 10 * 1024 * 1024;

    static McpServeOptions parse(String[] args, int startIndex) {
        String workspace = null;
        int maxResponseBytes = DEFAULT_MAX_RESPONSE_BYTES;
        int index = startIndex;
        if (index < args.length && !args[index].startsWith("-")) {
            workspace = args[index++];
        }
        while (index < args.length) {
            String argument = args[index++];
            switch (argument) {
                case "--max-response-bytes" -> maxResponseBytes = parseResponseLimit(
                        requiredValue(args, index++, argument)
                );
                default -> throw new IllegalArgumentException("Unknown option: " + argument);
            }
        }
        return new McpServeOptions(workspace, maxResponseBytes);
    }

    static int parseResponseLimit(String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < MIN_MAX_RESPONSE_BYTES || parsed > MAX_MAX_RESPONSE_BYTES) {
                throw new IllegalArgumentException(
                        "--max-response-bytes must be between "
                                + MIN_MAX_RESPONSE_BYTES + " and " + MAX_MAX_RESPONSE_BYTES
                );
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("--max-response-bytes must be a number");
        }
    }

    static String requiredValue(String[] args, int index, String option) {
        if (index >= args.length || args[index] == null || args[index].isBlank() || args[index].startsWith("-")) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return args[index];
    }
}
