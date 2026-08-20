package com.laker.postman.service.js.api;

/**
 * Request-scoped journal for explicit body writes. Value comparison remains responsible for
 * deciding whether the SDK view must overwrite {@code pm.request.raw}; the journal preserves
 * write intent when the assigned value is unchanged.
 */
final class ScriptRequestMutationTracker {
    private boolean bodyWriteRequested;

    void recordBodyWrite() {
        bodyWriteRequested = true;
    }

    boolean consumeBodyWrite() {
        boolean requested = bodyWriteRequested;
        bodyWriteRequested = false;
        return requested;
    }

    Runnable bodyWriteCallback() {
        return this::recordBodyWrite;
    }
}
