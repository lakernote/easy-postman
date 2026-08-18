package com.laker.postman.mock.cli;

import cn.hutool.json.JSONUtil;
import com.laker.postman.collection.model.CollectionDocument;
import com.laker.postman.collection.model.CollectionNode;
import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.mock.app.MockCollectionRouteProvider;
import com.laker.postman.mock.model.MockCollectionSource;
import com.laker.postman.mock.model.MockResponse;
import com.laker.postman.mock.model.MockRoute;
import com.laker.postman.mock.model.MockServerDefinition;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.SavedResponse;
import com.laker.postman.service.collections.CollectionDocumentJsonCodec;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;

public class MockRunWorkspaceLoaderTest {

    @Test
    public void shouldLoadWorkspaceSelectServerAndBuildRoutes() throws Exception {
        Path workspaceDirectory = Files.createTempDirectory("easy-postman-mock-cli-");
        RequestGroup collection = new RequestGroup("Payments");
        collection.setId("collection-1");
        HttpRequestItem request = new HttpRequestItem();
        request.setId("request-1");
        request.setName("Charge");
        request.setMethod("POST");
        request.setUrl("http://127.0.0.1:3001/charge");
        SavedResponse response = new SavedResponse();
        response.setId("response-1");
        response.setName("approved");
        response.setCode(200);
        response.setBody("{\"status\":\"approved\"}");
        request.setResponse(List.of(response));
        CollectionNode root = CollectionNode.group(collection);
        root.addChild(CollectionNode.request(request));
        CollectionDocumentJsonCodec.write(
                workspaceDirectory.resolve("collections.json").toFile(),
                new CollectionDocument(List.of(root))
        );

        MockServerDefinition definition = new MockServerDefinition();
        definition.setId("mock-1");
        definition.setName("Payments Mock");
        definition.setCollectionSources(List.of(new MockCollectionSource("collection-1", "Payments")));
        definition.setHost(MockServerDefinition.ALL_INTERFACES_HOST);
        definition.setAccessKey("secret");
        definition.setStandaloneRoutes(List.of(new MockRoute(
                "standalone-1", "request-standalone", "Health", "example-standalone", "OK",
                "GET", "/health", Map.of(), Map.of(), "",
                new MockResponse(200, Map.of("Content-Type", "application/json"), "{\"ok\":true}"), ""
        )));
        Files.writeString(
                workspaceDirectory.resolve("mock_servers.json"),
                JSONUtil.toJsonPrettyStr(List.of(definition)),
                StandardCharsets.UTF_8
        );

        MockRunWorkspace workspace = new MockRunWorkspaceLoader().load(workspaceDirectory);
        MockServerDefinition selected = new MockRunWorkspaceLoader().select(workspace, "Payments Mock");
        List<MockRoute> routes = new MockCollectionRouteProvider()
                .buildRoutes(workspace.collections(), selected.collectionSourceIds());

        assertEquals(selected.getHost(), MockServerDefinition.ALL_INTERFACES_HOST);
        assertEquals(selected.getAccessKey(), "secret");
        assertEquals(selected.getStandaloneRoutes().size(), 1);
        assertEquals(selected.getStandaloneRoutes().get(0).pathPattern(), "/health");
        assertEquals(routes.size(), 1);
        assertEquals(routes.get(0).pathPattern(), "/charge");
    }
}
