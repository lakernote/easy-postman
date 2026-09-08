package com.laker.postman.service.sync;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class WebDavSnapshotPolicy {
    static final String DATA_ROOT_TOKEN = "$EASY_POSTMAN_DATA$";

    private static final Set<String> ROOT_SYNC_FILES = Set.of(
            "workspaces.json",
            "global_variables.json",
            "shortcuts.properties",
            "easy_postman_settings.properties",
            "user_settings.json"
    );
    private static final Set<String> ROOT_LOCAL_STATE_FILES = Set.of(
            "request_history.json",
            "opened_requests.json",
            "workspace_settings.json",
            "client_certificates.json",
            "elasticsearch_connection_profiles.json",
            "influxdb_connection_profiles.json"
    );
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            ".git",
            "plugins",
            "logs",
            "backups",
            "capture-ca"
    );
    private static final Set<String> EXCLUDED_FILE_NAMES = Set.of(
            ".ds_store",
            "thumbs.db"
    );
    private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
            "con",
            "prn",
            "aux",
            "nul",
            "com1",
            "com2",
            "com3",
            "com4",
            "com5",
            "com6",
            "com7",
            "com8",
            "com9",
            "lpt1",
            "lpt2",
            "lpt3",
            "lpt4",
            "lpt5",
            "lpt6",
            "lpt7",
            "lpt8",
            "lpt9"
    );

    public boolean shouldInclude(Path dataRoot, Path path) {
        Path normalizedRoot = dataRoot.toAbsolutePath().normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (!normalizedPath.startsWith(normalizedRoot) || normalizedPath.equals(normalizedRoot)) {
            return false;
        }

        List<String> segments = relativeSegments(normalizedRoot, normalizedPath);
        if (segments.isEmpty()
                || hasUnsafeCrossPlatformSegment(segments)
                || hasExcludedSegment(segments)
                || hasExcludedFileName(segments.get(segments.size() - 1))) {
            return false;
        }

        if (segments.size() == 1) {
            String fileName = segments.get(0);
            return ROOT_SYNC_FILES.contains(fileName) && !ROOT_LOCAL_STATE_FILES.contains(fileName);
        }

        return "workspaces".equals(segments.get(0));
    }

    boolean shouldDescend(Path dataRoot, Path path) {
        Path normalizedRoot = dataRoot.toAbsolutePath().normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (!normalizedPath.startsWith(normalizedRoot)) {
            return false;
        }
        if (normalizedPath.equals(normalizedRoot)) {
            return true;
        }

        List<String> segments = relativeSegments(normalizedRoot, normalizedPath);
        return !segments.isEmpty()
                && !hasUnsafeCrossPlatformSegment(segments)
                && !hasExcludedSegment(segments);
    }

    boolean shouldRestoreEntry(String entryName) {
        if (entryName == null || entryName.isBlank() || entryName.startsWith("/") || entryName.startsWith("\\")) {
            return false;
        }
        String normalized = restoreEntryName(entryName);
        if (normalized.contains("../") || normalized.equals("..") || normalized.contains("/../")) {
            return false;
        }
        List<String> segments = new ArrayList<>();
        for (String segment : normalized.split("/")) {
            if (!segment.isBlank()) {
                if (isUnsafeCrossPlatformSegment(segment)) {
                    return false;
                }
                segments.add(segment);
            }
        }
        if (segments.isEmpty() || hasExcludedSegment(segments) || hasExcludedFileName(segments.get(segments.size() - 1))) {
            return false;
        }
        if (segments.size() == 1) {
            return ROOT_SYNC_FILES.contains(segments.get(0));
        }
        return "workspaces".equals(segments.get(0));
    }

    String restoreEntryName(String entryName) {
        return entryName == null ? "" : entryName.replace('\\', '/');
    }

    String entryName(Path dataRoot, Path path) {
        return dataRoot.toAbsolutePath()
                .normalize()
                .relativize(path.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
    }

    private static List<String> relativeSegments(Path normalizedRoot, Path normalizedPath) {
        Path relative = normalizedRoot.relativize(normalizedPath);
        List<String> segments = new ArrayList<>();
        for (Path segment : relative) {
            segments.add(segment.toString());
        }
        return segments;
    }

    private static boolean hasExcludedSegment(List<String> segments) {
        for (String segment : segments) {
            if (EXCLUDED_DIRECTORIES.contains(segment.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUnsafeCrossPlatformSegment(List<String> segments) {
        for (String segment : segments) {
            if (isUnsafeCrossPlatformSegment(segment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasExcludedFileName(String fileName) {
        String normalized = fileName == null ? "" : fileName.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        return normalized.isBlank()
                || EXCLUDED_FILE_NAMES.contains(lower)
                || lower.endsWith(".tmp")
                || lower.endsWith(".bak")
                || lower.endsWith(".lock")
                || lower.endsWith("~");
    }

    private static boolean isUnsafeCrossPlatformSegment(String segment) {
        if (segment == null || segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
            return true;
        }
        if (segment.endsWith(" ") || segment.endsWith(".")) {
            return true;
        }
        for (int i = 0; i < segment.length(); i++) {
            char ch = segment.charAt(i);
            if (ch == '\0' || "<>:\"|?*\\".indexOf(ch) >= 0) {
                return true;
            }
        }
        String lower = segment.toLowerCase(Locale.ROOT);
        int dotIndex = lower.indexOf('.');
        String baseName = dotIndex >= 0 ? lower.substring(0, dotIndex) : lower;
        return WINDOWS_RESERVED_NAMES.contains(baseName);
    }
}
