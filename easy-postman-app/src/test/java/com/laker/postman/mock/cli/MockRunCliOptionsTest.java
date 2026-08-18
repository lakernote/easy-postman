package com.laker.postman.mock.cli;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class MockRunCliOptionsTest {

    @Test
    public void shouldParseWorkspaceServerAndNetworkOverrides() {
        MockRunCliOptions options = MockRunCliOptions.parse(new String[]{
                "mock", "run", "/tmp/workspace",
                "--server", "Payments", "--host", "0.0.0.0", "--port", "4100",
                "--api-key-env", "PAYMENTS_MOCK_KEY"
        });

        assertEquals(options.getWorkspace().toString(), "/tmp/workspace");
        assertEquals(options.getServerSelector(), "Payments");
        assertEquals(options.getHost(), "0.0.0.0");
        assertEquals(options.getPort(), Integer.valueOf(4100));
        assertEquals(options.getAccessKeyEnvironment(), "PAYMENTS_MOCK_KEY");
    }
}
