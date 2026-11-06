package com.laker.postman.service.update;

import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class UpdateInstallerFileStoreTest {

    @Test
    public void shouldCreateUniquePartFileAndPromoteIt() throws Exception {
        Path directory = Files.createTempDirectory("update-store-test").resolve("updates");
        UpdateInstallerFileStore store = new UpdateInstallerFileStore(directory);

        Path part = store.createPartFile("EasyPostman-6.3.1.dmg");
        Path installer = store.complete(part);

        assertTrue(part.getFileName().toString().endsWith("-EasyPostman-6.3.1.dmg.part"));
        assertFalse(Files.exists(part));
        assertEquals(installer.getFileName().toString(),
                part.getFileName().toString().replace(".part", ""));
        assertTrue(Files.exists(installer));
        store.discard(installer);
        assertFalse(Files.exists(installer));
    }

    @Test
    public void shouldRemoveOnlyStaleFiles() throws Exception {
        Path directory = Files.createTempDirectory("update-store-test");
        Path stale = Files.createFile(directory.resolve("old.dmg.part"));
        Path fresh = Files.createFile(directory.resolve("new.dmg"));
        Files.setLastModifiedTime(stale, FileTime.from(Instant.now().minus(Duration.ofDays(2))));

        new UpdateInstallerFileStore(directory).cleanupStale();

        assertFalse(Files.exists(stale));
        assertTrue(Files.exists(fresh));
        Files.delete(fresh);
        Files.delete(directory);
    }

    @Test
    public void shouldIgnoreMissingDirectoryDuringCleanup() {
        new UpdateInstallerFileStore(Path.of("does-not-exist-" + System.nanoTime())).cleanupStale();
    }
}
