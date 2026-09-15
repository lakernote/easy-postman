package com.laker.postman.mcp;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class McpLauncherTest {

    @Test
    public void shouldPrefixEmptyArgumentsWithMcpServe() {
        assertEquals(McpLauncher.toAppArguments(null), new String[]{"mcp", "serve"});
    }

    @Test
    public void shouldAppendWorkspaceAndOptionsAfterMcpServe() {
        assertEquals(
                McpLauncher.toAppArguments(new String[]{"/srv/api workspace", "--max-response-bytes", "2048"}),
                new String[]{"mcp", "serve", "/srv/api workspace", "--max-response-bytes", "2048"}
        );
    }
}
