package com.laker.postman.service.ideahttp;

import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.model.Variable;
import com.laker.postman.request.model.RequestAuthTypes;
import com.laker.postman.request.model.RequestBodyTypes;
import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpFormUrlencoded;
import com.laker.postman.request.model.HttpRequestItem;

import com.laker.postman.collection.importer.AuthParserUtil;
import com.laker.postman.collection.model.CollectionParseResult;
import com.laker.postman.http.request.HttpRequestProtocol;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.laker.postman.request.model.RequestAuthTypes.AUTH_TYPE_BASIC;
import static com.laker.postman.request.model.RequestAuthTypes.AUTH_TYPE_BEARER;
import static com.laker.postman.request.model.RequestBodyTypes.BODY_TYPE_FORM_DATA;
import static com.laker.postman.request.model.RequestBodyTypes.BODY_TYPE_FORM_URLENCODED;
import static com.laker.postman.request.model.RequestBodyTypes.BODY_TYPE_NONE;
import static com.laker.postman.request.model.RequestBodyTypes.BODY_TYPE_RAW;

/**
 * IntelliJ IDEA HTTP Client 文件解析器
 * 负责解析 .http 文件格式（IntelliJ IDEA HTTP Client / REST Client 格式），转换为内部数据结构
 * 此类只负责解析，不涉及 UI 层的 TreeNode 组装
 */
@Slf4j
@UtilityClass
public class IntelliJHttpParser {
    // 匹配请求分隔符：### 开头的注释
    private static final Pattern REQUEST_SEPARATOR_PATTERN = Pattern.compile("^###\\s*(.+)$");
    // 匹配 HTTP 方法 + URL + 可选 HTTP 版本，支持自定义方法
    private static final Pattern HTTP_METHOD_PATTERN = Pattern.compile(
            "^([A-Z][A-Z0-9_-]*)\\s+(.+?)(?:\\s+(HTTP/1\\.1|HTTP/2(?:\\s*\\(Prior Knowledge\\))?))?$"
    );
    // 匹配头部：Key: Value
    private static final Pattern HEADER_PATTERN = Pattern.compile("^([^:]+):\\s*(.+)$");
    // 匹配单行注释：# 开头
    private static final Pattern COMMENT_PATTERN = Pattern.compile("^#\\s*(.*)$");
    // 匹配请求名称注解：# @name RequestName
    private static final Pattern REQUEST_NAME_PATTERN = Pattern.compile("^#\\s*@name\\s*(?:=\\s*)?(.+)$");
    // 匹配变量定义：@变量名=值
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("^@([a-zA-Z_][a-zA-Z0-9_]*)\\s*=\\s*(.*)$");
    // 匹配请求行末尾的内联 response handler
    private static final Pattern INLINE_RESPONSE_HANDLER_PATTERN = Pattern.compile("^(.*?)\\s+>\\s*\\{%\\s*(.*?)\\s*%}\\s*$");

    /**
     * 解析 HTTP 文件，返回包含分组信息和请求列表的解析结果
     *
     * @param content  HTTP 文件内容
     * @param filename 文件名（可选），用于生成更友好的组名
     * @return CollectionParseResult，如果解析失败返回 null
     */
    public static CollectionParseResult parseHttpFile(String content, String filename) {
        try {
            if (content == null || content.trim().isEmpty()) {
                log.error("HTTP file content is empty");
                return null;
            }

            // 创建分组 - 优先使用文件名，否则使用国际化的组名和格式化的时间戳
            String groupName;
            if (filename != null && !filename.trim().isEmpty()) {
                // 移除文件扩展名
                groupName = filename.replaceAll("\\.http$", "").trim();
            } else {
                // 使用默认的国际化组名
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                groupName = I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_HTTP_DEFAULT_GROUP, timestamp);
            }

            RequestGroup collectionGroup = new RequestGroup(groupName);

            // 按行分割
            String[] lines = content.split("\n");

            // 解析环境变量和请求
            ParseContext context = parseHttpContent(lines);

            List<HttpRequestItem> validRequests = new ArrayList<>();
            for (HttpRequestItem request : context.requests) {
                if (request != null && request.getUrl() != null && !request.getUrl().isEmpty()) {
                    validRequests.add(request);
                }
            }

            if (validRequests.isEmpty()) {
                log.warn("No valid requests found in HTTP file");
                return null;
            }

            // 如果有解析出的变量，将它们添加到 RequestGroup 的 variables 字段中（分组级别的变量）
            if (!context.variables.isEmpty()) {
                for (Map.Entry<String, String> entry : context.variables.entrySet()) {
                    Variable variable = new Variable(true, entry.getKey(), entry.getValue());
                    collectionGroup.getVariables().add(variable);
                }
            }

            // 创建解析结果
            return CollectionParseResult.createFlat(collectionGroup, validRequests);

        } catch (Exception e) {
            log.error("Failed to parse HTTP file", e);
            return null;
        }
    }

    /**
     * 解析上下文，包含请求列表和变量映射
     */
    private static class ParseContext {
        List<HttpRequestItem> requests = new ArrayList<>();
        Map<String, String> variables = new LinkedHashMap<>();
    }

    /**
     * 解析 HTTP 文件内容，提取变量和请求
     */
    private static ParseContext parseHttpContent(String[] lines) {
        ParseContext context = new ParseContext();
        context.requests = parseHttpRequests(lines, context.variables);
        return context;
    }

    /**
     * 解析 HTTP 请求列表
     *
     * @param lines     文件行数组
     * @param variables 用于存储解析出的变量定义
     * @return 请求列表
     */
    private static List<HttpRequestItem> parseHttpRequests(String[] lines, Map<String, String> variables) {
        List<HttpRequestItem> requests = new ArrayList<>();
        HttpRequestItem currentRequest = null;
        StringBuilder bodyBuilder = null;
        StringBuilder responseScriptBuilder = null;
        StringBuilder preRequestScriptBuilder = new StringBuilder();
        boolean inBody = false;
        boolean inResponseScript = false;
        boolean inPreRequestScript = false;
        String contentType = null;
        String boundary = null;
        String pendingRequestName = null;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmedLine = line.trim();

            // 跳过空行
            if (trimmedLine.isEmpty()) {
                if (inBody && bodyBuilder != null) {
                    // 空行可能是 body 的一部分
                    bodyBuilder.append("\n");
                }
                continue;
            }

            if (inPreRequestScript) {
                int endIndex = trimmedLine.indexOf("%}");
                if (endIndex >= 0) {
                    String scriptLine = trimmedLine.substring(0, endIndex).trim();
                    if (!scriptLine.isEmpty()) {
                        appendScriptLine(preRequestScriptBuilder, convertPreRequestScript(scriptLine));
                    }
                    inPreRequestScript = false;
                    trimmedLine = trimmedLine.substring(endIndex + 2).trim();
                    if (trimmedLine.isEmpty()) {
                        continue;
                    }
                } else {
                    appendScriptLine(preRequestScriptBuilder, convertPreRequestScript(trimmedLine));
                    continue;
                }
            }

            // 检查是否是变量定义（@ 开头）
            Matcher variableMatcher = VARIABLE_PATTERN.matcher(trimmedLine);
            if (variableMatcher.matches()) {
                String varName = variableMatcher.group(1).trim();
                String varValue = variableMatcher.group(2).trim();
                variables.put(varName, varValue);
                continue;
            }

            // JetBrains 官方支持 # @name 指定请求名称
            Matcher requestNameMatcher = REQUEST_NAME_PATTERN.matcher(trimmedLine);
            if (requestNameMatcher.matches()) {
                pendingRequestName = requestNameMatcher.group(1).trim();
                continue;
            }

            ScriptLineResult scriptLineResult = parseScriptLine(trimmedLine);
            if (!scriptLineResult.preScript.isEmpty()) {
                appendScriptLine(preRequestScriptBuilder, convertPreRequestScript(scriptLineResult.preScript));
            }
            if (!scriptLineResult.postScript.isEmpty()) {
                if (responseScriptBuilder == null) {
                    responseScriptBuilder = new StringBuilder();
                }
                appendScriptLine(responseScriptBuilder, convertResponseScriptToPostman(scriptLineResult.postScript));
            }
            if (scriptLineResult.startsMultilinePreScript) {
                inPreRequestScript = true;
            }
            trimmedLine = scriptLineResult.remainingLine;
            if (trimmedLine.isEmpty()) {
                continue;
            }

            // 跳过单行注释（# 开头，但不是 ###）
            if (COMMENT_PATTERN.matcher(trimmedLine).matches() && !trimmedLine.startsWith("###")) {
                continue;
            }

            // 检查响应处理脚本开始（> {% ... %} 格式）
            if (trimmedLine.startsWith(">") && trimmedLine.contains("{%")) {
                // 如果在 body 中遇到响应脚本，说明 body 已结束
                if (inBody) {
                    inBody = false;
                }
                // 初始化响应脚本 builder
                if (responseScriptBuilder == null) {
                    responseScriptBuilder = new StringBuilder();
                } else if (!responseScriptBuilder.isEmpty()) {
                    responseScriptBuilder.append("\n");
                }
                // 提取脚本内容（去掉 > {% 和 %}）
                String scriptLine = trimmedLine.substring(1).trim(); // 去掉 >
                if (scriptLine.startsWith("{%") && scriptLine.endsWith("%}")) {
                    // 单行脚本
                    scriptLine = scriptLine.substring(2, scriptLine.length() - 2).trim();
                    responseScriptBuilder.append(scriptLine);
                } else if (scriptLine.startsWith("{%")) {
                    // 多行脚本开始
                    scriptLine = scriptLine.substring(2).trim();
                    responseScriptBuilder.append(scriptLine);
                    inResponseScript = true;
                } else {
                    // 未知格式，跳过
                }
                continue;
            }

            // 如果在响应脚本模式中，继续收集多行脚本
            if (inResponseScript) {
                if (trimmedLine.endsWith("%}")) {
                    // 脚本结束
                    String scriptLine = trimmedLine;
                    if (scriptLine.endsWith("%}")) {
                        scriptLine = scriptLine.substring(0, scriptLine.length() - 2).trim();
                    }
                    if (!scriptLine.isEmpty()) {
                        responseScriptBuilder.append("\n").append(scriptLine);
                    }
                    inResponseScript = false;
                } else {
                    // 继续收集脚本内容
                    responseScriptBuilder.append("\n").append(trimmedLine);
                }
                continue;
            }

            // 检查是否是请求分隔符（### 开头的注释）
            Matcher separatorMatcher = REQUEST_SEPARATOR_PATTERN.matcher(trimmedLine);
            if (separatorMatcher.matches()) {
                // 保存上一个请求
                if (currentRequest != null) {
                    finishRequest(currentRequest, bodyBuilder, contentType, boundary);
                    // 处理响应脚本
                    if (responseScriptBuilder != null && !responseScriptBuilder.isEmpty()) {
                        String convertedScript = convertResponseScriptToPostman(responseScriptBuilder.toString());
                        currentRequest.setPostscript(convertedScript);
                    }
                    requests.add(currentRequest);
                }
                // 开始新请求
                String requestName = separatorMatcher.group(1).trim();
                currentRequest = new HttpRequestItem();
                currentRequest.setId(UUID.randomUUID().toString());
                currentRequest.setName(requestName.isEmpty() ? I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_HTTP_UNNAMED_REQUEST) : requestName);
                pendingRequestName = applyPendingRequestNameIfNeeded(currentRequest, pendingRequestName);
                bodyBuilder = new StringBuilder();
                responseScriptBuilder = new StringBuilder();
                inBody = false;
                contentType = null;
                boundary = null;
                continue;
            }

            // 检查是否是 HTTP 方法行（如果没有当前请求，自动创建一个）
            Matcher methodMatcher = HTTP_METHOD_PATTERN.matcher(trimmedLine);
            if (methodMatcher.matches()) {
                // 如果还没有当前请求，先创建一个（支持没有 ### 分隔符的情况）
                if (currentRequest == null) {
                    currentRequest = new HttpRequestItem();
                    currentRequest.setId(UUID.randomUUID().toString());
                    currentRequest.setName(I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_HTTP_UNNAMED_REQUEST));
                    if (bodyBuilder == null) {
                        bodyBuilder = new StringBuilder();
                    }
                    if (responseScriptBuilder == null) {
                        responseScriptBuilder = new StringBuilder();
                    }
                    inBody = false;
                    inResponseScript = false;
                    contentType = null;
                }
                String method = methodMatcher.group(1).toUpperCase();
                String url = methodMatcher.group(2).trim();
                String httpVersion = methodMatcher.group(3);
                pendingRequestName = applyPendingRequestNameIfNeeded(currentRequest, pendingRequestName);
                currentRequest.setMethod(method);
                currentRequest.setUrl(url);
                applyHttpVersion(currentRequest, httpVersion);
                if (!preRequestScriptBuilder.isEmpty()) {
                    currentRequest.setPrescript(preRequestScriptBuilder.toString());
                    preRequestScriptBuilder = new StringBuilder();
                }
                continue;
            }

            // 如果没有当前请求，跳过
            if (currentRequest == null) {
                continue;
            }

            // 检查是否是 Content-Type 头部（可能影响 body 解析）
            // 只有在未进入 body 时才作为请求头处理
            if (trimmedLine.toLowerCase().startsWith("content-type:") && !inBody) {
                Matcher contentTypeMatcher = HEADER_PATTERN.matcher(trimmedLine);
                if (contentTypeMatcher.matches()) {
                    String originalContentType = contentTypeMatcher.group(2).trim();
                    contentType = originalContentType.toLowerCase();

                    // 检查是否是 multipart/form-data 并提取 boundary
                    // 注意：boundary 是大小写敏感的，所以要从原始值中提取
                    if (contentType.contains("multipart/form-data")) {
                        // 使用不区分大小写的匹配来找到 boundary 参数
                        Pattern boundaryPattern = Pattern.compile("boundary=([^;\\s]+)", Pattern.CASE_INSENSITIVE);
                        Matcher boundaryMatcher = boundaryPattern.matcher(originalContentType);
                        if (boundaryMatcher.find()) {
                            boundary = boundaryMatcher.group(1).trim();
                            // 移除可能的引号
                            if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
                                boundary = boundary.substring(1, boundary.length() - 1);
                            }
                        }
                    }

                    if (currentRequest.getHeadersList() == null) {
                        currentRequest.setHeadersList(new ArrayList<>());
                    }
                    currentRequest.getHeadersList().add(new HttpHeader(true, "Content-Type", originalContentType));
                    continue;
                }
            }

            // 检查是否是头部行
            Matcher headerMatcher = HEADER_PATTERN.matcher(trimmedLine);
            if (headerMatcher.matches() && !inBody) {
                String headerName = headerMatcher.group(1).trim();
                String headerValue = headerMatcher.group(2).trim();
                // 处理 Authorization 头部
                if ("Authorization".equalsIgnoreCase(headerName)) {
                    parseAuthorization(currentRequest, headerValue);
                } else {
                    // 添加到头部列表
                    if (currentRequest.getHeadersList() == null) {
                        currentRequest.setHeadersList(new ArrayList<>());
                    }
                    currentRequest.getHeadersList().add(new HttpHeader(true, headerName, headerValue));
                }
                continue;
            }

            // 其他情况视为 body 内容
            if (currentRequest.getMethod() != null &&
                    ("POST".equals(currentRequest.getMethod()) ||
                            "PUT".equals(currentRequest.getMethod()) ||
                            "PATCH".equals(currentRequest.getMethod()) ||
                            "DELETE".equals(currentRequest.getMethod()))) {
                inBody = true;
                if (bodyBuilder != null && !bodyBuilder.isEmpty()) {
                    bodyBuilder.append("\n");
                }
                if (bodyBuilder == null) {
                    bodyBuilder = new StringBuilder();
                }
                bodyBuilder.append(line);
            }
        }

        // 保存最后一个请求
        if (currentRequest != null) {
            finishRequest(currentRequest, bodyBuilder, contentType, boundary);
            // 处理响应脚本
            if (responseScriptBuilder != null && !responseScriptBuilder.isEmpty()) {
                String convertedScript = convertResponseScriptToPostman(responseScriptBuilder.toString());
                currentRequest.setPostscript(convertedScript);
            }
            requests.add(currentRequest);
        }

        return requests;
    }

    private static void applyHttpVersion(HttpRequestItem request, String httpVersion) {
        if (httpVersion == null || httpVersion.isBlank()) {
            return;
        }
        if (httpVersion.startsWith("HTTP/1.1")) {
            request.setHttpVersion(HttpRequestItem.HTTP_VERSION_HTTP_1_1);
        } else if (httpVersion.startsWith("HTTP/2")) {
            request.setHttpVersion(HttpRequestItem.HTTP_VERSION_HTTP_2);
        }
    }

    private static void appendScriptLine(StringBuilder builder, String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append("\n");
        }
        builder.append(line.trim());
    }

    private static String convertPreRequestScript(String script) {
        if (script == null || script.trim().isEmpty()) {
            return "";
        }

        String converted = script.trim();
        converted = converted.replace("request.variables.set", "pm.variables.set");
        converted = converted.replace("request.variables.get", "pm.variables.get");
        converted = converted.replace("client.global.set", "pm.environment.set");
        converted = converted.replace("client.global.get", "pm.environment.get");
        return converted;
    }

    private static String applyPendingRequestNameIfNeeded(HttpRequestItem request, String pendingRequestName) {
        if (request == null || pendingRequestName == null || pendingRequestName.isEmpty()) {
            return pendingRequestName;
        }
        String currentName = request.getName();
        String unnamedRequest = I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_HTTP_UNNAMED_REQUEST);
        if (currentName == null || currentName.isEmpty() || unnamedRequest.equals(currentName)) {
            request.setName(pendingRequestName);
        }
        return null;
    }

    private static ScriptLineResult parseScriptLine(String trimmedLine) {
        ScriptLineResult result = new ScriptLineResult();
        result.remainingLine = trimmedLine;

        if (trimmedLine.startsWith("<")) {
            String afterMarker = trimmedLine.substring(1).trim();
            if (afterMarker.startsWith("{%")) {
                int endIndex = afterMarker.indexOf("%}");
                if (endIndex >= 0) {
                    result.preScript = afterMarker.substring(2, endIndex).trim();
                    result.remainingLine = afterMarker.substring(endIndex + 2).trim();
                } else {
                    result.preScript = afterMarker.substring(2).trim();
                    result.remainingLine = "";
                    result.startsMultilinePreScript = true;
                }
            }
        }

        String lineToCheck = result.remainingLine;
        Matcher inlineHandlerMatcher = INLINE_RESPONSE_HANDLER_PATTERN.matcher(lineToCheck);
        if (inlineHandlerMatcher.matches()) {
            result.remainingLine = inlineHandlerMatcher.group(1).trim();
            result.postScript = inlineHandlerMatcher.group(2).trim();
            return result;
        }

        if (lineToCheck.startsWith(">")) {
            String afterMarker = lineToCheck.substring(1).trim();
            if (afterMarker.startsWith("{%")) {
                int endIndex = afterMarker.indexOf("%}");
                if (endIndex >= 0) {
                    result.postScript = afterMarker.substring(2, endIndex).trim();
                    result.remainingLine = afterMarker.substring(endIndex + 2).trim();
                }
            }
        }

        return result;
    }

    private static class ScriptLineResult {
        String preScript = "";
        String postScript = "";
        String remainingLine = "";
        boolean startsMultilinePreScript = false;
    }

    /**
     * 完成请求解析，设置 body 和其他属性
     */
    private static void finishRequest(HttpRequestItem request, StringBuilder bodyBuilder, String contentType, String boundary) {
        // 设置协议类型
        if (HttpRequestProtocol.isWebSocketUrl(request.getUrl())) {
            request.setProtocol(RequestItemProtocolEnum.WEBSOCKET);
        } else {
            request.setProtocol(RequestItemProtocolEnum.HTTP);
        }

        // 处理 body
        if (bodyBuilder != null && !bodyBuilder.isEmpty()) {
            String body = bodyBuilder.toString().trim();
            if (!body.isEmpty()) {
                // 根据 Content-Type 处理 body
                if (contentType != null && contentType.contains("multipart/form-data")) {
                    // 解析 multipart/form-data 格式
                    parseMultipartFormData(request, body, boundary);
                } else if (contentType != null && contentType.contains("application/x-www-form-urlencoded")) {
                    // 解析 urlencoded 格式
                    parseUrlencodedBody(request, body);
                } else {
                    // 默认作为 raw body
                    request.setBody(body);
                    request.setBodyType(BODY_TYPE_RAW);
                }
            } else {
                // body 为空
                request.setBodyType(BODY_TYPE_NONE);
            }
        } else {
            // 没有 body
            request.setBodyType(BODY_TYPE_NONE);
        }

        // 如果没有设置名称，使用 URL
        String unnamedRequest = I18nUtil.getMessage(MessageKeys.COLLECTIONS_IMPORT_HTTP_UNNAMED_REQUEST);
        if (request.getName() == null || request.getName().isEmpty() || unnamedRequest.equals(request.getName())) {
            try {
                java.net.URI uri = new java.net.URI(request.getUrl());
                String path = uri.getPath();
                if (path != null && !path.isEmpty()) {
                    String[] parts = path.split("/");
                    String name = parts[parts.length - 1];
                    if (name.isEmpty() && parts.length > 1) {
                        name = parts[parts.length - 2];
                    }
                    request.setName(name.isEmpty() ? request.getUrl() : name);
                } else {
                    request.setName(request.getUrl());
                }
            } catch (Exception e) {
                request.setName(request.getUrl());
            }
        }
    }

    /**
     * 将 IntelliJ HTTP Client 的响应脚本转换为 Postman 风格的脚本
     * 例如：client.global.set("token", response.body.token) -> pm.environment.set("token", pm.response.json().token)
     */
    private static String convertResponseScriptToPostman(String httpClientScript) {
        if (httpClientScript == null || httpClientScript.trim().isEmpty()) {
            return "";
        }

        StringBuilder postmanScript = new StringBuilder();
        String[] lines = httpClientScript.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            String convertedLine = line;

            // 转换 client.global.set 为 pm.environment.set
            if (line.contains("client.global.set")) {
                convertedLine = line.replace("client.global.set", "pm.environment.set");

                // 转换 response.body 为 pm.response.json()
                // 例如：response.body.token -> pm.response.json().token
                convertedLine = convertedLine.replaceAll("response\\.body\\.(\\w+)", "pm.response.json().$1");

                // 如果只有 response.body（没有属性访问），转换为 pm.response.json()
                convertedLine = convertedLine.replace("response.body", "pm.response.json()");
            }

            // 转换 client.global.get 为 pm.environment.get
            if (line.contains("client.global.get")) {
                convertedLine = convertedLine.replace("client.global.get", "pm.environment.get");
            }

            // 转换 client.test 为 pm.test
            if (line.contains("client.test")) {
                convertedLine = convertedLine.replace("client.test", "pm.test");
            }

            // 转换 response.status 为 pm.response.code
            if (line.contains("response.status")) {
                convertedLine = convertedLine.replace("response.status", "pm.response.code");
            }

            // 转换 response.headers 为 pm.response.headers
            if (line.contains("response.headers")) {
                convertedLine = convertedLine.replace("response.headers", "pm.response.headers");
            }

            if (!postmanScript.isEmpty()) {
                postmanScript.append("\n");
            }
            postmanScript.append(convertedLine);
        }

        return postmanScript.toString();
    }

    /**
     * 解析 application/x-www-form-urlencoded 格式的 body
     */
    private static void parseUrlencodedBody(HttpRequestItem request, String body) {
        List<HttpFormUrlencoded> urlencodedList = new ArrayList<>();

        // 处理多行格式（每行可能以 & 结尾）
        // 先移除所有换行符和多余的空格，然后按 & 分割
        String normalizedBody = body.replaceAll("\\s*&\\s*\\n\\s*", "&")  // 处理 & 后换行
                .replaceAll("\\n\\s*", "&")           // 处理换行（视为 & 分隔）
                .replaceAll("&+", "&");              // 移除重复的 &

        String[] pairs = normalizedBody.split("&");
        for (String pair : pairs) {
            pair = pair.trim();
            if (pair.isEmpty()) {
                continue;
            }

            // 处理 key = value 或 key=value 格式
            int equalsIndex = pair.indexOf('=');
            if (equalsIndex > 0) {
                String name = pair.substring(0, equalsIndex).trim();
                String value = pair.substring(equalsIndex + 1).trim();
                if (!name.isEmpty()) {
                    urlencodedList.add(new HttpFormUrlencoded(true, name, value));
                }
            } else if (!pair.contains("=")) {
                // 没有等号，视为单独的 key
                urlencodedList.add(new HttpFormUrlencoded(true, pair, ""));
            }
        }
        request.setUrlencodedList(urlencodedList);
        request.setBodyType(BODY_TYPE_FORM_URLENCODED);
    }

    /**
     * 解析 multipart/form-data 格式的 body
     */
    private static void parseMultipartFormData(HttpRequestItem request, String body, String boundary) {
        if (boundary == null || boundary.isEmpty()) {
            log.warn("multipart/form-data 缺少 boundary，无法解析");
            // 降级为 raw body
            request.setBody(body);
            request.setBodyType(BODY_TYPE_RAW);
            return;
        }

        List<HttpFormData> formDataList = new ArrayList<>();

        // 添加 -- 前缀到 boundary（如果还没有）
        String fullBoundary = boundary.startsWith("--") ? boundary : "--" + boundary;

        // 使用正则表达式分割，匹配边界前面可能有换行符的情况
        // 分割模式：可选的换行符 + boundary
        String splitPattern = "(?:\\r?\\n)?" + Pattern.quote(fullBoundary);
        String[] parts = body.split(splitPattern);


        for (String part : parts) {
            part = part.trim();
            // 跳过空 part 和结束标志
            if (part.isEmpty() || part.equals("--")) {
                continue;
            }

            try {
                // 将 part 按行分割
                String[] lines = part.split("\\r?\\n");

                String fieldName = null;
                String fileName = null;
                StringBuilder contentBuilder = new StringBuilder();
                boolean foundDoubleNewline = false;

                // 解析 Content-Disposition 头部
                Pattern dispositionPattern = Pattern.compile(
                        "Content-Disposition:\\s*form-data;\\s*name=\"([^\"]+)\"(?:;\\s*filename=\"([^\"]+)\")?",
                        Pattern.CASE_INSENSITIVE
                );

                int lineIndex = 0;
                // 先处理所有头部（Content-Disposition, Content-Type等）
                for (; lineIndex < lines.length; lineIndex++) {
                    String line = lines[lineIndex];

                    // 检查是否是空行（表示头部结束）
                    if (line.trim().isEmpty()) {
                        foundDoubleNewline = true;
                        lineIndex++; // 跳过空行
                        break;
                    }

                    // 解析 Content-Disposition
                    Matcher dispositionMatcher = dispositionPattern.matcher(line);
                    if (dispositionMatcher.find()) {
                        fieldName = dispositionMatcher.group(1);
                        fileName = dispositionMatcher.group(2);
                    }

                    // 其他头部（如 Content-Type）我们暂时忽略，只关注 Content-Disposition
                }

                // 收集内容（空行之后的所有行）
                if (foundDoubleNewline && fieldName != null) {
                    for (; lineIndex < lines.length; lineIndex++) {
                        if (!contentBuilder.isEmpty()) {
                            contentBuilder.append("\n");
                        }
                        contentBuilder.append(lines[lineIndex]);
                    }

                    String value = contentBuilder.toString().trim();

                    // 检查是否是文件引用（< ./filename 格式）
                    if (value.startsWith("<")) {
                        value = value.substring(1).trim();
                    }

                    // 判断是文件还是文本字段
                    if (fileName != null && !fileName.isEmpty()) {
                        // 文件字段
                        formDataList.add(new HttpFormData(true, fieldName, HttpFormData.TYPE_FILE, value.isEmpty() ? fileName : value));
                    } else {
                        // 文本字段
                        formDataList.add(new HttpFormData(true, fieldName, HttpFormData.TYPE_TEXT, value));
                    }
                }
            } catch (Exception e) {
                log.warn("解析 multipart part 失败: " + e.getMessage(), e);
            }
        }

        if (formDataList.isEmpty()) {
            log.warn("multipart/form-data 解析结果为空，降级为 raw body");
            request.setBody(body);
            request.setBodyType(BODY_TYPE_RAW);
        } else {
            request.setFormDataList(formDataList);
            request.setBodyType(BODY_TYPE_FORM_DATA);
        }
    }

    /**
     * 解析 Authorization 头部
     */
    private static void parseAuthorization(HttpRequestItem request, String authValue) {
        if (authValue.startsWith("Basic ")) {
            request.setAuthType(AUTH_TYPE_BASIC);
            AuthParserUtil.BasicAuthCredentials credentials = AuthParserUtil.parseBasicAuthHeader(authValue);
            if (credentials != null) {
                request.setAuthUsername(credentials.getUsername());
                request.setAuthPassword(credentials.getPassword());
            }
        } else if (authValue.startsWith("Bearer ")) {
            request.setAuthType(AUTH_TYPE_BEARER);
            request.setAuthToken(authValue.substring(7).trim());
        }
    }

}
