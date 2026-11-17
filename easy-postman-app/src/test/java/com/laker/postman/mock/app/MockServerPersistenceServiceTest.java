package com.laker.postman.mock.app;

import com.laker.postman.mock.model.MockServerDefinition;
import com.laker.postman.util.SafeFileUtil;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class MockServerPersistenceServiceTest {

    @Test
    public void unreadableConfigShouldBeKeptUnchanged() throws Exception {
        Path file = Files.createTempDirectory("mock-persist-bad").resolve("mock_servers.json");
        Files.writeString(file, "[{\"id\":");
        MockServerPersistenceService service = serviceFor(file);

        assertTrue(service.load().isEmpty());
        assertEquals(Files.readString(file), "[{\"id\":");
    }

    @Test
    public void missingConfigShouldLoadEmptyWithoutCreatingFile() throws Exception {
        Path file = Files.createTempDirectory("mock-persist-missing").resolve("mock_servers.json");
        MockServerPersistenceService service = serviceFor(file);

        List<MockServerDefinition> loaded = service.load();
        service.rememberLoaded(loaded);

        assertTrue(loaded.isEmpty());
        assertFalse(Files.exists(file));
        assertTrue(SafeFileUtil.isLoadedVersion(file, null));
    }

    @Test
    public void ownSaveShouldBeRecognizedAsLoadedVersion() throws Exception {
        Path file = Files.createTempDirectory("mock-persist-save").resolve("mock_servers.json");
        MockServerPersistenceService service = serviceFor(file);
        MockServerDefinition definition = new MockServerDefinition();
        definition.setId("m1");
        definition.setName("orders");

        service.save(List.of(definition));

        assertTrue(SafeFileUtil.isLoadedVersion(file, SafeFileUtil.fileDigest(file).orElseThrow()));
        assertEquals(service.load().get(0).getName(), "orders");
    }

    private static MockServerPersistenceService serviceFor(Path file) {
        return new MockServerPersistenceService() {
            @Override
            Path configPath() {
                return file;
            }
        };
    }
}
