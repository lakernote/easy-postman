package com.laker.postman.mcp;

import com.laker.postman.workspace.cli.WorkspaceRunWorkspace;

import java.nio.file.Path;

record McpAuthorizedWorkspace(String id,
                              String name,
                              String description,
                              String type,
                              Path directory,
                              boolean current) {

    WorkspaceRunWorkspace runWorkspace() {
        return new WorkspaceRunWorkspace(name, directory);
    }
}
