package com.laker.postman.service.js.api;

import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpFormUrlencoded;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpParam;
import org.graalvm.polyglot.Value;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class JsListWrapperAdapterTest {

    @Test
    public void shouldKeepHeaderLookupCaseInsensitiveAndObjectKeysNormalized() {
        List<HttpHeader> rows = new ArrayList<>();
        JsListWrapper<HttpHeader> headers = new JsListWrapper<>(rows, JsListWrapper.ListType.HEADER);

        headers.add(Map.of("key", "X-Trace", "value", "first"));
        assertSame(headers.one("x-trace"), headers.idx(0));
        assertEquals(headers.toObject(), Map.of("x-trace", "first"));

        assertFalse(headers.upsert(Map.of("key", "X-TRACE", "value", "second")));
        assertEquals(rows.size(), 1);
        assertEquals(rows.get(0).getValue(), "second");

        headers.remove("x-Trace");
        assertTrue(rows.isEmpty());
    }

    @Test
    public void shouldKeepQueryLookupCaseSensitiveAndValuelessEntriesLossless() {
        List<HttpParam> rows = new ArrayList<>();
        JsListWrapper<HttpParam> query = new JsListWrapper<>(rows, JsListWrapper.ListType.PARAM);

        Map<String, Object> valueless = new LinkedHashMap<>();
        valueless.put("key", "Flag");
        valueless.put("value", null);
        query.add(valueless);

        assertNull(query.one("flag"));
        assertEquals(query.one("Flag").value, null);
        assertEquals(query.toObject(), Map.of("Flag", ""));
        assertNull(rows.get(0).getValue());
    }

    @Test
    public void shouldMapFormDataFileSourceWithoutLeakingListRulesIntoWrapper() {
        List<HttpFormData> rows = new ArrayList<>();
        JsListWrapper<HttpFormData> formData = new JsListWrapper<>(rows, JsListWrapper.ListType.FORM_DATA);

        formData.add(Map.of(
                "key", "attachment",
                "type", "file",
                "src", List.of("/tmp/report.txt"),
                "disabled", true
        ));

        assertEquals(rows.size(), 1);
        assertTrue(rows.get(0).isFile());
        assertEquals(rows.get(0).getValue(), "/tmp/report.txt");
        assertFalse(rows.get(0).isEnabled());
        assertEquals(formData.idx(0).src, List.of("/tmp/report.txt"));
    }

    @Test
    public void shouldTrackSameValueAssignmentWithoutChangingDisabledState() {
        HttpHeader row = new HttpHeader(false, "X-Trace", "same");
        List<HttpHeader> rows = new ArrayList<>(List.of(row));
        AtomicInteger mutations = new AtomicInteger();
        JsListWrapper<HttpHeader> headers = new JsListWrapper<>(
                rows,
                JsListWrapper.ListType.HEADER,
                mutations::incrementAndGet
        );

        headers.idx(0).putMember("value", Value.asValue("same"));
        headers.sync();

        assertEquals(mutations.get(), 1);
        assertEquals(row.getValue(), "same");
        assertFalse(row.isEnabled());
    }

    @Test
    public void shouldApplyPostmanKeyOnlyUpsertDefaultsByItemType() {
        HttpHeader headerRow = new HttpHeader(false, "X-Flag", "old", "header description");
        JsListWrapper<HttpHeader> headers = new JsListWrapper<>(
                new ArrayList<>(List.of(headerRow)),
                JsListWrapper.ListType.HEADER
        );
        assertFalse(headers.upsert(Map.of("key", "X-Flag")));
        assertEquals(headerRow.getValue(), "");
        assertEquals(headerRow.getDescription(), "header description");
        assertFalse(headerRow.isEnabled());

        Map<String, Object> explicitNullHeader = new LinkedHashMap<>();
        explicitNullHeader.put("key", "X-Flag");
        explicitNullHeader.put("value", null);
        headers.upsert(explicitNullHeader);
        assertNull(headerRow.getValue());

        HttpParam queryRow = new HttpParam(true, "flag", "old", "query description");
        JsListWrapper<HttpParam> query = new JsListWrapper<>(
                new ArrayList<>(List.of(queryRow)),
                JsListWrapper.ListType.PARAM
        );
        assertFalse(query.upsert(Map.of("key", "flag")));
        assertNull(queryRow.getValue());
        assertEquals(queryRow.getDescription(), "query description");

        HttpFormUrlencoded urlencodedRow = new HttpFormUrlencoded(
                true,
                "flag",
                "old",
                "body description"
        );
        JsListWrapper<HttpFormUrlencoded> urlencoded = new JsListWrapper<>(
                new ArrayList<>(List.of(urlencodedRow)),
                JsListWrapper.ListType.URLENCODED
        );
        assertFalse(urlencoded.upsert(Map.of("key", "flag")));
        assertNull(urlencodedRow.getValue());
        assertEquals(urlencodedRow.getDescription(), "body description");
    }

    @Test
    public void shouldApplyPostmanDefaultsWhenUpdatingElementsDirectly() {
        HttpHeader headerRow = new HttpHeader(true, "X-Flag", "old", "kept");
        JsListWrapper<HttpHeader> headers = new JsListWrapper<>(
                new ArrayList<>(List.of(headerRow)),
                JsListWrapper.ListType.HEADER
        );
        headers.idx(0).update(Map.of("key", "X-Flag"));
        headers.sync();
        assertEquals(headerRow.getValue(), "");
        assertEquals(headerRow.getDescription(), "kept");

        headers.idx(0).update(Map.of("value", "new"));
        headers.sync();
        assertEquals(headerRow.getKey(), "");
        assertEquals(headerRow.getValue(), "new");

        HttpParam queryRow = new HttpParam(true, "flag", "old", "kept");
        JsListWrapper<HttpParam> query = new JsListWrapper<>(
                new ArrayList<>(List.of(queryRow)),
                JsListWrapper.ListType.PARAM
        );
        query.idx(0).update(Map.of("key", "flag"));
        query.sync();
        assertNull(queryRow.getValue());

        query.idx(0).update(Map.of("value", "new"));
        query.sync();
        assertNull(queryRow.getKey());
        assertEquals(queryRow.getValue(), "new");

        HttpFormUrlencoded urlencodedRow = new HttpFormUrlencoded(true, "bodyFlag", "old", "kept");
        JsListWrapper<HttpFormUrlencoded> urlencoded = new JsListWrapper<>(
                new ArrayList<>(List.of(urlencodedRow)),
                JsListWrapper.ListType.URLENCODED
        );
        urlencoded.idx(0).update(Map.of("key", "bodyFlag"));
        urlencoded.sync();
        assertNull(urlencodedRow.getValue());
    }
}
