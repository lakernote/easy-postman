package com.laker.postman.service.update;

import com.laker.postman.ioc.BeanFactory;
import com.laker.postman.panel.lifecycle.AppExitCoordinator;
import com.laker.postman.platform.update.WindowsRegistryChecker;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 文件下载管理器 - 负责下载更新文件
 */
@Slf4j
public class UpdateDownloader {

    private static final String GITHUB_COM = "github.com";
    private static final String GITEE_COM = "gitee.com";

    /**
     * 下载进度回调接口
     */
    public interface DownloadProgressCallback {
        void onProgress(int percentage, long downloaded, long total, double speed);

        void onCompleted(File downloadedFile);

        void onError(String errorMessage);

        void onCancelled();
    }

    private volatile boolean cancelled = false;
    /**
     * 当前活跃连接，cancel() 可以直接 disconnect() 中断 socket 阻塞
     */
    private volatile HttpURLConnection activeConnection = null;

    /**
     * 异步下载文件（支持多源切换）
     */
    public CompletableFuture<File> downloadAsync(String downloadUrl, DownloadProgressCallback callback) {
        // 重置取消标志，确保新的下载任务从干净的状态开始
        cancelled = false;

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 尝试从原始 URL 下载
                log.info("Attempting to download from: {}", downloadUrl);
                return downloadFile(downloadUrl, callback);
            } catch (Exception e) {
                // 被取消时 downloadFile 内部已处理，这里直接返回
                if (cancelled) {
                    return null;
                }
                log.warn("Download failed from primary source: {}", e.getMessage());

                // 尝试切换到备用源
                String alternativeUrl = getAlternativeDownloadUrl(downloadUrl);
                if (alternativeUrl != null && !alternativeUrl.equals(downloadUrl)) {
                    log.info("Trying alternative source: {}", alternativeUrl);
                    callback.onProgress(0, 0, 0, 0); // 重置进度

                    try {
                        return downloadFile(alternativeUrl, callback);
                    } catch (Exception e2) {
                        if (cancelled) return null;
                        log.error("Download failed from alternative source: {}", e2.getMessage());
                        callback.onError(getFriendlyErrorMessage(e2));
                    }
                } else {
                    callback.onError(getFriendlyErrorMessage(e));
                }
                return null;
            }
        });
    }

    /**
     * 获取备用下载源
     * GitHub <-> Gitee 互为备用
     */
    private String getAlternativeDownloadUrl(String originalUrl) {
        if (originalUrl == null) {
            return null;
        }

        // GitHub -> Gitee
        if (originalUrl.contains(GITHUB_COM) || originalUrl.contains("githubusercontent.com")) {
            // GitHub Release 下载链接格式:
            // https://github.com/lakernote/easy-postman/releases/download/v1.0.0/EasyPostman-1.0.0.exe
            // Gitee Release 下载链接格式:
            // https://gitee.com/lakernote/easy-postman/releases/download/v1.0.0/EasyPostman-1.0.0.exe

            String giteeUrl = originalUrl
                    .replace(GITHUB_COM, GITEE_COM)
                    .replace("githubusercontent.com", GITEE_COM);

            log.debug("Switched from GitHub to Gitee: {} -> {}", originalUrl, giteeUrl);
            return giteeUrl;
        }

        // Gitee -> GitHub
        if (originalUrl.contains(GITEE_COM)) {
            String githubUrl = originalUrl.replace(GITEE_COM, GITHUB_COM);
            log.debug("Switched from Gitee to GitHub: {} -> {}", originalUrl, githubUrl);
            return githubUrl;
        }

        return null;
    }

    /**
     * 取消下载。
     * 同时 disconnect() 活跃连接，强制中断阻塞在 getResponseCode() / read() 的线程，
     * 而不是只靠 volatile boolean 等下一次循环检查。
     */
    public void cancel() {
        cancelled = true;
        HttpURLConnection conn = activeConnection;
        if (conn != null) {
            try {
                conn.disconnect(); // 强制关闭 socket，立即中断所有阻塞调用
            } catch (Exception e) {
                log.debug("Error disconnecting on cancel: {}", e.getMessage());
            }
        }
    }

    /**
     * 下载文件的核心方法
     */
    private File downloadFile(String downloadUrl, DownloadProgressCallback callback) throws IOException {
        URL url = new URL(downloadUrl);
        String fileName = downloadUrl.substring(downloadUrl.lastIndexOf('/') + 1);
        File tempFile = File.createTempFile("EasyPostman-", "-" + fileName);

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);  // 连接超时 5000 秒
            conn.setReadTimeout(10000);     // 单次 read() 超时 10 秒（网速慢但连上时不超时）
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; EasyPostman-Updater)");
            conn.setRequestProperty("Accept", "*/*");

            // 暴露给 cancel()，使其能 disconnect() 中断阻塞的 getResponseCode()/read()
            activeConnection = conn;

            // 如果在建立连接前就已取消，提前退出
            if (cancelled) {
                cleanupTempFile(tempFile);
                callback.onCancelled();
                return null;
            }

            // 检查响应码（网络差时会阻塞，cancel() 通过 disconnect() 中断）
            int responseCode;
            try {
                responseCode = conn.getResponseCode();
            } catch (IOException e) {
                // disconnect() 触发的 SocketException / IOException
                if (cancelled) {
                    cleanupTempFile(tempFile);
                    callback.onCancelled();
                    return null;
                }
                throw e;
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP error code: " + responseCode);
            }

            long totalSize = conn.getContentLengthLong();
            log.info("Starting download: {} bytes from {}", totalSize, downloadUrl);

            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 FileOutputStream out = new FileOutputStream(tempFile)) {

                long downloaded = performDownload(in, out, totalSize, callback);

                if (cancelled) {
                    cleanupTempFile(tempFile);
                    callback.onCancelled();
                    return null;
                }

                log.info("Download completed: {} bytes", downloaded);
                callback.onCompleted(tempFile);
                return tempFile;
            } catch (IOException e) {
                // disconnect() 触发的流读取中断
                if (cancelled) {
                    cleanupTempFile(tempFile);
                    callback.onCancelled();
                    return null;
                }
                throw e;
            }

        } finally {
            activeConnection = null; // 清空，避免野指针
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception e) {
                    log.debug("Failed to disconnect connection: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 执行实际的下载操作
     */
    private long performDownload(InputStream in, FileOutputStream out, long totalSize,
                                 DownloadProgressCallback callback) throws IOException {
        byte[] buffer = new byte[8192];
        long downloaded = 0;
        int bytesRead;
        long startTime = System.currentTimeMillis();
        long lastProgressTime = 0;

        while ((bytesRead = in.read(buffer)) != -1 && !cancelled) {
            out.write(buffer, 0, bytesRead);
            downloaded += bytesRead;

            // 限制进度回调频率，避免UI卡顿
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastProgressTime > 100) { // 每100ms更新一次
                double elapsedSeconds = (currentTime - startTime) / 1000.0;
                double speed = elapsedSeconds > 0 ? downloaded / elapsedSeconds : 0;
                int percentage = totalSize > 0 ? (int) ((downloaded * 100L) / totalSize) : 0;

                callback.onProgress(percentage, downloaded, totalSize, speed);
                lastProgressTime = currentTime;
            }
        }

        return downloaded;
    }

    /**
     * 清理临时文件
     */
    private void cleanupTempFile(File tempFile) {
        try {
            java.nio.file.Files.delete(tempFile.toPath());
        } catch (IOException e) {
            log.warn("Failed to delete temporary file: {}", e.getMessage());
        }
    }

    /**
     * 将技术异常转换为用户友好的错误消息
     */
    private String getFriendlyErrorMessage(Exception ex) {
        if (ex instanceof java.net.SocketTimeoutException) {
            return I18nUtil.getMessage(MessageKeys.ERROR_NETWORK_TIMEOUT);
        } else if (ex instanceof java.net.UnknownHostException) {
            return I18nUtil.getMessage(MessageKeys.ERROR_SERVER_UNREACHABLE);
        } else if (ex instanceof java.io.FileNotFoundException) {
            return I18nUtil.getMessage(MessageKeys.ERROR_INVALID_DOWNLOAD_LINK);
        } else if (ex instanceof java.io.IOException) {
            String message = ex.getMessage();
            if (message != null) {
                if (message.contains("No space left on device")) {
                    return I18nUtil.getMessage(MessageKeys.ERROR_DISK_SPACE_INSUFFICIENT);
                } else if (message.contains("Permission denied")) {
                    return I18nUtil.getMessage(MessageKeys.ERROR_PERMISSION_DENIED);
                }
            }
            return I18nUtil.getMessage(MessageKeys.ERROR_IO_EXCEPTION, message);
        }
        return I18nUtil.getMessage(MessageKeys.UPDATE_DOWNLOAD_FAILED, ex.getMessage());
    }

    /**
     * 安装下载的文件（静默安装）
     */
    public void installUpdate(File installerFile, Consumer<Boolean> callback) {
        try {
            String fileName = installerFile.getName().toLowerCase();

            // Windows EXE 安装包 - 支持静默安装
            if (fileName.endsWith(".exe")) {
                installWindowsExe(installerFile, callback);
                return;
            }

            // 其他文件类型（DMG、DEB、ZIP 等）使用系统默认方式打开
            log.info("Opening installer with default application: {}", installerFile.getAbsolutePath());
            Desktop.getDesktop().open(installerFile);

            // 全量更新：打开安装程序后，延迟退出当前应用
            scheduleApplicationExit();
            callback.accept(true);
        } catch (Exception e) {
            log.error("Failed to open installer", e);
            callback.accept(false);
        }
    }

    /**
     * 安装 Windows EXE 安装包（支持静默安装）
     */
    private void installWindowsExe(File exeFile, Consumer<Boolean> callback) {
        try {
            // 检查应用是否已安装（通过注册表）
            boolean isInstalled = checkIfAppInstalled();

            ProcessBuilder pb;
            if (isInstalled) {
                // 已安装：使用静默安装 + 自动启动 + 强制关闭占用文件的应用
                log.info("App is already installed, using silent install with auto-start");
                pb = new ProcessBuilder(
                        exeFile.getAbsolutePath(),
                        "/VERYSILENT",      // 静默安装（不显示任何界面）
                        "/AUTOSTART",       // 安装完成后自动启动（自定义参数）
                        "/NORESTART",       // 不重启系统
                        "/FORCECLOSEAPPLICATIONS"  // 强制关闭占用文件的应用
                );
            } else {
                // 未安装：使用交互式安装
                log.info("App is not installed, using interactive install");
                pb = new ProcessBuilder(exeFile.getAbsolutePath());
            }

            pb.start();
            log.info("Started EXE installer: {}", exeFile.getAbsolutePath());

            // 延迟退出当前应用，让安装程序接管
            scheduleApplicationExit();
            callback.accept(true);
        } catch (Exception e) {
            log.error("Failed to start EXE installer", e);
            callback.accept(false);
        }
    }

    private boolean checkIfAppInstalled() {
        // 优先使用注册表检查（最准确）
        boolean installedInRegistry = WindowsRegistryChecker.isAppInstalled();
        if (installedInRegistry) {
            log.info("App is installed (verified via registry)");
            String installedVersion = WindowsRegistryChecker.getInstalledVersion();
            if (installedVersion != null) {
                log.info("Currently installed version: {}", installedVersion);
            }
            return true;
        }
        return false;
    }


    /**
     * 调度应用退出（优雅关闭）
     * 与窗口关闭、菜单退出共用保存确认和资源清理流程。
     */
    private void scheduleApplicationExit() {
        log.info("Preparing to exit application for update installation...");
        SwingUtilities.invokeLater(() -> BeanFactory.getBean(AppExitCoordinator.class).exitApplication());
    }
}
