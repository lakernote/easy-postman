package com.laker.postman.http.runtime.okhttp;

import com.laker.postman.http.runtime.error.DownloadCancelledException;
import com.laker.postman.http.runtime.interaction.DownloadProgressSink;
import com.laker.postman.http.runtime.interaction.DownloadProgressSinkFactory;
import com.laker.postman.http.runtime.interaction.ResponseSizeLimitWarning;
import com.laker.postman.http.runtime.interaction.ResponseSizeLimitWarningSink;
import com.laker.postman.http.runtime.config.HttpRuntimeSettingsProvider;
import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.http.runtime.sse.SseEventReader;
import com.laker.postman.http.runtime.sse.SseResponseCallback;
import com.laker.postman.util.FileExtensionUtil;
import com.laker.postman.util.HttpHeaderConstants;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private static final String CONTENT_DISPOSITION_HEADER = "Content-Disposition";
    private static final String SSE_CONTENT_TYPE = "text/event-stream";

    /**
     * 私有构造函数，防止实例化
     */
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
            handleBinaryResponse(okResponse, response, downloadProgressSinkFactory, responseSizeLimitWarningSink);
        } else {
            handleTextResponse(okResponse, response, contentLengthHeader, downloadProgressSinkFactory, responseSizeLimitWarningSink);
        }
        if (okResponse.body() != null) {
            okResponse.body().close();
        }
        okResponse.close();
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
            }
        } catch (IOException e) {
            if (isIncompleteResponseBodyError(e)) {
                log.error("Failed to read complete response body preview: {}", e.getMessage());
                response.body = I18nUtil.getMessage(MessageKeys.RESPONSE_INCOMPLETE, e.getMessage());
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
        if (totalBytes > preview.size()) {
            response.body += I18nUtil.getMessage(
                    MessageKeys.RESPONSE_BODY_TRUNCATED_PERFORMANCE,
                    totalBytes,
                    preview.size()
            );
        }
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
                                                         DownloadProgressSinkFactory downloadProgressSinkFactory) throws IOException {
        File tempFile = File.createTempFile(prefix, suffix);
        int totalBytes = 0;
        byte[] buf = new byte[64 * 1024];
        int len;
        int contentLength = getContentLength(is, contentLengthHeader);

        DownloadProgressSink progressSink = createDownloadProgressSink(downloadProgressSinkFactory);
        progressSink.start(contentLength);

        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(tempFile), 64 * 1024)) {
            while ((len = is.read(buf)) != -1) {
                if (progressSink.isCancelled()) {
                    deleteTempFile(tempFile);
                    throw new DownloadCancelledException();
                }
                bos.write(buf, 0, len);
                totalBytes += len;
                progressSink.updateProgress(len);
            }
        } catch (DownloadCancelledException e) {
            // 用户取消下载，这是正常行为，直接向上抛出
            deleteTempFile(tempFile);
            throw e;
        } catch (IOException e) {
            // 其他IO异常，删除临时文件后抛出
            deleteTempFile(tempFile);
            throw e;
        } finally {
            progressSink.finish();
        }
        return new FileAndSize(tempFile, totalBytes);
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
     * 获取内容长度，优先使用响应头中的值，其次尝试从流中获取
     *
     * @param is                  输入流
     * @param contentLengthHeader Content-Length 响应头的值
     * @return 内容长度（字节），如果无法确定则返回 -1
     */
    private static int getContentLength(InputStream is, int contentLengthHeader) {
        int contentLength = contentLengthHeader;

        // 如果响应头没有，再从流获取
        if (contentLength < 0 && is instanceof FileInputStream) {
            try {
                contentLength = Math.toIntExact(((FileInputStream) is).getChannel().size());
            } catch (IOException ignored) {
                // 无法获取文件大小，继续尝试其他方法
            }
        }
        if (contentLength < 0) {
            try {
                contentLength = is.available();
            } catch (IOException ignored) {
                // 无法获取可用字节数，返回当前的contentLength（可能是-1）
            }
        }
        return contentLength;
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
                                             ResponseSizeLimitWarningSink responseSizeLimitWarningSink) throws IOException {
        InputStream is = okResponse.body() != null ? okResponse.body().byteStream() : null;
        String fileName = null;
        // 优先 Content-Disposition
        String contentDisposition = getContentDisposition(response.headers);
        if (contentDisposition != null) {
            fileName = parseFileNameFromContentDisposition(contentDisposition);
        }
        // 其次 URL 路径
        if (fileName == null) {
            String url = okResponse.request().url().encodedPath();
            int lastSlash = url.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < url.length() - 1) {
                String rawName = url.substring(lastSlash + 1);
                // 去除 !、?、# 及其后内容
                int excl = rawName.indexOf('!');
                int ques = rawName.indexOf('?');
                int sharp = rawName.indexOf('#');
                int cut = rawName.length();
                if (excl >= 0) cut = excl;
                if (ques >= 0 && ques < cut) cut = ques;
                if (sharp >= 0 && sharp < cut) cut = sharp;
                fileName = rawName.substring(0, cut);
            }
        }
        // 如果没有获取到文件名，或者文件名没有扩展名，尝试补充
        String ext = FileExtensionUtil.guessExtension(okResponse.header(CONTENT_TYPE_HEADER));
        if (fileName == null) {
            // 完全没有文件名，使用智能生成（根据 Content-Type）
            fileName = FileExtensionUtil.generateSmartFileName(ext);
        } else if (!fileName.contains(".") && ext != null) {
            // 有文件名但没有扩展名，根据 Content-Type 添加扩展名
            fileName += ext;
        }
        response.fileName = fileName;

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
                        downloadProgressSinkFactory
                );
                response.filePath = fs.file.getAbsolutePath();
                response.body = I18nUtil.getMessage(MessageKeys.BINARY_SAVED_TEMP_FILE);
                response.bodySize = fs.size;
                // 标记是否为图片类型，供 UI 层预览使用
                String ct = okResponse.header(CONTENT_TYPE_HEADER, "");
                response.isImage = ct != null && ct.toLowerCase().startsWith("image/");
            } catch (IOException e) {
                if (!isIncompleteResponseBodyError(e)) {
                    throw e;
                }
                // 处理下载过程中连接中断的情况
                log.error("Failed to download complete binary response: {}", e.getMessage());
                response.body = I18nUtil.getMessage(MessageKeys.RESPONSE_INCOMPLETE, e.getMessage());
                response.bodySize = 0;
                response.filePath = null;
            }
        } else {
            response.body = I18nUtil.getMessage(MessageKeys.NO_RESPONSE_BODY);
            response.bodySize = 0;
        }
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
                                           ResponseSizeLimitWarningSink responseSizeLimitWarningSink) throws IOException {
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
                    response.bodySize = 0;
                    response.filePath = null;
                    return;
                }
                // 处理其他 IO 异常
                log.error("Error reading response body: {}", e.getMessage(), e);
                throw e;
            }
            response.bodySize = bytes.length;
            if (bytes.length > getMaxBodySize()) { // 如果解压后内容超过设置值，保存为临时文件
                String extension = ext != null ? ext : ".txt";
                FileAndSize fs = saveInputStreamToTempFile(
                        new ByteArrayInputStream(bytes),
                        "easyPostman_text_download_",
                        extension,
                        contentLengthHeader,
                        downloadProgressSinkFactory
                );
                response.filePath = fs.file.getAbsolutePath();
                // 智能生成文件名：根据扩展名生成更友好的名称
                response.fileName = FileExtensionUtil.generateSmartFileName(extension);
                int maxBodySizeKB = getMaxBodySize() / 1024;
                response.body = I18nUtil.getMessage(MessageKeys.BODY_TOO_LARGE_SAVED, maxBodySizeKB);
            } else {
                // 确定字符集
                Charset charset = StandardCharsets.UTF_8; // 默认使用 UTF-8
                MediaType mediaType = body.contentType();
                if (mediaType != null) {
                    Charset detectedCharset = mediaType.charset();
                    if (detectedCharset != null) {
                        charset = detectedCharset;
                    }
                }
                response.body = new String(bytes, charset);
                response.filePath = null;
                // 即使不保存为文件，也设置一个默认文件名，方便用户下载
                String extension = ext != null ? ext : ".txt";
                response.fileName = FileExtensionUtil.generateSmartFileName(extension);
            }
        } else {
            response.body = "";
            response.bodySize = 0;
            response.filePath = null;
        }
    }

    private static void warnResponseTooLarge(ResponseSizeLimitWarningSink sink, ResponseSizeLimitWarning warning) {
        ResponseSizeLimitWarningSink resolvedSink = sink == null ? ResponseSizeLimitWarningSink.noop() : sink;
        resolvedSink.warn(warning);
    }

    /**
     * 从响应头中提取 Content-Disposition 字段
     * <p>
     * Content-Disposition 响应头通常包含文件下载时的文件名信息
     * </p>
     *
     * @param headers 响应头 Map
     * @return Content-Disposition 的值，如果不存在则返回 null
     */
    public static String getContentDisposition(Map<String, List<String>> headers) {
        if (headers == null) return null;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && CONTENT_DISPOSITION_HEADER.equalsIgnoreCase(entry.getKey())) {
                List<String> values = entry.getValue();
                // 修复：检查 list 不为空，避免 IndexOutOfBoundsException
                if (values != null && !values.isEmpty()) {
                    return values.get(0);
                }
            }
        }
        return null;
    }

    /**
     * 从 Content-Disposition 响应头中解析文件名
     * <p>
     * 支持两种格式：
     * 1. filename*=UTF-8''filename.txt (RFC 5987 编码格式)
     * 2. filename="filename.txt" (普通格式)
     * </p>
     *
     * @param contentDisposition Content-Disposition 响应头的值
     * @return 解析出的文件名，如果无法解析则返回 null
     */
    public static String parseFileNameFromContentDisposition(String contentDisposition) {
        if (contentDisposition == null) return null;
        String lower = contentDisposition.toLowerCase();

        // 优先解析 filename*= (RFC 5987 编码格式)
        int idxStar = lower.indexOf("filename*=");
        if (idxStar >= 0) {
            // 修复：偏移量应该是 10 ("filename*=" 的长度)
            String fn = contentDisposition.substring(idxStar + 10).trim();
            // 跳过编码声明部分 (如 UTF-8'')
            int firstQuote = fn.indexOf("''");
            if (firstQuote >= 0) {
                fn = fn.substring(firstQuote + 2);
            } else {
                // 如果没有编码声明，直接去除分号后的内容
                int semi = fn.indexOf(';');
                if (semi > 0) fn = fn.substring(0, semi);
            }
            // 再次去除可能的分号
            int semi = fn.indexOf(';');
            if (semi > 0) fn = fn.substring(0, semi);
            return fn.trim();
        }

        // 解析普通的 filename= 格式
        int idx = lower.indexOf("filename=");
        if (idx >= 0) {
            String fn = contentDisposition.substring(idx + 9).trim();
            // 去除引号
            if (fn.startsWith("\"")) fn = fn.substring(1);
            int end = fn.indexOf('"');
            if (end >= 0) {
                fn = fn.substring(0, end);
            } else {
                // 没有引号，去除分号后的内容
                int semi = fn.indexOf(';');
                if (semi > 0) fn = fn.substring(0, semi);
            }
            return fn.trim();
        }
        return null;
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
        final int size;

        /**
         * 构造函数
         *
         * @param file 临时文件
         * @param size 实际写入的字节数
         */
        FileAndSize(File file, int size) {
            this.file = file;
            this.size = size;
        }
    }
}
