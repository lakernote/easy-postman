package com.laker.postman.performance.core.request;

import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;

public class PerformanceOutboundRequestTest {

    @Test
    public void shouldBuildTransportNeutralRequestFromSnapshot() {
        PerformanceRequestSnapshot snapshot = PerformanceRequestSnapshot.builder()
                .id("api")
                .name("API")
                .protocol(PerformanceRequestProtocol.HTTP)
                .method("post")
                .url("https://example.test/events")
                .headers(List.of(
                        new PerformanceRequestKeyValue(true, "Accept", "text/event-stream"),
                        new PerformanceRequestKeyValue(false, "X-Skip", "1"),
                        new PerformanceRequestKeyValue(true, " ", "blank-key")
                ))
                .params(List.of(
                        new PerformanceRequestKeyValue(true, "q", "hello"),
                        new PerformanceRequestKeyValue(false, "disabled", "no")
                ))
                .formData(List.of(
                        new PerformanceRequestFormDataPart(true, "file", "File", "/tmp/a.txt"),
                        new PerformanceRequestFormDataPart(false, "skip", "Text", "no")
                ))
                .urlencoded(List.of(
                        new PerformanceRequestKeyValue(true, "token", "abc")
                ))
                .authType(PerformanceAuthType.BASIC)
                .authUsername("alice")
                .authPassword("secret")
                .authToken("bearer-token")
                .authApiKeyName("X-API-Key")
                .authApiKeyValue("api-secret")
                .authApiKeyPlacement(PerformanceRequestSnapshot.AUTH_API_KEY_PLACEMENT_HEADER)
                .bodyType("raw")
                .body("{\"ok\":true}")
                .requestTimeoutMs(1500)
                .webSocketPingIntervalMs(60000)
                .followRedirects(false)
                .cookieJarEnabled(true)
                .proxyPolicy(PerformanceRequestSnapshot.PROXY_POLICY_USE_PROXY)
                .httpVersion(PerformanceRequestSnapshot.HTTP_VERSION_HTTP_2)
                .build();

        PerformanceOutboundRequest request = PerformanceOutboundRequest.fromSnapshot(snapshot);

        assertEquals(request.getId(), "api");
        assertEquals(request.getName(), "API");
        assertEquals(request.getProtocol(), PerformanceRequestProtocol.HTTP);
        assertEquals(request.getMethod(), "POST");
        assertEquals(request.getUrl(), "https://example.test/events");
        assertEquals(request.getHeaders(), List.of(new PerformanceRequestKeyValue(true, "Accept", "text/event-stream")));
        assertEquals(request.getQueryParams(), List.of(new PerformanceRequestKeyValue(true, "q", "hello")));
        assertEquals(request.getFormData(), List.of(new PerformanceRequestFormDataPart(true, "file", "File", "/tmp/a.txt")));
        assertEquals(request.getUrlencoded(), List.of(new PerformanceRequestKeyValue(true, "token", "abc")));
        assertEquals(request.getAuthConfig().getType(), PerformanceAuthType.BASIC);
        assertEquals(request.getAuthConfig().getUsername(), "alice");
        assertEquals(request.getAuthConfig().getPassword(), "secret");
        assertEquals(request.getAuthConfig().getToken(), "bearer-token");
        assertEquals(request.getAuthConfig().getApiKeyName(), "X-API-Key");
        assertEquals(request.getAuthConfig().getApiKeyValue(), "api-secret");
        assertEquals(request.getAuthConfig().getApiKeyPlacement(), PerformanceRequestSnapshot.AUTH_API_KEY_PLACEMENT_HEADER);
        assertEquals(request.getBodyType(), "raw");
        assertEquals(request.getBody(), "{\"ok\":true}");
        assertEquals(request.getRequestTimeoutMs(), Integer.valueOf(1500));
        assertEquals(request.getWebSocketPingIntervalMs(), Integer.valueOf(60000));
        assertEquals(request.getFollowRedirects(), Boolean.FALSE);
        assertEquals(request.getCookieJarEnabled(), Boolean.TRUE);
        assertEquals(request.getProxyPolicy(), PerformanceRequestSnapshot.PROXY_POLICY_USE_PROXY);
        assertEquals(request.getHttpVersion(), PerformanceRequestSnapshot.HTTP_VERSION_HTTP_2);
    }
}
