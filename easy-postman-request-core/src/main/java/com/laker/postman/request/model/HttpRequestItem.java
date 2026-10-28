package com.laker.postman.request.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * HttpRequestItem 类表示一个HTTP请求的配置项
 * 包含请求的基本信息、头部、参数、认证等
 * 每个参数都要求有默认值，便于前端展示和编辑
 */
@Setter
@Getter
public class HttpRequestItem implements Serializable {
    public static final String HTTP_VERSION_AUTO = HttpRequestVersions.AUTO;
    public static final String HTTP_VERSION_HTTP_1_1 = HttpRequestVersions.HTTP_1_1;
    public static final String HTTP_VERSION_HTTP_2 = HttpRequestVersions.HTTP_2;
    public static final int DEFAULT_WEBSOCKET_PING_INTERVAL_MS = 30_000;
    public static final int DISABLED_WEBSOCKET_PING_INTERVAL_MS = 0;

    private String id = ""; // 唯一标识符
    private String name = ""; // 请求名称
    private String description = ""; // 请求描述（支持Markdown）
    private String url = ""; // 请求URL
    private String method = "GET"; // 请求方法（GET, POST, PUT, DELETE等）
    private RequestItemProtocolEnum protocol = RequestItemProtocolEnum.HTTP; // 协议类型，默认HTTP
    private List<HttpHeader> headersList = new ArrayList<>();
    private String bodyType = ""; // 请求体类型
    private String body = ""; // 请求体内容（如JSON、表单数据等）
    private List<HttpParam> pathVariablesList = new ArrayList<>();
    private List<HttpParam> paramsList = new ArrayList<>();
    private List<HttpFormData> formDataList = new ArrayList<>();
    private List<HttpFormUrlencoded> urlencodedList = new ArrayList<>();
    private String authType = AuthType.INHERIT.getConstant(); // 认证类型（inherit/none/apiKey/basic/bearer/digest），默认继承
    private String authUsername = ""; // Basic/Digest用户名
    private String authPassword = ""; // Basic/Digest密码
    private String authToken = "";    // Bearer Token
    private String authApiKeyName = ""; // API Key 名称
    private String authApiKeyValue = ""; // API Key 值
    private String authApiKeyPlacement = AuthApiKeyPlacement.HEADER.getConstant(); // API Key 添加位置
    private Boolean followRedirects; // 是否自动跟随重定向，null 表示跟随全局设置
    private Boolean cookieJarEnabled; // 是否启用 Cookie Jar，null 表示使用默认值
    private HttpRequestProxyPolicy proxyPolicy = HttpRequestProxyPolicy.DEFAULT; // 代理策略，默认跟随全局设置
    private String httpVersion = HTTP_VERSION_AUTO; // HTTP 协议偏好
    private Integer requestTimeoutMs; // 请求超时（毫秒），null 表示跟随全局设置
    private Integer webSocketPingIntervalMs; // WebSocket 协议 ping 间隔（毫秒），null 表示默认 30 秒，0 表示关闭
    // 前置脚本（请求前执行）
    private String prescript = "";
    // 后置脚本（响应后执行）
    private String postscript = "";
    // 保存的响应列表（类似 Postman 的 Examples/Response）
    private List<SavedResponse> response = new ArrayList<>();

    public void setProtocol(RequestItemProtocolEnum protocol) {
        this.protocol = protocol == null ? RequestItemProtocolEnum.HTTP : protocol;
    }

    /**
     * 判断该请求是否为新建（未命名）请求
     */
    public boolean isNewRequest() {
        return name == null || name.trim().isEmpty();
    }

    public String resolveHttpVersion() {
        if (httpVersion == null || httpVersion.trim().isEmpty()) {
            return HTTP_VERSION_AUTO;
        }
        if (HTTP_VERSION_HTTP_1_1.equals(httpVersion) || HTTP_VERSION_HTTP_2.equals(httpVersion)) {
            return httpVersion;
        }
        return HTTP_VERSION_AUTO;
    }

    public HttpRequestProxyPolicy resolveProxyPolicy() {
        return HttpRequestProxyPolicy.normalize(proxyPolicy);
    }

    public void setProxyPolicy(HttpRequestProxyPolicy proxyPolicy) {
        this.proxyPolicy = HttpRequestProxyPolicy.normalize(proxyPolicy);
    }

}
