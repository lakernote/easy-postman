package com.laker.postman.mock.cli;

import com.laker.postman.collection.model.CollectionDocument;
import com.laker.postman.mock.model.MockServerDefinition;

import java.nio.file.Path;
import java.util.List;

record MockRunWorkspace(Path directory,
                        CollectionDocument collections,
                        List<MockServerDefinition> servers) {
}
