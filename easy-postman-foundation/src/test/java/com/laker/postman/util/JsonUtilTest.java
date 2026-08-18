package com.laker.postman.util;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class JsonUtilTest {

    @Test(description = "JSON 格式化应保留 JSON5/JSONC 风格注释")
    public void prettyPrintShouldPreserveJsonComments() {
        String formatted = JsonUtil.toJsonPrettyStr("""
                {
                  // request metadata
                  "url": "https://example.com/api", // keep inline
                  "nested": {
                    "enabled": true // keep tail
                  }
                }""");

        assertEquals(formatted, """
                {
                    // request metadata
                    "url": "https://example.com/api", // keep inline
                    "nested": {
                        "enabled": true // keep tail
                    }
                }""");
    }

    @Test(description = "URL 字符串里的双斜杠不应被误认为注释")
    public void prettyPrintShouldNotTreatUrlStringAsComment() {
        String formatted = JsonUtil.toJsonPrettyStr("""
                {"url":"https://example.com/a//b"}""");

        assertEquals(formatted, """
                {
                    "url": "https://example.com/a//b"
                }""");
    }

    @Test(description = "JSON 结构比较应忽略对象字段顺序，但保留数组顺序")
    public void structurallyEqualShouldCompareParsedJson() {
        assertTrue(JsonUtil.isStructurallyEqual("{\"a\":1,\"b\":[2,3]}", "{\"b\":[2,3],\"a\":1}"));
        assertFalse(JsonUtil.isStructurallyEqual("[1,2]", "[2,1]"));
        assertFalse(JsonUtil.isStructurallyEqual("plain text", "plain text"));
    }
}
