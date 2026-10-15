package com.laker.postman.codegen.request;

import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.http.runtime.okhttp.OkHttpRequestBuilder;
import com.laker.postman.request.model.HttpFormUrlencoded;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.RequestBodyTypes;
import okhttp3.Request;
import okio.Buffer;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class RequestCodeSnippetSupportTest {
    @Test
    public void formPayloadShouldMatchActualTransportAndPreserveDecodedValues() throws IOException {
        HttpRequestItem item = request(RequestBodyTypes.BODY_TYPE_FORM_URLENCODED);
        item.setUrlencodedList(List.of(
                new HttpFormUrlencoded(true, "tag", "a%20b"),
                new HttpFormUrlencoded(true, "tag", "a+b"),
                new HttpFormUrlencoded(true, "key &=+%20", "中文 ~!*() &="),
                new HttpFormUrlencoded(true, " ", "space key"),
                new HttpFormUrlencoded(true, "empty", null),
                new HttpFormUrlencoded(false, "disabled", "hidden"),
                new HttpFormUrlencoded(true, "", "hidden")));

        String payload = RequestCodeSnippetSupport.bodyPayload(item);
        assertEquals(payload, transportBody(item, true));
        assertTrue(payload.startsWith("tag=a%2520b&tag=a%2Bb&"), payload);

        List<String> decoded = new ArrayList<>();
        for (String pair : payload.split("&")) {
            String[] fields = pair.split("=", 2);
            decoded.add(URLDecoder.decode(fields[0], StandardCharsets.UTF_8));
            decoded.add(URLDecoder.decode(fields[1], StandardCharsets.UTF_8));
        }
        assertEquals(decoded, List.of("tag", "a%20b", "tag", "a+b", "key &=+%20",
                "中文 ~!*() &=", " ", "space key", "empty", ""));
    }

    @Test
    public void formTemplatesShouldRemainEditableWithoutChangingLiteralEncoding() {
        HttpRequestItem item = request(RequestBodyTypes.BODY_TYPE_FORM_URLENCODED);
        item.setUrlencodedList(List.of(new HttpFormUrlencoded(true,
                "prefix {{key}} +%20", "{{token}} &=% +")));

        assertEquals(RequestCodeSnippetSupport.bodyPayload(item),
                "prefix+{{key}}+%2B%2520={{token}}+%26%3D%25+%2B");
    }

    @Test
    public void emptyFormShouldMatchActualTransport() throws IOException {
        HttpRequestItem item = request(RequestBodyTypes.BODY_TYPE_FORM_URLENCODED);
        assertEquals(RequestCodeSnippetSupport.bodyPayload(item), transportBody(item, true));
        assertEquals(RequestCodeSnippetSupport.bodyPayload(item), "");
    }

    @DataProvider
    public Object[][] rawBodies() {
        String untouchedJson = "  {\"url\": \"https://example.com//path\", \"text\": \"/* literal */\"}  ";
        String invalidJson = "{ // keep invalid input\n\"x\":";
        String plainText = "// plain text\n/* also plain text */";
        return new Object[][]{
                {null, "{\n // remove_me\n \"x\": 1\n}", "{\"x\":1}"},
                {"application/json; charset=UTF-8", "{/* remove_me */\"url\":\"http://example.com/*literal*/\"}",
                        "{\"url\":\"http://example.com/*literal*/\"}"},
                {"application/json", untouchedJson, untouchedJson},
                {"application/json", invalidJson, invalidJson},
                {"text/plain", plainText, plainText},
                {"application/problem+json", "{/* unchanged */\"x\":1}", "{/* unchanged */\"x\":1}"}
        };
    }

    @Test(dataProvider = "rawBodies")
    public void rawPayloadShouldMatchActualTransport(String contentType, String body, String expected) throws IOException {
        HttpRequestItem item = request(RequestBodyTypes.BODY_TYPE_RAW);
        item.setBody(body);
        if (contentType != null) {
            item.setHeadersList(List.of(new HttpHeader(true, "Content-Type", contentType)));
        }

        assertEquals(RequestCodeSnippetSupport.bodyPayload(item), transportBody(item, false));
        assertEquals(RequestCodeSnippetSupport.bodyPayload(item), expected);
        assertEquals(item.getBody(), body, "Code generation must not change the editable draft");
    }

    private static String transportBody(HttpRequestItem item, boolean form) throws IOException {
        PreparedRequest prepared = new PreparedRequest();
        prepared.method = item.getMethod();
        prepared.url = item.getUrl();
        prepared.body = item.getBody();
        prepared.headersList = item.getHeadersList();
        prepared.urlencodedList = item.getUrlencodedList();
        Request request = form ? OkHttpRequestBuilder.buildFormRequest(prepared) : OkHttpRequestBuilder.buildRequest(prepared);
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        return buffer.readUtf8();
    }

    private static HttpRequestItem request(String bodyType) {
        HttpRequestItem item = new HttpRequestItem();
        item.setMethod("POST");
        item.setUrl("https://example.com");
        item.setBodyType(bodyType);
        return item;
    }
}
