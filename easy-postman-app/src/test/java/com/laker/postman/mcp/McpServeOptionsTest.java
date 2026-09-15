package com.laker.postman.mcp;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;

public class McpServeOptionsTest {

    @Test
    public void shouldParseWorkspaceAndResponseLimit() {
        McpServeOptions options = McpServeOptions.parse(new String[]{
                "mcp", "serve", "/tmp/demo workspace", "--max-response-bytes", "2048"
        }, 2);

        assertEquals(options.workspace(), "/tmp/demo workspace");
        assertEquals(options.maxResponseBytes(), 2048);
    }

    @Test
    public void shouldUseRegisteredWorkspacesWhenDirectoryIsOmitted() {
        McpServeOptions serve = McpServeOptions.parse(new String[]{
                "mcp", "serve", "--max-response-bytes", "2048"
        }, 2);
        assertEquals(serve.workspace(), null);
    }

    @Test
    public void shouldRejectUnsafeResponseLimit() {
        IllegalArgumentException exception = expectThrows(
                IllegalArgumentException.class,
                () -> McpServeOptions.parse(new String[]{
                        "mcp", "serve", "/tmp/demo", "--max-response-bytes", "128"
                }, 2)
        );

        assertEquals(exception.getMessage(), "--max-response-bytes must be between 1024 and 10485760");
    }

}
