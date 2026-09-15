package com.laker.postman;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class AppTest {
    private static final String[] LOGGING_PROPERTIES = {
            "LOG_LEVEL",
            "CONSOLE_LOG_LEVEL",
            "HTTP_LOG_LEVEL"
    };

    private final Map<String, String> originalValues = new LinkedHashMap<>();

    @BeforeMethod
    public void captureLoggingProperties() {
        originalValues.clear();
        for (String property : LOGGING_PROPERTIES) {
            originalValues.put(property, System.getProperty(property));
            System.clearProperty(property);
        }
    }

    @AfterMethod
    public void restoreLoggingProperties() {
        for (String property : LOGGING_PROPERTIES) {
            String value = originalValues.get(property);
            if (value == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, value);
            }
        }
    }

    @Test
    public void shouldDisableRoutineLoggingForMcpByDefault() {
        App.configureMcpLogging(new String[]{"mcp", "serve"});

        assertEquals(System.getProperty("LOG_LEVEL"), "OFF");
        assertEquals(System.getProperty("CONSOLE_LOG_LEVEL"), "OFF");
        assertEquals(System.getProperty("HTTP_LOG_LEVEL"), "OFF");
    }

    @Test
    public void shouldPreserveExplicitMcpLoggingOverrides() {
        System.setProperty("LOG_LEVEL", "WARN");
        System.setProperty("CONSOLE_LOG_LEVEL", "ERROR");

        App.configureMcpLogging(new String[]{"mcp", "serve"});

        assertEquals(System.getProperty("LOG_LEVEL"), "WARN");
        assertEquals(System.getProperty("CONSOLE_LOG_LEVEL"), "ERROR");
        assertEquals(System.getProperty("HTTP_LOG_LEVEL"), "OFF");
    }

    @Test
    public void shouldLeaveGuiLoggingDefaultsUntouched() {
        App.configureMcpLogging(new String[0]);

        for (String property : LOGGING_PROPERTIES) {
            assertNull(System.getProperty(property));
        }
    }
}
