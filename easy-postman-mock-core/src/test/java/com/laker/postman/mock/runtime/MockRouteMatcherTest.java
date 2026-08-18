package com.laker.postman.mock.runtime;

import com.laker.postman.mock.model.MockRequest;
import com.laker.postman.mock.model.MockResponse;
import com.laker.postman.mock.model.MockRoute;
import com.laker.postman.mock.model.MockServerDefinition;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class MockRouteMatcherTest {

    @Test
    public void shouldMatchPathVariablesQueryHeadersAndJsonBody() {
        MockServerDefinition definition = new MockServerDefinition();
        definition.setMatchRequestBody(true);
        definition.setMatchHeaderNames(List.of("X-Tenant"));
        MockRoute route = route("success", 200, "/users/{id}",
                Map.of("view", List.of("full")),
                Map.of("X-Tenant", List.of("acme")),
                "{\"name\":\"Ada\",\"active\":true}");
        MockRequest request = new MockRequest(
                "POST", "/users/42",
                Map.of("view", List.of("full"), "debug", List.of("1")),
                Map.of("x-tenant", List.of("acme")),
                "{ \"active\" : true, \"name\" : \"Ada\" }",
                Map.of()
        );

        MockRouteMatcher.Match match = MockRouteMatcher.match(List.of(route), request, definition).orElseThrow();

        assertEquals(match.route().exampleName(), "success");
        assertEquals(match.pathVariables(), Map.of("id", "42"));
    }

    @Test
    public void shouldHonorExampleSelectorHeader() {
        MockServerDefinition definition = new MockServerDefinition();
        MockRoute success = route("success", 200, "/users", Map.of(), Map.of(), "");
        MockRoute missing = route("missing", 404, "/users", Map.of(), Map.of(), "");
        MockRequest request = new MockRequest(
                "POST", "/users", Map.of(),
                Map.of(MockRouteMatcher.RESPONSE_CODE_HEADER, List.of("404")), "", Map.of()
        );

        MockRouteMatcher.Match match = MockRouteMatcher.match(
                List.of(success, missing), request, definition).orElseThrow();

        assertEquals(match.route().exampleName(), "missing");
        assertTrue(match.score() > 200);
    }

    @Test
    public void shouldHonorPerRequestBodyAndHeaderMatchingSwitches() {
        MockServerDefinition definition = new MockServerDefinition();
        MockRoute expected = route("tenant-a", 200, "/users", Map.of(),
                Map.of("X-Tenant", List.of("acme")), "{\"tier\":1}");
        MockRequest request = new MockRequest(
                "POST", "/users", Map.of(),
                Map.of(
                        "X-Tenant", List.of("acme"),
                        MockRouteMatcher.MATCH_REQUEST_HEADERS_HEADER, List.of("X-Tenant"),
                        MockRouteMatcher.MATCH_REQUEST_BODY_HEADER, List.of("true")
                ),
                "{ \"tier\" : 1 }", Map.of()
        );

        MockRouteMatcher.Match match = MockRouteMatcher.match(List.of(expected), request, definition).orElseThrow();

        assertEquals(match.route().exampleName(), "tenant-a");
    }

    private MockRoute route(String name, int code, String path,
                            Map<String, List<String>> query,
                            Map<String, List<String>> headers,
                            String body) {
        return new MockRoute(
                "route-" + name, "request-id", "Create user", "example-" + name, name,
                "POST", path, query, headers, body,
                new MockResponse(code, Map.of("Content-Type", "application/json"), "{\"ok\":true}"),
                ""
        );
    }
}
