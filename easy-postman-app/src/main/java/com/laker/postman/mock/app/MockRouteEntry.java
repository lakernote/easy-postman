package com.laker.postman.mock.app;

/**
 * UI-facing route row. Requests without a saved response are included so the
 * Mock Server page can configure them directly.
 */
public record MockRouteEntry(
        String sourceCollectionId,
        String sourceName,
        String routeId,
        boolean standalone,
        String requestId,
        String requestName,
        String exampleId,
        String exampleName,
        String method,
        String path,
        int statusCode,
        int delayMs,
        boolean configured,
        boolean codeMock
) {
}
