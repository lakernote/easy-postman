package com.laker.postman.http.runtime.transport;

import com.laker.postman.http.runtime.model.HttpCaptureProfile;
import com.laker.postman.http.runtime.model.HttpCaptureProfiles;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.http.runtime.observation.NetworkLogEvent;
import com.laker.postman.http.runtime.observation.NetworkLogEventStage;
import okhttp3.Request;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.testng.annotations.Test;

import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;

public class SseNetworkLogEventSourceListenerTest {
    private static final EventSource EVENT_SOURCE = new FakeEventSource();

    @Test
    public void localCancellationShouldPublishCanceledTerminalOnly() {
        List<NetworkLogEvent> events = new ArrayList<>();
        PreparedRequest request = diagnosticRequest(events);
        SseNetworkLogEventSourceListener listener = new SseNetworkLogEventSourceListener(
                new EventSourceListener() {
                }, request, () -> true);

        listener.onFailure(EVENT_SOURCE, new SocketException("Socket closed"), null);

        assertEquals(count(events, NetworkLogEventStage.CANCELED), 1L);
        assertEquals(count(events, NetworkLogEventStage.CALL_FAILED), 0L);
        assertEquals(count(events, NetworkLogEventStage.CALL_END), 0L);
    }

    @Test
    public void unexpectedSocketCloseShouldPublishFailureTerminalOnly() {
        List<NetworkLogEvent> events = new ArrayList<>();
        PreparedRequest request = diagnosticRequest(events);
        SseNetworkLogEventSourceListener listener = new SseNetworkLogEventSourceListener(
                new EventSourceListener() {
                }, request, () -> false);

        listener.onFailure(EVENT_SOURCE, new SocketException("Socket closed"), null);

        assertEquals(count(events, NetworkLogEventStage.CANCELED), 0L);
        assertEquals(count(events, NetworkLogEventStage.CALL_FAILED), 1L);
        assertEquals(count(events, NetworkLogEventStage.CALL_END), 0L);
    }

    @Test
    public void gracefulCloseShouldPublishCallEndTerminalOnly() {
        List<NetworkLogEvent> events = new ArrayList<>();
        PreparedRequest request = diagnosticRequest(events);
        SseNetworkLogEventSourceListener listener = new SseNetworkLogEventSourceListener(
                new EventSourceListener() {
                }, request, () -> false);

        listener.onClosed(EVENT_SOURCE);

        assertEquals(count(events, NetworkLogEventStage.CANCELED), 0L);
        assertEquals(count(events, NetworkLogEventStage.CALL_FAILED), 0L);
        assertEquals(count(events, NetworkLogEventStage.CALL_END), 1L);
    }

    private static PreparedRequest diagnosticRequest(List<NetworkLogEvent> events) {
        PreparedRequest request = new PreparedRequest();
        HttpCaptureProfiles.apply(request, HttpCaptureProfile.COLLECTION_DIAGNOSTIC);
        request.networkLogSink = events::add;
        return request;
    }

    private static long count(List<NetworkLogEvent> events, NetworkLogEventStage stage) {
        return events.stream().filter(event -> event.stage() == stage).count();
    }

    private static final class FakeEventSource implements EventSource {
        @Override
        public Request request() {
            return new Request.Builder().url("http://localhost/events").build();
        }

        @Override
        public void cancel() {
        }
    }
}
