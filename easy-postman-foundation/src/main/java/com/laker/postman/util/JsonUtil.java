package com.laker.postman.util;

import cn.hutool.core.text.CharSequenceUtil;
import lombok.experimental.UtilityClass;
import tools.jackson.core.JacksonException;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.core.util.Separators;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Json 工具类
 */
@UtilityClass
public class JsonUtil {
    /**
     * JsonMapper 自定义 json 美化格式，4 个空格缩进，属性名后不加空格，属性值前加空格
     * 支持解析带注释的 JSON（JSON5）
     */
    private static final DefaultIndenter DEFAULT_INDENTER = new DefaultIndenter("    ", "\n");
    private static final DefaultPrettyPrinter DEFAULT_PRETTY_PRINTER = new DefaultPrettyPrinter(
            Separators.createDefaultInstance().withObjectNameValueSpacing(Separators.Spacing.AFTER))
            .withObjectIndenter(DEFAULT_INDENTER)
            .withArrayIndenter(DEFAULT_INDENTER);
    private static final JsonMapper mapper = JsonMapper.builder()
            .configure(JsonReadFeature.ALLOW_JAVA_COMMENTS, true)
            .defaultPrettyPrinter(DEFAULT_PRETTY_PRINTER)
            .build();

    /**
     * 创建 ObjectNode
     *
     * @return ObjectNode
     */
    public static ObjectNode createJsonNode() {
        return mapper.createObjectNode();
    }

    /**
     * 创建 ArrayNode
     *
     * @return ArrayNode
     */
    public static ArrayNode createArrayNode() {
        return mapper.createArrayNode();
    }

    /**
     * 对象转json
     *
     * @param object 待转换的对象
     * @return json
     */
    public static String toJsonStr(Object object) {
        return mapper.writeValueAsString(object);
    }

    /**
     * 将普通文本转成可嵌入 JSON 字符串内容的转义文本，不包含外层双引号
     *
     * @param text 原始文本
     * @return 转义后的文本内容
     */
    public static String escapeJsonStringContent(String text) {
        if (text == null) {
            return null;
        }
        String json = mapper.writeValueAsString(text);
        return json.length() >= 2 ? json.substring(1, json.length() - 1) : json;
    }

    /**
     * 对 JSON 字符串内容做单层反转义，不要求包含外层双引号
     *
     * @param text 被转义的文本内容
     * @return 反转义后的文本
     */
    public static String unescapeJsonStringContent(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch != '\\' || i == text.length() - 1) {
                sb.append(ch);
                continue;
            }

            char next = text.charAt(++i);
            switch (next) {
                case '"':
                    sb.append('"');
                    break;
                case '\\':
                    sb.append('\\');
                    break;
                case '/':
                    sb.append('/');
                    break;
                case 'b':
                    sb.append('\b');
                    break;
                case 'f':
                    sb.append('\f');
                    break;
                case 'n':
                    sb.append('\n');
                    break;
                case 'r':
                    sb.append('\r');
                    break;
                case 't':
                    sb.append('\t');
                    break;
                case 'u':
                    if (i + 4 < text.length()) {
                        String hex = text.substring(i + 1, i + 5);
                        try {
                            sb.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                            break;
                        } catch (NumberFormatException ignored) {
                            // 保留原样
                        }
                    }
                    sb.append('\\').append('u');
                    break;
                default:
                    sb.append('\\').append(next);
                    break;
            }
        }
        return sb.toString();
    }

    /**
     * json 字符串美化
     *
     * @param json 待转换的 json 字符串
     * @return json
     */
    public static String toJsonPrettyStr(String json) {
        JsonNode rootNode = mapper.readTree(json);
        if (containsJsonComment(json)) {
            return formatJsonWithComments(json);
        }
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
    }

    /**
     * json对象美化
     *
     * @param object 待转换的对象
     * @return json
     */
    public static String toJsonPrettyStr(Object object) {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
    }

    /**
     * 清理 json 注释（去除注释后返回纯 JSON）
     *
     * @param json 待清理的 json
     * @return 清理后的 json
     */
    public static String cleanJsonComments(String json) {
        return mapper.readTree(json).toString();
    }


    /**
     * 判断字符串是否为有效的 JSON（支持带注释的 JSON5）
     *
     * @param json 待判断的字符串
     * @return 是否为有效的 JSON/JSON5
     */
    public static boolean isTypeJSON(String json) {
        if (CharSequenceUtil.isBlank(json)) return false;
        try {
            JsonNode rootNode = mapper.readTree(json);
            // 严格校验：只有是JSON对象或JSON数组时才返回true
            return rootNode.isObject() || rootNode.isArray();
        } catch (JacksonException e) {
            return false;
        }
    }

    /**
     * Compares JSON objects or arrays by structure, ignoring object field order and formatting.
     */
    public static boolean isStructurallyEqual(String left, String right) {
        if (CharSequenceUtil.isBlank(left) || CharSequenceUtil.isBlank(right)) return false;
        try {
            JsonNode leftNode = mapper.readTree(left);
            JsonNode rightNode = mapper.readTree(right);
            boolean structured = (leftNode.isObject() || leftNode.isArray())
                    && (rightNode.isObject() || rightNode.isArray());
            return structured && leftNode.equals(rightNode);
        } catch (JacksonException exception) {
            return false;
        }
    }

    /**
     * 读取 json
     *
     * @param json 待读取的 json
     * @return JsonNode
     */
    public static JsonNode readTree(String json) {
        return mapper.readTree(json);
    }

    /**
     * 深度拷贝
     *
     * @param object 待拷贝的对象
     * @param clazz  目标对象类型
     * @param <T>    目标对象类型
     * @return 拷贝后的对象
     */
    public static <T> T deepCopy(T object, Class<T> clazz) {
        if (object == null) {
            return null;
        }
        return mapper.readValue(mapper.writeValueAsBytes(object), clazz);
    }

    /**
     * 类型转换
     *
     * @param fromValue   来源对象
     * @param toValueType 转换的类型
     * @param <T>         泛型标记
     * @return 转换结果
     */
    public static <T> T convertValue(Object fromValue, Class<T> toValueType) {
        return mapper.convertValue(fromValue, toValueType);
    }

    private static boolean containsJsonComment(String json) {
        if (json == null || json.length() < 2) {
            return false;
        }

        boolean inString = false;
        boolean escaping = false;
        for (int i = 0; i < json.length(); i++) {
            char current = json.charAt(i);
            if (inString) {
                if (escaping) {
                    escaping = false;
                } else if (current == '\\') {
                    escaping = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }

            if (current == '"') {
                inString = true;
                continue;
            }
            if (current == '/' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                if (next == '/' || next == '*') {
                    return true;
                }
            }
        }
        return false;
    }

    private static String formatJsonWithComments(String json) {
        List<JsonFormatToken> tokens = tokenizeJsonWithComments(json);
        StringBuilder formatted = new StringBuilder(json.length());
        int indentLevel = 0;
        boolean atLineStart = true;

        for (int i = 0; i < tokens.size(); i++) {
            JsonFormatToken token = tokens.get(i);
            JsonFormatToken previous = i > 0 ? tokens.get(i - 1) : null;
            JsonFormatToken next = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
            switch (token.type()) {
                case OPEN_OBJECT, OPEN_ARRAY -> {
                    atLineStart = appendIndentIfNeeded(formatted, indentLevel, atLineStart);
                    formatted.append(token.text());
                    atLineStart = false;
                    indentLevel++;
                    if (isMatchingClose(token, next)) {
                        continue;
                    }
                    if (isLineCommentOnSameLine(token, next)) {
                        formatted.append(' ');
                    } else {
                        atLineStart = appendNewLine(formatted);
                    }
                }
                case CLOSE_OBJECT, CLOSE_ARRAY -> {
                    indentLevel = Math.max(0, indentLevel - 1);
                    if (!isMatchingClose(previous, token) && !atLineStart) {
                        atLineStart = appendNewLine(formatted);
                    }
                    atLineStart = appendIndentIfNeeded(formatted, indentLevel, atLineStart);
                    formatted.append(token.text());
                    atLineStart = false;
                }
                case COLON -> {
                    formatted.append(": ");
                    atLineStart = false;
                }
                case COMMA -> {
                    atLineStart = appendIndentIfNeeded(formatted, indentLevel, atLineStart);
                    formatted.append(',');
                    atLineStart = false;
                    if (isLineCommentOnSameLine(token, next)) {
                        formatted.append(' ');
                    } else {
                        atLineStart = appendNewLine(formatted);
                    }
                }
                case LINE_COMMENT -> {
                    atLineStart = appendIndentIfNeeded(formatted, indentLevel, atLineStart);
                    appendSpaceIfNeeded(formatted);
                    formatted.append(token.text().trim());
                    atLineStart = appendNewLine(formatted);
                }
                case BLOCK_COMMENT -> atLineStart = appendBlockComment(formatted, token.text(), indentLevel, atLineStart);
                case VALUE -> {
                    atLineStart = appendIndentIfNeeded(formatted, indentLevel, atLineStart);
                    formatted.append(token.text());
                    atLineStart = false;
                }
            }
        }

        trimTrailingNewLines(formatted);
        return formatted.toString();
    }

    private static boolean isMatchingClose(JsonFormatToken token, JsonFormatToken next) {
        return next != null
                && ((token.type() == JsonFormatTokenType.OPEN_OBJECT && next.type() == JsonFormatTokenType.CLOSE_OBJECT)
                || (token.type() == JsonFormatTokenType.OPEN_ARRAY && next.type() == JsonFormatTokenType.CLOSE_ARRAY));
    }

    private static boolean isLineCommentOnSameLine(JsonFormatToken token, JsonFormatToken next) {
        return next != null
                && next.type() == JsonFormatTokenType.LINE_COMMENT
                && token.endLine() == next.startLine();
    }

    private static boolean appendIndentIfNeeded(StringBuilder formatted, int indentLevel, boolean atLineStart) {
        if (!atLineStart) {
            return false;
        }
        formatted.append(DEFAULT_INDENTER.getIndent().repeat(Math.max(0, indentLevel)));
        return false;
    }

    private static boolean appendNewLine(StringBuilder formatted) {
        trimTrailingSpaces(formatted);
        if (formatted.length() == 0 || formatted.charAt(formatted.length() - 1) != '\n') {
            formatted.append('\n');
        }
        return true;
    }

    private static void appendSpaceIfNeeded(StringBuilder formatted) {
        if (formatted.length() == 0) {
            return;
        }
        char last = formatted.charAt(formatted.length() - 1);
        if (!Character.isWhitespace(last)) {
            formatted.append(' ');
        }
    }

    private static boolean appendBlockComment(StringBuilder formatted, String comment, int indentLevel, boolean atLineStart) {
        boolean standalone = atLineStart;
        if (comment.indexOf('\n') >= 0 || comment.indexOf('\r') >= 0) {
            if (!atLineStart) {
                atLineStart = appendNewLine(formatted);
            }
            String normalized = comment.replace("\r\n", "\n").replace('\r', '\n');
            String[] lines = normalized.split("\n", -1);
            for (String line : lines) {
                if (line.isEmpty()) {
                    continue;
                }
                appendIndentIfNeeded(formatted, indentLevel, true);
                formatted.append(line.stripLeading());
                atLineStart = appendNewLine(formatted);
            }
            return atLineStart;
        }

        atLineStart = appendIndentIfNeeded(formatted, indentLevel, atLineStart);
        appendSpaceIfNeeded(formatted);
        formatted.append(comment.trim());
        if (standalone) {
            return appendNewLine(formatted);
        }
        return false;
    }

    private static void trimTrailingSpaces(StringBuilder formatted) {
        while (formatted.length() > 0) {
            char last = formatted.charAt(formatted.length() - 1);
            if (last != ' ' && last != '\t') {
                return;
            }
            formatted.deleteCharAt(formatted.length() - 1);
        }
    }

    private static void trimTrailingNewLines(StringBuilder formatted) {
        while (formatted.length() > 0) {
            char last = formatted.charAt(formatted.length() - 1);
            if (last != '\n' && last != '\r') {
                return;
            }
            formatted.deleteCharAt(formatted.length() - 1);
        }
    }

    private static List<JsonFormatToken> tokenizeJsonWithComments(String json) {
        List<JsonFormatToken> tokens = new ArrayList<>();
        int line = 1;
        int index = 0;
        while (index < json.length()) {
            char current = json.charAt(index);
            if (Character.isWhitespace(current)) {
                if (current == '\r') {
                    if (index + 1 < json.length() && json.charAt(index + 1) == '\n') {
                        index++;
                    }
                    line++;
                } else if (current == '\n') {
                    line++;
                }
                index++;
                continue;
            }

            int startLine = line;
            switch (current) {
                case '{' -> {
                    tokens.add(new JsonFormatToken(JsonFormatTokenType.OPEN_OBJECT, "{", startLine, startLine));
                    index++;
                }
                case '}' -> {
                    tokens.add(new JsonFormatToken(JsonFormatTokenType.CLOSE_OBJECT, "}", startLine, startLine));
                    index++;
                }
                case '[' -> {
                    tokens.add(new JsonFormatToken(JsonFormatTokenType.OPEN_ARRAY, "[", startLine, startLine));
                    index++;
                }
                case ']' -> {
                    tokens.add(new JsonFormatToken(JsonFormatTokenType.CLOSE_ARRAY, "]", startLine, startLine));
                    index++;
                }
                case ':' -> {
                    tokens.add(new JsonFormatToken(JsonFormatTokenType.COLON, ":", startLine, startLine));
                    index++;
                }
                case ',' -> {
                    tokens.add(new JsonFormatToken(JsonFormatTokenType.COMMA, ",", startLine, startLine));
                    index++;
                }
                case '"' -> {
                    TokenReadResult result = readJsonString(json, index, line);
                    tokens.add(new JsonFormatToken(JsonFormatTokenType.VALUE, result.text(), startLine, result.endLine()));
                    index = result.nextIndex();
                    line = result.endLine();
                }
                case '/' -> {
                    if (index + 1 < json.length() && json.charAt(index + 1) == '/') {
                        TokenReadResult result = readLineComment(json, index, line);
                        tokens.add(new JsonFormatToken(JsonFormatTokenType.LINE_COMMENT, result.text(), startLine, result.endLine()));
                        index = result.nextIndex();
                    } else if (index + 1 < json.length() && json.charAt(index + 1) == '*') {
                        TokenReadResult result = readBlockComment(json, index, line);
                        tokens.add(new JsonFormatToken(JsonFormatTokenType.BLOCK_COMMENT, result.text(), startLine, result.endLine()));
                        index = result.nextIndex();
                        line = result.endLine();
                    } else {
                        TokenReadResult result = readJsonLiteral(json, index, line);
                        tokens.add(new JsonFormatToken(JsonFormatTokenType.VALUE, result.text(), startLine, result.endLine()));
                        index = result.nextIndex();
                    }
                }
                default -> {
                    TokenReadResult result = readJsonLiteral(json, index, line);
                    tokens.add(new JsonFormatToken(JsonFormatTokenType.VALUE, result.text(), startLine, result.endLine()));
                    index = result.nextIndex();
                }
            }
        }
        return tokens;
    }

    private static TokenReadResult readJsonString(String json, int startIndex, int startLine) {
        boolean escaping = false;
        int line = startLine;
        int index = startIndex + 1;
        while (index < json.length()) {
            char current = json.charAt(index);
            if (current == '\r') {
                if (index + 1 < json.length() && json.charAt(index + 1) == '\n') {
                    index++;
                }
                line++;
            } else if (current == '\n') {
                line++;
            }

            if (escaping) {
                escaping = false;
            } else if (current == '\\') {
                escaping = true;
            } else if (current == '"') {
                index++;
                break;
            }
            index++;
        }
        return new TokenReadResult(json.substring(startIndex, index), index, line);
    }

    private static TokenReadResult readLineComment(String json, int startIndex, int startLine) {
        int index = startIndex + 2;
        while (index < json.length() && json.charAt(index) != '\n' && json.charAt(index) != '\r') {
            index++;
        }
        return new TokenReadResult(json.substring(startIndex, index), index, startLine);
    }

    private static TokenReadResult readBlockComment(String json, int startIndex, int startLine) {
        int line = startLine;
        int index = startIndex + 2;
        while (index < json.length()) {
            char current = json.charAt(index);
            if (current == '\r') {
                if (index + 1 < json.length() && json.charAt(index + 1) == '\n') {
                    index++;
                }
                line++;
            } else if (current == '\n') {
                line++;
            }
            if (current == '*' && index + 1 < json.length() && json.charAt(index + 1) == '/') {
                index += 2;
                break;
            }
            index++;
        }
        return new TokenReadResult(json.substring(startIndex, index), index, line);
    }

    private static TokenReadResult readJsonLiteral(String json, int startIndex, int startLine) {
        int index = startIndex;
        while (index < json.length()) {
            char current = json.charAt(index);
            if (Character.isWhitespace(current)
                    || current == '{'
                    || current == '}'
                    || current == '['
                    || current == ']'
                    || current == ':'
                    || current == ','
                    || current == '"'
                    || (current == '/' && index + 1 < json.length()
                    && (json.charAt(index + 1) == '/' || json.charAt(index + 1) == '*'))) {
                break;
            }
            index++;
        }
        return new TokenReadResult(json.substring(startIndex, index), index, startLine);
    }

    private enum JsonFormatTokenType {
        OPEN_OBJECT,
        CLOSE_OBJECT,
        OPEN_ARRAY,
        CLOSE_ARRAY,
        COLON,
        COMMA,
        LINE_COMMENT,
        BLOCK_COMMENT,
        VALUE
    }

    private record JsonFormatToken(JsonFormatTokenType type, String text, int startLine, int endLine) {
    }

    private record TokenReadResult(String text, int nextIndex, int endLine) {
    }

}
