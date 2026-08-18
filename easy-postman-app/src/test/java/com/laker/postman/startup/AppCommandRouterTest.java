package com.laker.postman.startup;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.OptionalInt;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class AppCommandRouterTest {

    @Test
    public void shouldRoutePerformanceCommandsBeforeSwingStartup() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        AppCommandRouter router = new AppCommandRouter();

        OptionalInt exitCode = router.route(new String[]{"performance", "run", "--help"},
                new PrintStream(stdout),
                new PrintStream(new ByteArrayOutputStream()));

        assertTrue(exitCode.isPresent());
        assertTrue(stdout.toString().contains("performance run --plan"));
    }

    @DataProvider
    public Object[][] headlessHelpCommands() {
        return new Object[][]{
                {new String[]{"performance", "run", "--help"}},
                {new String[]{"performance", "worker", "--help"}},
                {new String[]{"performance", "master", "run", "--help"}},
                {new String[]{"collection", "run", "--help"}},
                {new String[]{"functional", "run", "--help"}},
                {new String[]{"mock", "run", "--help"}}
        };
    }

    @Test(dataProvider = "headlessHelpCommands")
    public void shouldForceHeadlessModeForCliCommands(String[] args) {
        String previous = System.getProperty("java.awt.headless");
        System.clearProperty("java.awt.headless");
        try {
            OptionalInt exitCode = new AppCommandRouter().route(args,
                    new PrintStream(new ByteArrayOutputStream()),
                    new PrintStream(new ByteArrayOutputStream()));

            assertTrue(exitCode.isPresent());
            assertEquals(System.getProperty("java.awt.headless"), "true");
        } finally {
            if (previous == null) {
                System.clearProperty("java.awt.headless");
            } else {
                System.setProperty("java.awt.headless", previous);
            }
        }
    }

    @Test
    public void shouldRouteCollectionCommandsBeforeSwingStartup() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        OptionalInt exitCode = new AppCommandRouter().route(
                new String[]{"collection", "run", "--help"},
                new PrintStream(stdout),
                new PrintStream(new ByteArrayOutputStream())
        );

        assertTrue(exitCode.isPresent());
        assertEquals(exitCode.getAsInt(), 0);
        assertTrue(stdout.toString().contains("collection run <workspace-directory>"));
    }

    @Test
    public void shouldRouteFunctionalCommandsBeforeSwingStartup() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        OptionalInt exitCode = new AppCommandRouter().route(
                new String[]{"functional", "run", "--help"},
                new PrintStream(stdout),
                new PrintStream(new ByteArrayOutputStream())
        );

        assertTrue(exitCode.isPresent());
        assertEquals(exitCode.getAsInt(), 0);
        assertTrue(stdout.toString().contains("functional run <workspace-directory>"));
    }

    @Test
    public void shouldRouteMockCommandsBeforeSwingStartup() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        OptionalInt exitCode = new AppCommandRouter().route(
                new String[]{"mock", "run", "--help"},
                new PrintStream(stdout),
                new PrintStream(new ByteArrayOutputStream())
        );

        assertTrue(exitCode.isPresent());
        assertEquals(exitCode.getAsInt(), 0);
        assertTrue(stdout.toString().contains("mock run <workspace-directory>"));
    }

    @Test
    public void shouldIgnoreGuiStartupArguments() {
        String previous = System.getProperty("java.awt.headless");
        System.clearProperty("java.awt.headless");
        try {
            OptionalInt exitCode = new AppCommandRouter().route(new String[0],
                    new PrintStream(new ByteArrayOutputStream()),
                    new PrintStream(new ByteArrayOutputStream()));

            assertFalse(exitCode.isPresent());
            assertFalse(Boolean.parseBoolean(System.getProperty("java.awt.headless")));
        } finally {
            if (previous == null) {
                System.clearProperty("java.awt.headless");
            } else {
                System.setProperty("java.awt.headless", previous);
            }
        }
    }
}
