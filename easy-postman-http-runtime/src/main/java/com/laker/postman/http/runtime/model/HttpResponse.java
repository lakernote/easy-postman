package com.laker.postman.http.runtime.model;

import com.laker.postman.http.runtime.file.ResponseFiles;
import com.laker.postman.util.HttpHeaderConstants;

import java.util.List;
import java.util.Map;

/**
 * HTTP 响应类
 */
public class HttpResponse {
    public Map<String, List<String>> headers;
    public String body;
    /**
     * 原始响应体字节，仅在完整读取、未落临时文件，且识别为文件/附件或文本无法无损还原原始字节时保留。
     * 普通 UTF-8 文本响应不保留，{@link #body} 按 UTF-8 编码即可还原原始字节。
     * 下载响应时必须优先使用该字段，避免预览文本的字符集转换破坏原始内容。
     */
    public transient byte[] rawBodyBytes;
    /**
     * 根据响应体特征高置信识别出的文件扩展名（包含点），未识别时为 null。
     */
    public transient String detectedFileExtension;
    /**
     * Content-Disposition 声明为 attachment，说明服务端希望以文件方式下载（inline 不算）。
     */
    public transient boolean fileAttachment;
    /**
     * 文件响应的内容未被保留（如从只保存元信息的示例中恢复），此时只能展示文件信息，无法下载。
     */
    public transient boolean fileContentOmitted;
    public int code; // 添加响应状态码字段
    public String threadName; // 添加线程名称字段
    public String filePath; // 临时文件下载路径字段
    public String fileName; // 如果是文件下载，从响应头中获取的文件名字段
    public long costMs; // 请求耗时，单位毫秒
    public long endTime; // 响应结束时间，单位毫秒
    public String protocol; // 协议类型字段，例如 HTTP/1.1 或 HTTP/2
    public int idleConnectionCount; // 空闲连接数
    public int connectionCount; // 连接总数
    public HttpEventInfo httpEventInfo;
    public long bodySize; // 响应体字节数
    public long headersSize; // 响应头字节数
    public boolean isSse = false; // 是否为SSE响应
    public boolean isImage = false; // 是否为图片响应（用于预览）

    public void addHeader(String name, List<String> value) {
        if (headers == null) {
            return;
        }
        if (HttpHeaderConstants.EASY_CONTENT_ENCODING.equalsIgnoreCase(name)) {
            headers.put(HttpHeaderConstants.CONTENT_ENCODING, value);
            headers.remove(HttpHeaderConstants.EASY_CONTENT_ENCODING);
        } else if (HttpHeaderConstants.EASY_CONTENT_LENGTH.equalsIgnoreCase(name)) {
            headers.put(HttpHeaderConstants.CONTENT_LENGTH, value);
            headers.remove(HttpHeaderConstants.EASY_CONTENT_LENGTH);
        } else {
            headers.put(name, value);
        }
    }

    /**
     * 响应体被识别为二进制文件（或文件内容已被省略），其 {@link #body} 文本不适合直接展示。
     */
    public boolean isRecognizedFile() {
        return detectedFileExtension != null || fileContentOmitted;
    }

    /**
     * 简化对象，将渲染时不需要的字段置为 null，减少内存占用。
     * 保留的字段：code, protocol, threadName, httpEventInfo, headers, body, costMs, endTime, bodySize, headersSize,
     * idleConnectionCount, connectionCount, detectedFileExtension, fileAttachment；
     * 置为 null 的字段：filePath, rawBodyBytes；fileName 保留用于展示文件信息。
     * 文件响应（见 {@link ResponseFiles#isFileResponse}）不保留按文本解码出的乱码或临时文件提示，
     * 并标记内容已省略，渲染时只展示文件信息。
     */
    public void simplify() {
        // 正确声明 MIME 的二进制文件（PDF/PNG 等）不会设置 detectedFileExtension，同样按文件处理
        boolean file = ResponseFiles.isFileResponse(this);
        this.filePath = null;    // 文件下载路径，渲染时不需要
        this.rawBodyBytes = null; // 原始响应体字节，渲染时不需要
        if (file) {
            this.body = "";
            this.fileContentOmitted = true;
        }
        // isSse, idleConnectionCount, connectionCount 保留（Timing标签页可能需要）
    }
}
