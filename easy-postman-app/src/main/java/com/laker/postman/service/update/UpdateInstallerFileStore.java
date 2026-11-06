package com.laker.postman.service.update;

import com.laker.postman.util.SystemUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

/**
 * Stores downloaded installers under the user's EasyPostman data directory.
 * <p>
 * The data directory is already private to the current user, so no extra permission handling is needed.
 * Downloads are written to a unique {@code .part} file and renamed only after they are complete.
 */
@Slf4j
@RequiredArgsConstructor
class UpdateInstallerFileStore {

    private static final String PART_SUFFIX = ".part";
    private static final Duration STALE_AGE = Duration.ofHours(24);

    private final Path directory;

    static UpdateInstallerFileStore forCurrentUser() {
        return new UpdateInstallerFileStore(Path.of(SystemUtil.getEasyPostmanPath(), "updates"));
    }

    Path createPartFile(String fileName) throws IOException {
        Files.createDirectories(directory);
        return Files.createTempFile(directory, "EasyPostman-", "-" + fileName + PART_SUFFIX);
    }

    Path complete(Path partFile) throws IOException {
        String name = partFile.getFileName().toString();
        Path target = partFile.resolveSibling(name.substring(0, name.length() - PART_SUFFIX.length()));
        return Files.move(partFile, target);
    }

    void discard(File file) {
        if (file != null) {
            discard(file.toPath());
        }
    }

    void discard(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Failed to delete update installer {}: {}", path, ex.getMessage());
        }
    }

    /**
     * Removes installers and partial downloads left behind by earlier runs.
     */
    void cleanupStale() {
        if (!Files.isDirectory(directory)) {
            return;
        }
        Instant cutoff = Instant.now().minus(STALE_AGE);
        List<Path> files;
        try (Stream<Path> entries = Files.list(directory)) {
            files = entries.filter(Files::isRegularFile).toList();
        } catch (IOException ex) {
            log.debug("Failed to list update installer directory {}: {}", directory, ex.getMessage());
            return;
        }
        for (Path file : files) {
            try {
                if (Files.getLastModifiedTime(file).toInstant().isBefore(cutoff)) {
                    Files.deleteIfExists(file);
                }
            } catch (IOException ex) {
                log.debug("Failed to delete stale update installer {}: {}", file, ex.getMessage());
            }
        }
    }
}
