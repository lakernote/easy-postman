package com.laker.postman.mock.app;

import com.laker.postman.mock.model.MockRequest;
import com.laker.postman.mock.model.MockResponse;
import com.laker.postman.mock.script.MockScriptContext;
import com.laker.postman.mock.script.MockState;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.testng.Assert.assertEquals;

public class MockScriptExecutorAdapterTest {

    @Test
    public void shouldExposeRequestMutableResponseAndDetachedSessionStateToGraalJs() throws Exception {
        MockRequest request = new MockRequest(
                "GET", "/users/42", Map.of(), Map.of("X-Tenant", java.util.List.of("acme")),
                "", Map.of("id", "42")
        );
        MockResponse response = new MockResponse();
        ConcurrentHashMap<String, Object> values = new ConcurrentHashMap<>();
        MockScriptContext context = new MockScriptContext(request, response, new MockState(values));

        new MockScriptExecutorAdapter().execute("""
                const next = Number(pm.state.get('count') || 0) + 1;
                pm.state.set('count', next);
                pm.response.setStatusCode(201);
                pm.response.setHeader('X-User', pm.request.pathVariable('id'));
                pm.response.setBody(JSON.stringify({ count: next, tenant: pm.request.header('X-Tenant') }));
                """, context);

        assertEquals(response.getStatusCode(), 201);
        assertEquals(response.getHeader("X-User"), "42");
        assertEquals(response.getBody(), "{\"count\":1,\"tenant\":\"acme\"}");
        assertEquals(values.get("count"), 1L);
    }

    @Test
    public void shouldSupportCodeMockConditionsFromRequestBody() throws Exception {
        MockRequest request = new MockRequest(
                "POST", "/payments", Map.of(), Map.of(), "{\"amount\":0.5}", Map.of()
        );
        MockResponse response = new MockResponse();
        MockScriptContext context = new MockScriptContext(
                request, response, new MockState(new ConcurrentHashMap<>())
        );

        new MockScriptExecutorAdapter().execute("""
                const input = JSON.parse(pm.request.body || '{}');
                if (Number(input.amount) === 0.5) {
                    pm.response.setStatusCode(402);
                    pm.response.setBody(JSON.stringify({ status: 'deny' }));
                }
                """, context);

        assertEquals(response.getStatusCode(), 402);
        assertEquals(response.getBody(), "{\"status\":\"deny\"}");
    }
}
