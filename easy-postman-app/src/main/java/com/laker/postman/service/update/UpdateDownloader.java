package com.laker.postman.service.update;

import com.laker.postman.ioc.BeanFactory;
import com.laker.postman.panel.lifecycle.AppExitCoordinator;
import com.laker.postman.platform.update.WindowsRegistryChecker;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.extern.slf4j.Slf4j;

import javax.swing.SwingUtilities;
import java.awt.Desktop;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Downloads update installers with one retry per source and an optional fallback source.
 * <p>
 * Exactly one terminal callback ({@code onCompleted}, {@code onError} or {@code onCancelled}) is delivered per
 * download. Cancellation takes effect immediately: the callback fires on the cancelling thread, the downloader
 * accepts a new download right away, and any file the worker still produces is discarded.
 */
@Slf4j
public class UpdateDownloader {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;
    private static final int MAX_ATTEMPTS_PER_SOURCE = 2;
    private static final long RETRY_DELAY_MS = 750;
    private static final long PROGRESS_INTERVAL_MS = 100;

    private final UpdateInstallerFileStore fileStore;
    private final AtomicReference<Task> activeTask = new AtomicReference<>();

    public UpdateDownloader() {
        this(UpdateInstallerFileStore.forCurrentUser());
    }

    UpdateDownloader(UpdateInstallerFileStore fileStore) {
        this.fileStore = Objects.requireNonNull(fileStore, "fileStore");
    }

    public interface DownloadProgressCallback {
        /**
         * @param total total bytes, or {@code <= 0} when unknown
         */
        void onProgress(int percentage, long downloaded, long total, double speed);

        void onCompleted(File downloadedFile);

        void onError(String errorMessage);

        void onCancelled();
    }

    public CompletableFuture<File> downloadAsync(String downloadUrl, DownloadProgressCallback callback) {
        return downloadAsync(downloadUrl, () -> null, callback);
    }

    /**
     * Starts a background download. The returned future completes with the installer, or {@code null} when the
     * download failed or was cancelled; it never completes exceptionally.
     *
     * @param fallbackUrlSupplier resolves an alternative URL after the primary source fails; called on the
     *                            download thread
     * @throws DownloadInProgressException when another download is still active
     */
    public CompletableFuture<File> downloadAsync(String downloadUrl,
                                                 Supplier<String> fallbackUrlSupplier,
                                                 DownloadProgressCallback callback) {
        Objects.requireNonNull(downloadUrl, "downloadUrl");
        Objects.requireNonNull(fallbackUrlSupplier, "fallbackUrlSupplier");
        Objects.requireNonNull(callback, "callback");

        Task task = new Task(callback);
        if (!activeTask.compareAndSet(null, task)) {
            throw new DownloadInProgressException();
        }
        Thread worker = new Thread(() -> run(task, downloadUrl, fallbackUrlSupplier), "UpdateDownloader");
        worker.setDaemon(true);
        worker.start();
        return task.future;
    }

    public void cancel() {
        Task task = activeTask.get();
        if (task != null) {
            task.cancel();
        }
    }

    private void run(Task task, String primaryUrl, Supplier<String> fallbackUrlSupplier) {
        File file = null;
        String error = null;
        try {
            file = downloadWithFallback(task, primaryUrl, fallbackUrlSupplier);
        } catch (CancelledException ignored) {
            // cancel() already delivered the terminal callback
        } catch (DownloadFailedException ex) {
            error = ex.getMessage();
        } catch (RuntimeException ex) {
            log.error("Unexpected update download failure", ex);
            error = friendlyMessage(ex);
        }

        if (!task.finish()) {
            fileStore.discard(file);
            return;
        }
        if (file != null) {
            File downloaded = file;
            if (!notifySafely(() -> task.callback.onCompleted(downloaded))) {
                fileStore.discard(downloaded);
                file = null;
            }
        } else {
            String message = error;
            notifySafely(() -> task.callback.onError(message));
        }
        task.future.complete(file);
    }

    private File downloadWithFallback(Task task, String primaryUrl, Supplier<String> fallbackUrlSupplier)
            throws DownloadFailedException {
        List<IOException> failures = new ArrayList<>();
        try {
            return downloadWithRetry(task, "primary", primaryUrl);
        } catch (LocalFileException ex) {
            throw new DownloadFailedException(List.of(ex));
        } catch (IOException ex) {
            failures.add(ex);
            log.warn("Download failed from primary source: {}", ex.getMessage());
        }

        String fallbackUrl = resolveFallbackUrl(fallbackUrlSupplier);
        task.checkCancelled();
        if (fallbackUrl == null || fallbackUrl.isBlank() || fallbackUrl.equals(primaryUrl)) {
            throw new DownloadFailedException(failures);
        }

        task.progress(0, 0, 0, 0);
        try {
            return downloadWithRetry(task, "fallback", fallbackUrl);
        } catch (IOException ex) {
            failures.add(ex);
            log.warn("Download failed from fallback source: {}", ex.getMessage());
            throw new DownloadFailedException(failures);
        }
    }

    private String resolveFallbackUrl(Supplier<String> fallbackUrlSupplier) {
        try {
            String url = fallbackUrlSupplier.get();
            log.info("Fallback download URL: {}", url);
            return url;
        } catch (RuntimeException ex) {
            log.warn("Failed to resolve fallback download URL: {}", ex.getMessage());
            return null;
        }
    }

    private File downloadWithRetry(Task task, String sourceName, String url) throws IOException {
        for (int attempt = 1; ; attempt++) {
            task.checkCancelled();
            log.info("Downloading update from {} source ({}/{}): {}", sourceName, attempt, MAX_ATTEMPTS_PER_SOURCE, url);
            try {
                return downloadOnce(task, url);
            } catch (IOException ex) {
                task.checkCancelled();
                if (attempt >= MAX_ATTEMPTS_PER_SOURCE || !isRetryable(ex)) {
                    throw ex;
                }
                log.warn("Download attempt {} from {} source failed: {}. Retrying...", attempt, sourceName, ex.getMessage());
                task.progress(0, 0, 0, 0);
                sleepBeforeRetry();
            }
        }
    }

    private File downloadOnce(Task task, String downloadUrl) throws IOException {
        URL url = new URL(downloadUrl);
        URLConnection opened = url.openConnection();
        if (!(opened instanceof HttpURLConnection connection)) {
            throw new IOException("Unsupported download protocol: " + url.getProtocol());
        }
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; EasyPostman-Updater)");
        connection.setRequestProperty("Accept", "*/*");

        task.connection = connection;
        Path partFile = null;
        boolean completed = false;
        try {
            task.checkCancelled();
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new HttpStatusException(status);
            }
            long total = connection.getContentLengthLong();
            log.info("Starting download: {} bytes from {}", total, downloadUrl);

            partFile = createPartFile(fileNameOf(url));
            long downloaded;
            try (InputStream input = connection.getInputStream();
                 OutputStream output = openOutput(partFile)) {
                downloaded = copy(task, input, output, total);
            }
            if (downloaded <= 0) {
                throw new EOFException("Downloaded file is empty");
            }
            if (total > 0 && downloaded != total) {
                throw new EOFException("Incomplete download: expected " + total + " bytes but received " + downloaded);
            }

            Path installer = completePartFile(partFile);
            completed = true;
            task.progress(100, downloaded, total, 0);
            log.info("Download completed: {} bytes -> {}", downloaded, installer);
            return installer.toFile();
        } finally {
            task.connection = null;
            connection.disconnect();
            if (!completed) {
                fileStore.discard(partFile);
            }
        }
    }

    private long copy(Task task, InputStream input, OutputStream output, long total) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long downloaded = 0;
        long startTime = System.currentTimeMillis();
        long lastProgressTime = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            task.checkCancelled();
            try {
                output.write(buffer, 0, read);
            } catch (IOException ex) {
                throw new LocalFileException(ex);
            }
            downloaded += read;

            long now = System.currentTimeMillis();
            if (now - lastProgressTime >= PROGRESS_INTERVAL_MS) {
                double elapsedSeconds = (now - startTime) / 1000.0;
                double speed = elapsedSeconds > 0 ? downloaded / elapsedSeconds : 0;
                int percentage = total > 0 ? (int) Math.min(99, downloaded * 100 / total) : 0;
                task.progress(percentage, downloaded, total, speed);
                lastProgressTime = now;
            }
        }
        return downloaded;
    }

    private Path createPartFile(String fileName) throws LocalFileException {
        try {
            return fileStore.createPartFile(fileName);
        } catch (IOException ex) {
            throw new LocalFileException(ex);
        }
    }

    private OutputStream openOutput(Path partFile) throws LocalFileException {
        try {
            return Files.newOutputStream(partFile);
        } catch (IOException ex) {
            throw new LocalFileException(ex);
        }
    }

    private Path completePartFile(Path partFile) throws LocalFileException {
        try {
            return fileStore.complete(partFile);
        } catch (IOException ex) {
            throw new LocalFileException(ex);
        }
    }

    private static String fileNameOf(URL url) {
        String path = url.getPath();
        String fileName = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
        String sanitized = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized.isBlank() ? "update.bin" : sanitized;
    }

    private static boolean isRetryable(IOException ex) {
        if (ex instanceof HttpStatusException statusException) {
            int status = statusException.statusCode;
            return status == HttpURLConnection.HTTP_CLIENT_TIMEOUT || status == 429 || status >= 500;
        }
        return ex instanceof SocketException
                || ex instanceof SocketTimeoutException
                || ex instanceof UnknownHostException
                || ex instanceof EOFException;
    }

    private static void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CancelledException();
        }
    }

    private static boolean notifySafely(Runnable notification) {
        try {
            notification.run();
            return true;
        } catch (RuntimeException ex) {
            log.error("Update download callback failed", ex);
            return false;
        }
    }

    private static String friendlyMessage(Exception ex) {
        Throwable cause = ex instanceof LocalFileException && ex.getCause() != null ? ex.getCause() : ex;
        String message = cause.getMessage();
        if (cause instanceof HttpStatusException statusException
                && statusException.statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
            return I18nUtil.getMessage(MessageKeys.ERROR_INVALID_DOWNLOAD_LINK);
        }
        if (cause instanceof SocketTimeoutException) {
            return I18nUtil.getMessage(MessageKeys.ERROR_NETWORK_TIMEOUT);
        }
        if (cause instanceof UnknownHostException) {
            return I18nUtil.getMessage(MessageKeys.ERROR_SERVER_UNREACHABLE);
        }
        if (cause instanceof IOException) {
            if (message != null && message.contains("No space left on device")) {
                return I18nUtil.getMessage(MessageKeys.ERROR_DISK_SPACE_INSUFFICIENT);
            }
            if (cause instanceof AccessDeniedException
                    || message != null && message.contains("Permission denied")) {
                return I18nUtil.getMessage(MessageKeys.ERROR_PERMISSION_DENIED);
            }
            return I18nUtil.getMessage(MessageKeys.ERROR_IO_EXCEPTION, message);
        }
        return I18nUtil.getMessage(MessageKeys.UPDATE_DOWNLOAD_FAILED, message);
    }

    /**
     * Opens the downloaded installer and schedules a normal application shutdown when successful.
     */
    public void installUpdate(File installerFile, Consumer<Boolean> callback) {
        Objects.requireNonNull(installerFile, "installerFile");
        Objects.requireNonNull(callback, "callback");

        boolean success;
        try {
            if (installerFile.getName().toLowerCase().endsWith(".exe")) {
                startWindowsInstaller(installerFile);
            } else {
                log.info("Opening installer with default application: {}", installerFile.getAbsolutePath());
                Desktop.getDesktop().open(installerFile);
            }
            scheduleApplicationExit();
            success = true;
        } catch (Exception ex) {
            log.error("Failed to open installer", ex);
            success = false;
        }

        boolean result = success;
        notifySafely(() -> callback.accept(result));
    }

    void discardDownloadedInstaller(File installerFile) {
        fileStore.discard(installerFile);
    }

    private void startWindowsInstaller(File executable) throws IOException {
        ProcessBuilder processBuilder;
        if (WindowsRegistryChecker.isAppInstalled()) {
            log.info("App is installed (version {}), using silent install with auto-start",
                    WindowsRegistryChecker.getInstalledVersion());
            processBuilder = new ProcessBuilder(
                    executable.getAbsolutePath(),
                    "/VERYSILENT",
                    "/AUTOSTART",
                    "/NORESTART",
                    "/FORCECLOSEAPPLICATIONS");
        } else {
            log.info("App is not installed, using interactive install");
            processBuilder = new ProcessBuilder(executable.getAbsolutePath());
        }
        processBuilder.start();
        log.info("Started EXE installer: {}", executable.getAbsolutePath());
    }

    private void scheduleApplicationExit() {
        log.info("Preparing to exit application for update installation...");
        SwingUtilities.invokeLater(() -> BeanFactory.getBean(AppExitCoordinator.class).exitApplication());
    }

    /**
     * State of one download. Whoever calls {@link #cancel()} or {@link #finish()} first owns the terminal callback.
     */
    private final class Task {
        private final DownloadProgressCallback callback;
        private final CompletableFuture<File> future = new CompletableFuture<>();
        private volatile HttpURLConnection connection;
        private volatile boolean cancelled;
        private boolean finished;

        private Task(DownloadProgressCallback callback) {
            this.callback = callback;
        }

        private void cancel() {
            synchronized (this) {
                if (finished) {
                    return;
                }
                finished = true;
                cancelled = true;
            }
            HttpURLConnection current = connection;
            if (current != null) {
                current.disconnect();
            }
            activeTask.compareAndSet(this, null);
            notifySafely(callback::onCancelled);
            future.complete(null);
        }

        private boolean finish() {
            synchronized (this) {
                if (finished) {
                    return false;
                }
                finished = true;
            }
            activeTask.compareAndSet(this, null);
            return true;
        }

        private void checkCancelled() {
            if (cancelled) {
                throw new CancelledException();
            }
        }

        private void progress(int percentage, long downloaded, long total, double speed) {
            if (!cancelled) {
                notifySafely(() -> callback.onProgress(percentage, downloaded, total, speed));
            }
        }
    }

    public static final class DownloadInProgressException extends IllegalStateException {
        private DownloadInProgressException() {
            super("An update download is already in progress");
        }
    }

    private static final class CancelledException extends RuntimeException {
        private CancelledException() {
            super(null, null, false, false);
        }
    }

    private static final class HttpStatusException extends IOException {
        private final int statusCode;

        private HttpStatusException(int statusCode) {
            super("HTTP error code: " + statusCode);
            this.statusCode = statusCode;
        }
    }

    /**
     * Local disk failure; retrying or switching sources cannot help.
     */
    private static final class LocalFileException extends IOException {
        private LocalFileException(IOException cause) {
            super(cause.getMessage(), cause);
        }
    }

    private static final class DownloadFailedException extends Exception {
        private DownloadFailedException(List<IOException> failures) {
            super(failures.stream()
                    .map(UpdateDownloader::friendlyMessage)
                    .distinct()
                    .reduce((a, b) -> a + "; " + b)
                    .orElseGet(() -> I18nUtil.getMessage(MessageKeys.GENERAL_ERROR_MESSAGE)));
        }
    }
}
