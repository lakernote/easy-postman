package com.laker.postman.mock.model;

/**
 * Neutral reference to a collection whose requests and Examples feed a mock server.
 */
public record MockCollectionSource(String id, String name) {
    public MockCollectionSource {
        id = id == null ? "" : id.trim();
        name = name == null ? "" : name.trim();
    }
}
