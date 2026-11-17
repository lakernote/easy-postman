package com.laker.postman.service.env;

import com.laker.postman.service.EnvironmentService;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class EnvironmentServiceMissingFileTest {

    @Test
    public void missingFileShouldLoadEmptyWithoutWritingDefaults() throws Exception {
        Path dir = Files.createTempDirectory("env-service-missing");
        Path shared = dir.resolve("environments.json");

        EnvironmentService.setDataFilePath(shared.toString());

        assertTrue(EnvironmentService.getAllEnvironments().isEmpty());
        assertFalse(Files.exists(shared));
    }
}
