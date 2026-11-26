package com.laker.postman.util;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.InvalidJsonException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.json.AbstractJsonProvider;
import com.jayway.jsonpath.spi.json.JsonSmartJsonProvider;
import com.jayway.jsonpath.spi.mapper.MappingException;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import lombok.experimental.UtilityClass;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public class JsonPathUtil {

    /**
     * 小数先解析为 BigDecimal，再由 {@link #normalizeNumbers(Object)} 把 double 能精确表示的值转为 Double：
     * 既与过滤表达式中由 json-smart 解析的数组/对象字面量（Double）保持相等，又不丢失
     * double 无法表示的原始精度（如 0.1000000000000000055、12345678901234567.89、1e400）。
     */
    private static final JsonMapper QUERY_MAPPER = JsonUtil.sharedMapper().rebuild()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    private static final Configuration QUERY_CONFIGURATION = Configuration.builder()
            .jsonProvider(new JacksonValueJsonProvider())
            .mappingProvider(new JacksonValueMappingProvider())
            .build();

    /**
     * 已解析的 JSON 文档，可对同一响应重复执行多个 JSONPath 查询而无需重新解析。
     * 查询不会修改该文档，因此可在线程间共享。
     */
    public record JsonPathDocument(Object value) {
    }

    /**
     * 解析 JSON（支持带注释的 JSON）供 {@link #queryAsPrettyJson(JsonPathDocument, String)} 复用。
     *
     * @throws IllegalArgumentException JSON 无效时抛出
     */
    public static JsonPathDocument parseDocument(String json) {
        try {
            return new JsonPathDocument(QUERY_CONFIGURATION.jsonProvider().parse(json));
        } catch (InvalidJsonException e) {
            throw new IllegalArgumentException("Invalid JSON: " + e.getMessage(), e);
        }
    }

    /**
     * 使用完整 JSONPath 语法（通配符、递归下降、切片、过滤表达式等）查询 JSON，并以美化 JSON 返回结果。
     *
     * @param json       JSON 文本，支持带注释的 JSON
     * @param expression JSONPath 表达式，如 {@code $.data[*].name}
     * @return 美化后的查询结果；确定路径不存在时返回 {@code null}
     * @throws IllegalArgumentException JSON 或表达式无效时抛出，消息可直接展示给用户
     */
    public static String queryAsPrettyJson(String json, String expression) {
        requireExpression(expression);
        return queryAsPrettyJson(parseDocument(json), expression);
    }

    /**
     * 对已解析的文档执行 JSONPath 查询，语义同 {@link #queryAsPrettyJson(String, String)}。
     */
    public static String queryAsPrettyJson(JsonPathDocument document, String expression) {
        return queryAsPrettyJson(document, expression, Integer.MAX_VALUE);
    }

    /**
     * 同 {@link #queryAsPrettyJson(JsonPathDocument, String)}，但美化结果超过 {@code maxResultChars} 个字符时
     * 立即停止序列化并抛出 {@link ResultTooLargeException}，避免为超大结果构建完整字符串。
     */
    public static String queryAsPrettyJson(JsonPathDocument document, String expression, int maxResultChars) {
        requireExpression(expression);
        Object result;
        try {
            String trimmed = expression.trim();
            requireExactLiteralNumbers(trimmed);
            // 每次重新编译：Jayway 全局缓存的 JsonPath 会在函数参数中保留上一次求值的结果，且非线程安全
            JsonPath path = JsonPath.compile(trimmed);
            if (document.value() == null) {
                // Jayway 不接受 null 文档；JSON 根值为 null 时只有根路径能命中
                return path.getPath().equals("$") ? "null" : null;
            }
            // Jayway 函数中只有 append() 会就地修改被查询的数组，此时查询副本以保证缓存文档不变；
            // 其余查询直接读取，避免大文档每次查询都复制整棵树
            Object model = trimmed.contains("append") ? deepCopy(document.value()) : document.value();
            result = path.read(model, QUERY_CONFIGURATION);
        } catch (PathNotFoundException e) {
            return null;
        } catch (RuntimeException e) {
            // 过滤表达式求值也可能抛出非 JsonPathException（如类型不匹配），统一转换为可展示的错误
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw new IllegalArgumentException(message, e);
        }
        BoundedWriter writer = new BoundedWriter(maxResultChars);
        try {
            QUERY_MAPPER.writerWithDefaultPrettyPrinter().writeValue(writer, result);
        } catch (RuntimeException e) {
            if (writer.exceeded) {
                throw new ResultTooLargeException(maxResultChars);
            }
            throw e;
        }
        return writer.toString();
    }

    /** 查询结果超过调用方指定的大小上限。 */
    public static final class ResultTooLargeException extends RuntimeException {
        private final int maxResultChars;

        ResultTooLargeException(int maxResultChars) {
            super("JSONPath result exceeds " + maxResultChars + " characters");
            this.maxResultChars = maxResultChars;
        }

        public int getMaxResultChars() {
            return maxResultChars;
        }
    }

    private static final class BoundedWriter extends Writer {
        private final StringBuilder buffer = new StringBuilder();
        private final int maxChars;
        private boolean exceeded;

        BoundedWriter(int maxChars) {
            this.maxChars = maxChars;
        }

        @Override
        public void write(char[] chars, int offset, int length) {
            if (length > maxChars - buffer.length()) {
                exceeded = true;
                throw new IllegalStateException("result too large");
            }
            buffer.append(chars, offset, length);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        @Override
        public String toString() {
            return buffer.toString();
        }
    }

    /**
     * 过滤表达式中的数组/对象字面量（如 {@code @.v == [1.5]}、{@code @.p in [1.5]}）由 Jayway 交给 json-smart 解析，
     * 18 个字符以内的小数一律转为 double。若 double 无法精确表示原文（如 1.0000000000000001 会变成 1.0），
     * 比较会命中错误的记录，因此直接拒绝；标量比较（{@code @.p == 1.0000000000000001}）按 BigDecimal 精确比较，不受影响。
     */
    private static void requireExactLiteralNumbers(String expression) {
        Deque<Character> brackets = new ArrayDeque<>();
        int length = expression.length();
        for (int i = 0; i < length; i++) {
            char c = expression.charAt(i);
            if (c == '\'' || c == '"') {
                i = skipQuoted(expression, i, c);
            } else if (c == '/' && isRegexStart(expression, i)) {
                i = skipQuoted(expression, i, '/');
            } else if (c == '[' || c == '{' || c == '(') {
                brackets.push(c);
            } else if (c == ']' || c == '}' || c == ')') {
                brackets.poll();
            } else if (startsNumber(expression, i)) {
                Matcher matcher = NUMBER_LITERAL.matcher(expression).region(i, length);
                if (matcher.lookingAt()) {
                    String literal = matcher.group();
                    Character enclosing = brackets.peek();
                    if (enclosing != null && enclosing != '(' && !literalComparesExactly(literal)) {
                        throw new IllegalArgumentException("Number " + literal
                                + " in an array/object literal exceeds double precision and cannot be compared exactly");
                    }
                    i = matcher.end() - 1;
                }
            }
        }
    }

    private static final JsonSmartJsonProvider LITERAL_PROVIDER = new JsonSmartJsonProvider();

    private static final Pattern NUMBER_LITERAL = Pattern.compile("-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?");

    private static boolean startsNumber(String expression, int index) {
        char c = expression.charAt(index);
        if (!(Character.isDigit(c) || c == '-')) {
            return false;
        }
        if (index == 0) {
            return true;
        }
        char previous = expression.charAt(index - 1);
        // 属性名（@.a1）、数组索引后缀等不是独立的数字字面量
        return !(Character.isLetterOrDigit(previous) || previous == '_' || previous == '.' || previous == '$'
                || previous == '@' || previous == '-');
    }

    /** 与 Jayway 的字面量解析结果（json-smart）对比本工具对同一数字的解析结果。 */
    private static boolean literalComparesExactly(String literal) {
        if (literal.indexOf('.') < 0 && literal.indexOf('e') < 0 && literal.indexOf('E') < 0) {
            return true;
        }
        Object asLiteral;
        try {
            // 与 Jayway 过滤字面量相同：json-smart MODE_PERMISSIVE
            asLiteral = LITERAL_PROVIDER.parse(literal);
        } catch (InvalidJsonException e) {
            return true;
        }
        return normalizeNumbers(new BigDecimal(literal)).equals(asLiteral);
    }

    private static boolean isRegexStart(String expression, int index) {
        int i = index - 1;
        while (i >= 0 && Character.isWhitespace(expression.charAt(i))) {
            i--;
        }
        return i >= 1 && expression.charAt(i) == '~' && expression.charAt(i - 1) == '=';
    }

    private static int skipQuoted(String expression, int start, char quote) {
        for (int i = start + 1; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == quote) {
                return i;
            }
        }
        return expression.length() - 1;
    }

    private static Object normalizeNumbers(Object value) {
        if (value instanceof BigDecimal decimal) {
            double asDouble = decimal.doubleValue();
            boolean exact = Double.isFinite(asDouble)
                    && new BigDecimal(Double.toString(asDouble)).compareTo(decimal) == 0;
            return exact ? asDouble : decimal;
        }
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> mutable = (Map<Object, Object>) map;
            mutable.replaceAll((key, child) -> normalizeNumbers(child));
            return map;
        }
        if (value instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Object> mutable = (List<Object>) list;
            mutable.replaceAll(JsonPathUtil::normalizeNumbers);
            return list;
        }
        return value;
    }

    private static Object deepCopy(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>(Math.max(16, (int) (map.size() / 0.75f) + 1));
            map.forEach((key, child) -> copy.put(key, deepCopy(child)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(child -> copy.add(deepCopy(child)));
            return copy;
        }
        return value;
    }

    private static void requireExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("JSONPath expression is empty");
        }
    }

    /**
     * 从JSON对象中提取指定路径的值，支持简单的$.a.b[0].c格式
     *
     * @param jsonObj 可以是JSONObject/JSONArray/String
     * @param path    形如$.a.b[0].c
     * @return 提取到的值，找不到返回null
     */
    public static String extractJsonPath(Object jsonObj, String path) {
        if (jsonObj == null || path == null || path.isEmpty()) return null;
        if (jsonObj instanceof String str) {
            if (!JSONUtil.isTypeJSON(str)) return null;
            jsonObj = JSONUtil.parse(str);
        }
        if (path.startsWith("$.")) path = path.substring(2);
        String[] segments = path.split("\\.");
        Object current = jsonObj;
        for (String segment : segments) {
            if (current == null) return null;
            if (segment.contains("[") && segment.contains("]")) {
                String arrayName = segment.substring(0, segment.indexOf("["));
                String indexStr = segment.substring(segment.indexOf("[") + 1, segment.indexOf("]"));
                int index;
                try {
                    index = Integer.parseInt(indexStr);
                } catch (Exception e) {
                    return null;
                }
                if (current instanceof JSONObject obj) {
                    Object arr = obj.get(arrayName);
                    if (arr instanceof JSONArray jsonArr) {
                        if (index >= 0 && index < jsonArr.size()) {
                            current = jsonArr.get(index);
                        } else return null;
                    } else return null;
                } else return null;
            } else {
                if (current instanceof JSONObject obj) {
                    current = obj.get(segment);
                } else return null;
            }
        }
        return current != null ? current.toString() : null;
    }

    /**
     * 基于 Jackson 3 的 Jayway JSON provider，文档以 Map/List/标量形式参与查询。
     */
    private static final class JacksonValueJsonProvider extends AbstractJsonProvider {

        @Override
        public Object parse(String json) throws InvalidJsonException {
            if (json == null || json.isBlank()) {
                throw new InvalidJsonException("empty content");
            }
            try {
                return normalizeNumbers(QUERY_MAPPER.readValue(json, Object.class));
            } catch (JacksonException e) {
                throw new InvalidJsonException(e.getOriginalMessage(), e);
            }
        }

        @Override
        public Object parse(InputStream jsonStream, String charset) throws InvalidJsonException {
            try {
                return parse(new String(jsonStream.readAllBytes(), Charset.forName(charset)));
            } catch (java.io.IOException e) {
                throw new InvalidJsonException(e);
            }
        }

        @Override
        public String toJson(Object obj) {
            return QUERY_MAPPER.writeValueAsString(obj);
        }

        @Override
        public Object createArray() {
            return new ArrayList<>();
        }

        @Override
        public Object createMap() {
            return new LinkedHashMap<String, Object>();
        }
    }

    private static final class JacksonValueMappingProvider implements MappingProvider {

        @Override
        public <T> T map(Object source, Class<T> targetType, Configuration configuration) {
            if (source == null || targetType.isInstance(source)) {
                // 过滤比较时 Jayway 会把数组/对象映射为 List/Map；原样返回以保留已规整的数字类型
                return targetType.cast(source);
            }
            try {
                T converted = QUERY_MAPPER.convertValue(source, targetType);
                if (converted instanceof Map<?, ?> || converted instanceof List<?>) {
                    normalizeNumbers(converted);
                }
                return converted;
            } catch (JacksonException e) {
                throw new MappingException(e);
            }
        }

        @Override
        public <T> T map(Object source, TypeRef<T> targetType, Configuration configuration) {
            throw new UnsupportedOperationException("TypeRef mapping is not supported");
        }
    }
}
