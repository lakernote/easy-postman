package com.laker.postman.mcp;

import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class McpSensitiveDataRedactorTest {

    @Test
    public void shouldRedactCommonCredentialParametersAndUrlUserInfo() {
        String redacted = McpSensitiveDataRedactor.url(
                "https://user:password@example.test/callback?client_secret=one"
                        + "&refresh_token=two&id_token=three&x-amz-security-token=four&visible=value"
        );

        assertFalse(redacted.contains("user:password"));
        assertFalse(redacted.contains("one"));
        assertFalse(redacted.contains("two"));
        assertFalse(redacted.contains("three"));
        assertFalse(redacted.contains("four"));
        assertTrue(redacted.contains("visible=value"));
    }

    @Test
    public void shouldRedactEncodedCredentialParameterNames() {
        String redacted = McpSensitiveDataRedactor.url(
                "https://example.test?client%5Fsecret=hidden&query=public"
        );

        assertFalse(redacted.contains("hidden"));
        assertTrue(redacted.contains("query=public"));
    }

    @Test
    public void shouldRedactCredentialsAndLocalPathsFromMessages() {
        String redacted = McpSensitiveDataRedactor.message(
                "Request failed at /Users/alice/private/data.json with apiKey=key-value-123 "
                        + "and Authorization: Bearer bearer-value-456; fallback C:\\Users\\alice\\secret.txt"
        );

        assertFalse(redacted.contains("alice"));
        assertFalse(redacted.contains("key-value-123"));
        assertFalse(redacted.contains("bearer-value-456"));
        assertTrue(redacted.contains("<path>"));
        assertTrue(redacted.contains("<redacted>"));
    }

    @Test
    public void shouldCollapseAPathContainingSensitiveQueryDataToOnePlaceholder() {
        String redacted = McpSensitiveDataRedactor.message(
                "Workspace is not authorized: /Users/alice/private?api_key=secret-value"
        );

        assertFalse(redacted.contains("alice"));
        assertFalse(redacted.contains("secret-value"));
        assertTrue(redacted.endsWith("<path>"), redacted);
    }

    @Test
    public void shouldRedactStructuredSecretsAndEmbeddedUrlsFromResponsePayloads() {
        String redacted = McpSensitiveDataRedactor.payload(
                "{\"access_token\":\"response-secret\",\"callback\":"
                        + "\"https://example.test/return?client_secret=query-secret&visible=value\","
                        + "\"visible\":\"public\"}"
        );

        assertFalse(redacted.contains("response-secret"));
        assertFalse(redacted.contains("query-secret"));
        assertTrue(redacted.contains("\"access_token\":\"<redacted>\""), redacted);
        assertTrue(redacted.contains("visible=value"), redacted);
        assertTrue(redacted.contains("\"visible\":\"public\""), redacted);
    }

    @Test
    public void shouldRedactKnownSecretValuesEvenWithoutAKeyName() {
        String redacted = McpSensitiveDataRedactor.message(
                "Script failed with opaque-secret-value",
                java.util.List.of("opaque-secret-value")
        );

        assertFalse(redacted.contains("opaque-secret-value"));
        assertTrue(redacted.contains("<redacted>"));
    }
}
