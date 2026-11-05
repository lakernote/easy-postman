package com.laker.postman.panel.collections.editor.request.sub;

import com.laker.postman.util.FileSizeDisplayUtil;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.http.runtime.model.HttpEventInfo;
import lombok.Data;
import lombok.experimental.UtilityClass;

import java.awt.*;

/**
 * 响应大小计算器
 * 负责计算响应大小、压缩率等信息
 */
@UtilityClass
public class ResponseSizeCalculator {

    /**
     * 响应大小信息
     */
    @Data
    public static class SizeInfo {
        private final String displayText;
        private final Color normalColor;
        private final Color hoverColor;
        private final boolean compressed;
        private final double compressionRatio;
        private final long savedBytes;
        /**
         * 实际压缩算法，如 gzip / br / zstd / deflate，未知时为 "compressed"
         */
        private final String encoding;
    }

    /**
     * 计算响应大小信息
     *
     * @param encoding Content-Encoding 头的值，如 "gzip" / "br" / "zstd"，可为 null
     */
    public static SizeInfo calculate(long uncompressedBytes, HttpEventInfo httpEventInfo, String encoding) {
        // 检查响应是否被压缩
        // uncompressedBytes = 解压后的响应体大小（从 body.bytes() 获取，OkHttp 自动解压）
        // bodyBytesReceived = 网络层实际接收的字节数（从 OkHttp 事件监听器获取）
        //
        // 必须确保 uncompressedBytes > bodyBytesReceived 才认为是压缩，原因如下：
        // 1. Chunked 编码：bodyBytesReceived 包含 chunk 头部元数据
        // 2. HTTP/2 协议：bodyBytesReceived 包含 frame 头部开销
        // 3. 统计方式差异：事件监听器可能统计了额外的协议层开销
        boolean isCompressed = httpEventInfo != null && uncompressedBytes > 0 &&
                httpEventInfo.getBodyBytesReceived() > 0 &&
                uncompressedBytes > httpEventInfo.getBodyBytesReceived();

        double compressionRatio = 0;
        long savedBytes = 0;

        if (isCompressed) {
            compressionRatio = (1 - (double) httpEventInfo.getBodyBytesReceived() / uncompressedBytes) * 100;
            savedBytes = uncompressedBytes - httpEventInfo.getBodyBytesReceived();
        }

        String displayText;
        Color normalColor;
        Color hoverColor;

        String enc = (encoding != null && !encoding.isBlank()) ? encoding.toLowerCase() : "compressed";

        if (isCompressed) {
            displayText = String.format("%s (%s %.0f%%)",
                    formatBytes(httpEventInfo.getBodyBytesReceived()), enc, compressionRatio);
            normalColor = ModernColors.getTextSecondary();
            hoverColor = ModernColors.getPrimary();
        } else {
            displayText = formatBytes(uncompressedBytes);
            normalColor = ModernColors.getTextHint();
            hoverColor = ModernColors.getPrimary();
            enc = null;
        }

        return new SizeInfo(displayText, normalColor, hoverColor, isCompressed, compressionRatio, savedBytes, enc);
    }

    /**
     * 格式化字节大小为可读文本
     */
    public static String formatBytes(long bytes) {
        return FileSizeDisplayUtil.formatCompactSize(bytes);
    }
}
