package com.laker.postman.model;

public enum RequestImportProtocol {
    HTTP,
    WEBSOCKET,
    /**
     * Legacy plugin import value. Imported requests are stored and executed as HTTP.
     * Kept to preserve binary compatibility with already released plugins.
     */
    @Deprecated
    SSE
}
