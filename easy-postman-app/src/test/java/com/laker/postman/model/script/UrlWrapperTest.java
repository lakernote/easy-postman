package com.laker.postman.service.js.api;

import com.laker.postman.request.model.HttpParam;
import org.testng.annotations.Test;

import java.util.ArrayList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * UrlWrapper 测试
 */
public class UrlWrapperTest {

    @Test
    public void testGetPath_withFullUrl() {
        UrlWrapper url = new UrlWrapper("https://api.example.com:8080/users/123?id=1&name=test", new ArrayList<>());
        assertEquals(url.getPath(), "/users/123");
    }

    @Test
    public void testGetPath_withoutQueryParams() {
        UrlWrapper url = new UrlWrapper("https://api.example.com/users/123", new ArrayList<>());
        assertEquals(url.getPath(), "/users/123");
    }

    @Test
    public void testGetPath_rootPath() {
        UrlWrapper url = new UrlWrapper("https://api.example.com", new ArrayList<>());
        assertEquals(url.getPath(), "/");
    }

    @Test
    public void testGetPath_withoutProtocol() {
        UrlWrapper url = new UrlWrapper("api.example.com/users/123", new ArrayList<>());
        assertEquals(url.getPath(), "/users/123");
    }

    @Test
    public void testGetPath_complexPath() {
        UrlWrapper url = new UrlWrapper("https://api.example.com/api/v1/users/123/orders/456?sort=desc", new ArrayList<>());
        assertEquals(url.getPath(), "/api/v1/users/123/orders/456");
    }

    @Test
    public void testGetPath_emptyUrl() {
        UrlWrapper url = new UrlWrapper("", new ArrayList<>());
        assertEquals(url.getPath(), "");
    }

    @Test
    public void testGetPath_nullUrl() {
        UrlWrapper url = new UrlWrapper(null, new ArrayList<>());
        assertEquals(url.getPath(), "");
    }

    @Test
    public void testGetHost_withPort() {
        UrlWrapper url = new UrlWrapper("https://api.example.com:8080/users", new ArrayList<>());
        assertEquals(url.getHost(), "api.example.com");
    }

    @Test
    public void testGetHost_withoutPort() {
        UrlWrapper url = new UrlWrapper("https://api.example.com/users", new ArrayList<>());
        assertEquals(url.getHost(), "api.example.com");
    }

    @Test
    public void testGetHost_withSubdomain() {
        UrlWrapper url = new UrlWrapper("https://api.staging.example.com/users", new ArrayList<>());
        assertEquals(url.getHost(), "api.staging.example.com");
    }

    @Test
    public void testGetQueryString_withParams() {
        UrlWrapper url = new UrlWrapper("https://api.example.com/users?id=1&name=test", new ArrayList<>());
        assertEquals(url.getQueryString(), "id=1&name=test");
    }

    @Test
    public void testGetQueryString_withoutParams() {
        UrlWrapper url = new UrlWrapper("https://api.example.com/users", new ArrayList<>());
        assertEquals(url.getQueryString(), "");
    }

    @Test
    public void testGetQueryString_emptyParams() {
        UrlWrapper url = new UrlWrapper("https://api.example.com/users?", new ArrayList<>());
        assertEquals(url.getQueryString(), "");
    }

    @Test
    public void testToString_preservesRawPostmanQueryEntries() {
        UrlWrapper emptyQuery = new UrlWrapper("https://api.example.com/users?", new ArrayList<>());
        UrlWrapper trailingEmptyParam = new UrlWrapper("https://api.example.com/users?id=1&", new ArrayList<>());
        UrlWrapper middleEmptyParam = new UrlWrapper("https://api.example.com/users?id=1&&active=true", new ArrayList<>());
        UrlWrapper encodedParam = new UrlWrapper("https://api.example.com/users?%61=%26", new ArrayList<>());

        assertEquals(emptyQuery.toString(), "https://api.example.com/users?");
        assertEquals(emptyQuery.getPathWithQuery(), "/users");
        assertEquals(trailingEmptyParam.toString(), "https://api.example.com/users?id=1&");
        assertEquals(middleEmptyParam.toString(), "https://api.example.com/users?id=1&&active=true");
        assertEquals(middleEmptyParam.query.count(), 3);
        assertNull(middleEmptyParam.query.all().get(1).key);
        assertEquals(encodedParam.toString(), "https://api.example.com/users?%61=%26");
        assertEquals(encodedParam.query.all().get(0).key, "%61");
        assertEquals(encodedParam.query.all().get(0).value, "%26");
    }

    @Test
    public void testToString_deduplicatesPercentEncodedQueryKeysAgainstDecodedParams() {
        ArrayList<HttpParam> params = new ArrayList<>();
        params.add(new HttpParam(true, "a", "1", "metadata"));
        ArrayList<HttpParam> staleParams = new ArrayList<>();
        staleParams.add(new HttpParam(true, "a", "stale", "metadata"));

        UrlWrapper url = new UrlWrapper("https://api.example.com/users?%61=1", params);
        UrlWrapper urlWithStaleParam = new UrlWrapper("https://api.example.com/users?%61=raw", staleParams);

        assertEquals(url.toString(), "https://api.example.com/users?a=1");
        assertEquals(params.size(), 1);
        assertEquals(params.get(0).getKey(), "a");
        assertEquals(params.get(0).getDescription(), "metadata");
        assertEquals(urlWithStaleParam.toString(), "https://api.example.com/users?%61=raw");
        assertEquals(staleParams.size(), 1);
        assertEquals(staleParams.get(0).getKey(), "%61");
        assertEquals(staleParams.get(0).getValue(), "raw");
        assertEquals(staleParams.get(0).getDescription(), "metadata");
    }

    @Test
    public void testToString_keepsEncodedQueryReconciliationIdempotent() {
        ArrayList<HttpParam> params = new ArrayList<>();
        params.add(new HttpParam(true, "a", "stale", "metadata"));

        UrlWrapper first = new UrlWrapper("https://api.example.com/users?%61=raw", params);
        UrlWrapper second = new UrlWrapper(first.toString(), params);
        UrlWrapper third = new UrlWrapper(second.toString(), params);

        assertEquals(third.toString(), "https://api.example.com/users?%61=raw");
        assertEquals(params.size(), 1);
        assertEquals(params.get(0).getDescription(), "metadata");
    }

    @Test
    public void testGetPathWithQuery_withParams() {
        UrlWrapper url = new UrlWrapper("https://api.example.com/users?id=1&name=test", new ArrayList<>());
        assertEquals(url.getPathWithQuery(), "/users?id=1&name=test");
    }

    @Test
    public void testGetPathWithQuery_withoutParams() {
        UrlWrapper url = new UrlWrapper("https://api.example.com/users", new ArrayList<>());
        assertEquals(url.getPathWithQuery(), "/users");
    }

    @Test
    public void testGetPathWithQuery_rootPath() {
        UrlWrapper url = new UrlWrapper("https://api.example.com", new ArrayList<>());
        assertEquals(url.getPathWithQuery(), "/");
    }

    @Test
    public void testToString() {
        UrlWrapper url = new UrlWrapper("https://api.example.com/users/123", new ArrayList<>());
        assertEquals(url.toString(), "https://api.example.com/users/123");
    }
}
