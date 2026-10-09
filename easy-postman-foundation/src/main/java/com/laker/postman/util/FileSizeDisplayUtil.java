package com.laker.postman.util;

import lombok.experimental.UtilityClass;

/**
 * 文件大小显示工具类
 * 提供将字节数转换为更友好的显示格式
 */
@UtilityClass
public class FileSizeDisplayUtil {

    /**
     * 格式化文件大小显示，自动选择单位，保留两位小数，去除无意义的.00
     *
     * @param sizeBytes 字节数
     * @return 友好的显示字符串，如 1.23 MB
     */
    public static String formatSize(int sizeBytes) {
        return formatSize((long) sizeBytes);
    }

    /**
     * Formats long-running transfers without overflowing the byte count.
     */
    public static String formatSize(long sizeBytes) {
        String unit;
        double value;
        if (sizeBytes >= 1024L * 1024 * 1024 * 1024) {
            unit = "TB";
            value = sizeBytes / (1024.0 * 1024 * 1024 * 1024);
        } else if (sizeBytes >= 1024 * 1024 * 1024) {
            unit = "GB";
            value = sizeBytes / (1024.0 * 1024 * 1024);
        } else if (sizeBytes >= 1024 * 1024) {
            unit = "MB";
            value = sizeBytes / (1024.0 * 1024);
        } else if (sizeBytes >= 1024) {
            unit = "KB";
            value = sizeBytes / 1024.0;
        } else {
            unit = "B";
            value = sizeBytes;
        }
        if (unit.equals("B")) {
            return String.format("%d %s", sizeBytes, unit);
        } else {
            return String.format("%.2f %s", value, unit);
        }
    }

    /**
     * 格式化文件下载进度显示
     *
     * @param totalBytes    已下载字节数
     * @param contentLength 总字节数
     * @return 友好的显示字符串
     */
    public static String formatDownloadSize(int totalBytes, int contentLength) {
        if (contentLength > 0) {
            return String.format("Downloaded: %s / %s", formatSize(totalBytes), formatSize(contentLength));
        } else {
            return String.format("Downloaded: %s", formatSize(totalBytes));
        }
    }
}
