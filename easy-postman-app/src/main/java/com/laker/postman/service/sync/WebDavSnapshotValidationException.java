package com.laker.postman.service.sync;

import java.io.IOException;

public class WebDavSnapshotValidationException extends IOException {
    public enum Reason {
        INVALID_ARCHIVE,
        SIZE_MISMATCH
    }

    private final Reason reason;
    private final long expectedBytes;
    private final long actualBytes;

    private WebDavSnapshotValidationException(Reason reason,
                                              String message,
                                              long expectedBytes,
                                              long actualBytes,
                                              Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.expectedBytes = expectedBytes;
        this.actualBytes = actualBytes;
    }

    static WebDavSnapshotValidationException invalidArchive(Throwable cause) {
        return new WebDavSnapshotValidationException(
                Reason.INVALID_ARCHIVE,
                "WebDAV snapshot is invalid or contains no restorable data",
                -1,
                -1,
                cause
        );
    }

    static WebDavSnapshotValidationException sizeMismatch(long expectedBytes, long actualBytes) {
        return new WebDavSnapshotValidationException(
                Reason.SIZE_MISMATCH,
                "WebDAV snapshot size mismatch: expected " + expectedBytes
                        + " bytes but downloaded " + actualBytes + " bytes",
                expectedBytes,
                actualBytes,
                null
        );
    }

    public Reason reason() {
        return reason;
    }

    public long expectedBytes() {
        return expectedBytes;
    }

    public long actualBytes() {
        return actualBytes;
    }
}
