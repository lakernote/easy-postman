package com.laker.postman.http.runtime.okhttp;

import com.laker.postman.http.runtime.error.DownloadCancelledException;
import com.laker.postman.http.runtime.interaction.DownloadProgressSink;
import com.laker.postman.http.runtime.interaction.DownloadProgressSinkFactory;
import com.laker.postman.http.runtime.interaction.ResponseSizeLimitWarning;
import com.laker.postman.http.runtime.interaction.ResponseSizeLimitWarningSink;
import com.laker.postman.http.runtime.config.HttpRuntimeSettingsProvider;
import com.laker.postman.http.runtime.file.ResponseFileNames;
import com.laker.postman.http.runtime.file.ResponseFileTypeDetector;
import com.laker.postman.http.runtime.file.ResponseFiles;
import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.http.runtime.sse.SseEventReader;
import com.laker.postman.http.runtime.sse.SseResponseCallback;
import com.laker.postman.util.FileExtensionUtil;
import com.laker.postman.util.HttpHeaderConstants;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * OkHttp 响应处理工具类
 * <p>
 * 负责处理 HTTP 响应，包括：
 * - 区分文本和二进制响应
 * - 处理大文件下载
 * - 解析文件名
 * - 字符集检测
 * - 临时文件管理
 * </p>
 */
@Slf4j
public class OkHttpResponseHandler {

    // 常量定义
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String SSE_CONTENT_TYPE = "text/event-stream";

    /**
     * 私有构造函数，防止实例化
     */
    private static final String UNKNOWN_BINARY_EXTENSION = ".bin";
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".png", ".jpg", ".gif", ".webp");

    private OkHttpResponseHandler() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 获取最大响应体大小设置（字节）
     */
    private static int getMaxBodySize() {
        return HttpRuntimeSettingsProvider.get().getMaxBodySize();
    }

    /**
     * 获取最大下载大小设置（字节）
     */
    private static int getMaxDownloadSize() {
        return HttpRuntimeSettingsProvider.get().getMaxDownloadSize();
    }

    /**
     * 处理 HTTP 响应，将 OkHttp Response 转换为内部 HttpResponse 对象
     *
     * @param okResponse OkHttp 的响应对象
     * @param response   内部响应对象（用于填充数据）
     * @throws IOException 读取响应体时可能抛出的异常
     */
    public static void handleResponse(Response okResponse, HttpResponse response, SseResponseCallback callback) throws IOException {
        handleResponse(okResponse, response, callback, PreparedRequest.ResponseBodyMode.FULL, 64 * 1024);
    }

    public static void handleResponse(Response okResponse,
                                      HttpResponse response,
                                      SseResponseCallback callback,
                                      PreparedRequest.ResponseBodyMode bodyMode,
                                      int previewLimitBytes) throws IOException {
        handleResponse(
                okResponse,
                response,
                callback,
                bodyMode,
                previewLimitBytes,
                DownloadProgressSinkFactory.noop(),
                ResponseSizeLimitWarningSink.noop()
        );
    }

    public static void handleResponse(Response okResponse,
                                      HttpResponse response,
                                      SseResponseCallback callback,
                                      PreparedRequest.ResponseBodyMode bodyMode,
                                      int previewLimitBytes,
                                      DownloadProgressSinkFactory downloadProgressSinkFactory,
                                      ResponseSizeLimitWarningSink responseSizeLimitWarningSink) throws IOException {
        handleResponse(okResponse, response, callback, bodyMode, previewLimitBytes,
                downloadProgressSinkFactory, responseSizeLimitWarningSink, null);
    }

    public static void handleResponse(Response okResponse,
                                      HttpResponse response,
                                      SseResponseCallback callback,
                                      PreparedRequest.ResponseBodyMode bodyMode,
                                      int previewLimitBytes,
                                      DownloadProgressSinkFactory downloadProgressSinkFactory,
                                      ResponseSizeLimitWarningSink responseSizeLimitWarningSink,
                                      Call call) throws IOException {
        try {
            readResponse(okResponse, response, callback, bodyMode, previewLimitBytes,
                    downloadProgressSinkFactory, responseSizeLimitWarningSink, call);
        } finally {
            if (okResponse.body() != null) {
                okResponse.close();
            }
        }
    }

    private static void readResponse(Response okResponse,
                                     HttpResponse response,
                                     SseResponseCallback callback,
                                     PreparedRequest.ResponseBodyMode bodyMode,
                                     int previewLimitBytes,
                                     DownloadProgressSinkFactory downloadProgressSinkFactory,
                                     ResponseSizeLimitWarningSink responseSizeLimitWarningSink,
                                     Call call) throws IOException {
        response.code = okResponse.code();
        response.headers = new LinkedHashMap<>();
        for (String name : okResponse.headers().names()) {
            // 使用 headers(name) 获取所有同名的 header 值（例如多个 Set-Cookie）
            List<String> values = okResponse.headers(name);
            if (!values.isEmpty()) {
                response.addHeader(name, values);
            }
        }
        response.headersSize = response.httpEventInfo != null ? response.httpEventInfo.getHeaderBytesReceived() : 0;
        response.threadName = Thread.currentThread().getName();
        response.protocol = okResponse.protocol().toString();
        String contentType = okResponse.header(CONTENT_TYPE_HEADER, "");
        int contentLengthHeader = parseContentLength(okResponse);

        if (isSSEContent(contentType)) {
            handleSseResponse(response);
            if (callback != null) {
                ResponseBody body = okResponse.body();
                callback.onOpen(response);
                boolean failed = false;
                if (body != null) {
                    var reader = new SseEventReader(body.source(), callback);
                    try {
                        while (reader.processNextEvent()) {
                            // 持续读取直到流结束
                        }
                    } catch (RuntimeException ex) {
                        failed = true;
                        callback.onFailure(ex.getMessage(), response);
                    } catch (IOException ex) {
                        failed = true;
                        callback.onFailure(ex.getMessage(), response);
                    }
                }
                if (!failed) {
                    callback.onClosed(response);
                }
            }
        } else if (bodyMode != null && bodyMode != PreparedRequest.ResponseBodyMode.FULL) {
            handleLightweightResponse(okResponse, response, bodyMode, previewLimitBytes);
        } else if (FileExtensionUtil.isBinaryType(contentType)) {
            handleBinaryResponse(okResponse, response, downloadProgressSinkFactory, responseSizeLimitWarningSink, call);
        } else {
            handleTextResponse(okResponse, response, contentLengthHeader, downloadProgressSinkFactory,
                    responseSizeLimitWarningSink, call);
        }
    }

    private static void handleLightweightResponse(Response okResponse,
                                                  HttpResponse response,
                                                  PreparedRequest.ResponseBodyMode bodyMode,
                                                  int previewLimitBytes) throws IOException {
        ResponseBody body = okResponse.body();
        if (body == null) {
            response.body = "";
            response.bodySize = 0;
            response.filePath = null;
            return;
        }

        int maxDownloadSize = getMaxDownloadSize();
        if (maxDownloadSize > 0 && parseContentLength(okResponse) > maxDownloadSize) {
            body.close();
            markLightweightBodyTooLarge(response, maxDownloadSize);
            return;
        }

        int limit = Math.max(0, previewLimitBytes);
        boolean keepPreview = bodyMode == PreparedRequest.ResponseBodyMode.PREVIEW
                && limit > 0
                && !FileExtensionUtil.isBinaryType(okResponse.header(CONTENT_TYPE_HEADER, ""));
        ByteArrayOutputStream preview = keepPreview ? new ByteArrayOutputStream(Math.min(limit, 64 * 1024)) : null;
        byte[] buffer = new byte[64 * 1024];
        long totalBytes = 0;

        try (InputStream inputStream = body.byteStream()) {
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                if (keepPreview && preview.size() < limit) {
                    int allowed = Math.min(len, limit - preview.size());
                    preview.write(buffer, 0, allowed);
                }
                totalBytes += len;
                if (maxDownloadSize > 0 && totalBytes > maxDownloadSize) {
                    markLightweightBodyTooLarge(response, maxDownloadSize);
                    return;
                }
            }
        } catch (IOException e) {
            if (isIncompleteResponseBodyError(e)) {
                log.error("Failed to read complete response body preview: {}", e.getMessage());
                response.body = I18nUtil.getMessage(MessageKeys.RESPONSE_INCOMPLETE, e.getMessage());
                response.bodyIncomplete = true;
                response.bodySize = totalBytes;
                response.filePath = null;
                return;
            }
            log.error("Error reading response body preview: {}", e.getMessage(), e);
            throw e;
        }

        response.bodySize = totalBytes;
        response.filePath = null;
        String extension = FileExtensionUtil.guessExtension(okResponse.header(CONTENT_TYPE_HEADER));
        response.fileName = FileExtensionUtil.generateSmartFileName(extension != null ? extension : ".txt");

        if (!keepPreview) {
            response.body = I18nUtil.getMessage(MessageKeys.RESPONSE_BODY_SKIPPED_PERFORMANCE, totalBytes);
            return;
        }

        Charset charset = resolveCharset(body);
        response.body = new String(preview.toByteArray(), charset);
        // Keep the body a clean prefix: callers evaluate assertions/extractors on it before deciding how to display it.
        response.bodyPreviewTruncated = totalBytes > preview.size();
    }

    private static void markLightweightBodyTooLarge(HttpResponse response, int maxDownloadSize) {
        response.body = I18nUtil.getMessage(MessageKeys.TEXT_TOO_LARGE_BODY, maxDownloadSize / 1024 / 1024);
        response.bodySize = 0;
        response.filePath = null;
    }

    private static Charset resolveCharset(ResponseBody body) {
        MediaType mediaType = body.contentType();
        if (mediaType != null) {
            Charset detectedCharset = mediaType.charset();
            if (detectedCharset != null) {
                return detectedCharset;
            }
        }
        return StandardCharsets.UTF_8;
    }

    /**
     * 处理 SSE（Server-Sent Events）类型的响应
     * SSE 是流式响应，不读取完整内容
     *
     * @param response 内部响应对象
     */
    private static void handleSseResponse(HttpResponse response) {
        response.body = I18nUtil.getMessage(MessageKeys.SSE_STREAM_UNSUPPORTED);
        response.bodySize = 0;
        response.isSse = true;
        // 不在这里关闭，统一在 handleResponse 方法中关闭
    }

    /**
     * 解析 Content-Length 响应头
     *
     * @param contentLengthStr Content-Length 响应头的值
     * @return 内容长度（字节），如果无法解析则返回 -1
     */
    private static int parseContentLength(String contentLengthStr) {
        if (contentLengthStr != null) {
            try {
                return Integer.parseInt(contentLengthStr);
            } catch (NumberFormatException ignore) {
                // 忽略解析失败，返回-1表示未知长度
            }
        }
        return -1;
    }

    /**
     * 读取响应中的内容长度：
     * 优先读 Content-Length，若被 CompressionDecompressNetworkInterceptor 移除则
     * 回退到 Easy-Content-Length（存储的是压缩前的原始大小，可作为近似估算）
     */
    private static int parseContentLength(Response okResponse) {
        String contentLength = okResponse.header(HttpHeaderConstants.CONTENT_LENGTH);
        if (contentLength == null) {
            contentLength = okResponse.header(HttpHeaderConstants.EASY_CONTENT_LENGTH);
        }
        return parseContentLength(contentLength);
    }

    /**
     * 判断是否为 SSE（Server-Sent Events）类型的响应
     *
     * @param contentType Content-Type 响应头的值
     * @return 如果是 SSE 类型返回 true，否则返回 false
     */
    private static boolean isSSEContent(String contentType) {
        if (contentType == null) return false;
        String ct = contentType.toLowerCase();
        return ct.contains(SSE_CONTENT_TYPE);
    }

    private static boolean isIncompleteResponseBodyError(IOException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof EOFException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("unexpected end of stream")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }


    /**
     * 保存输入流到临时文件，返回文件对象和写入的字节数
     * <p>
     * 该方法会显示下载进度对话框，支持用户取消下载。
     * 如果下载被取消或发生异常，会自动删除临时文件。
     * </p>
     *
     * @param is                  输入流
     * @param prefix              临时文件名前缀
     * @param suffix              临时文件名后缀（扩展名）
     * @param contentLengthHeader Content-Length 响应头的值（用于显示进度）
     * @return FileAndSize 对象，包含临时文件和实际写入的字节数
     * @throws IOException 读写文件时可能抛出的异常
     */
    private static FileAndSize saveInputStreamToTempFile(InputStream is,
                                                         String prefix,
                                                         String suffix,
                                                         int contentLengthHeader,
                                                         DownloadProgressSinkFactory downloadProgressSinkFactory,
                                                         boolean streamingMedia,
                                                         String sourceUrl,
                                                         Call call) throws IOException {
        DownloadProgressSink progressSink = createDownloadProgressSink(downloadProgressSinkFactory);
        File tempFile = File.createTempFile(prefix, suffix);
        long totalBytes = 0;
        int maxDownloadSize = getMaxDownloadSize();
        byte[] buf = new byte[64 * 1024];
        boolean completed = false;
        try {
            progressSink.start(contentLengthHeader, streamingMedia, sourceUrl, call == null ? null : call::cancel);
            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(tempFile), 64 * 1024)) {
                while (true) {
                    checkDownloadCancelled(progressSink, call);
                    int len = is.read(buf);
                    checkDownloadCancelled(progressSink, call);
                    if (len == -1) {
                        break;
                    }
                    long receivedBytes = totalBytes + len;
                    if (maxDownloadSize > 0 && receivedBytes > maxDownloadSize) {
                        throw new DownloadSizeLimitException(receivedBytes, maxDownloadSize);
                    }
                    bos.write(buf, 0, len);
                    totalBytes = receivedBytes;
                    progressSink.updateProgress(len);
                }
            }
            checkDownloadCancelled(progressSink, call);
            completed = true;
            return new FileAndSize(tempFile, totalBytes);
        } catch (IOException e) {
            // A cancelled socket read is a user action, not a network failure.
            if (e instanceof DownloadCancelledException || isDownloadCancelled(progressSink, call)) {
                throw new DownloadCancelledException(totalBytes);
            }
            throw e;
        } finally {
            if (!completed) {
                // The output stream is closed before deleting, including on Windows.
                deleteTempFile(tempFile);
            }
            progressSink.finish(completed, isDownloadCancelled(progressSink, call));
        }
    }

    private static void checkDownloadCancelled(DownloadProgressSink sink, Call call) throws DownloadCancelledException {
        if (isDownloadCancelled(sink, call)) {
            throw new DownloadCancelledException();
        }
    }

    private static boolean isDownloadCancelled(DownloadProgressSink sink, Call call) {
        return sink.isCancelled() || (call != null && call.isCanceled());
    }

    private static DownloadProgressSink createDownloadProgressSink(DownloadProgressSinkFactory factory) {
        DownloadProgressSinkFactory resolvedFactory = factory == null ? DownloadProgressSinkFactory.noop() : factory;
        DownloadProgressSink sink = resolvedFactory.create();
        return sink == null ? DownloadProgressSink.NOOP : sink;
    }

    /**
     * 安全删除临时文件
     *
     * @param tempFile 要删除的临时文件
     */
    private static void deleteTempFile(File tempFile) {
        if (tempFile != null && tempFile.exists()) {
            boolean deleted = tempFile.delete();
            if (!deleted) {
                log.warn("Failed to delete temp file: {}", tempFile.getAbsolutePath());
                // 标记为退出时删除
                tempFile.deleteOnExit();
            }
        }
    }

    /**
     * 处理二进制类型的响应（图片、PDF、压缩包等）
     * <p>
     * 二进制响应会被保存到临时文件，文件名按以下优先级获取：
     * 1. Content-Disposition 响应头中的文件名
     * 2. URL 路径中的文件名
     * 3. 根据 Content-Type 智能生成
     * </p>
     *
     * @param okResponse OkHttp 的响应对象
     * @param response   内部响应对象
     * @throws IOException 读取响应体时可能抛出的异常
     */
    private static void handleBinaryResponse(Response okResponse,
                                             HttpResponse response,
                                             DownloadProgressSinkFactory downloadProgressSinkFactory,
                                             ResponseSizeLimitWarningSink responseSizeLimitWarningSink,
                                             Call call) throws IOException {
        InputStream is = okResponse.body() != null ? okResponse.body().byteStream() : null;
        String ext = FileExtensionUtil.guessExtension(okResponse.header(CONTENT_TYPE_HEADER));
        response.fileName = resolveFileName(okResponse, response.headers, ext, (ResponseFileTypeDetector.Detection) null);
        String contentDisposition = ResponseFileNames.getContentDisposition(response.headers);
        response.fileAttachment = ResponseFileNames.isAttachment(contentDisposition);

        // 检查文件大小限制
        int maxDownloadSize = getMaxDownloadSize();
        int contentLengthHeader = parseContentLength(okResponse);
        if (maxDownloadSize > 0 && contentLengthHeader > maxDownloadSize) {
            if (is != null) is.close();
            warnResponseTooLarge(responseSizeLimitWarningSink,
                    new ResponseSizeLimitWarning(ResponseSizeLimitWarning.Kind.BINARY, contentLengthHeader, maxDownloadSize));
            response.body = I18nUtil.getMessage(MessageKeys.BINARY_TOO_LARGE_BODY, maxDownloadSize / 1024 / 1024);
            response.bodySize = 0;
            response.filePath = null;
            return;
        }
        if (is != null) {
            try {
                FileAndSize fs = saveInputStreamToTempFile(
                        is,
                        "easyPostman_download_",
                        ext,
                        contentLengthHeader,
                        downloadProgressSinkFactory,
                        isStreamingMediaResponse(okResponse, contentDisposition),
                        okResponse.request().url().toString(),
                        call
                );
                response.filePath = fs.file.getAbsolutePath();
                response.body = I18nUtil.getMessage(MessageKeys.BINARY_SAVED_TEMP_FILE);
                response.bodySize = fs.size;
                applyDetectedBinaryType(okResponse, response, fs.file, ext);
                // 标记是否为图片类型，供 UI 层预览使用
                String ct = okResponse.header(CONTENT_TYPE_HEADER, "");
                // 内容与声明冲突时以识别结果为准，避免把声明为 image/png 的 PDF 当作图片预览
                response.isImage = response.detectedFileExtension != null
                        ? IMAGE_EXTENSIONS.contains(response.detectedFileExtension)
                        : ct.toLowerCase(Locale.ROOT).startsWith("image/");
            } catch (DownloadSizeLimitException e) {
                warnResponseTooLarge(responseSizeLimitWarningSink,
                        new ResponseSizeLimitWarning(ResponseSizeLimitWarning.Kind.BINARY,
                                (int) Math.min(e.receivedBytes, Integer.MAX_VALUE), e.maxDownloadBytes));
                response.body = I18nUtil.getMessage(MessageKeys.BINARY_TOO_LARGE_BODY, e.maxDownloadBytes / 1024 / 1024);
                response.bodySize = 0;
                response.filePath = null;
            } catch (IOException e) {
                if (!isIncompleteResponseBodyError(e)) {
                    throw e;
                }
                // 处理下载过程中连接中断的情况
                log.error("Failed to download complete binary response: {}", e.getMessage());
                response.body = I18nUtil.getMessage(MessageKeys.RESPONSE_INCOMPLETE, e.getMessage());
                response.bodyIncomplete = true;
                response.bodySize = 0;
                response.filePath = null;
            }
        } else {
            response.body = I18nUtil.getMessage(MessageKeys.NO_RESPONSE_BODY);
            response.bodySize = 0;
        }
    }


    /**
     * 所有二进制响应都检查文件头：声明笼统（application/octet-stream）时采用识别结果；
     * 声明具体但与内容冲突（application/vnd.ms-excel 实际是 xlsx）时以内容为准并提示；
     * 一致或声明更具体（声明 .xlsm、文件头只能确定 .xlsx）时以声明为准。识别结果同时用于纠正 URL 中错误的后缀。
     */
    private static void applyDetectedBinaryType(Response okResponse, HttpResponse response, File file, String declaredExtension) {
        ResponseFileTypeDetector.Detection detection = ResponseFileTypeDetector.detect(file.toPath());
        if (detection == null) {
            return;
        }
        String detected = detection.extension();
        String contentType = okResponse.header(CONTENT_TYPE_HEADER, "");
        // application/zip 同样只说明是容器，docx/jar 等基于 ZIP 的具体格式不算类型冲突
        boolean genericDeclared = contentType.isBlank() || ".zip".equals(declaredExtension)
                || ResponseFiles.isGenericBinaryContentType(contentType);
        if (!genericDeclared && declaredExtension == null && ResponseFileTypeDetector.isGenericContainer(detected)) {
            // 具体但没有扩展名映射的 MIME（如 APK 的 application/vnd.android.package-archive），只识别到容器时信任声明
            return;
        }
        String naming = genericDeclared ? detected : ResponseFileTypeDetector.reconcile(declaredExtension, detection);
        // 只有内容与声明不一致时才提示：application/pdf + PDF、application/zip + ZIP/docx/jar 都不提示
        boolean consistent = naming.equalsIgnoreCase(String.valueOf(declaredExtension))
                || (".zip".equals(declaredExtension) && ResponseFileTypeDetector.reconcile(naming, ".zip").equals(naming));
        if (!consistent) {
            response.detectedFileExtension = detected;
        }
        response.fileName = resolveFileName(okResponse, response.headers, naming, detection);
    }

    private static boolean isStreamingMediaResponse(Response response, String contentDisposition) {
        String contentType = response.header(CONTENT_TYPE_HEADER, "");
        String mediaType = contentType.split(";", 2)[0].trim();
        String disposition = contentDisposition == null ? "" : contentDisposition.split(";", 2)[0].trim();
        // FLV may also be a finite file. This selects unknown-length transfer UX
        // without claiming the stream is live or changing the EOF completion rule.
        return "video/x-flv".equalsIgnoreCase(mediaType)
                && response.body() != null && response.body().contentLength() < 0
                && !"attachment".equalsIgnoreCase(disposition);
    }

    /**
     * 处理文本类型的响应（JSON、XML、HTML、纯文本等）
     * <p>
     * 文本响应的处理策略：
     * 1. 如果内容超过最大下载大小限制，显示警告并拒绝加载
     * 2. 如果内容超过最大显示大小限制，保存到临时文件
     * 3. 否则直接显示在编辑器中
     * </p>
     *
     * @param okResponse          OkHttp 的响应对象
     * @param response            内部响应对象
     * @param contentLengthHeader Content-Length 响应头的值
     * @throws IOException 读取响应体时可能抛出的异常
     */
    private static void handleTextResponse(Response okResponse,
                                           HttpResponse response,
                                           int contentLengthHeader,
                                           DownloadProgressSinkFactory downloadProgressSinkFactory,
                                           ResponseSizeLimitWarningSink responseSizeLimitWarningSink,
                                           Call call) throws IOException {
        String ext = FileExtensionUtil.guessExtension(okResponse.header(CONTENT_TYPE_HEADER));
        int maxDownloadSize = getMaxDownloadSize();
        ResponseBody body = okResponse.body();

        // 检查是否超过最大下载大小
        if (maxDownloadSize > 0 && contentLengthHeader > maxDownloadSize) {
            if (body != null) body.close();
            warnResponseTooLarge(responseSizeLimitWarningSink,
                    new ResponseSizeLimitWarning(ResponseSizeLimitWarning.Kind.TEXT, contentLengthHeader, maxDownloadSize));
            response.body = I18nUtil.getMessage(MessageKeys.TEXT_TOO_LARGE_BODY, maxDownloadSize / 1024 / 1024);
            response.bodySize = 0;
            response.filePath = null;
            return;
        }
        if (body != null) {
            byte[] bytes;
            try {
                bytes = body.bytes();
            } catch (IOException e) {
                if (isIncompleteResponseBodyError(e)) {
                    log.error("Failed to read complete response body: {}", e.getMessage());
                    response.body = I18nUtil.getMessage(MessageKeys.RESPONSE_INCOMPLETE, e.getMessage());
                    response.bodyIncomplete = true;
                    response.bodySize = 0;
                    response.filePath = null;
                    return;
                }
                // 处理其他 IO 异常
                log.error("Error reading response body: {}", e.getMessage(), e);
                throw e;
            }
            if (maxDownloadSize > 0 && bytes.length > maxDownloadSize) {
                warnResponseTooLarge(responseSizeLimitWarningSink,
                        new ResponseSizeLimitWarning(ResponseSizeLimitWarning.Kind.TEXT, bytes.length, maxDownloadSize));
                response.body = I18nUtil.getMessage(MessageKeys.TEXT_TOO_LARGE_BODY, maxDownloadSize / 1024 / 1024);
                response.bodySize = 0;
                response.filePath = null;
                return;
            }
            response.bodySize = bytes.length;
            String extension = applyFileMetadata(okResponse, response, bytes, ext);
            if (bytes.length > getMaxBodySize()) { // 如果解压后内容超过设置值，保存为临时文件
                FileAndSize fs = saveInputStreamToTempFile(
                        new ByteArrayInputStream(bytes),
                        "easyPostman_text_download_",
                        extension,
                        bytes.length,
                        downloadProgressSinkFactory,
                        false,
                        okResponse.request().url().toString(),
                        call
                );
                response.filePath = fs.file.getAbsolutePath();
                int maxBodySizeKB = getMaxBodySize() / 1024;
                response.body = I18nUtil.getMessage(MessageKeys.BODY_TOO_LARGE_SAVED, maxBodySizeKB);
            } else {
                decodeInMemoryBody(response, bytes, body.contentType());
                response.filePath = null;
            }
        } else {
            response.body = "";
            response.bodySize = 0;
            response.filePath = null;
        }
    }

    /**
     * 根据内容识别文件格式与附件声明，填充文件元信息并返回保存用扩展名。
     * 普通文本接口保持智能文件名；只有明确的文件响应才从 Content-Disposition / URL 推导文件名。
     */
    private static String applyFileMetadata(Response okResponse, HttpResponse response, byte[] bytes, String contentTypeExtension) {
        ResponseFileTypeDetector.Detection signature = ResponseFileTypeDetector.detect(bytes);
        String detectedExtension = signature == null ? null : signature.extension();
        if (detectedExtension == null
                && mayBeUnlabeledBinary(okResponse.header(CONTENT_TYPE_HEADER, ""), ResponseFiles.declaredCharset(response))
                && ResponseFileTypeDetector.looksBinary(bytes)) {
            // 未声明类型（或声明为 text/plain）却含有文本中不会出现的控制字节：按未知二进制文件处理
            detectedExtension = UNKNOWN_BINARY_EXTENSION;
        }
        response.detectedFileExtension = detectedExtension;
        response.fileAttachment = ResponseFileNames.isAttachment(
                ResponseFileNames.getContentDisposition(response.headers));
        String extension = detectedExtension != null
                ? detectedExtension
                : (contentTypeExtension != null ? contentTypeExtension : ".txt");
        response.fileName = detectedExtension != null || response.fileAttachment
                ? resolveFileName(okResponse, response.headers, extension, signature)
                : FileExtensionUtil.generateSmartFileName(extension);
        return extension;
    }

    /**
     * 解码内存中的响应体；仅在文本无法无损还原原始字节或响应是文件时保留原始字节，避免普通 UTF-8 文本内存翻倍。
     */
    /**
     * 控制字节判定只适用于 ASCII 兼容的编码；UTF-16/UTF-32 文本本身就含有大量 0x00，不能按此判断。
     */
    private static boolean mayBeUnlabeledBinary(String contentType, Charset declaredCharset) {
        String mediaType = contentType == null ? "" : contentType.split(";", 2)[0].trim();
        boolean unlabeledOrPlainText = mediaType.isEmpty() || "text/plain".equalsIgnoreCase(mediaType);
        return unlabeledOrPlainText && isAsciiCompatible(declaredCharset);
    }

    private static boolean isAsciiCompatible(Charset charset) {
        try {
            byte[] encoded = "\t\n\r A~".getBytes(charset);
            return Arrays.equals(encoded, "\t\n\r A~".getBytes(StandardCharsets.US_ASCII));
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }

    private static void decodeInMemoryBody(HttpResponse response, byte[] bytes, MediaType mediaType) {
        Charset charset = mediaType != null && mediaType.charset() != null
                ? mediaType.charset()
                : StandardCharsets.UTF_8;
        String strictText = decodeStrictly(bytes, charset);
        response.body = strictText != null ? strictText : new String(bytes, charset);
        boolean losslessUtf8Text = strictText != null && StandardCharsets.UTF_8.equals(charset);
        if (response.detectedFileExtension != null || response.fileAttachment || !losslessUtf8Text) {
            response.rawBodyBytes = bytes;
        }
    }

    private static String decodeStrictly(byte[] bytes, Charset charset) {
        try {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private static String resolveFileName(Response okResponse, Map<String, List<String>> headers,
                                          String extension, ResponseFileTypeDetector.Detection detection) {
        return ResponseFileNames.resolve(
                ResponseFileNames.getContentDisposition(headers),
                okResponse.request().url().encodedPath(),
                extension,
                detection);
    }

    private static void warnResponseTooLarge(ResponseSizeLimitWarningSink sink, ResponseSizeLimitWarning warning) {
        ResponseSizeLimitWarningSink resolvedSink = sink == null ? ResponseSizeLimitWarningSink.noop() : sink;
        resolvedSink.warn(warning);
    }

    /**
     * 文件和大小的简单封装类
     * 用于 saveInputStreamToTempFile 方法的返回值
     */
    private static class FileAndSize {
        /**
         * 临时文件
         */
        final File file;
        /**
         * 实际写入的字节数
         */
        final long size;

        /**
         * 构造函数
         *
         * @param file 临时文件
         * @param size 实际写入的字节数
         */
        FileAndSize(File file, long size) {
            this.file = file;
            this.size = size;
        }
    }

    private static final class DownloadSizeLimitException extends IOException {
        private final long receivedBytes;
        private final int maxDownloadBytes;

        private DownloadSizeLimitException(long receivedBytes, int maxDownloadBytes) {
            super("Response exceeded the download size limit");
            this.receivedBytes = receivedBytes;
            this.maxDownloadBytes = maxDownloadBytes;
        }
    }
}
