package com.laker.postman.http.runtime.okhttp;

import com.laker.postman.http.runtime.model.HttpCaptureProfile;
import com.laker.postman.http.runtime.model.HttpEventInfo;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.http.runtime.transport.HttpExchangeTraceSupport;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class OkHttpExchangeEventListenerTest {

    @Test
    public void shouldCaptureDnsAnswersAndOkHttp5Decisions() throws Exception {
        PreparedRequest preparedRequest = new PreparedRequest();
        preparedRequest.url = "https://example.test/";
        preparedRequest.captureProfile = HttpCaptureProfile.COLLECTION_DIAGNOSTIC;
        OkHttpExchangeEventListener listener = new OkHttpExchangeEventListener(preparedRequest);

        listener.dispatcherQueueStart(null, null);
        listener.dispatcherQueueEnd(null, null);
        listener.dnsStart(null, "example.test");
        listener.dnsEnd(null, "example.test", List.of(
                InetAddress.getByName("2001:db8::10"),
                InetAddress.getByName("192.0.2.10")
        ));
        listener.retryDecision(null, new IOException("route failed"), true);

        Request original = new Request.Builder().url("https://example.test/").build();
        Request next = new Request.Builder().url("https://example.test/authenticated").build();
        Response response = new Response.Builder()
                .request(original)
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .build();
        listener.followUpDecision(null, response, next);

        HttpEventInfo info = HttpExchangeTraceSupport.resolveFromRequest(preparedRequest);
        assertEquals(info.getDnsHost(), "example.test");
        assertEquals(info.getDnsAddresses(), List.of("[2001:db8:0:0:0:0:0:10]", "192.0.2.10"));
        assertEquals(info.getRetryDecisionCount(), 1);
        assertEquals(info.getRetryCount(), 1);
        assertEquals(info.getFollowUpDecisionCount(), 1);
        assertEquals(info.getFollowUpCount(), 1);
        assertTrue(info.getDispatcherQueueStart() > 0);
        assertTrue(info.getDispatcherQueueEnd() > 0);
    }
}
