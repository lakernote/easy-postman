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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

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

    @Test
    public void shouldNotServeEmptyBodyForFileExampleWithoutContent() {
        HttpRequestItem request = new HttpRequestItem();
        request.setId("request-file");
        request.setMethod("GET");
        request.setUrl("/export");

        SavedResponse.FileInfo fileInfo = new SavedResponse.FileInfo();
        fileInfo.setFileName("orders.xlsx");
        fileInfo.setExtension(".xlsx");
        SavedResponse fileExample = new SavedResponse();
        fileExample.setId("file");
        fileExample.setName("excel");
        fileExample.setCode(200);
        fileExample.setBody("");
        fileExample.setBodySize(4096);
        fileExample.setFile(fileInfo);
        fileExample.setHeaders(List.of(
                new HttpHeader(true, "Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                new HttpHeader(true, "Content-Disposition", "attachment; filename=orders.xlsx")));
        request.setResponse(List.of(fileExample));

        MockRoute route = new MockCollectionRouteProvider()
                .buildRoutes(new CollectionDocument(List.of(collection("c", "Files", request))), List.of("c"))
                .get(0);

        assertEquals(route.response().getStatusCode(), 501);
        assertEquals(route.response().getHeader("Content-Type"), "text/plain; charset=UTF-8");
        assertNull(route.response().getHeader("Content-Disposition"));
        assertTrue(route.response().getBody().contains("excel"), route.response().getBody());
    }

    @Test
    public void shouldServeTextAttachmentExampleSavedInBody() {
        HttpRequestItem request = new HttpRequestItem();
        request.setId("request-csv");
        request.setMethod("GET");
        request.setUrl("/export.csv");

        SavedResponse.FileInfo fileInfo = new SavedResponse.FileInfo();
        fileInfo.setFileName("orders.csv");
        fileInfo.setAttachment(true);
        fileInfo.setContentInBody(true);
        SavedResponse csvExample = new SavedResponse();
        csvExample.setId("csv");
        csvExample.setCode(200);
        csvExample.setBody("a,b\n1,2\n");
        csvExample.setFile(fileInfo);
        request.setResponse(List.of(csvExample));

        MockRoute route = new MockCollectionRouteProvider()
                .buildRoutes(new CollectionDocument(List.of(collection("c", "Files", request))), List.of("c"))
                .get(0);

        assertEquals(route.response().getStatusCode(), 200);
        assertEquals(route.response().getBody(), "a,b\n1,2\n");
    }

    @Test
    public void shouldKeepDeclaredCharsetSoMockReturnsOriginalBytes() {
        HttpRequestItem request = new HttpRequestItem();
        request.setId("request-gbk");
        request.setMethod("GET");
        request.setUrl("/export.csv");

        SavedResponse gbkExample = new SavedResponse();
        gbkExample.setId("gbk");
        gbkExample.setCode(200);
        gbkExample.setBody("名称,数量\n");
        gbkExample.setHeaders(List.of(new HttpHeader(true, "Content-Type", "text/csv; charset=GBK")));
        request.setResponse(List.of(gbkExample));

        MockRoute route = new MockCollectionRouteProvider()
                .buildRoutes(new CollectionDocument(List.of(collection("c", "Files", request))), List.of("c"))
                .get(0);

        assertEquals(route.response().getHeader("Content-Type"), "text/csv; charset=GBK");
        assertEquals(route.response().getBody(), "名称,数量\n");
    }

    @Test
    public void shouldNotRejectFileExamplesThatHaveNoBodyByDefinition() {
        SavedResponse.FileInfo fileInfo = new SavedResponse.FileInfo();
        fileInfo.setFileName("orders.xlsx");
        SavedResponse example = new SavedResponse();
        example.setFile(fileInfo);
        example.setBodySize(4096);

        assertTrue(MockCollectionRouteProvider.isFileContentUnavailable(example, "GET", 200));
        assertFalse(MockCollectionRouteProvider.isFileContentUnavailable(example, "HEAD", 200));
        assertFalse(MockCollectionRouteProvider.isFileContentUnavailable(example, "GET", 204));
        assertFalse(MockCollectionRouteProvider.isFileContentUnavailable(example, "GET", 205));
        assertFalse(MockCollectionRouteProvider.isFileContentUnavailable(example, "GET", 304));

        example.setBodySize(0);
        assertFalse(MockCollectionRouteProvider.isFileContentUnavailable(example, "GET", 200), "零字节文件无需内容");
    }

    @Test
    public void shouldRejectLegacyBinaryPlaceholderExample() {
        SavedResponse legacy = new SavedResponse();
        legacy.setBody("[Binary content saved as temp file]");
        legacy.setBodySize(4096);
        legacy.setHeaders(List.of(new HttpHeader(true, "Content-Type", "application/pdf")));

        assertTrue(MockCollectionRouteProvider.isFileContentUnavailable(legacy, "GET", 200));
    }
}
