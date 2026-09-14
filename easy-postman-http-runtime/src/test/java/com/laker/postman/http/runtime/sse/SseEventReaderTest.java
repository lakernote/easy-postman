package com.laker.postman.http.runtime.sse;

import com.laker.postman.http.runtime.model.HttpResponse;
import okio.Buffer;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class SseEventReaderTest {

    @Test
    public void shouldParseMultilineEventsAndRetryWithCrLf() throws Exception {
        Buffer source = new Buffer().writeUtf8(
                ": keep-alive\r\n" +
                        "id: 42\r\n" +
                        "event: update\r\n" +
                        "data: first\r\n" +
                        "data: second\r\n" +
                        "retry: 1500\r\n" +
                        "\r\n");
        RecordingCallback callback = new RecordingCallback();

        SseEventReader reader = new SseEventReader(source, callback);

        assertTrue(reader.processNextEvent());
        assertEquals(callback.events, List.of("42|update|first\nsecond"));
        assertEquals(callback.retryDelays, List.of(1500L));
        assertFalse(reader.processNextEvent());
    }

    @Test
    public void shouldKeepLastEventIdAndIgnoreInvalidFields() throws Exception {
        Buffer source = new Buffer().writeUtf8(
                "id: first\n" +
                        "data: one\n\n" +
                        "retry: nope\n" +
                        "id\n" +
                        "data: two\n\n");
        RecordingCallback callback = new RecordingCallback();

        SseEventReader reader = new SseEventReader(source, callback);

        assertTrue(reader.processNextEvent());
        assertTrue(reader.processNextEvent());
        assertEquals(callback.events, List.of("first|null|one", "|null|two"));
        assertTrue(callback.retryDelays.isEmpty());
    }

    @Test
    public void shouldParseCrOnlyLineEndings() throws Exception {
        Buffer source = new Buffer().writeUtf8(
                "id: cr-only\r" +
                        "event: update\r" +
                        "data: first\r" +
                        "data: second\r" +
                        "retry: 25\r" +
                        "\r");
        RecordingCallback callback = new RecordingCallback();

        SseEventReader reader = new SseEventReader(source, callback);

        assertTrue(reader.processNextEvent());
        assertEquals(callback.events, List.of("cr-only|update|first\nsecond"));
        assertEquals(callback.retryDelays, List.of(25L));
        assertFalse(reader.processNextEvent());
    }

    @Test
    public void shouldIgnoreIdContainingNullAndNonAsciiRetryDigits() throws Exception {
        Buffer source = new Buffer().writeUtf8(
                "id: stable\n" +
                        "data: first\n\n" +
                        "id: ignored\u0000value\n" +
                        "retry: ١٢\n" +
                        "data: second\n\n");
        RecordingCallback callback = new RecordingCallback();

        SseEventReader reader = new SseEventReader(source, callback);

        assertTrue(reader.processNextEvent());
        assertTrue(reader.processNextEvent());
        assertEquals(callback.events, List.of("stable|null|first", "stable|null|second"));
        assertTrue(callback.retryDelays.isEmpty());
    }

    private static final class RecordingCallback implements SseResponseCallback {
        private final List<String> events = new ArrayList<>();
        private final List<Long> retryDelays = new ArrayList<>();

        @Override
        public void onOpen(HttpResponse response) {
        }

        @Override
        public void onEvent(String id, String type, String data) {
            events.add(id + "|" + type + "|" + data);
        }

        @Override
        public void onRetryChange(long timeMs) {
            retryDelays.add(timeMs);
        }
    }
}
