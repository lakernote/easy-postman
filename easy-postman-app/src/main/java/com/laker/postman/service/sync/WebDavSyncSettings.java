package com.laker.postman.service.sync;

public record WebDavSyncSettings(
        boolean enabled,
        String serverUrl,
        String remoteDirectory,
        String username,
        String password,
        boolean autoUploadEnabled,
        int autoUploadIntervalMinutes
) {
    public static final String DEFAULT_REMOTE_DIRECTORY = "EasyPostman";
    public static final int DEFAULT_AUTO_UPLOAD_INTERVAL_MINUTES = 60;
    public static final int MIN_AUTO_UPLOAD_INTERVAL_MINUTES = 15;
    public static final int MAX_AUTO_UPLOAD_INTERVAL_MINUTES = 7 * 24 * 60;

    public WebDavSyncSettings(boolean enabled,
                              String serverUrl,
                              String remoteDirectory,
                              String username,
                              String password) {
        this(enabled, serverUrl, remoteDirectory, username, password,
                false, DEFAULT_AUTO_UPLOAD_INTERVAL_MINUTES);
    }

    public WebDavSyncSettings {
        serverUrl = normalize(serverUrl);
        remoteDirectory = normalize(remoteDirectory);
        if (remoteDirectory.isEmpty()) {
            remoteDirectory = DEFAULT_REMOTE_DIRECTORY;
        }
        username = username == null ? "" : username.trim();
        password = password == null ? "" : password;
        autoUploadIntervalMinutes = Math.max(
                MIN_AUTO_UPLOAD_INTERVAL_MINUTES,
                Math.min(MAX_AUTO_UPLOAD_INTERVAL_MINUTES, autoUploadIntervalMinutes)
        );
    }

    public boolean hasEndpoint() {
        return !serverUrl.isBlank() && !remoteDirectory.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
