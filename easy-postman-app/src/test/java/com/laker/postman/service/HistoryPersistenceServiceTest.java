package com.laker.postman.service;

import cn.hutool.json.JSONObject;
import com.laker.postman.http.runtime.model.HttpEventInfo;
import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.HttpRouteAttempt;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.history.RequestHistoryItem;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class HistoryPersistenceServiceTest {

    @Test
    public void shouldTruncateLargeRequestBodyBeforePersistingHistory() throws Exception {
        HistoryPersistenceService service = new HistoryPersistenceService();
        String largeBody = "x".repeat(12 * 1024);

        PreparedRequest request = new PreparedRequest();
        request.method = "POST";
        request.url = "https://example.com/api/test";
        request.body = largeBody;
        request.sentRequestBody = largeBody;

        HttpResponse response = new HttpResponse();
        response.code = 200;
        response.body = "{\"ok\":true}";

        RequestHistoryItem item = new RequestHistoryItem(request, response, 123456789L);

        JSONObject json = invokeConvertToJson(service, item);
        RequestHistoryItem restored = invokeConvertFromJson(service, json);

        assertNotNull(restored.getRequest());
        assertTrue(restored.getRequest().body.length() < largeBody.length());
        assertEquals(restored.getRequest().body, restored.getRequest().sentRequestBody);
        assertTrue(restored.getRequest().body.contains("内容过大，已截断"));
    }

    @Test
    public void shouldPersistIpv4AndIpv6RouteAttempts() throws Exception {
        PreparedRequest request = new PreparedRequest();
        request.method = "GET";
        request.url = "https://example.com/api/test";

        HttpResponse response = new HttpResponse();
        response.code = 200;
        response.body = "ok";
        HttpEventInfo eventInfo = new HttpEventInfo();
        eventInfo.setDispatcherQueueStart(90L);
        eventInfo.setDispatcherQueueEnd(95L);
        eventInfo.setDnsHost("example.com");
        eventInfo.replaceDnsAddresses(List.of("2001:db8::10", "192.0.2.10"));
        eventInfo.setDnsError("secondary resolver failed");
        eventInfo.recordRetryDecision(true);
        eventInfo.recordFollowUpDecision(true);
        eventInfo.addRouteAttempt(new HttpRouteAttempt(
                "[2001:db8::10]:443", "IPv6", 100L, 125L, 20L,
                false, true, null, "canceled after IPv4 connected"
        ));
        // Keep exercising history compatibility with the original constructor shape.
        eventInfo.addRouteAttempt(new HttpRouteAttempt(
                "192.0.2.10:443", "IPv4", 126L, 140L, true, "h2", null
        ));
        response.httpEventInfo = eventInfo;

        JSONObject json = invokeConvertToJson(new HistoryPersistenceService(),
                new RequestHistoryItem(request, response, 123456789L));
        RequestHistoryItem restored = invokeConvertFromJson(new HistoryPersistenceService(), json);

        assertEquals(restored.getResponse().httpEventInfo.getRouteAttempts().size(), 2);
        assertEquals(restored.getResponse().httpEventInfo.getRouteAttempts().get(0).addressFamily(), "IPv6");
        assertEquals(restored.getResponse().httpEventInfo.getRouteAttempts().get(0).durationMs(), 20L);
        assertTrue(restored.getResponse().httpEventInfo.getRouteAttempts().get(0).canceled());
        assertTrue(restored.getResponse().httpEventInfo.getRouteAttempts().get(1).connected());
        assertFalse(restored.getResponse().httpEventInfo.getRouteAttempts().get(1).canceled());
        assertEquals(restored.getResponse().httpEventInfo.getDispatcherQueueStart(), 90L);
        assertEquals(restored.getResponse().httpEventInfo.getDispatcherQueueEnd(), 95L);
        assertEquals(restored.getResponse().httpEventInfo.getDnsHost(), "example.com");
        assertEquals(restored.getResponse().httpEventInfo.getDnsAddresses(),
                List.of("2001:db8::10", "192.0.2.10"));
        assertEquals(restored.getResponse().httpEventInfo.getDnsError(), "secondary resolver failed");
        assertEquals(restored.getResponse().httpEventInfo.getRetryDecisionCount(), 1);
        assertEquals(restored.getResponse().httpEventInfo.getRetryCount(), 1);
        assertEquals(restored.getResponse().httpEventInfo.getFollowUpDecisionCount(), 1);
        assertEquals(restored.getResponse().httpEventInfo.getFollowUpCount(), 1);
    }

    private JSONObject invokeConvertToJson(HistoryPersistenceService service, RequestHistoryItem item) throws Exception {
        Method method = HistoryPersistenceService.class.getDeclaredMethod("convertToJson", RequestHistoryItem.class);
        method.setAccessible(true);
        return (JSONObject) method.invoke(service, item);
    }

    private RequestHistoryItem invokeConvertFromJson(HistoryPersistenceService service, JSONObject json) throws Exception {
        Method method = HistoryPersistenceService.class.getDeclaredMethod("convertFromJson", JSONObject.class);
        method.setAccessible(true);
        return (RequestHistoryItem) method.invoke(service, json);
    }
}
