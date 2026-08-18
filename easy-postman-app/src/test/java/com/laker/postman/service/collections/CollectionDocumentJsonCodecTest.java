package com.laker.postman.service.collections;

import cn.hutool.json.JSONArray;
import com.laker.postman.collection.model.CollectionDocument;
import com.laker.postman.collection.model.CollectionNode;
import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.SavedResponse;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class CollectionDocumentJsonCodecTest {

    @Test
    public void shouldRoundTripNestedGroupsRequestsAndSavedResponses() {
        RequestGroup rootGroup = new RequestGroup("Root");
        rootGroup.setId("group-root");
        RequestGroup childGroup = new RequestGroup("Child");
        childGroup.setId("group-child");
        HttpRequestItem request = new HttpRequestItem();
        request.setId("request-1");
        request.setName("Get user");
        request.setBody(null);
        SavedResponse savedResponse = new SavedResponse();
        savedResponse.setId("response-1");
        savedResponse.setMockDelayMs(75);
        savedResponse.setMockScript("pm.response.setStatusCode(201);");
        request.setResponse(List.of(savedResponse));

        CollectionNode rootNode = CollectionNode.group(rootGroup);
        CollectionNode childNode = CollectionNode.group(childGroup);
        childNode.addChild(CollectionNode.request(request));
        rootNode.addChild(childNode);
        CollectionDocument document = new CollectionDocument(List.of(rootNode));

        JSONArray encoded = CollectionDocumentJsonCodec.toJson(document);
        CollectionDocument decoded = CollectionDocumentJsonCodec.fromJson(encoded);

        CollectionNode decodedRoot = decoded.getRoots().get(0);
        CollectionNode decodedChild = decodedRoot.getChildren().get(0);
        HttpRequestItem decodedRequest = decodedChild.getChildren().get(0).asRequest();
        assertEquals(decodedRoot.asGroup().getId(), "group-root");
        assertEquals(decodedChild.asGroup().getId(), "group-child");
        assertEquals(decodedRequest.getId(), "request-1");
        assertEquals(decodedRequest.getBody(), "");
        assertEquals(decodedRequest.getResponse().get(0).getId(), "response-1");
        assertEquals(decodedRequest.getResponse().get(0).getMockDelayMs(), 75);
        assertEquals(decodedRequest.getResponse().get(0).getMockScript(), "pm.response.setStatusCode(201);");
    }

    @Test
    public void shouldRejectGroupsWithoutIds() {
        JSONArray input = new JSONArray();
        input.add(new cn.hutool.json.JSONObject()
                .set("type", "group")
                .set("name", "Broken")
                .set("children", new JSONArray()));

        try {
            CollectionDocumentJsonCodec.fromJson(input);
        } catch (IllegalArgumentException expected) {
            assertFalse(expected.getMessage().isBlank());
            return;
        }
        throw new AssertionError("Expected missing group id to be rejected");
    }

    @Test
    public void shouldSanitizeLargeOriginalRequestBodiesDuringRoundTrip() {
        RequestGroup rootGroup = new RequestGroup("Root");
        rootGroup.setId("group-root");
        HttpRequestItem request = new HttpRequestItem();
        request.setId("request-1");
        request.setName("Import");
        SavedResponse savedResponse = new SavedResponse();
        savedResponse.setId("response-1");
        SavedResponse.OriginalRequest originalRequest = new SavedResponse.OriginalRequest();
        originalRequest.setBody("x".repeat(512 * 1024));
        savedResponse.setOriginalRequest(originalRequest);
        request.setResponse(List.of(savedResponse));
        CollectionNode rootNode = CollectionNode.group(rootGroup);
        rootNode.addChild(CollectionNode.request(request));

        JSONArray encoded = CollectionDocumentJsonCodec.toJson(new CollectionDocument(List.of(rootNode)));
        CollectionDocument decoded = CollectionDocumentJsonCodec.fromJson(encoded);

        SavedResponse.OriginalRequest decodedOriginalRequest = decoded.getRoots().get(0)
                .getChildren().get(0)
                .asRequest()
                .getResponse().get(0)
                .getOriginalRequest();
        assertTrue(encoded.toString().length() < 80 * 1024);
        assertTrue(decodedOriginalRequest.isBodyTruncated());
        assertEquals(decodedOriginalRequest.getOriginalBodySize(), 512 * 1024);
        assertTrue(decodedOriginalRequest.getBody().length() <= 64 * 1024);
    }
}
