package com.laker.postman.mock.app;

import com.laker.postman.collection.model.CollectionDocument;
import com.laker.postman.collection.model.CollectionNode;
import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.mock.model.MockRoute;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpParam;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.SavedResponse;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;

public class MockCollectionRouteProviderTest {

    @Test
    public void shouldMapSavedResponseOriginalRequestToMockRoute() {
        HttpRequestItem request = new HttpRequestItem();
        request.setId("request-1");
        request.setName("Get user");
        request.setMethod("GET");
        request.setUrl("{{baseUrl}}/users/{{id}}");

        SavedResponse.OriginalRequest original = new SavedResponse.OriginalRequest();
        original.setMethod("GET");
        original.setUrl("https://api.example.test/users/{{id}}?view=full");
        original.setParams(List.of(new HttpParam(true, "view", "full")));
        original.setHeaders(List.of(new HttpHeader(true, "X-Tenant", "acme")));

        SavedResponse example = new SavedResponse();
        example.setId("example-1");
        example.setName("success");
        example.setCode(200);
        example.setPreviewLanguage("json");
        example.setBody("{\"id\":42}");
        example.setOriginalRequest(original);
        example.setMockDelayMs(125);
        example.setMockScript("pm.response.setStatusCode(201);");
        request.setResponse(List.of(example));

        CollectionNode collection = collection("collection-1", "Users", request);
        MockRoute route = new MockCollectionRouteProvider()
                .buildRoutes(new CollectionDocument(List.of(collection)), List.of("collection-1"))
                .get(0);

        assertEquals(route.method(), "GET");
        assertEquals(route.pathPattern(), "/users/{{id}}");
        assertEquals(route.queryParameters().get("view"), List.of("full"));
        assertEquals(route.requestHeaders().get("X-Tenant"), List.of("acme"));
        assertEquals(route.exampleName(), "success");
        assertEquals(route.response().getHeader("Content-Type"), "application/json; charset=UTF-8");
        assertEquals(route.response().getDelayMs(), 125);
        assertEquals(route.script(), "pm.response.setStatusCode(201);");
    }

    @Test
    public void shouldExtractPathsFromAbsoluteVariableAndRelativeUrls() {
        assertEquals(MockCollectionRouteProvider.extractPath("https://api.example.test/v1/users?a=1"), "/v1/users");
        assertEquals(MockCollectionRouteProvider.extractPath("{{baseUrl}}/v1/users/{{id}}"), "/v1/users/{{id}}");
        assertEquals(MockCollectionRouteProvider.extractPath("v1/health"), "/v1/health");
    }

    @Test
    public void shouldBuildRoutesFromMultipleCollectionsInSelectedOrder() {
        CollectionNode users = collection("users", "Users", request("request-users", "/users"));
        CollectionNode orders = collection("orders", "Orders", request("request-orders", "/orders"));
        CollectionDocument document = new CollectionDocument(List.of(users, orders));

        List<MockRoute> routes = new MockCollectionRouteProvider()
                .buildRoutes(document, List.of("orders", "users"));

        assertEquals(routes.size(), 2);
        assertEquals(routes.get(0).pathPattern(), "/orders");
        assertEquals(routes.get(1).pathPattern(), "/users");
        assertEquals(routes.get(0).routeId(), "orders:request-orders:example-request-orders");
    }

    private CollectionNode collection(String id, String name, HttpRequestItem request) {
        RequestGroup group = new RequestGroup(name);
        group.setId(id);
        CollectionNode root = CollectionNode.group(group);
        root.addChild(CollectionNode.request(request));
        return root;
    }

    private HttpRequestItem request(String id, String path) {
        HttpRequestItem request = new HttpRequestItem();
        request.setId(id);
        request.setName(id);
        request.setMethod("GET");
        request.setUrl(path);
        SavedResponse response = new SavedResponse();
        response.setId("example-" + id);
        response.setName("OK");
        response.setCode(200);
        request.setResponse(List.of(response));
        return request;
    }
}
