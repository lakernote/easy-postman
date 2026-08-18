package com.laker.postman.mock.app;

import com.laker.postman.mock.model.MockResponse;
import com.laker.postman.mock.model.MockRoute;
import com.laker.postman.mock.model.MockServerDefinition;
import com.laker.postman.mock.runtime.LocalMockServer;
import org.testng.annotations.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;

public class MockCodeMockEndToEndTest {

    @Test
    public void shouldReturnPaymentResponsesFromRouteCodeMock() throws Exception {
        MockServerDefinition definition = new MockServerDefinition();
        definition.setPort(0);
        MockRoute route = new MockRoute(
                "payments:success", "payments", "Payments", "success", "Success Response",
                "POST", "/payments", Map.of(), Map.of(), "",
                new MockResponse(200, Map.of("Content-Type", "application/json"), "{\"status\":\"approved\"}"),
                """
                        const input = JSON.parse(pm.request.body || '{}');
                        if (Number(input.amount) === 0.5) {
                            pm.response.setStatusCode(402);
                            pm.response.setBody(JSON.stringify({ status: 'deny' }));
                        } else if (Number(input.amount) === 1.1) {
                            pm.response.setStatusCode(200);
                            pm.response.setBody(JSON.stringify({ status: 'partial_approval' }));
                        }
                        """
        );

        try (LocalMockServer server = new LocalMockServer(
                definition, List.of(route), new MockScriptExecutorAdapter())) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> deny = client.send(request(server, 0.5), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> partial = client.send(request(server, 1.1), HttpResponse.BodyHandlers.ofString());

            assertEquals(deny.statusCode(), 402);
            assertEquals(deny.body(), "{\"status\":\"deny\"}");
            assertEquals(partial.statusCode(), 200);
            assertEquals(partial.body(), "{\"status\":\"partial_approval\"}");
        }
    }

    private HttpRequest request(LocalMockServer server, double amount) {
        return HttpRequest.newBuilder(URI.create(server.baseUrl() + "/payments"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"amount\":" + amount + "}"))
                .build();
    }
}
