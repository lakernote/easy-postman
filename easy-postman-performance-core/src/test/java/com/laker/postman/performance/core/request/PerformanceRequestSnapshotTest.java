package com.laker.postman.performance.core.request;

import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public class PerformanceRequestSnapshotTest {

    @Test
    public void shouldProvideHeadlessSafeDefaults() {
        PerformanceRequestSnapshot snapshot = PerformanceRequestSnapshot.empty();

        assertEquals(snapshot.getMethod(), "GET");
        assertEquals(snapshot.getProtocol(), PerformanceRequestProtocol.HTTP);
        assertEquals(snapshot.getHttpVersion(), PerformanceRequestSnapshot.HTTP_VERSION_AUTO);
        assertEquals(snapshot.getProxyPolicy(), PerformanceRequestSnapshot.PROXY_POLICY_DEFAULT);
        assertEquals(snapshot.getAuthType(), PerformanceAuthType.INHERIT);
        assertTrue(snapshot.getHeaders().isEmpty());
        assertTrue(snapshot.getExecutionScope().getGroupVariables().isEmpty());
        assertFalse(snapshot.executesChildrenInSamplerOrder());
    }

    @Test
    public void shouldDefensivelyCopyCollectionsAndExecutionScope() {
        List<PerformanceRequestKeyValue> headers = new ArrayList<>();
        headers.add(new PerformanceRequestKeyValue(true, "Authorization", "Bearer token"));
        Map<String, String> groupVariables = new LinkedHashMap<>();
        groupVariables.put("tenant", "acme");

        PerformanceRequestSnapshot snapshot = PerformanceRequestSnapshot.builder()
                .id("request-1")
                .name("stream")
                .url("wss://example.test/ws")
                .method("POST")
                .protocol(PerformanceRequestProtocol.WEBSOCKET)
                .headers(headers)
                .executionScope(PerformanceRequestExecutionScopeSnapshot.fromGroupVariables(groupVariables))
                .build();

        headers.add(new PerformanceRequestKeyValue(true, "X-Late", "ignored"));
        groupVariables.put("tenant", "mutated");

        assertEquals(snapshot.getHeaders().size(), 1);
        assertEquals(snapshot.getHeaders().get(0).getKey(), "Authorization");
        assertEquals(snapshot.getExecutionScope().getGroupVariable("tenant"), "acme");
        assertTrue(snapshot.executesChildrenInSamplerOrder());

        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.getHeaders().add(new PerformanceRequestKeyValue(true, "X", "Y")));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.getExecutionScope().getGroupVariables().put("x", "y"));
    }

    @Test
    public void shouldNormalizeBlankAndNullValues() {
        List<PerformanceRequestKeyValue> headers = new ArrayList<>();
        headers.add(null);

        PerformanceRequestSnapshot snapshot = PerformanceRequestSnapshot.builder()
                .method(" ")
                .protocol(null)
                .proxyPolicy("invalid")
                .headers(headers)
                .formData(List.of(new PerformanceRequestFormDataPart(true, "file", "file", "/tmp/a.txt")))
                .build();

        assertEquals(snapshot.getMethod(), "GET");
        assertEquals(snapshot.getProtocol(), PerformanceRequestProtocol.HTTP);
        assertEquals(snapshot.getProxyPolicy(), PerformanceRequestSnapshot.PROXY_POLICY_DEFAULT);
        assertTrue(snapshot.getHeaders().isEmpty());
        assertEquals(snapshot.getFormData().get(0).getType(), PerformanceRequestFormDataPart.TYPE_FILE);
    }
}
