package com.laker.postman.mock.cli;

import lombok.Getter;

import java.nio.file.Path;

@Getter
final class MockRunCliOptions {
    private Path workspace;
    private String serverSelector;
    private String host;
    private Integer port;
    private String accessKey;
    private String accessKeyEnvironment = "EASY_POSTMAN_MOCK_API_KEY";
    private boolean help;

    static MockRunCliOptions parse(String[] args) {
        MockRunCliOptions options = new MockRunCliOptions();
        if (args == null) {
            throw new IllegalArgumentException("Mock command arguments are required");
        }
        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-h", "--help" -> options.help = true;
                case "-w", "--workspace" -> options.workspace = Path.of(requireValue(args, ++i, arg));
                case "-s", "--server" -> options.serverSelector = requireValue(args, ++i, arg);
                case "--host" -> options.host = requireValue(args, ++i, arg);
                case "--port" -> options.port = parsePort(requireValue(args, ++i, arg));
                case "--api-key" -> options.accessKey = requireValue(args, ++i, arg);
                case "--api-key-env" -> options.accessKeyEnvironment = requireValue(args, ++i, arg);
                default -> {
                    if (arg.startsWith("-")) {
                        throw new IllegalArgumentException("Unknown mock option: " + arg);
                    }
                    if (options.workspace != null) {
                        throw new IllegalArgumentException("Only one workspace directory can be specified");
                    }
                    options.workspace = Path.of(arg);
                }
            }
        }
        if (!options.help && options.workspace == null) {
            throw new IllegalArgumentException("Workspace directory is required");
        }
        return options;
    }

    private static int parsePort(String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1 || parsed > 65_535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid port: " + value, ex);
        }
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length || args[index] == null || args[index].isBlank()) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args[index];
    }
}
