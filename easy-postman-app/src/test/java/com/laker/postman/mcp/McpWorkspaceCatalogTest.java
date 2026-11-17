package com.laker.postman.mcp;

import org.eclipse.jgit.api.Git;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.assertEquals;

public class McpWorkspaceCatalogTest {

    @Test
    public void explicitWorkspaceTypeShouldComeFromDisk() throws Exception {
        Path plain = Files.createTempDirectory("mcp-catalog-plain");
        Files.writeString(plain.resolve("collections.json"), "[]", StandardCharsets.UTF_8);
        assertEquals(new McpWorkspaceCatalog(new McpServeOptions(plain.toString(), 1024))
                .workspaces().get(0).type(), "LOCAL");

        Path repo = Files.createTempDirectory("mcp-catalog-repo");
        Git.init().setDirectory(repo.toFile()).call().close();
        Path embedded = Files.createDirectories(repo.resolve("api"));
        Files.writeString(embedded.resolve("collections.json"), "[]", StandardCharsets.UTF_8);
        assertEquals(new McpWorkspaceCatalog(new McpServeOptions(embedded.toString(), 1024))
                .workspaces().get(0).type(), "GIT");
    }
}
