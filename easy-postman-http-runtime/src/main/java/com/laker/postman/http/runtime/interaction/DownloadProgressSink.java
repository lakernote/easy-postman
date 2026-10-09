package com.laker.postman.http.runtime.interaction;

public interface DownloadProgressSink {
    DownloadProgressSink NOOP = new DownloadProgressSink() {
    };

    default void start(int contentLength) {
    }

    /**
     * Starts a transfer after the response headers have arrived. Streaming media
     * means an unknown-length media response, not a guarantee that it is live.
     * The cancellation action must interrupt a pending network read.
     */
    default void start(int contentLength, boolean streamingMedia, String sourceUrl, Runnable cancelAction) {
        start(contentLength);
    }

    default boolean isCancelled() {
        return false;
    }

    default void updateProgress(int bytesRead) {
    }

    default void finish() {
    }

    default void finish(boolean completed) {
        finish();
    }

    default void finish(boolean completed, boolean cancelled) {
        finish(completed);
    }
}
