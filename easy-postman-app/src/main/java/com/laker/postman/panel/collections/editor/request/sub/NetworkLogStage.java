package com.laker.postman.panel.collections.editor.request.sub;

import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.http.runtime.observation.NetworkLogEventStage;
import lombok.Getter;

import java.awt.*;
import java.util.function.Supplier;

/**
 * 网络日志阶段枚举
 * 统一管理日志阶段的 emoji 图标、颜色方案和粗体配置
 */
@Getter
public enum NetworkLogStage {
    // ==================== 错误和失败（红色系，粗体）====================
    FAILED("Failed", "❌", ModernColors::getError, true),
    CALL_FAILED("CallFailed", "💥", ModernColors::getError, true),
    REQUEST_FAILED("RequestFailed", "❌", ModernColors::getError, true),
    RESPONSE_FAILED("ResponseFailed", "❌", ModernColors::getError, true),
    CONNECT_FAILED("ConnectFailed", "⚠️", ModernColors::getError, true),
    CANCELED("Canceled", "🚫", ModernColors::getError, true),

    // ==================== 成功和完成（绿色系）====================
    CALL_START("RequestStart", "🚀", ModernColors::getSuccess, true),
    CALL_END("RequestEnd", "✅", ModernColors::getSuccess, true),
    DISPATCHER_QUEUE_START("DispatcherQueueStart", "⏳", ModernColors::getInfo, false),
    DISPATCHER_QUEUE_END("DispatcherQueueEnd", "▶️", ModernColors::getInfo, false),
    RETRY_DECISION("RetryDecision", "🔁", ModernColors::getWarning, false),
    FOLLOW_UP_DECISION("FollowUpDecision", "↪️", ModernColors::getWarning, false),
    CACHE_HIT("CacheHit", "💾", ModernColors::getSuccess, false),
    CACHE_MISS("CacheMiss", "❌", ModernColors::getInfo, false),
    CACHE_CONDITIONAL_HIT("CacheConditionalHit", "💾", ModernColors::getInfo, false),
    SATISFACTION_FAILURE("SatisfactionFailure", "⚠️", ModernColors::getWarning, false),
    REQUEST_PREPARED("RequestPrepared", "🧾", ModernColors::getPrimary, false),

    // ==================== 安全连接（由主色和错误色派生的紫色系）====================
    SECURE_CONNECT_START("TLSHandshakeStart", "🔐", NetworkLogStage::getSecureConnectColor, false),
    SECURE_CONNECT_END("TLSHandshakeEnd", "🔒", NetworkLogStage::getSecureConnectColor, false),

    // ==================== 连接相关（蓝色系）====================
    CONNECT_START("ConnectStart", "🔌", ModernColors::getPrimary, false),
    CONNECT_END("ConnectEnd", "✅", ModernColors::getPrimary, false),
    CONNECTION_ACQUIRED("ConnectionReady", "🔗", ModernColors::getPrimary, false),
    CONNECTION_RELEASED("ConnectionReleased", "🔓", ModernColors::getPrimary, false),

    // ==================== DNS（蓝色系）====================
    DNS_START("DNSStart", "🔍", ModernColors::getPrimary, false),
    DNS_END("DNSEnd", "📍", ModernColors::getPrimary, false),

    // ==================== 代理（蓝色系）====================
    PROXY_SELECT("ProxySelect", "🌐", ModernColors::getPrimary, false),
    PROXY_SELECT_START("ProxySelectStart", "🌐", ModernColors::getPrimary, false),
    PROXY_SELECT_END("ProxySelectEnd", "🌐", ModernColors::getPrimary, false),

    // ==================== 请求（橙色系）====================
    REQUEST_HEADERS_START("RequestHeadersStart", "📤", ModernColors::getWarning, false),
    REQUEST_HEADERS_END("RequestHeadersEnd", "📨", ModernColors::getWarning, false),
    REQUEST_BODY_START("RequestBodyStart", "📦", ModernColors::getWarning, false),
    REQUEST_BODY_END("RequestBodyEnd", "✅", ModernColors::getWarning, false),

    // ==================== 响应（青色系）====================
    RESPONSE_HEADERS_START("ResponseHeadersStart", "📥", ModernColors::getInfo, false),
    RESPONSE_HEADERS_END("ResponseHeadersEnd", "📬", ModernColors::getInfo, false),
    RESPONSE_HEADERS_END_REDIRECT("ResponseHeadersEnd:Redirect", "🔀", ModernColors::getWarning, true),
    RESPONSE_BODY_START("ResponseBodyStart", "📄", ModernColors::getInfo, false),
    RESPONSE_BODY_END("ResponseBodyEnd", "✅", ModernColors::getInfo, false),

    // ==================== 重定向（橙色，粗体）====================
    REDIRECT("Redirect", "↪️", ModernColors::getWarning, true),

    // ==================== 默认 ====================
    DEFAULT("Default", "📋", ModernColors::getTextPrimary, false);

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

    private static Color getSecureConnectColor() {
        return blend(ModernColors.getPrimary(), ModernColors.getError());
    }

    private static Color blend(Color first, Color second) {
        return new Color(
                (first.getRed() + second.getRed()) / 2,
                (first.getGreen() + second.getGreen()) / 2,
                (first.getBlue() + second.getBlue()) / 2
        );
    }

    /**
     * 判断是否为失败或错误类型
     */
    public boolean isError() {
        return this == FAILED || this == CALL_FAILED || this == REQUEST_FAILED
                || this == RESPONSE_FAILED || this == CONNECT_FAILED || this == CANCELED;
    }

    /**
     * 判断是否为成功类型
     */
    public boolean isSuccess() {
        return this == CALL_END || this == CACHE_HIT;
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
