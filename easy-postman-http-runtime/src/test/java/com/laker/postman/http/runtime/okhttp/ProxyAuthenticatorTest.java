package com.laker.postman.http.runtime.okhttp;

import okhttp3.Credentials;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

public class ProxyAuthenticatorTest {
    private static final String CREDENTIAL = Credentials.basic("user", "secret");

    @Test
    public void shouldAnswerBasicChallengeOnce() {
        Request authorized = OkHttpClientManager.buildProxyAuthorizationRequest(
                challenge(new Request.Builder().url("https://example.com/").build(), "Basic realm=\"proxy\""),
                CREDENTIAL,
                false
        );

        assertNotNull(authorized);
        assertEquals(authorized.header("Proxy-Authorization"), CREDENTIAL);
        assertNull(OkHttpClientManager.buildProxyAuthorizationRequest(
                challenge(authorized, "Basic realm=\"proxy\""),
                CREDENTIAL,
                false
        ));
    }

    @Test
    public void shouldIgnoreUnsupportedChallenges() {
        assertNull(OkHttpClientManager.buildProxyAuthorizationRequest(
                challenge(new Request.Builder().url("https://example.com/").build(), "NTLM"),
                CREDENTIAL,
                true
        ));
    }

    @Test
    public void shouldOnlyAuthenticatePreemptivelyWhenAllowed() {
        Request request = new Request.Builder().url("https://example.com/").build();

        assertNotNull(OkHttpClientManager.buildProxyAuthorizationRequest(
                challenge(request, "OkHttp-Preemptive"),
                CREDENTIAL,
                true
        ));
        assertNull(OkHttpClientManager.buildProxyAuthorizationRequest(
                challenge(request, "OkHttp-Preemptive"),
                CREDENTIAL,
                false
        ));
    }

    private static Response challenge(Request request, String proxyAuthenticate) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(407)
                .message("Proxy Authentication Required")
                .header("Proxy-Authenticate", proxyAuthenticate)
                .build();
    }
}
