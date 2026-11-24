package com.laker.postman.service.collections;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class OpenedRequestTabsStoreTest {
    private Path openedRequestsPath;
    private Path backupPath;
    private boolean hadOriginalFile;

    @BeforeMethod
    public void backUpOpenedRequestsFile() throws IOException {
        openedRequestsPath = Path.of(OpenedRequestTabsStore.PATHNAME);
        backupPath = Files.createTempFile("opened-requests", ".json");
        hadOriginalFile = Files.exists(openedRequestsPath);
        if (hadOriginalFile) {
            Files.copy(openedRequestsPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @AfterMethod
    public void restoreOpenedRequestsFile() throws IOException {
        if (hadOriginalFile) {
            Files.createDirectories(openedRequestsPath.getParent());
            Files.copy(backupPath, openedRequestsPath, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.deleteIfExists(openedRequestsPath);
        }
        Files.deleteIfExists(backupPath);
    }

    @Test
    public void shouldClearStaleOpenedRequestsFileWhenSavingEmptyList() throws IOException {
        Files.createDirectories(openedRequestsPath.getParent());
        Files.writeString(openedRequestsPath, """
                [
                  {
                    "id": "stale",
                    "name": "Stale"
                  }
                ]
                """, StandardCharsets.UTF_8);

        OpenedRequestTabsStore.saveAll(List.of());

        assertFalse(Files.exists(openedRequestsPath));
    }

    @Test
    public void tryLoadAllShouldDistinguishUnreadableSessionFromMissingSession() throws IOException {
        Files.deleteIfExists(openedRequestsPath);
        assertEquals(OpenedRequestTabsStore.tryLoadAll().orElseThrow(), List.of());

        Files.createDirectories(openedRequestsPath.getParent());
        Files.writeString(openedRequestsPath, "[{ broken", StandardCharsets.UTF_8);
        assertTrue(OpenedRequestTabsStore.tryLoadAll().isEmpty());
    }
}
