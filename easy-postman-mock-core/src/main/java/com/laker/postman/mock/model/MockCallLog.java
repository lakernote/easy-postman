package com.laker.postman.mock.model;

import java.time.Instant;

/**
 * Bounded in-memory request log entry.
 */
public record MockCallLog(
        Instant timestamp,
        String method,
        String path,
        int statusCode,
        long durationMs,
        String requestName,
        String exampleName,
        String requestBody,
        String responseBody,
        String error
) {
}
