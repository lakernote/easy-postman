package com.laker.postman.request.model;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 保存的响应对象
 * 类似 Postman 中保存的 Example 响应
 */
@Data
public class SavedResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id; // 响应唯一标识
    private String name; // 响应名称
    private long timestamp; // 保存时间戳

    // 原始请求信息（用于显示和对比）
    private OriginalRequest originalRequest;

    // 响应信息
    private int code; // 状态码
    private String status; // 状态描述，如 "OK"
    private List<HttpHeader> headers = new ArrayList<>(); // 响应头
    private List<CookieInfo> cookies = new ArrayList<>(); // Cookies
    private String body; // 响应体
    private String previewLanguage; // 预览语言类型，如 "json", "html", "xml" 等

    // 本地 Mock 扩展。为空时保持普通静态 Example 行为。
    private int mockDelayMs;
    private String mockScript = "";

    // 性能信息
    private long costMs; // 请求耗时（毫秒）
    private long bodySize; // 响应体大小（字节）
    private long headersSize; // 响应头大小（字节）

    /**
     * 原始请求信息（保存的快照）
     */
    @Data
    public static class OriginalRequest implements Serializable {
        private static final long serialVersionUID = 1L;

        private String method; // 请求方法
        private String url; // 请求URL
        private List<HttpHeader> headers = new ArrayList<>(); // 请求头
        private List<HttpParam> pathVariables = new ArrayList<>(); // Path Variables
        private List<HttpParam> params = new ArrayList<>(); // Query 参数
        private String bodyType; // 请求体类型
        private String body; // 请求体内容
        private boolean bodyTruncated; // 请求体内容是否仅保存了预览
        private long originalBodySize; // 原始请求体大小（字节）
        private List<HttpFormData> formDataList = new ArrayList<>(); // FormData
        private List<HttpFormUrlencoded> urlencodedList = new ArrayList<>(); // URL编码表单
    }

}
