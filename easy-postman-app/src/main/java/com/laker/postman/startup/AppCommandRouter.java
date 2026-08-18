package com.laker.postman.startup;

import com.laker.postman.collection.cli.CollectionCliCommand;
import com.laker.postman.functional.cli.FunctionalCliCommand;
import com.laker.postman.mock.cli.MockCliCommand;
import com.laker.postman.performance.cli.PerformanceCliCommand;

import java.io.PrintStream;
import java.util.OptionalInt;

public class AppCommandRouter {
    private final PerformanceCliCommand performanceCliCommand;
    private final CollectionCliCommand collectionCliCommand;
    private final FunctionalCliCommand functionalCliCommand;
    private final MockCliCommand mockCliCommand;

    public AppCommandRouter() {
        this.performanceCliCommand = new PerformanceCliCommand();
        this.collectionCliCommand = new CollectionCliCommand();
        this.functionalCliCommand = new FunctionalCliCommand();
        this.mockCliCommand = new MockCliCommand();
    }

    public OptionalInt route(String[] args, PrintStream out, PrintStream err) {
        if (PerformanceCliCommand.matches(args)) {
            System.setProperty("java.awt.headless", "true");
            return OptionalInt.of(performanceCliCommand.run(args, out, err));
        }
        if (CollectionCliCommand.matches(args)) {
            System.setProperty("java.awt.headless", "true");
            return OptionalInt.of(collectionCliCommand.run(args, out, err));
        }
        if (FunctionalCliCommand.matches(args)) {
            System.setProperty("java.awt.headless", "true");
            return OptionalInt.of(functionalCliCommand.run(args, out, err));
        }
        if (MockCliCommand.matches(args)) {
            System.setProperty("java.awt.headless", "true");
            return OptionalInt.of(mockCliCommand.run(args, out, err));
        }
        return OptionalInt.empty();
    }
}
