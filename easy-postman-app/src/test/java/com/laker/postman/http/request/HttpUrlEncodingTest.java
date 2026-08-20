package com.laker.postman.http.request;

import com.laker.postman.request.model.HttpParam;
import com.laker.postman.request.util.HttpUrlUtil;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class HttpUrlEncodingTest {

    @Test
    public void shouldEncodeUnicodeQueryParameterValues() {
        String url = "https://httpbin.org/get?q=easytools&lang=en&page=1&size=10&sort=desc&filter=测试中文";

        String encoded = HttpUrlUtil.buildEncodedUrl(url, List.of());

        assertEquals(
                encoded,
                "https://httpbin.org/get?q=easytools&lang=en&page=1&size=10&sort=desc&filter=%E6%B5%8B%E8%AF%95%E4%B8%AD%E6%96%87"
        );
    }

    @Test
    public void shouldNotDoubleEncodeExistingEncodedUnicodeQueryValues() {
        String url = "https://httpbin.org/get?filter=%E6%B5%8B%E8%AF%95%E4%B8%AD%E6%96%87";

        String encoded = HttpUrlUtil.buildEncodedUrl(url, List.of());

        assertEquals(
                encoded,
                "https://httpbin.org/get?filter=%E6%B5%8B%E8%AF%95%E4%B8%AD%E6%96%87"
        );
    }

    @Test
    public void shouldEncodeUnicodeParamsFromParamsList() {
        String encoded = HttpUrlUtil.buildEncodedUrl(
                "https://httpbin.org/get?q=easytools",
                List.of(new HttpParam(true, "filter", "测试中文"))
        );

        assertEquals(
                encoded,
                "https://httpbin.org/get?q=easytools&filter=%E6%B5%8B%E8%AF%95%E4%B8%AD%E6%96%87"
        );
    }

    @Test
    public void shouldPreserveQueryFlagWithoutEqualsSign() {
        String encoded = HttpUrlUtil.buildEncodedUrl(
                "https://example.com/path",
                List.of(new HttpParam(true, "flag", null))
        );

        assertEquals(encoded, "https://example.com/path?flag");
    }

    @Test
    public void shouldParseQueryFlagAndIgnoreFragment() {
        List<HttpParam> params = HttpUrlUtil.parseQueryParams(
                "https://example.com/path?flag&name=value#section"
        );

        assertEquals(params.size(), 2);
        assertEquals(params.get(0).getKey(), "flag");
        assertNull(params.get(0).getValue());
        assertEquals(params.get(1).getKey(), "name");
        assertEquals(params.get(1).getValue(), "value");
    }

    @Test
    public void shouldAppendQueryParametersBeforeFragment() {
        String encoded = HttpUrlUtil.buildEncodedUrl(
                "https://example.com/path?first=1#section",
                List.of(new HttpParam(true, "second", "2"))
        );

        assertEquals(encoded, "https://example.com/path?first=1&second=2#section");
    }
}
