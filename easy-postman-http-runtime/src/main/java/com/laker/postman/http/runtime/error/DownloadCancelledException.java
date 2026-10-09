package com.laker.postman.http.runtime.error;

import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.Getter;

import java.io.IOException;

/**
 * 下载取消异常
 * 当用户主动取消下载时抛出此异常，表示这是正常的用户行为，而非错误
 */
public class DownloadCancelledException extends IOException {
    @Getter
    private final long receivedBytes;

    public DownloadCancelledException() {
        this(0L);
    }

    public DownloadCancelledException(long receivedBytes) {
        super(I18nUtil.getMessage(MessageKeys.DOWNLOAD_CANCELLED));
        this.receivedBytes = Math.max(0L, receivedBytes);
    }
}
