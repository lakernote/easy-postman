package com.laker.postman.mock.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persisted configuration for one local mock server.
 */
@Data
@NoArgsConstructor
public class MockServerDefinition {
    public static final String LOOPBACK_HOST = "127.0.0.1";
    public static final String ALL_INTERFACES_HOST = "0.0.0.0";

    private String id = UUID.randomUUID().toString();
    private String name = "Mock Server";
    private String host = ALL_INTERFACES_HOST;
    private int port = 3001;
    private List<MockCollectionSource> collectionSources = new ArrayList<>();
    private List<MockRoute> standaloneRoutes = new ArrayList<>();
    private int fixedDelayMs;
    private boolean corsEnabled = true;
    private boolean matchRequestBody;
    private List<String> matchHeaderNames = new ArrayList<>();
    private String script = "";
    private String accessKey = "";
    private boolean autoStart;
    private boolean recordCallLogs = true;

    public MockServerDefinition copy() {
        MockServerDefinition copy = new MockServerDefinition();
        copy.id = id;
        copy.name = name;
        copy.host = host;
        copy.port = port;
        copy.collectionSources = normalizedCollectionSources().stream()
                .map(source -> new MockCollectionSource(source.id(), source.name()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        copy.standaloneRoutes = copyRoutes(standaloneRoutes);
        copy.fixedDelayMs = fixedDelayMs;
        copy.corsEnabled = corsEnabled;
        copy.matchRequestBody = matchRequestBody;
        copy.matchHeaderNames = matchHeaderNames == null ? new ArrayList<>() : new ArrayList<>(matchHeaderNames);
        copy.script = script;
        copy.accessKey = accessKey;
        copy.autoStart = autoStart;
        copy.recordCallLogs = recordCallLogs;
        return copy;
    }

    public List<String> collectionSourceIds() {
        return normalizedCollectionSources().stream().map(MockCollectionSource::id).toList();
    }

    public List<String> collectionSourceNames() {
        return normalizedCollectionSources().stream().map(MockCollectionSource::name).toList();
    }

    public List<MockCollectionSource> normalizedCollectionSources() {
        if (collectionSources == null || collectionSources.isEmpty()) {
            return List.of();
        }
        java.util.LinkedHashMap<String, MockCollectionSource> unique = new java.util.LinkedHashMap<>();
        for (MockCollectionSource source : collectionSources) {
            if (source != null && !source.id().isBlank()) {
                unique.putIfAbsent(source.id(), source);
            }
        }
        return List.copyOf(unique.values());
    }

    public boolean usesCollection(String id) {
        return id != null && collectionSourceIds().contains(id);
    }

    public String baseUrl() {
        String address = host == null || host.isBlank() ? LOOPBACK_HOST : host;
        if (ALL_INTERFACES_HOST.equals(address)) {
            address = LOOPBACK_HOST;
        }
        return "http://" + address + ":" + port;
    }

    private static List<MockRoute> copyRoutes(List<MockRoute> routes) {
        if (routes == null || routes.isEmpty()) {
            return new ArrayList<>();
        }
        return routes.stream()
                .filter(java.util.Objects::nonNull)
                .map(route -> new MockRoute(
                        route.routeId(), route.requestId(), route.requestName(),
                        route.exampleId(), route.exampleName(), route.method(), route.pathPattern(),
                        route.queryParameters(), route.requestHeaders(), route.requestBody(),
                        route.response(), route.script()
                ))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }
}
