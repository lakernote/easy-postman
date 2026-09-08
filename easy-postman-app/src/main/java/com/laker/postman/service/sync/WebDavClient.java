package com.laker.postman.service.sync;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class WebDavClient {
    private static final MediaType OCTET_STREAM = MediaType.get("application/octet-stream");
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final MediaType XML = MediaType.get("application/xml; charset=utf-8");
    private static final String SNAPSHOT_FILE = "snapshot.zip";
    private static final String MANIFEST_FILE = "manifest.json";
    private static final String PROPFIND_ALLPROP = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:propfind xmlns:D="DAV:"><D:allprop/></D:propfind>
            """;

    private final OkHttpClient client;
    private final HttpUrl serverUrl;
    private final String remoteDirectory;
    private final String username;
    private final String password;

    public WebDavClient(String serverUrl, String remoteDirectory, String username, String password) {
        this(new OkHttpClient(), serverUrl, remoteDirectory, username, password);
    }

    WebDavClient(OkHttpClient client, String serverUrl, String remoteDirectory, String username, String password) {
        HttpUrl parsedUrl = HttpUrl.parse(serverUrl == null ? "" : serverUrl.trim());
        if (parsedUrl == null) {
            throw new IllegalArgumentException("Invalid WebDAV server URL");
        }
        this.client = client;
        this.serverUrl = parsedUrl;
        this.remoteDirectory = remoteDirectory == null ? "" : remoteDirectory.trim();
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
    }

    public void testConnection() throws IOException {
        ensureRemoteDirectoryExists();
    }

    private void ensureRemoteDirectoryExists() throws IOException {
        List<String> segments = remoteDirectorySegments();
        if (segments.isEmpty()) {
            ensureCollection(remoteUrl(null, true));
            return;
        }
        for (int i = 1; i <= segments.size(); i++) {
            ensureCollection(directoryUrl(segments.subList(0, i)));
        }
    }

    private void ensureCollection(HttpUrl directoryUrl) throws IOException {
        Request propfind = requestBuilder(directoryUrl)
                .header("Depth", "0")
                .method("PROPFIND", RequestBody.create(PROPFIND_ALLPROP.getBytes(java.nio.charset.StandardCharsets.UTF_8), XML))
                .build();
        try (Response response = execute("PROPFIND directory", propfind)) {
            if (isSuccessfulWebDavResponse(response)) {
                return;
            }
            if (response.code() != 404) {
                throw responseException("WebDAV PROPFIND directory failed", response);
            }
        }

        Request mkcol = requestBuilder(directoryUrl)
                .method("MKCOL", RequestBody.create(new byte[0], null))
                .build();
        try (Response response = execute("MKCOL directory", mkcol)) {
            if (!isSuccessfulWebDavResponse(response) && response.code() != 405) {
                throw responseException("WebDAV MKCOL directory failed", response);
            }
        }
    }

    public void uploadSnapshot(byte[] snapshot) throws IOException {
        upload(SNAPSHOT_FILE, snapshot, OCTET_STREAM);
    }

    public void uploadSnapshot(Path snapshotPath) throws IOException {
        upload(SNAPSHOT_FILE, snapshotPath, OCTET_STREAM);
    }

    public byte[] downloadSnapshot() throws IOException {
        return download(SNAPSHOT_FILE);
    }

    public void downloadSnapshot(Path targetPath) throws IOException {
        download(SNAPSHOT_FILE, targetPath);
    }

    public void uploadManifest(String manifestJson) throws IOException {
        upload(MANIFEST_FILE, manifestJson == null ? new byte[0] : manifestJson.getBytes(StandardCharsets.UTF_8), JSON);
    }

    public byte[] downloadManifest() throws IOException {
        return download(MANIFEST_FILE);
    }

    public Optional<byte[]> downloadManifestIfPresent() throws IOException {
        return downloadIfPresent(MANIFEST_FILE);
    }

    private void upload(String fileName, byte[] content, MediaType mediaType) throws IOException {
        Request request = requestBuilder(remoteUrl(fileName, false))
                .put(RequestBody.create(content == null ? new byte[0] : content, mediaType))
                .build();
        try (Response response = execute("PUT " + fileName, request)) {
            if (!isSuccessfulWebDavResponse(response)) {
                throw responseException("WebDAV PUT " + fileName + " failed", response);
            }
        }
    }

    private void upload(String fileName, Path contentPath, MediaType mediaType) throws IOException {
        Request request = requestBuilder(remoteUrl(fileName, false))
                .put(fileRequestBody(contentPath, mediaType))
                .build();
        try (Response response = execute("PUT " + fileName, request)) {
            if (!isSuccessfulWebDavResponse(response)) {
                throw responseException("WebDAV PUT " + fileName + " failed", response);
            }
        }
    }

    private byte[] download(String fileName) throws IOException {
        Request request = requestBuilder(remoteUrl(fileName, false)).get().build();
        try (Response response = execute("GET " + fileName, request)) {
            if (!isSuccessfulWebDavResponse(response) || response.body() == null) {
                throw responseException("WebDAV GET " + fileName + " failed", response);
            }
            return response.body().bytes();
        }
    }

    private Optional<byte[]> downloadIfPresent(String fileName) throws IOException {
        Request request = requestBuilder(remoteUrl(fileName, false)).get().build();
        try (Response response = execute("GET " + fileName, request)) {
            if (response.code() == 404) {
                return Optional.empty();
            }
            if (!isSuccessfulWebDavResponse(response) || response.body() == null) {
                throw responseException("WebDAV GET " + fileName + " failed", response);
            }
            return Optional.of(response.body().bytes());
        }
    }

    private void download(String fileName, Path targetPath) throws IOException {
        Request request = requestBuilder(remoteUrl(fileName, false)).get().build();
        try (Response response = execute("GET " + fileName, request)) {
            if (!isSuccessfulWebDavResponse(response) || response.body() == null) {
                throw responseException("WebDAV GET " + fileName + " failed", response);
            }
            Path parent = targetPath.toAbsolutePath().normalize().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (InputStream inputStream = response.body().byteStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private Request.Builder requestBuilder(HttpUrl url) {
        Request.Builder builder = new Request.Builder().url(url);
        if (!username.isBlank()) {
            builder.header("Authorization", Credentials.basic(username, password));
        }
        return builder;
    }

    private HttpUrl remoteUrl(String fileName, boolean directory) {
        HttpUrl.Builder builder = serverUrl.newBuilder();
        String normalizedDirectory = trimSlashes(remoteDirectory);
        if (!normalizedDirectory.isBlank()) {
            for (String segment : normalizedDirectory.split("/")) {
                if (!segment.isBlank()) {
                    builder.addPathSegment(segment);
                }
            }
        }
        if (directory) {
            builder.addPathSegment("");
        } else if (fileName != null && !fileName.isBlank()) {
            builder.addPathSegment(fileName);
        }
        return builder.build();
    }

    private HttpUrl directoryUrl(List<String> directorySegments) {
        HttpUrl.Builder builder = serverUrl.newBuilder();
        for (String segment : directorySegments) {
            if (!segment.isBlank()) {
                builder.addPathSegment(segment);
            }
        }
        builder.addPathSegment("");
        return builder.build();
    }

    private List<String> remoteDirectorySegments() {
        String normalizedDirectory = trimSlashes(remoteDirectory);
        List<String> segments = new ArrayList<>();
        if (normalizedDirectory.isBlank()) {
            return segments;
        }
        for (String segment : normalizedDirectory.split("/")) {
            if (!segment.isBlank()) {
                segments.add(segment);
            }
        }
        return segments;
    }

    private static String trimSlashes(String value) {
        String trimmed = value == null ? "" : value.trim().replace('\\', '/');
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static RequestBody fileRequestBody(Path path, MediaType mediaType) {
        return new RequestBody() {
            @Override
            public MediaType contentType() {
                return mediaType;
            }

            @Override
            public long contentLength() throws IOException {
                return Files.size(path);
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                try (InputStream inputStream = Files.newInputStream(path);
                     var source = Okio.source(inputStream)) {
                    sink.writeAll(source);
                }
            }
        };
    }

    private static boolean isSuccessfulWebDavResponse(Response response) {
        int code = response.code();
        return response.isSuccessful() || code == 207;
    }

    private Response execute(String operation, Request request) throws IOException {
        String endpoint = request.url().toString();
        log.debug("WebDAV {} started: {} {}", operation, request.method(), endpoint);
        try {
            Response response = client.newCall(request).execute();
            log.debug("WebDAV {} completed: HTTP {} ({})", operation, response.code(), endpoint);
            return response;
        } catch (IOException e) {
            log.error("WebDAV {} failed: {} {}: {}", operation, request.method(), endpoint, e.getMessage(), e);
            throw new IOException(operation + " failed: " + e.getMessage(), e);
        }
    }

    private IOException responseException(String message, Response response) {
        IOException exception = new IOException(
                message + ": HTTP " + response.code() + " (" + response.request().url() + ")"
        );
        log.error("{}", exception.getMessage());
        return exception;
    }
}
