package com.laker.postman.service.sync;

import okhttp3.Credentials;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.testng.Assert.*;

public class WebDavClientTest {

    @Test
    public void uploadSnapshotShouldPutSnapshotZipWithBasicAuthentication() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(201));
            server.start();
            WebDavClient client = new WebDavClient(
                    server.url("/dav/").toString(),
                    "/EasyPostman/",
                    "alice",
                    "secret"
            );

            client.uploadSnapshot("zip-data".getBytes(StandardCharsets.UTF_8));

            RecordedRequest request = server.takeRequest();
            assertEquals(request.getMethod(), "PUT");
            assertEquals(request.getPath(), "/dav/EasyPostman/snapshot.zip");
            assertEquals(request.getHeader("Authorization"), Credentials.basic("alice", "secret"));
            assertEquals(request.getBody().readUtf8(), "zip-data");
        }
    }

    @Test
    public void uploadSnapshotShouldPutSnapshotFileWithoutLoadingThroughByteArrayApi() throws Exception {
        Path snapshot = Files.createTempFile("webdav-client-upload", ".zip");
        Files.writeString(snapshot, "zip-file-data", StandardCharsets.UTF_8);
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(201));
            server.start();
            WebDavClient client = new WebDavClient(server.url("/dav/").toString(), "EasyPostman", "", "");

            client.uploadSnapshot(snapshot);

            RecordedRequest request = server.takeRequest();
            assertEquals(request.getMethod(), "PUT");
            assertEquals(request.getPath(), "/dav/EasyPostman/snapshot.zip");
            assertEquals(request.getBody().readUtf8(), "zip-file-data");
        }
    }

    @Test
    public void downloadSnapshotShouldGetSnapshotZip() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("zip-data").setResponseCode(200));
            server.start();
            WebDavClient client = new WebDavClient(server.url("/dav/").toString(), "EasyPostman", "", "");

            byte[] snapshot = client.downloadSnapshot();

            RecordedRequest request = server.takeRequest();
            assertEquals(request.getMethod(), "GET");
            assertEquals(request.getPath(), "/dav/EasyPostman/snapshot.zip");
            assertEquals(new String(snapshot, StandardCharsets.UTF_8), "zip-data");
        }
    }

    @Test
    public void downloadSnapshotShouldStreamSnapshotFileToTargetPath() throws Exception {
        Path target = Files.createTempFile("webdav-client-download", ".zip");
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("zip-file-data").setResponseCode(200));
            server.start();
            WebDavClient client = new WebDavClient(server.url("/dav/").toString(), "EasyPostman", "", "");

            client.downloadSnapshot(target);

            RecordedRequest request = server.takeRequest();
            assertEquals(request.getMethod(), "GET");
            assertEquals(request.getPath(), "/dav/EasyPostman/snapshot.zip");
            assertEquals(Files.readString(target, StandardCharsets.UTF_8), "zip-file-data");
        }
    }

    @Test
    public void downloadManifestIfPresentShouldReturnEmptyWhenManifestDoesNotExist() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(404));
            server.start();
            WebDavClient client = new WebDavClient(server.url("/dav/").toString(), "EasyPostman", "", "");

            Optional<byte[]> manifest = client.downloadManifestIfPresent();

            assertTrue(manifest.isEmpty());
            RecordedRequest request = server.takeRequest();
            assertEquals(request.getMethod(), "GET");
            assertEquals(request.getPath(), "/dav/EasyPostman/manifest.json");
        }
    }

    @Test
    public void testConnectionShouldCreateRemoteDirectoryWhenMissing() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(404));
            server.enqueue(new MockResponse().setResponseCode(201));
            server.start();
            WebDavClient client = new WebDavClient(server.url("/dav/").toString(), "EasyPostman", "", "");

            client.testConnection();

            RecordedRequest propfind = server.takeRequest();
            assertEquals(propfind.getMethod(), "PROPFIND");
            assertEquals(propfind.getHeader("Depth"), "0");
            assertEquals(propfind.getPath(), "/dav/EasyPostman/");
            assertTrue(propfind.getBody().readUtf8().contains("<D:allprop/>"));
            RecordedRequest mkcol = server.takeRequest();
            assertEquals(mkcol.getMethod(), "MKCOL");
            assertEquals(mkcol.getPath(), "/dav/EasyPostman/");
        }
    }

    @Test
    public void testConnectionShouldTreatExistingDirectoryAsSuccess() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(207));
            server.start();
            WebDavClient client = new WebDavClient(server.url("/dav/").toString(), "EasyPostman", "", "");

            client.testConnection();

            RecordedRequest propfind = server.takeRequest();
            assertEquals(propfind.getMethod(), "PROPFIND");
            assertEquals(propfind.getPath(), "/dav/EasyPostman/");
            assertEquals(server.getRequestCount(), 1);
        }
    }

    @Test
    public void testConnectionShouldCreateNestedDirectoriesOneLevelAtATime() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(404));
            server.enqueue(new MockResponse().setResponseCode(201));
            server.enqueue(new MockResponse().setResponseCode(404));
            server.enqueue(new MockResponse().setResponseCode(201));
            server.start();
            WebDavClient client = new WebDavClient(server.url("/dav/").toString(), "EasyPostman/sync", "", "");

            client.testConnection();

            assertEquals(server.takeRequest().getPath(), "/dav/EasyPostman/");
            assertEquals(server.takeRequest().getPath(), "/dav/EasyPostman/");
            assertEquals(server.takeRequest().getPath(), "/dav/EasyPostman/sync/");
            assertEquals(server.takeRequest().getPath(), "/dav/EasyPostman/sync/");
        }
    }

    @Test
    public void testConnectionShouldAcceptMkcolAlreadyExists() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(404));
            server.enqueue(new MockResponse().setResponseCode(405));
            server.start();
            WebDavClient client = new WebDavClient(server.url("/dav/").toString(), "EasyPostman", "", "");

            client.testConnection();

            assertEquals(server.takeRequest().getMethod(), "PROPFIND");
            assertEquals(server.takeRequest().getMethod(), "MKCOL");
        }
    }

    @Test
    public void shouldAcceptBareIpv6LiteralWhenNoPortIsPresent() {
        WebDavClient client = new WebDavClient("http://::1/dav/", "EasyPostman", "", "");

        assertNotNull(client);
    }
}
