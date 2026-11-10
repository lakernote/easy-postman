package com.laker.postman.collection;

import com.laker.postman.collection.model.CollectionDocument;
import com.laker.postman.collection.model.CollectionNode;
import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.SavedResponse;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.testng.Assert.*;

public class CollectionIdNormalizerTest {

    @Test
    public void shouldReassignDuplicateAndBlankExampleIds() {
        SavedResponse first = example("json");
        SavedResponse second = example("json");
        SavedResponse third = example("json");
        SavedResponse blank = example(null);
        SavedResponse unique = example("unique-id");

        CollectionNode root = group("g1");
        root.addChild(CollectionNode.request(request("r1", first, second, unique)));
        root.addChild(CollectionNode.request(request("r2", third, blank)));

        int reassigned = CollectionIdNormalizer.ensureUniqueIds(document(root));

        assertEquals(reassigned, 3);
        assertEquals(first.getId(), "json");
        assertEquals(unique.getId(), "unique-id");
        assertEquals(distinctCount(first.getId(), second.getId(), third.getId(), blank.getId(), unique.getId()), 5);
    }

    @Test
    public void shouldReassignDuplicateAndBlankRequestAndGroupIds() {
        HttpRequestItem firstRequest = request("same");
        HttpRequestItem duplicateRequest = request("same");
        HttpRequestItem blankRequest = request("");
        CollectionNode firstGroup = group("g");
        CollectionNode duplicateGroup = group("g");
        firstGroup.addChild(CollectionNode.request(firstRequest));
        firstGroup.addChild(duplicateGroup);
        duplicateGroup.addChild(CollectionNode.request(duplicateRequest));
        duplicateGroup.addChild(CollectionNode.request(blankRequest));

        assertEquals(CollectionIdNormalizer.ensureUniqueIds(document(firstGroup)), 3);
        assertEquals(firstGroup.asGroup().getId(), "g");
        assertNotEquals(duplicateGroup.asGroup().getId(), "g");
        assertEquals(firstRequest.getId(), "same");
        assertEquals(distinctCount(firstRequest.getId(), duplicateRequest.getId(), blankRequest.getId()), 3);
        assertFalse(blankRequest.getId().isBlank());
    }

    @Test
    public void shouldKeepAlreadyUniqueIds() {
        CollectionNode root = group("g1");
        root.addChild(CollectionNode.request(request("r1", example("a"), example("b"))));
        root.addChild(CollectionNode.request(request("r2", example("c"))));

        assertEquals(CollectionIdNormalizer.ensureUniqueIds(document(root)), 0);
        assertEquals(CollectionIdNormalizer.ensureUniqueIds(null), 0);
    }

    @Test
    public void shouldReassignIdsConflictingWithReservedIds() {
        SavedResponse importedExample = example("existing-example");
        SavedResponse freshExample = example("fresh-example");
        HttpRequestItem importedRequest = request("existing-request", importedExample, freshExample);
        CollectionNode importedGroup = group("existing-group");
        importedGroup.addChild(CollectionNode.request(importedRequest));
        CollectionDocument imported = document(importedGroup);

        CollectionNode existingGroup = group("existing-group");
        existingGroup.addChild(CollectionNode.request(request("existing-request", example("existing-example"))));
        CollectionIdNormalizer.UsedIds reserved = CollectionIdNormalizer.collectIds(document(existingGroup));

        assertEquals(reserved.groups(), Set.of("existing-group"));
        assertEquals(reserved.requests(), Set.of("existing-request"));
        assertEquals(reserved.examples(), Set.of("existing-example"));
        assertEquals(CollectionIdNormalizer.ensureUniqueIds(imported, reserved), 3);
        assertNotEquals(importedGroup.asGroup().getId(), "existing-group");
        assertNotEquals(importedRequest.getId(), "existing-request");
        assertNotEquals(importedExample.getId(), "existing-example");
        assertEquals(freshExample.getId(), "fresh-example");
        assertEquals(reserved.examples(), Set.of("existing-example"), "reserved ids must not be mutated");
    }

    @Test
    public void shouldCopyRequestWithNewIdsWithoutTouchingSource() {
        SavedResponse first = example("a");
        SavedResponse second = example("b");
        HttpRequestItem source = request("source", first, second);
        source.setName("Source");

        HttpRequestItem copy = CollectionIdNormalizer.copyRequestWithNewIds(source);

        assertEquals(source.getId(), "source");
        assertEquals(first.getId(), "a");
        assertEquals(second.getId(), "b");
        assertEquals(copy.getName(), "Source");
        assertNotEquals(copy.getId(), "source");
        assertEquals(copy.getResponse().size(), 2);
        assertNotSame(copy.getResponse().get(0), first);
        assertEquals(distinctCount("a", "b", copy.getResponse().get(0).getId(), copy.getResponse().get(1).getId()), 4);
    }

    private static long distinctCount(String... ids) {
        return new HashSet<>(List.of(ids)).size();
    }

    private static CollectionDocument document(CollectionNode... roots) {
        return new CollectionDocument(List.of(roots));
    }

    private static CollectionNode group(String id) {
        RequestGroup group = new RequestGroup(id);
        group.setId(id);
        return CollectionNode.group(group);
    }

    private static HttpRequestItem request(String id, SavedResponse... responses) {
        HttpRequestItem item = new HttpRequestItem();
        item.setId(id);
        item.setResponse(new ArrayList<>(List.of(responses)));
        return item;
    }

    private static SavedResponse example(String id) {
        SavedResponse response = new SavedResponse();
        response.setId(id);
        return response;
    }
}
