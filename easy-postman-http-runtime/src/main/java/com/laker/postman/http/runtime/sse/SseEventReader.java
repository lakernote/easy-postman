package com.laker.postman.http.runtime.sse;

import okio.BufferedSource;
import okio.ByteString;

import java.io.IOException;

/**
 * Small UTF-8 SSE parser for the blocking request path.
 *
 * <p>The asynchronous path uses OkHttp's public {@code okhttp-sse} API. The
 * blocking path needs to consume the response body itself, so it uses this
 * project-owned parser rather than depending on OkHttp's internal classes.</p>
 */
public final class SseEventReader {
    private static final ByteString LINE_ENDINGS = ByteString.of((byte) '\r', (byte) '\n');
    private final BufferedSource source;
    private final SseResponseCallback callback;
    private String lastEventId;
    private boolean firstLine = true;

    public SseEventReader(BufferedSource source, SseResponseCallback callback) {
        this.source = source;
        this.callback = callback;
    }

    /**
     * Reads until the next empty line, dispatching one event if it has data.
     *
     * @return {@code false} when EOF is reached before another event boundary
     */
    public boolean processNextEvent() throws IOException {
        String eventId = lastEventId;
        String eventType = null;
        StringBuilder data = new StringBuilder();

        while (true) {
            String line = readLine();
            if (line == null) {
                return false;
            }
            if (firstLine) {
                firstLine = false;
                if (!line.isEmpty() && line.charAt(0) == '\ufeff') {
                    line = line.substring(1);
                }
            }

            if (line.isEmpty()) {
                dispatch(eventId, eventType, data);
                return true;
            }
            if (line.charAt(0) == ':') {
                continue;
            }

            int colon = line.indexOf(':');
            String field = colon >= 0 ? line.substring(0, colon) : line;
            String value = colon >= 0 ? line.substring(colon + 1) : "";
            if (value.startsWith(" ")) {
                value = value.substring(1);
            }

            switch (field) {
                case "data" -> data.append(value).append('\n');
                case "event" -> eventType = value;
                case "id" -> {
                    // WHATWG SSE: ignore id fields containing U+0000.
                    if (value.indexOf('\0') < 0) {
                        eventId = value;
                        lastEventId = value;
                    }
                }
                case "retry" -> parseRetry(value);
                default -> {
                    // Unknown fields are ignored by the SSE specification.
                }
            }
        }
    }

    /**
     * Reads lines terminated by CRLF, LF, or CR. Okio's readUtf8Line()
     * handles LF and CRLF only, while all three forms are valid SSE input.
     */
    private String readLine() throws IOException {
        long lineEnd = source.indexOfElement(LINE_ENDINGS);
        if (lineEnd < 0) {
            if (source.getBuffer().size() == 0L) {
                return null;
            }
            return source.readUtf8();
        }

        String line = source.readUtf8(lineEnd);
        byte terminator = source.readByte();
        if (terminator == '\r' && source.request(1L) && source.getBuffer().getByte(0L) == '\n') {
            source.skip(1L);
        }
        return line;
    }

    private void dispatch(String eventId, String eventType, StringBuilder data) {
        if (data.isEmpty()) {
            return;
        }
        data.setLength(data.length() - 1);
        callback.onEvent(eventId, eventType, data.toString());
    }

    private void parseRetry(String value) {
        if (value.isEmpty()) {
            return;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return;
            }
        }
        try {
            callback.onRetryChange(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            // Values outside the long range are invalid retry values.
        }
    }
}
