package com.laker.postman.service.js.api;

import com.laker.postman.request.model.HttpParam;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class PostmanQueryCodecTest {

    @DataProvider
    public Object[][] queryComponents() {
        return new Object[][]{
                {"a&b", true, "a%26b"},
                {"a=b", true, "a%3Db"},
                {"a=b", false, "a=b"},
                {"{{query&key}}&tail", true, "{{query&key}}%26tail"},
                {"%61", true, "%61"},
                {null, true, ""}
        };
    }

    @Test(dataProvider = "queryComponents")
    public void shouldNormalizePostmanQueryComponents(String input, boolean encodeEquals, String expected) {
        assertEquals(PostmanQueryCodec.normalizeComponent(input, encodeEquals), expected);
    }

    @Test
    public void shouldPreserveEmptyAndValuelessQueryEntries() {
        List<HttpParam> params = PostmanQueryCodec.parse("flag=&bare&&tail=");

        assertEquals(params.size(), 4);
        assertEquals(params.get(0).getKey(), "flag");
        assertEquals(params.get(0).getValue(), "");
        assertEquals(params.get(1).getKey(), "bare");
        assertNull(params.get(1).getValue());
        assertNull(params.get(2).getKey());
        assertNull(params.get(2).getValue());
        assertEquals(params.get(3).getKey(), "tail");
        assertEquals(params.get(3).getValue(), "");
        assertEquals(PostmanQueryCodec.build(params), "flag=&bare&&tail=");
    }
}
