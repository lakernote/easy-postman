package com.laker.postman.panel.collections.editor.request.sub;

import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.http.runtime.observation.NetworkLogEventStage;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.Getter;

import java.awt.*;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * 网络日志阶段枚举
 * 统一管理日志阶段的默认图标、颜色和标题强调。
 * HTTP 结果和重试判定的颜色由日志面板根据事件内容补充。
 */
@Getter
public enum NetworkLogStage {
    // ==================== 错误和失败（红色系，粗体）====================
    FAILED("Failed", "❌", ModernColors::getError, true),
    CALL_FAILED("CallFailed", "❌", ModernColors::getError, true),
    REQUEST_FAILED("RequestFailed", "❌", ModernColors::getError, true),
    RESPONSE_FAILED("ResponseFailed", "❌", ModernColors::getError, true),
    CANCELED("Canceled", "⏹", ModernColors::getTextSecondary, true),

    // ==================== 请求生命周期和诊断 ====================
    CALL_START("RequestStart", "▶", ModernColors::getPrimary, true),
    CALL_END("RequestEnd", "■", ModernColors::getTextSecondary, true),
    REQUEST_COMPLETE("RequestComplete", "■", ModernColors::getTextSecondary, true),
    DISPATCHER_QUEUE_START("DispatcherQueueStart", "⏳", ModernColors::getTextSecondary, false),
    DISPATCHER_QUEUE_END("DispatcherQueueEnd", "▶", ModernColors::getTextSecondary, false),
    RETRY_DECISION("RetryDecision", "🔁", ModernColors::getTextSecondary, false),
    FOLLOW_UP_DECISION("FollowUpDecision", "↪", ModernColors::getTextSecondary, false),
    CACHE_HIT("CacheHit", "💾", ModernColors::getTextSecondary, false),
    CACHE_MISS("CacheMiss", "💾", ModernColors::getTextSecondary, false),
    CACHE_CONDITIONAL_HIT("CacheConditionalHit", "💾", ModernColors::getTextSecondary, false),
    SATISFACTION_FAILURE("SatisfactionFailure", "⚠️", ModernColors::getWarning, false),
    REQUEST_PREPARED("RequestPrepared", "", ModernColors::getTextSecondary, false),

    // ==================== TLS ====================
    SECURE_CONNECT_START("TLSHandshakeStart", "🔒", ModernColors::getTextSecondary, false),
    SECURE_CONNECT_END("TLSHandshakeEnd", "🔒", ModernColors::getTextSecondary, false),

    // ==================== 连接 ====================
    CONNECT_START("ConnectStart", "🔌", ModernColors::getTextSecondary, false),
    // A failed route can be followed by another successful route; terminal errors use CALL_FAILED.
    CONNECT_FAILED("ConnectFailed", "⚠️", ModernColors::getWarning, true),
    CONNECT_END("ConnectEnd", "🔗", ModernColors::getTextSecondary, false),
    CONNECTION_ACQUIRED("ConnectionReady", "🔗", ModernColors::getTextSecondary, false),
    CONNECTION_RELEASED("ConnectionReleased", "↩", ModernColors::getTextSecondary, false),

    // ==================== DNS ====================
    DNS_START("DNSStart", "🔍", ModernColors::getTextSecondary, false),
    DNS_END("DNSEnd", "📍", ModernColors::getTextSecondary, false),

    // ==================== 代理 ====================
    PROXY_SELECT("ProxySelect", "🌐", ModernColors::getTextSecondary, false),
    PROXY_SELECT_START("ProxySelectStart", "🌐", ModernColors::getTextSecondary, false),
    PROXY_SELECT_END("ProxySelectEnd", "🌐", ModernColors::getTextSecondary, false),

    // ==================== 请求发送 ====================
    REQUEST_HEADERS_START("RequestHeadersStart", "↑", ModernColors::getTextSecondary, false),
    REQUEST_HEADERS_END("RequestHeadersEnd", "↑", ModernColors::getTextSecondary, false),
    REQUEST_BODY_START("RequestBodyStart", "↑", ModernColors::getTextSecondary, false),
    REQUEST_BODY_END("RequestBodyEnd", "↑", ModernColors::getTextSecondary, false),

    // ==================== 响应接收 ====================
    RESPONSE_HEADERS_START("ResponseHeadersStart", "↓", ModernColors::getTextSecondary, false),
    RESPONSE_HEADERS_END("ResponseHeadersEnd", "↓", ModernColors::getTextSecondary, false),
    RESPONSE_HEADERS_END_REDIRECT("ResponseHeadersEnd:Redirect", "↓", ModernColors::getTextSecondary, false),
    RESPONSE_BODY_START("ResponseBodyStart", "↓", ModernColors::getTextSecondary, false),
    RESPONSE_BODY_END("ResponseBodyEnd", "↓", ModernColors::getTextSecondary, false),

    // ==================== 重定向 ====================
    REDIRECT("Redirect", "↪", ModernColors::getPrimary, true),

    // ==================== 默认 ====================
    DEFAULT("Default", "", ModernColors::getTextPrimary, false);

    private final String stageName;
    private final String emoji;
    private final Supplier<Color> colorProvider;
    private final boolean bold;

    NetworkLogStage(String stageName, String emoji, Supplier<Color> colorProvider, boolean bold) {
        this.stageName = stageName;
        this.emoji = emoji;
        this.colorProvider = colorProvider;
        this.bold = bold;
    }

    /**
     * 获取当前主题适配的颜色
     */
    public Color getColor() {
        return colorProvider.get();
    }

    /**
     * 获取当前语言的用户可读阶段名称。技术标识仍由 {@link #getStageName()} 保留，
     * 便于开发者在日志中定位底层事件。
     */
    public String getDisplayName() {
        return I18nUtil.getMessage(MessageKeys.NETWORK_LOG_STAGE_PREFIX
                + name().toLowerCase(Locale.ROOT));
    }

    /**
     * 判断是否为失败或错误类型
     */
    public boolean isError() {
        return this == FAILED || this == CALL_FAILED || this == REQUEST_FAILED
                || this == RESPONSE_FAILED;
    }

    /**
     * 判断是否为可能展示成功结果的阶段；实际结果由响应状态决定。
     */
    public boolean isSuccess() {
        return this == REQUEST_COMPLETE;
    }

    /**
     * 将 HTTP 执行层的日志阶段映射成 UI 渲染阶段。
     * <p>
     * 这里是 service 层事件和 Swing 展示样式之间的唯一转换点，避免执行层反向依赖 UI 枚举。
     */
    public static NetworkLogStage fromEventStage(NetworkLogEventStage stage) {
        if (stage == null) {
            return DEFAULT;
        }
        try {
            return NetworkLogStage.valueOf(stage.name());
        } catch (IllegalArgumentException ex) {
            return DEFAULT;
        }
    }
}
