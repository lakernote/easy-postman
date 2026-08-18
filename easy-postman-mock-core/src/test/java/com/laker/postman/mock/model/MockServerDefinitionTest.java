package com.laker.postman.mock.model;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class MockServerDefinitionTest {

    @Test
    public void shouldSupportNoOrMultipleCollectionSources() {
        MockServerDefinition definition = new MockServerDefinition();

        assertTrue(definition.collectionSourceIds().isEmpty());

        definition.setCollectionSources(
                List.of(
                        new MockCollectionSource(" collection-a ", "Users"),
                        new MockCollectionSource("collection-b", "Orders"),
                        new MockCollectionSource("collection-a", "Duplicate")
                ));

        assertEquals(definition.collectionSourceIds(), List.of("collection-a", "collection-b"));
        assertEquals(definition.collectionSourceNames(), List.of("Users", "Orders"));
        assertTrue(definition.usesCollection("collection-b"));
        assertFalse(definition.usesCollection("collection-c"));
    }

    @Test
    public void shouldDeepCopyStandaloneRoutes() {
        MockResponse response = new MockResponse(200, Map.of("X-Test", "yes"), "ok");
        MockRoute route = new MockRoute(
                "route-1", "request-1", "Request", "example-1", "Success",
                "GET", "/users", Map.of(), Map.of(), "", response, ""
        );
        MockServerDefinition definition = new MockServerDefinition();
        definition.setStandaloneRoutes(List.of(route));

        MockServerDefinition copy = definition.copy();
        response.setBody("changed");

        assertEquals(copy.getStandaloneRoutes().get(0).response().getBody(), "ok");
    }
}
