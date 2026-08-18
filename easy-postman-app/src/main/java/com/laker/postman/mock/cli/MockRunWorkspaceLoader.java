package com.laker.postman.mock.cli;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.laker.postman.collection.model.CollectionDocument;
import com.laker.postman.mock.model.MockServerDefinition;
import com.laker.postman.service.collections.CollectionDocumentJsonCodec;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class MockRunWorkspaceLoader {
    private static final long MAX_MOCK_CONFIG_BYTES = 16L * 1024 * 1024;

    MockRunWorkspace load(Path workspaceDirectory) {
        if (workspaceDirectory == null) {
            throw new IllegalArgumentException("Workspace directory is required");
        }
        Path directory = workspaceDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Workspace directory does not exist: " + directory);
        }
        Path collectionsPath = requireFile(directory.resolve("collections.json"), "collections.json");
        Path serversPath = requireFile(directory.resolve("mock_servers.json"), "mock_servers.json");
        try {
            if (Files.size(serversPath) > MAX_MOCK_CONFIG_BYTES) {
                throw new IllegalArgumentException("Mock server configuration exceeds 16 MiB: " + serversPath);
            }
            CollectionDocument collections = CollectionDocumentJsonCodec.read(collectionsPath.toFile());
            JSONArray array = JSONUtil.readJSONArray(serversPath.toFile(), StandardCharsets.UTF_8);
            List<MockServerDefinition> servers = List.copyOf(JSONUtil.toList(array, MockServerDefinition.class));
            if (servers.isEmpty()) {
                throw new IllegalArgumentException("No Mock Server definitions found: " + serversPath);
            }
            return new MockRunWorkspace(directory, collections, servers);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to read Mock Server workspace: " + ex.getMessage(), ex);
        }
    }

    MockServerDefinition select(MockRunWorkspace workspace, String selector) {
        if (selector != null && !selector.isBlank()) {
            return workspace.servers().stream()
                    .filter(server -> selector.equals(server.getId()) || selector.equals(server.getName()))
                    .findFirst()
                    .map(MockServerDefinition::copy)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Mock Server not found: " + selector + ". Available: " + available(workspace.servers())
                    ));
        }
        if (workspace.servers().size() == 1) {
            return workspace.servers().get(0).copy();
        }
        throw new IllegalArgumentException(
                "Multiple Mock Servers found. Select one with --server <name-or-id>. Available: "
                        + available(workspace.servers())
        );
    }

    private Path requireFile(Path path, String name) {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalArgumentException("EasyPostman " + name + " does not exist: " + path);
        }
        return path;
    }

    private String available(List<MockServerDefinition> servers) {
        return servers.stream().map(MockServerDefinition::getName).toList().toString();
    }
}
