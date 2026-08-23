package com.laker.postman.service.js.api;

import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpFormUrlencoded;
import com.laker.postman.request.model.RequestBodyTypes;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class PostmanRequestBodyCodecTest {

    @Test
    public void shouldRenderPostmanUrlencodedBodyWithoutLosingValuelessEntries() {
        List<HttpFormUrlencoded> rows = List.of(
                new HttpFormUrlencoded(true, "flag", null),
                new HttpFormUrlencoded(true, "name", "easy postman"),
                new HttpFormUrlencoded(false, "ignored", "value")
        );

        assertEquals(PostmanRequestBodyCodec.render("urlencoded", null, rows),
                "flag&name=easy postman");
    }

    @Test
    public void shouldApplyFormDataDefinitionAsOneAtomicTransportMode() {
        PreparedRequest request = rawRequest("previous");
        Map<String, Object> disabledRow = new LinkedHashMap<>();
        disabledRow.put("key", "optional");
        disabledRow.put("value", null);
        disabledRow.put("disabled", true);

        assertTrue(PostmanRequestBodyCodec.applyToRequest(
                request,
                "formdata",
                null,
                null,
                List.of(
                        Map.of("key", "name", "value", "easy-postman", "type", "text"),
                        disabledRow
                ),
                null,
                false
        ));

        assertEquals(request.bodyType, RequestBodyTypes.BODY_TYPE_FORM_DATA);
        assertTrue(request.isMultipart);
        assertNull(request.body);
        assertTrue(request.urlencodedList.isEmpty());
        assertEquals(request.formDataList.size(), 2);
        assertEquals(request.formDataList.get(0).getValue(), "easy-postman");
        assertFalse(request.formDataList.get(1).isEnabled());
    }

    @Test
    public void shouldNotTreatUnsupportedGraphqlMutationAsRawBody() {
        PreparedRequest request = rawRequest("original");

        assertFalse(PostmanRequestBodyCodec.applyToRequest(
                request,
                "graphql",
                "replacement",
                null,
                null,
                null,
                false
        ));

        assertEquals(request.bodyType, RequestBodyTypes.BODY_TYPE_RAW);
        assertEquals(request.body, "original");
    }

    @Test
    public void shouldNormalizeBodyModeIndependentlyOfDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals(PostmanRequestBodyCodec.normalizeMode("FILE"), "file");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    public void shouldSerializeDisabledRowsUsingPostmanCollectionShape() {
        HttpFormData row = new HttpFormData(false, "attachment", HttpFormData.TYPE_FILE,
                "/tmp/report.txt", "report");

        Map<String, Object> json = PostmanRequestBodyCodec.toJson(
                "formdata",
                null,
                null,
                List.of(row),
                null,
                null,
                false
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> formdata = (List<Map<String, Object>>) json.get("formdata");
        assertEquals(formdata.get(0), Map.of(
                "key", "attachment",
                "type", "file",
                "src", "/tmp/report.txt",
                "description", "report",
                "disabled", true
        ));
    }

    @Test
    public void shouldRenderNonStringRawBodyUsingPostmanJavaScriptSemantics() {
        assertEquals(PostmanRequestBodyCodec.render("raw", 0, null), "");
        assertEquals(PostmanRequestBodyCodec.render("raw", false, null), "");
        assertEquals(PostmanRequestBodyCodec.render("raw", null, null), "");
        assertEquals(PostmanRequestBodyCodec.render("raw", true, null), "true");
        assertEquals(PostmanRequestBodyCodec.render("raw", Map.of("a", 1), null),
                "[object Object]");
        assertEquals(PostmanRequestBodyCodec.render("raw", List.of(1, false, Map.of("a", 1)), null),
                "1,false,[object Object]");

        PreparedRequest request = rawRequest("original");
        assertTrue(PostmanRequestBodyCodec.applyToRequest(
                request,
                "raw",
                Map.of("a", 1),
                null,
                null,
                null,
                false
        ));
        assertEquals(request.body, "[object Object]");
    }

    private static PreparedRequest rawRequest(String body) {
        PreparedRequest request = new PreparedRequest();
        request.bodyType = RequestBodyTypes.BODY_TYPE_RAW;
        request.body = body;
        request.formDataList = new ArrayList<>();
        request.urlencodedList = new ArrayList<>();
        return request;
    }
}
