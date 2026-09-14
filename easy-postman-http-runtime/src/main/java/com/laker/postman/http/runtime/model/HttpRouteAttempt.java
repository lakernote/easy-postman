package com.laker.postman.http.runtime.model;

/**
 * One concrete route attempted while establishing a connection.
 *
 * <p>OkHttp 5 may race IPv6 and IPv4 routes. Keeping these attempts separate
 * from the final remote address makes fallback behavior visible in diagnostics
 * without changing the request execution result.</p>
 */
public record HttpRouteAttempt(
        String address,
        String addressFamily,
        long startTime,
        long endTime,
        long durationMs,
        boolean connected,
        boolean canceled,
        String protocol,
        String error
) {
    /**
     * Backward-compatible constructor for callers that do not provide a
     * monotonic duration or a canceled state.
     */
    public HttpRouteAttempt(String address,
                            String addressFamily,
                            long startTime,
                            long endTime,
                            boolean connected,
                            String protocol,
                            String error) {
        this(address,
                addressFamily,
                startTime,
                endTime,
                wallClockDuration(startTime, endTime),
                connected,
                false,
                protocol,
                error);
    }

    private static long wallClockDuration(long startTime, long endTime) {
        return startTime > 0 && endTime >= startTime ? endTime - startTime : 0L;
    }
}
