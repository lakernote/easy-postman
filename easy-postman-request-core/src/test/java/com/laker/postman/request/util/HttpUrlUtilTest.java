package com.laker.postman.request.util;

import com.laker.postman.request.model.HttpParam;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;

public class HttpUrlUtilTest {

    @Test
    public void shouldPreserveVariableNamesAndLiteralSuffixesInPathTemplates() {
        String path = "/{{folder}}/{name}-latest/v{version}beta/{file.name}/{tag$}/{part-id}.json";

        assertEquals(
                HttpUrlUtil.toRequestPathTemplate(path),
                "/{{folder}}/:{name}-latest/v:{version}beta/:{file.name}/:{tag$}/:part-id.json"
        );
    }

    @Test
    public void shouldResolveDelimitedAndShortVariablesOnlyInThePath() {
        String url = "https://example.com:8443/files/:{name}-latest/:{file.name}/:part-id/{literal}"
                + "?q=:{query}#/:{fragment}";
        List<HttpParam> pathVariables = List.of(
                new HttpParam(true, "name", "report"),
                new HttpParam(false, "file.name", "photo"),
                new HttpParam(true, "part-id", "42"),
                new HttpParam(true, "query", "unused"),
                new HttpParam(true, "fragment", "unused")
        );
        List<HttpUrlUtil.PathVariableSegment> segments = HttpUrlUtil.extractPathVariableSegments(url);

        assertEquals(segments.stream().map(HttpUrlUtil.PathVariableSegment::name).toList(),
                List.of("name", "file.name", "part-id"));
        HttpUrlUtil.PathVariableSegment first = segments.get(0);
        assertEquals(url.substring(first.startIndex(), first.endIndex()), ":{name}");
        assertEquals(HttpUrlUtil.replacePathVariables(url, pathVariables),
                "https://example.com:8443/files/report-latest/:{file.name}/42/{literal}?q=:{query}#/:{fragment}");
    }

    @Test
    public void shouldDecodeReadableTextButPreserveReservedQueryEscapesForDisplay() {
        String url = "https://example.com/api?" +
                "nested=https%3A%2F%2Fstatic.example.com%2Fpath%2Fk%3Dv%26next%3D1" +
                "&keyword=%E4%B8%AD%E6%96%87";

        String displayUrl = HttpUrlUtil.decodeQueryForDisplay(url);

        assertEquals(displayUrl,
                "https://example.com/api?" +
                        "nested=https%3A%2F%2Fstatic.example.com%2Fpath%2Fk%3Dv%26next%3D1" +
                        "&keyword=中文");
    }

    @Test
    public void shouldKeepBracketedIpv6HostWhenExtractingBaseUri() {
        assertEquals(HttpUrlUtil.extractBaseUri("http://[::1]:8080/api?ready=true"),
                "http://[::1]:8080");
        assertEquals(HttpUrlUtil.extractBaseUri("https://[2001:db8::10]/api"),
                "https://[2001:db8::10]");
        assertEquals(HttpUrlUtil.extractBaseUri("http://2001:db8::10/api"),
                "http://[2001:db8::10]");
    }

    @Test
    public void shouldBracketBareIpv6HostForHttpStyleUrls() {
        assertEquals(HttpUrlUtil.normalizeIpv6Url("http://2001:db8::10/api"),
                "http://[2001:db8::10]/api");
        assertEquals(HttpUrlUtil.normalizeIpv6Url("http://user:pass@2001:db8::10/api"),
                "http://user:pass@[2001:db8::10]/api");
        assertEquals(HttpUrlUtil.normalizeIpv6Url("http://fe80::1%25en0/api"),
                "http://[fe80::1%25en0]/api");
        assertEquals(HttpUrlUtil.normalizeIpv6Url("http://[::1]:8080/api"),
                "http://[::1]:8080/api");
    }

    @Test
    public void shouldNotTreatNonIpv6ColonAuthorityAsIpv6() {
        assertEquals(HttpUrlUtil.normalizeIpv6Url("http://host:part:other/api"),
                "http://host:part:other/api");
    }

    @Test
    public void shouldNormalizeIpv6HostForCertificateMatching() {
        assertEquals(HttpUrlUtil.normalizeHost(" [fe80::1%25en0] "), "fe80::1%25en0");
    }
}
