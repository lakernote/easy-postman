package com.laker.postman.codegen.request;

import com.laker.postman.request.model.AuthApiKeyPlacement;
import com.laker.postman.request.model.AuthType;
import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpFormUrlencoded;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.RequestBodyTypes;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class GoRequestCodeGeneratorTest {

    @Test
    public void shouldGenerateCompleteProgramWithoutUnusedBodyImports() {
        HttpRequestItem request = request();
        request.setHeadersList(null);
        request.setFormDataList(null);
        request.setUrlencodedList(null);

        String code = GoRequestCodeGenerator.generate(request);

        assertTrue(code.startsWith("package main\n\nimport ("), code);
        assertTrue(code.contains("func main()"), code);
        assertTrue(code.contains("func run() error"), code);
        assertTrue(code.contains("http.NewRequest(\"GET\", \"https://example.com\", nil)"), code);
        assertTrue(code.contains("defer resp.Body.Close()"), code);
        assertTrue(code.contains("io.Copy(os.Stdout, resp.Body)"), code);
        assertFalse(code.contains("\"strings\""), code);
        assertFalse(code.contains("\"mime"), code);
        assertFalse(code.contains("\"bytes\""), code);
    }

    @Test
    public void shouldEscapeRawBodyAndKeepEmptyAndWhitespaceBodies() {
        HttpRequestItem request = request();
        request.setMethod("POST");
        request.setBodyType(RequestBodyTypes.BODY_TYPE_RAW);
        request.setBody("\"\\\n\r\t\b\f\u0000\u001f\u007f中文😀\u2028\ufeff");

        String code = GoRequestCodeGenerator.generate(request);

        assertTrue(code.contains("strings.NewReader(\"\\\"\\\\\\n\\r\\t\\b\\f\\u0000\\u001f\\u007f中文😀\\u2028\\ufeff\")"), code);
        assertFalse(code.contains("\ufeff"), code);
        request.setBody("  \n");
        assertTrue(GoRequestCodeGenerator.generate(request).contains("strings.NewReader(\"  \\n\")"));
        request.setBody("");
        code = GoRequestCodeGenerator.generate(request);
        assertTrue(code.contains("strings.NewReader(\"\")"), code);
        assertTrue(code.contains("req.Header.Set(\"Content-Type\", \"application/json\")"), code);
    }

    @Test
    public void shouldEncodeLiteralFormValuesAndPreserveDuplicateKeys() {
        HttpRequestItem request = request();
        request.setMethod("POST");
        request.setBodyType(RequestBodyTypes.BODY_TYPE_FORM_URLENCODED);
        request.setUrlencodedList(Arrays.asList(
                new HttpFormUrlencoded(true, "tag", "a%20b"),
                new HttpFormUrlencoded(true, "tag", "中文 &"),
                new HttpFormUrlencoded(true, "empty", null),
                new HttpFormUrlencoded(false, "disabled", "ignored"),
                new HttpFormUrlencoded(true, "", "ignored"), null));

        String code = GoRequestCodeGenerator.generate(request);

        assertTrue(code.contains("strings.NewReader(\"tag=a%2520b&tag=%E4%B8%AD%E6%96%87+%26&empty=\")"), code);
        assertTrue(code.contains("application/x-www-form-urlencoded"), code);
        assertFalse(code.contains("disabled"), code);
        assertFalse(code.contains("tag=a%20b"), code);
    }

    @Test
    public void shouldEncodeLatin1RawBodyAsBytesAndKeepAsciiTemplatesReadable() {
        HttpRequestItem request = request();
        request.setBodyType(RequestBodyTypes.BODY_TYPE_RAW);
        request.setBody("{{name}}=café\n");
        request.setHeadersList(List.of(new HttpHeader(true, "Content-Type", "text/plain; charset=ISO-8859-1")));

        String code = GoRequestCodeGenerator.generate(request);

        assertTrue(code.contains("strings.NewReader(\"{{name}}=caf\\xe9\\x0a\")"), code);
        assertFalse(code.contains("\\xc3\\xa9"), code);
        assertTrue(code.contains("text/plain; charset=ISO-8859-1"), code);
    }

    @Test
    public void shouldEncodeUtf16RawBodyWithByteOrderMark() {
        HttpRequestItem request = request();
        request.setBodyType(RequestBodyTypes.BODY_TYPE_RAW);
        request.setBody("Aé");
        request.setHeadersList(List.of(new HttpHeader(true, "Content-Type", "text/plain; charset=UTF-16")));

        String code = GoRequestCodeGenerator.generate(request);

        assertTrue(code.contains("strings.NewReader(\"\\xfe\\xff\\x00A\\x00\\xe9\")"), code);
        assertFalse(code.contains("\"é\""), code);
    }

    @Test
    public void shouldOpenAndCloseBinaryFiles() {
        HttpRequestItem request = request();
        request.setBodyType(RequestBodyTypes.BODY_TYPE_BINARY);
        request.setBody("C:\\uploads\\file.bin");

        String code = GoRequestCodeGenerator.generate(request);

        assertTrue(code.contains("body, err := os.Open(\"C:\\\\uploads\\\\file.bin\")"), code);
        assertTrue(code.contains("defer body.Close()"), code);
        assertTrue(code.contains("fileStat, err := body.Stat()"), code);
        assertTrue(code.contains("http.NewRequest(\"GET\", \"https://example.com\", body)"), code);
        assertTrue(code.contains("req.ContentLength = fileStat.Size()"), code);
        assertTrue(code.contains("req.GetBody = func() (io.ReadCloser, error) {\n\t\treturn os.Open(\"C:\\\\uploads\\\\file.bin\")\n\t}"), code);
        assertFalse(code.contains("\"strings\""), code);
    }

    @Test
    public void shouldEscapeByteOrderMarksInBinaryPathsAndKeepDisabledRedirects() {
        HttpRequestItem request = request();
        request.setBodyType(RequestBodyTypes.BODY_TYPE_BINARY);
        request.setBody("/tmp/\ufefffile.bin");
        request.setFollowRedirects(false);

        String code = GoRequestCodeGenerator.generate(request);

        assertTrue(code.contains("body, err := os.Open(\"/tmp/\\ufefffile.bin\")"), code);
        assertTrue(code.contains("req.GetBody = func() (io.ReadCloser, error) {\n\t\treturn os.Open(\"/tmp/\\ufefffile.bin\")\n\t}"), code);
        assertTrue(code.contains("CheckRedirect: func(req *http.Request, via []*http.Request) error"), code);
        assertTrue(code.contains("return http.ErrUseLastResponse"), code);
        assertFalse(code.contains("\ufeff"), code);
    }

    @Test
    public void shouldWriteMultipartWithRepeatedFilesAndMatchingBoundary() {
        HttpRequestItem request = request();
        request.setBodyType(RequestBodyTypes.BODY_TYPE_FORM_DATA);
        request.setFormDataList(Arrays.asList(
                new HttpFormData(true, "note", HttpFormData.TYPE_TEXT, "中文\ntext"),
                new HttpFormData(true, "empty", HttpFormData.TYPE_TEXT, null),
                new HttpFormData(true, "files", HttpFormData.TYPE_FILE, "a.bin"),
                new HttpFormData(true, "files", HttpFormData.TYPE_FILE, "b.bin"),
                new HttpFormData(false, "disabled", HttpFormData.TYPE_FILE, "ignored"), null));

        String code = GoRequestCodeGenerator.generate(request);

        assertTrue(code.contains("writer.WriteField(\"note\", \"中文\\ntext\")"), code);
        assertTrue(code.contains("writer.WriteField(\"empty\", \"\")"), code);
        assertTrue(code.contains("addFilePart(writer, \"files\", \"a.bin\", \"application/octet-stream\")"), code);
        assertTrue(code.contains("addFilePart(writer, \"files\", \"b.bin\", \"application/octet-stream\")"), code);
        assertTrue(code.indexOf("writer.Close()") < code.indexOf("http.NewRequest("), code);
        assertTrue(code.contains("req.Header.Set(\"Content-Type\", writer.FormDataContentType())"), code);
        assertTrue(code.contains("fmt.Sprintf(`form-data; name=\"%s\"; filename=\"%s\"`"), code);
        assertTrue(code.contains("quoteEscaper.Replace(filepath.Base(filePath))"), code);
        assertTrue(code.contains("defer file.Close()"), code);
        assertTrue(code.contains("io.Copy(part, file)"), code);
        assertFalse(code.contains("disabled"), code);
    }

    @Test
    public void shouldQuoteUnicodeMultipartNamesWithoutExtendedParameters() {
        HttpRequestItem request = request();
        request.setBodyType(RequestBodyTypes.BODY_TYPE_FORM_DATA);
        request.setFormDataList(List.of(new HttpFormData(true, "附件", HttpFormData.TYPE_FILE, "/tmp/图片.png")));

        String code = GoRequestCodeGenerator.generate(request);

        assertTrue(code.contains("addFilePart(writer, \"附件\", \"/tmp/图片.png\""), code);
        assertTrue(code.contains("strings.NewReplacer(\"\\\\\", \"\\\\\\\\\", \"\\\"\", \"\\\\\\\"\", \"\\r\", \"%0D\", \"\\n\", \"%0A\")"), code);
        assertTrue(code.contains("quoteEscaper.Replace(fieldName)"), code);
        assertTrue(code.contains("fmt.Sprintf(`form-data; name=\"%s\"; filename=\"%s\"`"), code);
        assertFalse(code.contains("mime.FormatMediaType"), code);
        assertFalse(code.contains("\"mime\""), code);
        assertFalse(code.contains("filename*="), code);
        assertFalse(code.contains("name*="), code);
    }

    @Test
    public void shouldPreserveCustomMultipartTypeWithWriterBoundary() {
        HttpRequestItem request = request();
        request.setBodyType(RequestBodyTypes.BODY_TYPE_FORM_DATA);
        request.setFormDataList(List.of());
        request.setHeadersList(List.of(new HttpHeader(true, "content-type", "multipart/mixed; boundary=stale; charset=UTF-8")));

        String code = GoRequestCodeGenerator.generate(request);

        assertTrue(code.contains("mime.ParseMediaType(\"multipart/mixed; boundary=stale; charset=UTF-8\")"), code);
        assertTrue(code.contains("mediaParams[\"boundary\"] = writer.Boundary()"), code);
        assertTrue(code.contains("mime.FormatMediaType(mediaType, mediaParams)"), code);
        assertTrue(code.contains("writer.Close()"), code);
        assertFalse(code.contains("req.Header.Add(\"content-type\""), code);
        assertFalse(code.contains("func addFilePart("), code);
        assertFalse(code.contains("\"net/textproto\""), code);
    }

    @Test
    public void shouldKeepTemplateCredentialsAndRespectExplicitAuthAndHost() {
        HttpRequestItem request = request();
        request.setAuthType(AuthType.BASIC.getConstant());
        request.setAuthUsername("{{username}}");
        request.setAuthPassword("密码");

        String code = GoRequestCodeGenerator.generate(request);
        assertTrue(code.contains("req.SetBasicAuth(\"{{username}}\", \"密码\")"), code);

        request.setHeadersList(List.of(new HttpHeader(true, "authorization", "Custom explicit"),
                new HttpHeader(true, "Host", "virtual.example.com")));
        code = GoRequestCodeGenerator.generate(request);
        assertTrue(code.contains("req.Header.Add(\"authorization\", \"Custom explicit\")"), code);
        assertTrue(code.contains("req.Host = \"virtual.example.com\""), code);
        assertFalse(code.contains("SetBasicAuth"), code);
    }

    @Test
    public void shouldIncludeQueryAuthAndRedirectPolicy() {
        HttpRequestItem request = request();
        request.setAuthType(AuthType.API_KEY.getConstant());
        request.setAuthApiKeyPlacement(AuthApiKeyPlacement.QUERY_PARAMS.getConstant());
        request.setAuthApiKeyName("api_key");
        request.setAuthApiKeyValue("a&b c");
        request.setFollowRedirects(false);

        String code = GoRequestCodeGenerator.generate(request);

        assertTrue(code.contains("https://example.com?api_key=a%26b%20c"), code);
        assertTrue(code.contains("CheckRedirect: func(req *http.Request, via []*http.Request) error"), code);
        assertTrue(code.contains("return http.ErrUseLastResponse"), code);
    }

    private static HttpRequestItem request() {
        HttpRequestItem request = new HttpRequestItem();
        request.setUrl("https://example.com");
        return request;
    }
}
