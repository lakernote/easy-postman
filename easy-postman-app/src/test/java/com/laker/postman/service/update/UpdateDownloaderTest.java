package com.laker.postman.service.update;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class UpdateDownloaderTest {

    private Path directory;
    private UpdateDownloader downloader;

    @BeforeMethod
    public void setUp() throws Exception {
        directory = Files.createTempDirectory("update-downloader-test");
        downloader = new UpdateDownloader(new UpdateInstallerFileStore(directory));
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        downloader.cancel();
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    public void shouldRetryTransientPrimaryFailureBeforeUsingFallback() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(503));
            server.enqueue(new MockResponse().setBody("installer"));
            RecordingCallback callback = new RecordingCallback();
            AtomicInteger fallbackLookups = new AtomicInteger();

            File downloaded = downloader.downloadAsync(
                    server.url("/EasyPostman-retry.dmg").toString(),
                    () -> {
                        fallbackLookups.incrementAndGet();
                        return null;
                    },
                    callback).get(10, TimeUnit.SECONDS);

            assertNotNull(downloaded);
            assertEquals(Files.readString(downloaded.toPath()), "installer");
            assertTrue(downloaded.getName().endsWith("-EasyPostman-retry.dmg"));
            assertEquals(server.getRequestCount(), 2);
            assertEquals(fallbackLookups.get(), 0);
            callback.assertTerminal(1, 0, 0);
        }
    }

    @Test
    public void shouldUseFallbackWithoutRetryingPrimary404() throws Exception {
        try (MockWebServer primary = new MockWebServer(); MockWebServer fallback = new MockWebServer()) {
            primary.enqueue(new MockResponse().setResponseCode(404));
            fallback.enqueue(new MockResponse().setBody("fallback-installer"));
            RecordingCallback callback = new RecordingCallback();

            File downloaded = downloader.downloadAsync(
                    primary.url("/EasyPostman.dmg").toString(),
                    () -> fallback.url("/EasyPostman.dmg").toString(),
                    callback).get(10, TimeUnit.SECONDS);

            assertNotNull(downloaded);
            assertEquals(Files.readString(downloaded.toPath()), "fallback-installer");
            assertEquals(primary.getRequestCount(), 1);
            assertEquals(fallback.getRequestCount(), 1);
            callback.assertTerminal(1, 0, 0);
        }
    }

    @Test
    public void shouldRejectIncompleteDownloadAndRemovePartialFiles() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(incompleteResponse());
            server.enqueue(incompleteResponse());
            RecordingCallback callback = new RecordingCallback();

            File downloaded = downloader.downloadAsync(server.url("/EasyPostman.dmg").toString(), callback)
                    .get(10, TimeUnit.SECONDS);

            assertNull(downloaded);
            assertEquals(server.getRequestCount(), 2);
            callback.assertTerminal(0, 1, 0);
            assertDirectoryEventuallyEmpty();
        }
    }

    @Test
    public void shouldNotRetryOrFallbackWhenLocalFileCannotBeCreated() throws Exception {
        Path notADirectory = directory.resolve("blocked");
        Files.writeString(notADirectory, "file");
        UpdateDownloader blocked = new UpdateDownloader(new UpdateInstallerFileStore(notADirectory));
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("installer"));
            RecordingCallback callback = new RecordingCallback();
            AtomicInteger fallbackLookups = new AtomicInteger();

            File downloaded = blocked.downloadAsync(
                    server.url("/EasyPostman.dmg").toString(),
                    () -> {
                        fallbackLookups.incrementAndGet();
                        return null;
                    },
                    callback).get(10, TimeUnit.SECONDS);

            assertNull(downloaded);
            assertEquals(server.getRequestCount(), 1);
            assertEquals(fallbackLookups.get(), 0);
            callback.assertTerminal(0, 1, 0);
        }
    }

    @Test
    public void shouldCancelPartialDownloadImmediatelyAndRemovePartialFile() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setBody("x".repeat(256 * 1024))
                    .throttleBody(1024, 50, TimeUnit.MILLISECONDS));
            CountDownLatch firstProgress = new CountDownLatch(1);
            RecordingCallback callback = new RecordingCallback() {
                @Override
                public void onProgress(int percentage, long downloaded, long total, double speed) {
                    if (downloaded > 0) {
                        firstProgress.countDown();
                    }
                }
            };

            var future = downloader.downloadAsync(server.url("/EasyPostman.dmg").toString(), callback);
            assertTrue(firstProgress.await(3, TimeUnit.SECONDS), "Download never reported progress");
            downloader.cancel();

            callback.assertTerminal(0, 0, 1);
            assertTrue(future.isDone());
            assertNull(future.get());
            assertDirectoryEventuallyEmpty();
        }
    }

    @Test
    public void shouldDiscardFileWhenCancelledAtCompletionBoundary() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("installer"));
            RecordingCallback callback = new RecordingCallback() {
                @Override
                public void onProgress(int percentage, long downloaded, long total, double speed) {
                    if (percentage == 100) {
                        downloader.cancel();
                    }
                }
            };

            assertNull(downloader.downloadAsync(server.url("/EasyPostman.dmg").toString(), callback)
                    .get(3, TimeUnit.SECONDS));

            callback.assertTerminal(0, 0, 1);
            assertDirectoryEventuallyEmpty();
        }
    }

    @Test
    public void shouldCancelBlockedConnectionWithoutFallbackLookup() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
            RecordingCallback callback = new RecordingCallback();
            AtomicInteger fallbackLookups = new AtomicInteger();

            var future = downloader.downloadAsync(
                    server.url("/EasyPostman.dmg").toString(),
                    () -> {
                        fallbackLookups.incrementAndGet();
                        return null;
                    },
                    callback);
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
            downloader.cancel();

            assertNull(future.get(3, TimeUnit.SECONDS));
            callback.assertTerminal(0, 0, 1);
            Thread.sleep(200);
            assertEquals(fallbackLookups.get(), 0);
        }
    }

    @Test
    public void shouldIgnoreFallbackResultWhenCancelledDuringLookup() throws Exception {
        try (MockWebServer primary = new MockWebServer(); MockWebServer fallback = new MockWebServer()) {
            primary.enqueue(new MockResponse().setResponseCode(404));
            CountDownLatch lookupStarted = new CountDownLatch(1);
            CountDownLatch releaseLookup = new CountDownLatch(1);
            RecordingCallback callback = new RecordingCallback();

            var future = downloader.downloadAsync(
                    primary.url("/EasyPostman.dmg").toString(),
                    () -> {
                        lookupStarted.countDown();
                        awaitLatch(releaseLookup);
                        return fallback.url("/EasyPostman.dmg").toString();
                    },
                    callback);
            assertTrue(lookupStarted.await(3, TimeUnit.SECONDS), "Fallback lookup did not start");
            downloader.cancel();

            assertNull(future.get(1, TimeUnit.SECONDS));
            callback.assertTerminal(0, 0, 1);
            releaseLookup.countDown();
            Thread.sleep(200);
            assertEquals(fallback.getRequestCount(), 0);
            callback.assertTerminal(0, 0, 1);
        }
    }

    @Test
    public void shouldDiscardInstallerWhenCompletionCallbackFails() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("installer"));
            RecordingCallback callback = new RecordingCallback() {
                @Override
                public void onCompleted(File downloadedFile) {
                    super.onCompleted(downloadedFile);
                    throw new IllegalStateException("simulated UI callback failure");
                }
            };

            assertNull(downloader.downloadAsync(server.url("/EasyPostman.dmg").toString(), callback)
                    .get(10, TimeUnit.SECONDS));

            assertEquals(server.getRequestCount(), 1);
            callback.assertTerminal(1, 0, 0);
            assertDirectoryEventuallyEmpty();
        }
    }

    @Test
    public void shouldReportUnsupportedProtocolThroughErrorCallback() throws Exception {
        RecordingCallback callback = new RecordingCallback();

        File downloaded = downloader.downloadAsync(directory.resolve("x.bin").toUri().toString(), callback)
                .get(3, TimeUnit.SECONDS);

        assertNull(downloaded);
        callback.assertTerminal(0, 1, 0);
    }

    @Test
    public void shouldRejectOverlappingDownloadAndAcceptNewOneAfterCancel() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
            server.enqueue(new MockResponse().setBody("second-installer"));
            RecordingCallback first = new RecordingCallback();
            RecordingCallback second = new RecordingCallback();

            var firstFuture = downloader.downloadAsync(server.url("/first.dmg").toString(), first);
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
            expectThrows(UpdateDownloader.DownloadInProgressException.class,
                    () -> downloader.downloadAsync(server.url("/second.dmg").toString(), second));

            downloader.cancel();
            assertNull(firstFuture.get(1, TimeUnit.SECONDS));
            first.assertTerminal(0, 0, 1);

            File downloaded = downloader.downloadAsync(server.url("/second.dmg").toString(), second)
                    .get(10, TimeUnit.SECONDS);
            assertNotNull(downloaded);
            assertEquals(Files.readString(downloaded.toPath()), "second-installer");
            second.assertTerminal(1, 0, 0);
        }
    }

    @Test
    public void shouldDiscardDownloadedInstaller() throws Exception {
        Path installer = Files.createFile(directory.resolve("EasyPostman.dmg"));

        downloader.discardDownloadedInstaller(installer.toFile());

        assertFalse(Files.exists(installer));
    }

    private void assertDirectoryEventuallyEmpty() throws Exception {
        long deadline = System.currentTimeMillis() + 3_000;
        while (true) {
            try (Stream<Path> entries = Files.list(directory)) {
                if (entries.findAny().isEmpty()) {
                    return;
                }
            }
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Download directory still contains files");
            }
            Thread.sleep(20);
        }
    }

    private static MockResponse incompleteResponse() {
        return new MockResponse()
                .setBody("truncated-response-body")
                .setHeader("Content-Length", 200)
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY);
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static class RecordingCallback implements UpdateDownloader.DownloadProgressCallback {
        private final AtomicInteger completedCount = new AtomicInteger();
        private final AtomicInteger errorCount = new AtomicInteger();
        private final AtomicInteger cancelledCount = new AtomicInteger();

        @Override
        public void onProgress(int percentage, long downloaded, long total, double speed) {
        }

        @Override
        public void onCompleted(File downloadedFile) {
            completedCount.incrementAndGet();
        }

        @Override
        public void onError(String errorMessage) {
            errorCount.incrementAndGet();
        }

        @Override
        public void onCancelled() {
            cancelledCount.incrementAndGet();
        }

        void assertTerminal(int completed, int errors, int cancelled) {
            assertEquals(completedCount.get(), completed, "completed callbacks");
            assertEquals(errorCount.get(), errors, "error callbacks");
            assertEquals(cancelledCount.get(), cancelled, "cancelled callbacks");
        }
    }
}
