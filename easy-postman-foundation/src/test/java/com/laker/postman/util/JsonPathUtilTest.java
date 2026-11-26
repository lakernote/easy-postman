package com.laker.postman.util;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class JsonPathUtilTest {

    @Test
    public void shouldExtractNestedArrayValueFromJsonString() {
        String json = """
                {
                  "users": [
                    {"name": "alice"},
                    {"name": "bob"}
                  ]
                }
                """;

        String value = JsonPathUtil.extractJsonPath(json, "$.users[1].name");

        assertEquals(value, "bob");
    }

    @Test
    public void shouldReturnNullForMissingPath() {
        String value = JsonPathUtil.extractJsonPath("{\"users\":[]}", "$.users[0].name");

        assertNull(value);
    }

    private static final String ORDERS = """
            {
              // comments are allowed
              "data": [
                {"id": 1, "city": "Jazan", "address": "1 الملك ,8114", "price": 8},
                {"id": 2, "city": "Riyadh", "address": "x", "price": 20}
              ]
            }
            """;

    @Test
    public void queryShouldSupportWildcardsAndPrettyPrintResult() {
        String result = JsonPathUtil.queryAsPrettyJson(ORDERS, "$.data[*].city");

        assertTrue(JsonUtil.isStructurallyEqual(result, "[\"Jazan\", \"Riyadh\"]"), result);
        assertTrue(result.contains("\n"), "result should be pretty printed");
    }

    @Test
    public void queryShouldSupportFiltersRecursiveDescentAndUnicode() {
        assertTrue(JsonUtil.isStructurallyEqual(
                JsonPathUtil.queryAsPrettyJson(ORDERS, "$.data[?(@.price > 10)].id"), "[2]"));
        assertTrue(JsonUtil.isStructurallyEqual(
                JsonPathUtil.queryAsPrettyJson(ORDERS, "$..address"),
                "[\"1 الملك ,8114\", \"x\"]"));
        assertEquals(JsonPathUtil.queryAsPrettyJson(ORDERS, "$.data[0].id"), "1");
        assertEquals(JsonPathUtil.queryAsPrettyJson(ORDERS, "$.data.length()"), "2");
    }

    @Test
    public void queryShouldSupportFiltersWithJsonArrayLiterals() {
        assertTrue(JsonUtil.isStructurallyEqual(
                JsonPathUtil.queryAsPrettyJson(ORDERS, "$.data[?(@.city in ['Jazan', 'Mecca'])].id"), "[1]"));
        assertTrue(JsonUtil.isStructurallyEqual(
                JsonPathUtil.queryAsPrettyJson(ORDERS, "$.data[?(@.id in [2, 3])].city"), "[\"Riyadh\"]"));
    }

    @Test
    public void queryShouldReturnNullForMissingDefinitePath() {
        assertNull(JsonPathUtil.queryAsPrettyJson(ORDERS, "$.missing.value"));
        assertEquals(JsonPathUtil.queryAsPrettyJson(ORDERS, "$.data[*].missing").replaceAll("\\s", ""), "[]");
    }

    @Test
    public void queryShouldRejectInvalidPathOrJson() {
        assertThrows(IllegalArgumentException.class, () -> JsonPathUtil.queryAsPrettyJson(ORDERS, "$.data[?(@.price >"));
        assertThrows(IllegalArgumentException.class, () -> JsonPathUtil.queryAsPrettyJson("not json", "$.a"));
        assertThrows(IllegalArgumentException.class, () -> JsonPathUtil.queryAsPrettyJson(ORDERS, " "));
    }

    @Test
    public void queryShouldNotMutateCachedDocument() {
        JsonPathUtil.JsonPathDocument document = JsonPathUtil.parseDocument("{\"a\":[1,2]}");

        assertTrue(JsonUtil.isStructurallyEqual(JsonPathUtil.queryAsPrettyJson(document, "$.a.append(9)"), "[1,2,9]"));
        assertTrue(JsonUtil.isStructurallyEqual(JsonPathUtil.queryAsPrettyJson(document, "$.a.append(9)"), "[1,2,9]"));
        assertTrue(JsonUtil.isStructurallyEqual(JsonPathUtil.queryAsPrettyJson(document, "$.a"), "[1,2]"));
    }

    @Test
    public void queryShouldPreserveNumberPrecision() {
        String json = "{\"p\":0.1000000000000000055511151231257827,\"q\":12345678901234567.89,"
                + "\"r\":1.10,\"s\":1e400,\"big\":123456789012345678901234567890}";

        String result = JsonPathUtil.queryAsPrettyJson(json, "$").replaceAll("\\s", "");

        assertEquals(result, "{\"p\":0.1000000000000000055511151231257827,\"q\":12345678901234567.89,"
                + "\"r\":1.1,\"s\":1E+400,\"big\":123456789012345678901234567890}");
        assertTrue(JsonUtil.isStructurallyEqual(JsonPathUtil.queryAsPrettyJson(
                "{\"items\":[{\"id\":1,\"price\":1.10},{\"id\":2,\"price\":0.99}]}",
                "$.items[?(@.price > 1.05)].id"), "[1]"));
    }

    @Test
    public void queryShouldMatchDecimalArrayAndObjectLiterals() {
        String json = "{\"items\":[{\"id\":1,\"v\":[1.1,2],\"o\":{\"x\":1.5}},{\"id\":2,\"v\":[3],\"o\":{}}]}";

        assertTrue(JsonUtil.isStructurallyEqual(JsonPathUtil.queryAsPrettyJson(json, "$.items[?(@.v == [1.1, 2])].id"), "[1]"));
        assertTrue(JsonUtil.isStructurallyEqual(JsonPathUtil.queryAsPrettyJson(json, "$.items[?(@.o == {\"x\":1.5})].id"), "[1]"));
    }

    @Test
    public void queryShouldNotReuseFunctionParametersFromPreviousDocument() {
        String expression = "$.n.append($.a)";
        JsonPathUtil.queryAsPrettyJson("{\"a\":\"1\",\"n\":[0]}", expression);

        String result = JsonPathUtil.queryAsPrettyJson("{\"a\":1,\"n\":[0]}", expression);

        assertTrue(JsonUtil.isStructurallyEqual(result, "[0,1]"), result);
    }

    @Test
    public void queryShouldSupportNullJsonRoot() {
        assertEquals(JsonPathUtil.queryAsPrettyJson("null", "$"), "null");
        assertNull(JsonPathUtil.queryAsPrettyJson("null", "$.a"));
        assertThrows(IllegalArgumentException.class, () -> JsonPathUtil.queryAsPrettyJson("null", "$.["));
    }

    @Test
    public void queryShouldNotMatchWrongRecordForHighPrecisionLiterals() {
        String json = "{\"items\":[{\"id\":1,\"p\":1.0,\"v\":[1.0]},{\"id\":2,\"p\":1.0000000000000001,\"v\":[1.0000000000000001]},"
                + "{\"id\":3,\"p\":0.1000000000000000055511151231257827,\"v\":[0.1000000000000000055511151231257827]}]}";

        assertTrue(JsonUtil.isStructurallyEqual(JsonPathUtil.queryAsPrettyJson(json, "$.items[?(@.p == 1.0000000000000001)].id"), "[2]"));
        for (String lossy : new String[]{"$.items[?(@.v == [1.0000000000000001])].id",
                "$.items[?(@.p in [1.0000000000000001])].id",
                "$.items[?(@.v anyof [1.0000000000000001])].id",
                "$.items[?(@.o == {\"x\":1.0000000000000001})].id"}) {
            IllegalArgumentException error = expectThrows(IllegalArgumentException.class,
                    () -> JsonPathUtil.queryAsPrettyJson(json, lossy));
            assertTrue(error.getMessage().contains("1.0000000000000001"), error.getMessage());
        }
        assertTrue(JsonUtil.isStructurallyEqual(JsonPathUtil.queryAsPrettyJson(json, "$.items[?(@.v == [1.0])].id"), "[1]"));
        assertTrue(JsonUtil.isStructurallyEqual(
                JsonPathUtil.queryAsPrettyJson(json, "$.items[?(@.p in [0.1000000000000000055511151231257827])].id"), "[3]"));
        // 字符串和正则中的数字不是数字字面量
        assertEquals(JsonPathUtil.queryAsPrettyJson(json, "$.items[?(@.id in ['1.0000000000000001'])].id").replaceAll("\\s", ""), "[]");
        assertEquals(JsonPathUtil.queryAsPrettyJson("{\"a\":[{\"s\":\"x[1.0000000000000001]\"}]}",
                "$.a[?(@.s =~ /x\\[1.0000000000000001\\]/)].s").replaceAll("\\s", ""), "[\"x[1.0000000000000001]\"]");
    }

    @Test
    public void queryShouldStopSerializingResultsOverLimit() {
        JsonPathUtil.JsonPathDocument document = JsonPathUtil.parseDocument("{\"items\":[\"" + "x".repeat(200) + "\"]}");

        JsonPathUtil.ResultTooLargeException error = expectThrows(JsonPathUtil.ResultTooLargeException.class,
                () -> JsonPathUtil.queryAsPrettyJson(document, "$.items", 100));

        assertEquals(error.getMaxResultChars(), 100);
        assertEquals(JsonPathUtil.queryAsPrettyJson(document, "$.items.length()", 100), "1");
    }
}
