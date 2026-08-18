package com.laker.postman.mock.cli;

import java.io.PrintStream;

public class MockCliCommand {
    private final MockRunCliCommand runCommand = new MockRunCliCommand();

    public static boolean matches(String[] args) {
        return args != null && args.length > 0 && "mock".equals(args[0]);
    }

    public int run(String[] args, PrintStream out, PrintStream err) {
        if (args == null || args.length < 2 || isHelp(args[1])) {
            MockRunCliCommand.printUsage(out);
            return 0;
        }
        if (!"run".equals(args[1])) {
            err.println("Unknown mock command: " + args[1]);
            MockRunCliCommand.printUsage(err);
            return 2;
        }
        return runCommand.run(args, out, err);
    }

    private static boolean isHelp(String arg) {
        return "--help".equals(arg) || "-h".equals(arg) || "help".equals(arg);
    }
}
