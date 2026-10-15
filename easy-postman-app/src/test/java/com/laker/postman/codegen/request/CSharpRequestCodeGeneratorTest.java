package com.laker.postman.codegen.request;

import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpFormUrlencoded;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.RequestAuthTypes;
import com.laker.postman.request.model.RequestBodyTypes;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class CSharpRequestCodeGeneratorTest {

    @Test
    public void shouldGenerateAStandaloneProgramWithDisposedHttpResources() {
        HttpRequestItem request = request();
        request.setHeadersList(List.of(
                new HttpHeader(true, "X-Tag", "one"),
                new HttpHeader(true, "X-Tag", "two"),
                new HttpHeader(false, "X-Disabled", "hidden")));

        String code = CSharpRequestCodeGenerator.generate(request);

        assertTrue(code.contains("using System.Net.Http;"), code);
        assertTrue(code.contains("private static async Task Main()"), code);
        assertTrue(code.contains("using var client = new HttpClient();"), code);
        assertTrue(code.contains("using var request = new HttpRequestMessage(new HttpMethod(\"GET\"), url);"), code);
        assertTrue(code.contains("using var response = await client.SendAsync(request);"), code);
        assertTrue(code.contains("Console.WriteLine(await response.Content.ReadAsStringAsync());"), code);
        assertEquals(occurrences(code, "request.Headers.TryAddWithoutValidation(\"X-Tag\""), 2, code);
        assertFalse(code.contains("X-Disabled"), code);
        assertFalse(code.contains("request.Content ="), code);
    }

    @Test
    public void shouldEscapeCSharpStringsAndKeepTheExactRawUtf8Payload() {
        HttpRequestItem request = request();
        request.setMethod("POST");
        request.setBodyType(RequestBodyTypes.BODY_TYPE_RAW);
        request.setBody("{\"text\":\"中文\"}\n\\path\t\r" + (char) 0 + (char) 0x1b + '\u2028' + "😀");
        request.setHeadersList(List.of(new HttpHeader(true, "Content-Type", "application/example+json")));

        String code = CSharpRequestCodeGenerator.generate(request);

        assertTrue(code.contains("new ByteArrayContent(Encoding.UTF8.GetBytes("), code);
        assertTrue(code.contains("\\\"text\\\":\\\"中文\\\""), code);
        assertTrue(code.contains("\\n\\\\path\\t\\r\\u0000\\u001b\\u2028\\ud83d\\ude00"), code);
        assertTrue(code.contains("request.Content.Headers.TryAddWithoutValidation(\"Content-Type\", \"application/example+json\");"), code);
        assertFalse(code.contains("request.Headers.TryAddWithoutValidation(\"Content-Type\""), code);
        assertEquals(occurrences(code, "\"Content-Type\""), 1, code);
    }

    @Test
    public void shouldPreserveWhitespaceAndEmptyRawBodies() {
        HttpRequestItem request = request();
        request.setBodyType(RequestBodyTypes.BODY_TYPE_RAW);
        request.setBody(" \t\n");

        String code = CSharpRequestCodeGenerator.generate(request);
        assertTrue(code.contains("Encoding.UTF8.GetBytes(\" \\t\\n\")"), code);
        assertTrue(code.contains("\"Content-Type\", \"application/json\""), code);

        request.setBody("");
        code = CSharpRequestCodeGenerator.generate(request);
        assertTrue(code.contains("Encoding.UTF8.GetBytes(\"\")"), code);
    }

    @DataProvider
    public Object[][] nonUtf8Bodies() {
        return new Object[][]{
                {"ISO-8859-1", "new byte[] { 0xE9 }"},
                {"UTF-16", "new byte[] { 0xFE, 0xFF, 0x00, 0xE9 }"}
        };
    }

    @Test(dataProvider = "nonUtf8Bodies")
    public void shouldMatchTheDeclaredRawBodyCharsetWithoutAdditionalDotNetEncodingPackages(String charset, String expectedBytes) {
        HttpRequestItem request = request();
        request.setMethod("POST");
        request.setBodyType(RequestBodyTypes.BODY_TYPE_RAW);
        request.setBody("é");
        request.setHeadersList(List.of(new HttpHeader(true, "Content-Type", "text/plain; charset=" + charset)));

        String code = CSharpRequestCodeGenerator.generate(request);

        assertTrue(code.contains("request.Content = new ByteArrayContent(" + expectedBytes + ");"), code);
        assertTrue(code.contains("\"Content-Type\", \"text/plain; charset=" + charset + "\""), code);
        assertFalse(code.contains("Encoding.UTF8.GetBytes("), code);
        assertFalse(code.contains("CodePagesEncodingProvider"), code);
    }

    @Test
    public void shouldPutContentHeadersOnAnEmptyContentAndComputeLengthAutomatically() {
        HttpRequestItem request = request();
        request.setHeadersList(List.of(
                new HttpHeader(true, "Content-Language", "zh-CN"),
                new HttpHeader(true, "Content-Encoding", "identity"),
                new HttpHeader(true, "Content-Disposition", "attachment"),
                new HttpHeader(true, "Content-Type", "application/json"),
                new HttpHeader(true, "Content-Length", "9999")));

        String code = CSharpRequestCodeGenerator.generate(request);

        assertTrue(code.contains("request.Content = new ByteArrayContent(Array.Empty<byte>());"), code);
        assertTrue(code.contains("request.Content.Headers.TryAddWithoutValidation(\"Content-Language\", \"zh-CN\");"), code);
        assertTrue(code.contains("request.Content.Headers.TryAddWithoutValidation(\"Content-Encoding\", \"identity\");"), code);
        assertTrue(code.contains("request.Content.Headers.TryAddWithoutValidation(\"Content-Disposition\", \"attachment\");"), code);
        assertTrue(code.contains("request.Content.Headers.TryAddWithoutValidation(\"Content-Type\", \"application/json\");"), code);
        assertFalse(code.contains("Content-Length"), code);
    }

    @Test
    public void shouldEncodeUrlencodedLiteralValuesAndPreserveDuplicates() {
        HttpRequestItem request = request();
        request.setMethod("POST");
        request.setBodyType(RequestBodyTypes.BODY_TYPE_FORM_URLENCODED);
        request.setUrlencodedList(List.of(
                new HttpFormUrlencoded(true, "tag", "a&b c"),
                new HttpFormUrlencoded(true, "tag", "already%20encoded"),
                new HttpFormUrlencoded(false, "tag", "hidden")));

        String code = CSharpRequestCodeGenerator.generate(request);

        assertTrue(code.contains("Encoding.UTF8.GetBytes(\"tag=a%26b+c&tag=already%2520encoded\")"), code);
        assertTrue(code.contains("\"Content-Type\", \"application/x-www-form-urlencoded\""), code);
        assertFalse(code.contains("hidden"), code);
        assertFalse(code.contains("tag=already%20encoded"), code);
    }

    @Test
    public void shouldGenerateBinaryBodyWithContentOwnedFileStream() {
        HttpRequestItem request = request();
        request.setMethod("PUT");
        request.setBodyType(RequestBodyTypes.BODY_TYPE_BINARY);
        request.setBody("C:\\uploads\\example.png");

        String code = CSharpRequestCodeGenerator.generate(request);

        assertTrue(code.contains("request.Content = new StreamContent(File.OpenRead(\"C:\\\\uploads\\\\example.png\"));"), code);
        assertTrue(code.contains("\"Content-Type\", \"application/octet-stream\""), code);
        assertFalse(code.contains("Encoding.UTF8.GetBytes(\"C:"), code);
    }

    @Test
    public void shouldPreserveDuplicateMultipartPartsAndLetTheFrameworkSetTheBoundary() throws IOException {
        HttpRequestItem request = request();
        request.setMethod("POST");
        request.setBodyType(RequestBodyTypes.BODY_TYPE_FORM_DATA);
        request.setHeadersList(List.of(new HttpHeader(true, "Content-Type", "multipart/form-data; boundary=stale")));
        Path photo = Files.createTempFile("csharp-snippet-photo-", ".png");
        Path document = Files.createTempFile("csharp-snippet-document-", ".pdf");
        try {
            request.setFormDataList(List.of(
                    new HttpFormData(true, "tag", HttpFormData.TYPE_TEXT, "one"),
                    new HttpFormData(true, "tag", HttpFormData.TYPE_TEXT, "two"),
                    new HttpFormData(true, "upload", HttpFormData.TYPE_FILE, photo.toString()),
                    new HttpFormData(true, "upload", HttpFormData.TYPE_FILE, document.toString()),
                    new HttpFormData(false, "hidden", HttpFormData.TYPE_TEXT, "disabled")));

            String code = CSharpRequestCodeGenerator.generate(request);

            assertTrue(code.contains("using var multipart = new MultipartFormDataContent();"), code);
            assertTrue(code.contains("request.Content = multipart;"), code);
            assertTrue(code.contains("using var textContent1 = new StringContent(\"one\", Encoding.UTF8);"), code);
            assertTrue(code.contains("using var textContent2 = new StringContent(\"two\", Encoding.UTF8);"), code);
            assertTrue(code.contains("multipart.Add(textContent1);"), code);
            assertTrue(code.contains("multipart.Add(textContent2);"), code);
            assertTrue(code.contains("using var fileContent1 = new StreamContent(File.OpenRead("), code);
            assertTrue(code.contains("using var fileContent2 = new StreamContent(File.OpenRead("), code);
            assertTrue(code.contains("multipart.Add(fileContent1);"), code);
            assertTrue(code.contains("multipart.Add(fileContent2);"), code);
            assertEquals(occurrences(code, "EscapeMultipartParameter(\"tag\")"), 2, code);
            assertEquals(occurrences(code, "EscapeMultipartParameter(\"upload\")"), 2, code);
            assertTrue(code.contains(photo.getFileName().toString()), code);
            assertTrue(code.contains(document.getFileName().toString()), code);
            assertTrue(code.contains("\"Content-Type\", \"image/png\""), code);
            assertTrue(code.contains("\"Content-Type\", \"application/pdf\""), code);
            assertTrue(code.contains("multipartContentType.Parameters.Remove(parameter);"), code);
            assertTrue(code.contains("multipart.Headers.ContentType = multipartContentType;"), code);
            assertFalse(code.contains("hidden"), code);
        } finally {
            Files.deleteIfExists(photo);
            Files.deleteIfExists(document);
        }
    }

    @Test
    public void shouldUseUtf8AndEscapeMultipartNamesAndFilenamesWithoutTypedDispositionSetters() {
        HttpRequestItem request = request();
        request.setBodyType(RequestBodyTypes.BODY_TYPE_FORM_DATA);
        request.setFormDataList(List.of(
                new HttpFormData(true, "na\"me\\中文\r\n", HttpFormData.TYPE_TEXT, "value"),
                new HttpFormData(true, "文件", HttpFormData.TYPE_FILE, "/tmp/图\"片.png")));

        String code = CSharpRequestCodeGenerator.generate(request);

        assertTrue(code.contains("multipart.HeaderEncodingSelector = (_, _) => Encoding.UTF8;"), code);
        assertEquals(occurrences(code, ".Headers.TryAddWithoutValidation(\"Content-Disposition\""), 2, code);
        assertTrue(code.contains("EscapeMultipartParameter(\"na\\\"me\\\\中文\\r\\n\")"), code);
        assertTrue(code.contains("EscapeMultipartParameter(\"文件\")"), code);
        assertTrue(code.contains("EscapeMultipartParameter(Path.GetFileName(\"/tmp/图\\\"片.png\"))"), code);
        assertTrue(code.contains("return value.Replace(\"\\\\\", \"\\\\\\\\\")"), code);
        assertTrue(code.contains(".Replace(\"\\\"\", \"\\\\\\\"\")"), code);
        assertTrue(code.contains(".Replace(\"\\r\", \"%0D\")"), code);
        assertTrue(code.contains(".Replace(\"\\n\", \"%0A\");"), code);
        assertFalse(code.contains("multipart.Add(fileContent1,"), code);
        assertFalse(code.contains("multipart.Add(textContent1,"), code);
    }

    @DataProvider
    public Object[][] customMultipartContentTypes() {
        return new Object[][]{
                {"multipart/related; BOUNDARY=stale; charset=utf-8"},
                {"multipart/form-data; charset=utf-8; profile=example"}
        };
    }

    @Test(dataProvider = "customMultipartContentTypes")
    public void shouldPreserveCustomMultipartMediaTypeAndParametersWhileReplacingBoundary(String configured) {
        HttpRequestItem request = request();
        request.setBodyType(RequestBodyTypes.BODY_TYPE_FORM_DATA);
        request.setHeadersList(List.of(new HttpHeader(true, "Content-Type", configured)));

        String code = CSharpRequestCodeGenerator.generate(request);

        assertTrue(code.contains("var multipartContentType = MediaTypeHeaderValue.Parse(\"" + configured + "\");"), code);
        assertTrue(code.contains("var multipartBoundary = multipart.Headers.ContentType!.Parameters.First(parameter =>"), code);
        assertTrue(code.contains("string.Equals(parameter.Name, \"boundary\", StringComparison.OrdinalIgnoreCase)).ToArray()"), code);
        assertTrue(code.contains("multipartContentType.Parameters.Remove(parameter);"), code);
        assertTrue(code.contains("multipartContentType.Parameters.Add(new NameValueHeaderValue(\"boundary\", multipartBoundary.Value));"), code);
        assertTrue(code.contains("multipart.Headers.ContentType = multipartContentType;"), code);
        assertFalse(code.contains("request.Content.Headers.TryAddWithoutValidation(\"Content-Type\""), code);
    }

    @Test
    public void shouldEncodeTemplatedBasicCredentialsAtRuntimeAsUtf8() {
        HttpRequestItem request = request();
        request.setAuthType(RequestAuthTypes.AUTH_TYPE_BASIC);
        request.setAuthUsername("{{username}}");
        request.setAuthPassword("密码");

        String code = CSharpRequestCodeGenerator.generate(request);

        assertTrue(code.contains("request.Headers.Authorization = new AuthenticationHeaderValue(\"Basic\","), code);
        assertTrue(code.contains("Convert.ToBase64String(Encoding.UTF8.GetBytes(\"{{username}}:密码\")));"), code);

        request.setHeadersList(List.of(new HttpHeader(true, "Authorization", "Bearer explicit")));
        code = CSharpRequestCodeGenerator.generate(request);
        assertTrue(code.contains("\"Authorization\", \"Bearer explicit\""), code);
        assertFalse(code.contains("AuthenticationHeaderValue(\"Basic\""), code);
    }

    @Test
    public void shouldScopeDigestCredentialsToTheRequestOriginAndDigestScheme() {
        HttpRequestItem request = request();
        request.setUrl("https://example.com:8443/nested/start?value=1");
        request.setAuthType(RequestAuthTypes.AUTH_TYPE_DIGEST);
        request.setAuthUsername("alice");
        request.setAuthPassword("secret");

        String code = CSharpRequestCodeGenerator.generate(request);

        assertTrue(code.contains("var url = \"https://example.com:8443/nested/start?value=1\";"), code);
        assertTrue(code.contains("var credentialOrigin = new Uri(new Uri(url), \"/\");"), code);
        assertTrue(code.contains("credentials.Add(credentialOrigin, \"Digest\", new NetworkCredential(\"alice\", \"secret\"));"), code);
        assertFalse(code.contains("credentials.Add(new Uri(url),"), code);
        assertTrue(code.contains("using var handler = new HttpClientHandler { Credentials = credentials };"), code);
        assertTrue(code.contains("using var client = new HttpClient(handler);"), code);
        assertEquals(occurrences(code, "client.SendAsync(request)"), 1, code);

        request.setHeadersList(List.of(new HttpHeader(true, "authorization", "Bearer explicit")));
        code = CSharpRequestCodeGenerator.generate(request);
        assertFalse(code.contains("CredentialCache"), code);
        assertFalse(code.contains("secret"), code);
        assertTrue(code.contains("\"authorization\", \"Bearer explicit\""), code);
    }

    @DataProvider
    public Object[][] digestRequestUrls() {
        return new Object[][]{
                {"https://example.com:8443/nested/start?value=1"},
                {"http://[::1]:8080/nested/start?value=1"},
                {"https://example.com/nested/start#fragment"},
                {"{{baseUrl}}/nested/start"}
        };
    }

    @Test(dataProvider = "digestRequestUrls")
    public void shouldResolveDigestOriginFromTheCompleteRequestUrlAtRuntime(String url) {
        HttpRequestItem request = request();
        request.setUrl(url);
        request.setAuthType(RequestAuthTypes.AUTH_TYPE_DIGEST);
        request.setAuthUsername("alice");

        String code = CSharpRequestCodeGenerator.generate(request);

        assertTrue(code.contains("var url = \"" + url + "\";"), code);
        assertTrue(code.contains("var credentialOrigin = new Uri(new Uri(url), \"/\");"), code);
        assertTrue(code.contains("credentials.Add(credentialOrigin, \"Digest\","), code);
        assertTrue(code.contains("new HttpRequestMessage(new HttpMethod(\"GET\"), url)"), code);
        assertFalse(code.contains("Credentials = new NetworkCredential("), code);
    }

    @Test
    public void shouldDisableAutomaticRedirectsOnlyWhenExplicitlyFalse() {
        HttpRequestItem request = request();
        assertFalse(CSharpRequestCodeGenerator.generate(request).contains("HttpClientHandler"));

        request.setFollowRedirects(false);
        String code = CSharpRequestCodeGenerator.generate(request);
        assertTrue(code.contains("using var handler = new HttpClientHandler { AllowAutoRedirect = false };"), code);
        assertTrue(code.contains("using var client = new HttpClient(handler);"), code);
        assertFalse(code.contains("Credentials ="), code);

        request.setFollowRedirects(true);
        assertFalse(CSharpRequestCodeGenerator.generate(request).contains("HttpClientHandler"));
    }

    @Test
    public void shouldCombineDigestAuthenticationAndDisabledRedirectsInOneHandler() {
        HttpRequestItem request = request();
        request.setAuthType(RequestAuthTypes.AUTH_TYPE_DIGEST);
        request.setAuthUsername("alice");
        request.setAuthPassword("secret");
        request.setFollowRedirects(false);

        String code = CSharpRequestCodeGenerator.generate(request);

        assertTrue(code.contains("using var handler = new HttpClientHandler { Credentials = credentials, AllowAutoRedirect = false };"), code);
        assertEquals(occurrences(code, "new HttpClientHandler"), 1, code);
        assertTrue(code.contains("credentials.Add(credentialOrigin, \"Digest\", new NetworkCredential(\"alice\", \"secret\"));"), code);
    }

    @Test
    public void shouldHandleNullInput() {
        assertEquals(CSharpRequestCodeGenerator.generate(null), "");
    }

    private static HttpRequestItem request() {
        HttpRequestItem request = new HttpRequestItem();
        request.setMethod("GET");
        request.setUrl("https://example.com/api");
        request.setBodyType(RequestBodyTypes.BODY_TYPE_NONE);
        return request;
    }

    private static int occurrences(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
    }
}
