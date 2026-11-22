package com.laker.postman.service.swagger;

import com.laker.postman.model.Environment;
import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.request.model.AuthApiKeyPlacement;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpParam;
import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpFormUrlencoded;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.util.HttpUrlUtil;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.laker.postman.collection.model.CollectionNode;
import com.laker.postman.collection.model.CollectionParseResult;
import com.laker.postman.collection.model.CollectionNodeType;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

import static com.laker.postman.request.model.RequestAuthTypes.AUTH_TYPE_BASIC;
import static com.laker.postman.request.model.RequestAuthTypes.AUTH_TYPE_API_KEY;
import static com.laker.postman.request.model.RequestAuthTypes.AUTH_TYPE_BEARER;
import static com.laker.postman.request.model.RequestAuthTypes.AUTH_TYPE_NONE;
import static com.laker.postman.request.model.RequestBodyTypes.BODY_TYPE_FORM_DATA;
import static com.laker.postman.request.model.RequestBodyTypes.BODY_TYPE_FORM_URLENCODED;
import static com.laker.postman.request.model.RequestBodyTypes.BODY_TYPE_RAW;

/**
 * OpenAPI 3.x 格式解析器
 * 支持 OpenAPI 3.0.x 和 3.1.x
 */
@Slf4j
@UtilityClass
class OpenApi3Parser {

    static CollectionParseResult parse(JSONObject openApiRoot) {
        JSONObject info = openApiRoot.getJSONObject("info");
        String title = info != null ? info.getStr("title", "OpenAPI") : "OpenAPI";
        String version = info != null ? info.getStr("version", "") : "";
        String collectionName = version.isEmpty() ? title : title + " (" + version + ")";

        RequestGroup collectionGroup = new RequestGroup(collectionName);
        CollectionParseResult result = new CollectionParseResult(collectionGroup);

        parseServersToEnvironments(openApiRoot, result, title);
        parseExtensionEnvironments(openApiRoot, result, title);

        JSONArray servers = openApiRoot.getJSONArray("servers");
        String baseUrl = (servers != null && !servers.isEmpty()) ? "{{baseUrl}}" : "";
        if (!baseUrl.isEmpty()) {
            log.debug("检测到 {} 个服务器配置，请求URL将使用 {{{{baseUrl}}}} 变量", servers.size());
        }

        JSONObject components = openApiRoot.getJSONObject("components");
        JSONObject securitySchemes = components != null ? components.getJSONObject("securitySchemes") : null;

        JSONObject paths = openApiRoot.getJSONObject("paths");
        if (paths == null || paths.isEmpty()) {
            log.warn("OpenAPI 文件中没有定义任何路径");
            return result;
        }

        Map<String, CollectionNode> tagNodes = new HashMap<>();
        for (String path : paths.keySet()) {
            JSONObject pathItem = paths.getJSONObject(path);
            for (String method : pathItem.keySet()) {
                if (SwaggerCommonUtil.isNotHttpMethod(method)) {
                    continue;
                }

                JSONObject operation = pathItem.getJSONObject(method);
                HttpRequestItem requestItem = parseOperation(
                        openApiRoot,
                        method.toUpperCase(),
                        path,
                        pathItem,
                        operation,
                        baseUrl,
                        securitySchemes
                );
                if (requestItem == null) {
                    continue;
                }

                CollectionNode tagNode = getOrCreateTagNode(tagNodes, getTag(operation));
                tagNode.addChild(new CollectionNode(CollectionNodeType.REQUEST, requestItem));
            }
        }

        tagNodes.values().forEach(result::addChild);
        return result;
    }

    private static String getTag(JSONObject operation) {
        JSONArray tags = operation.getJSONArray("tags");
        return (tags != null && !tags.isEmpty()) ? tags.getStr(0) : "Default";
    }

    private static CollectionNode getOrCreateTagNode(Map<String, CollectionNode> tagNodes, String tag) {
        return tagNodes.computeIfAbsent(tag, key -> new CollectionNode(CollectionNodeType.GROUP, new RequestGroup(key)));
    }

    private static HttpRequestItem parseOperation(
            JSONObject openApiRoot,
            String method,
            String path,
            JSONObject pathItem,
            JSONObject operation,
            String baseUrl,
            JSONObject securitySchemes) {

        HttpRequestItem req = new HttpRequestItem();
        req.setId(UUID.randomUUID().toString());
        req.setMethod(method);

        String summary = operation.getStr("summary", "");
        String operationId = operation.getStr("operationId", "");
        req.setName(summary.isEmpty() ? (operationId.isEmpty() ? method + " " + path : operationId) : summary);
        req.setDescription(operation.getStr("description", ""));
        String urlPath = HttpUrlUtil.toRequestPathTemplate(path);
        req.setUrl(baseUrl.isEmpty() ? urlPath : baseUrl + urlPath);

        parseParameters(openApiRoot, pathItem, operation, req);
        parseRequestBody(openApiRoot, operation, req);
        parseScripts(operation, req);
        parseSecurity(openApiRoot, operation, req, securitySchemes);
        return req;
    }

    private static void parseParameters(JSONObject openApiRoot, JSONObject pathItem, JSONObject operation, HttpRequestItem req) {
        List<JSONObject> parameters = SwaggerCommonUtil.mergeParameters(openApiRoot, pathItem, operation);
        if (parameters.isEmpty()) {
            return;
        }

        List<HttpParam> queryParams = new ArrayList<>();
        List<HttpParam> pathVariables = new ArrayList<>();
        List<HttpHeader> headers = req.getHeadersList() == null ? new ArrayList<>() : new ArrayList<>(req.getHeadersList());

        for (JSONObject param : parameters) {
            if (param == null) {
                continue;
            }

            String in = param.getStr("in", "");
            String name = param.getStr("name", "");
            if (name.isBlank()) {
                continue;
            }

            JSONObject schema = SwaggerCommonUtil.resolveRefObject(openApiRoot, param.getJSONObject("schema"));
            Object parameterValue = schema != null && schema.containsKey("default")
                    ? schema.get("default")
                    : param.get("example");
            if (parameterValue == null) {
                parameterValue = SwaggerCommonUtil.getExampleValue(schema);
            }
            String value = parameterValue == null ? "" : String.valueOf(parameterValue);

            switch (in) {
                case "query":
                    queryParams.add(new HttpParam(true, name, value));
                    break;
                case "header":
                    headers.add(new HttpHeader(true, name, value));
                    break;
                case "cookie":
                    headers.add(new HttpHeader(true, "Cookie", name + "=" + value));
                    break;
                case "path":
                    pathVariables.add(new HttpParam(true, name, value));
                    break;
                default:
                    break;
            }
        }

        if (!queryParams.isEmpty()) {
            req.setParamsList(queryParams);
        }
        if (!pathVariables.isEmpty()) {
            req.setPathVariablesList(pathVariables);
        }
        if (!headers.isEmpty()) {
            req.setHeadersList(headers);
        }
    }

    private static void parseRequestBody(JSONObject openApiRoot, JSONObject operation, HttpRequestItem req) {
        JSONObject requestBody = SwaggerCommonUtil.resolveRefObject(openApiRoot, operation.getJSONObject("requestBody"));
        if (requestBody == null) {
            return;
        }

        JSONObject content = requestBody.getJSONObject("content");
        if (content == null || content.isEmpty()) {
            return;
        }

        String mediaType = selectPreferredMediaType(content);
        JSONObject mediaContent = content.getJSONObject(mediaType);
        if (mediaContent == null) {
            return;
        }

        if (req.getHeadersList() == null) {
            req.setHeadersList(new ArrayList<>());
        }
        SwaggerCommonUtil.upsertHeader(req.getHeadersList(), "Content-Type", mediaType);

        if ("application/x-www-form-urlencoded".equals(mediaType)) {
            req.setBodyType(BODY_TYPE_FORM_URLENCODED);
            req.setUrlencodedList(buildUrlEncoded(openApiRoot, mediaContent));
            return;
        }

        if ("multipart/form-data".equals(mediaType)) {
            req.setBodyType(BODY_TYPE_FORM_DATA);
            req.setFormDataList(buildFormData(openApiRoot, mediaContent));
            return;
        }

        req.setBodyType(BODY_TYPE_RAW);
        String exampleBody = SwaggerCommonUtil.extractExampleFromMediaType(openApiRoot, mediaContent);
        req.setBody(exampleBody);
    }

    private static String selectPreferredMediaType(JSONObject content) {
        if (content.containsKey("application/json")) {
            return "application/json";
        }
        if (content.containsKey("application/x-www-form-urlencoded")) {
            return "application/x-www-form-urlencoded";
        }
        if (content.containsKey("multipart/form-data")) {
            return "multipart/form-data";
        }
        for (String key : content.keySet()) {
            if (key != null && key.toLowerCase().contains("json")) {
                return key;
            }
        }
        return content.keySet().iterator().next();
    }

    private static List<HttpFormUrlencoded> buildUrlEncoded(JSONObject openApiRoot, JSONObject mediaContent) {
        List<HttpFormUrlencoded> urlencoded = new ArrayList<>();
        JSONObject schema = SwaggerCommonUtil.resolveRefObject(openApiRoot, mediaContent.getJSONObject("schema"));
        JSONObject example = parseExampleObject(SwaggerCommonUtil.extractExampleFromMediaType(openApiRoot, mediaContent));
        JSONObject properties = schema != null ? schema.getJSONObject("properties") : null;

        if (properties != null) {
            for (String key : properties.keySet()) {
                Object value = example != null ? example.get(key) : "";
                urlencoded.add(new HttpFormUrlencoded(true, key, value == null ? "" : String.valueOf(value)));
            }
        } else if (example != null) {
            for (String key : example.keySet()) {
                Object value = example.get(key);
                urlencoded.add(new HttpFormUrlencoded(true, key, value == null ? "" : String.valueOf(value)));
            }
        }
        return urlencoded;
    }

    private static List<HttpFormData> buildFormData(JSONObject openApiRoot, JSONObject mediaContent) {
        List<HttpFormData> formData = new ArrayList<>();
        JSONObject schema = SwaggerCommonUtil.resolveRefObject(openApiRoot, mediaContent.getJSONObject("schema"));
        JSONObject example = parseExampleObject(SwaggerCommonUtil.extractExampleFromMediaType(openApiRoot, mediaContent));
        JSONObject properties = schema != null ? schema.getJSONObject("properties") : null;

        if (properties == null && example != null) {
            for (String key : example.keySet()) {
                Object value = example.get(key);
                formData.add(new HttpFormData(true, key, HttpFormData.TYPE_TEXT, value == null ? "" : String.valueOf(value)));
            }
            return formData;
        }

        if (properties == null) {
            return formData;
        }

        for (String key : properties.keySet()) {
            JSONObject propSchema = SwaggerCommonUtil.resolveRefObject(openApiRoot, properties.getJSONObject(key));
            String format = propSchema != null ? propSchema.getStr("format", "") : "";
            Object value = example != null ? example.get(key) : "";
            if ("binary".equalsIgnoreCase(format)) {
                formData.add(new HttpFormData(true, key, HttpFormData.TYPE_FILE, value == null ? "" : String.valueOf(value)));
            } else {
                formData.add(new HttpFormData(true, key, HttpFormData.TYPE_TEXT, value == null ? "" : String.valueOf(value)));
            }
        }
        return formData;
    }

    private static JSONObject parseExampleObject(String exampleText) {
        if (exampleText == null || exampleText.isBlank() || !JSONUtil.isTypeJSON(exampleText)) {
            return null;
        }
        try {
            return JSONUtil.parseObj(exampleText);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void parseScripts(JSONObject operation, HttpRequestItem req) {
        String prescript = SwaggerCommonUtil.extractExtensionScript(operation, true);
        if (!prescript.isBlank()) {
            req.setPrescript(prescript);
        }

        String postscript = SwaggerCommonUtil.extractExtensionScript(operation, false);
        if (!postscript.isBlank()) {
            req.setPostscript(postscript);
        }
    }

    private static void parseSecurity(JSONObject openApiRoot, JSONObject operation, HttpRequestItem req, JSONObject securitySchemes) {
        // OpenAPI 允许在根节点声明全局认证；操作节点显式声明时才覆盖全局配置。
        JSONArray security = operation.containsKey("security")
                ? operation.getJSONArray("security")
                : openApiRoot.getJSONArray("security");
        if (security == null || security.isEmpty() || securitySchemes == null) {
            req.setAuthType(AUTH_TYPE_NONE);
            return;
        }

        JSONObject firstSecurity = security.getJSONObject(0);
        if (firstSecurity == null || firstSecurity.isEmpty()) {
            req.setAuthType(AUTH_TYPE_NONE);
            return;
        }

        String securityName = firstSecurity.keySet().iterator().next();
        JSONObject securityScheme = SwaggerCommonUtil.resolveRefObject(openApiRoot, securitySchemes.getJSONObject(securityName));
        if (securityScheme == null) {
            req.setAuthType(AUTH_TYPE_NONE);
            return;
        }

        String type = securityScheme.getStr("type", "");
        switch (type) {
            case "http":
                String scheme = securityScheme.getStr("scheme", "");
                if ("basic".equalsIgnoreCase(scheme)) {
                    req.setAuthType(AUTH_TYPE_BASIC);
                    req.setAuthUsername("");
                    req.setAuthPassword("");
                } else if ("bearer".equalsIgnoreCase(scheme)) {
                    req.setAuthType(AUTH_TYPE_BEARER);
                    req.setAuthToken("");
                }
                break;

            case "apiKey":
                String in = securityScheme.getStr("in", "");
                String name = securityScheme.getStr("name", "");
                if ("header".equals(in)) {
                    if ("Authorization".equalsIgnoreCase(name)) {
                        req.setAuthType(AUTH_TYPE_BEARER);
                        req.setAuthToken("");
                    } else {
                        req.setAuthType(AUTH_TYPE_API_KEY);
                        req.setAuthApiKeyName(name);
                        req.setAuthApiKeyValue("");
                        req.setAuthApiKeyPlacement(AuthApiKeyPlacement.HEADER.getConstant());
                    }
                } else if ("query".equals(in)) {
                    req.setAuthType(AUTH_TYPE_API_KEY);
                    req.setAuthApiKeyName(name);
                    req.setAuthApiKeyValue("");
                    req.setAuthApiKeyPlacement(AuthApiKeyPlacement.QUERY_PARAMS.getConstant());
                }
                break;

            case "oauth2":
            case "openIdConnect":
                req.setAuthType(AUTH_TYPE_BEARER);
                req.setAuthToken("");
                break;

            default:
                req.setAuthType(AUTH_TYPE_NONE);
                break;
        }
    }

    private static void parseServersToEnvironments(JSONObject openApiRoot, CollectionParseResult result, String apiTitle) {
        JSONArray servers = openApiRoot.getJSONArray("servers");
        if (servers == null || servers.isEmpty()) {
            log.debug("OpenAPI 文件中没有定义 servers，跳过环境变量创建");
            return;
        }

        log.info("解析到 {} 个服务器配置，将创建对应的环境变量", servers.size());
        for (int i = 0; i < servers.size(); i++) {
            JSONObject server = servers.getJSONObject(i);
            String url = resolveServerUrl(server);
            if (url.isBlank()) {
                continue;
            }

            String description = server.getStr("description", "");
            String envName = description.isBlank() ? apiTitle + " - Server " + (i + 1) : apiTitle + " - " + description;
            Environment env = new Environment(envName);
            env.setId(UUID.randomUUID().toString());
            env.addVariable("baseUrl", url);

            JSONObject variables = server.getJSONObject("variables");
            if (variables != null) {
                for (String varName : variables.keySet()) {
                    JSONObject varObj = variables.getJSONObject(varName);
                    env.addVariable(varName, varObj.getStr("default", ""));
                }
            }

            result.addEnvironment(env);
        }
    }

    private static String resolveServerUrl(JSONObject server) {
        String url = server.getStr("url", "");
        JSONObject variables = server.getJSONObject("variables");
        if (variables == null || variables.isEmpty()) {
            return url;
        }
        for (String varName : variables.keySet()) {
            JSONObject varObj = variables.getJSONObject(varName);
            if (varObj != null) {
                url = url.replace("{" + varName + "}", varObj.getStr("default", ""));
            }
        }
        return url;
    }

    private static void parseExtensionEnvironments(JSONObject openApiRoot, CollectionParseResult result, String apiTitle) {
        for (String key : openApiRoot.keySet()) {
            if (!key.toLowerCase().startsWith("x-")) {
                continue;
            }
            JSONObject extension = openApiRoot.getJSONObject(key);
            if (extension == null) {
                continue;
            }
            JSONArray envs = extension.getJSONArray("envs");
            if (envs == null || envs.isEmpty()) {
                envs = extension.getJSONArray("environments");
            }
            if (envs == null || envs.isEmpty()) {
                continue;
            }

            for (int i = 0; i < envs.size(); i++) {
                JSONObject envObj = envs.getJSONObject(i);
                if (envObj == null) {
                    continue;
                }

                String envName = SwaggerCommonUtil.firstNonBlank(
                        envObj.getStr("name", ""),
                        envObj.getStr("title", ""),
                        apiTitle + " - Env " + (i + 1)
                );
                Environment env = new Environment(envName);
                env.setId(UUID.randomUUID().toString());

                List<String> urls = SwaggerCommonUtil.extractServerUrlsFromExtensionEnv(envObj);
                if (!urls.isEmpty()) {
                    env.set("baseUrl", urls.get(0));
                }

                SwaggerCommonUtil.addVariablesFromUnknownStructure(env, envObj.get("variables"));
                SwaggerCommonUtil.addVariablesFromUnknownStructure(env, envObj.get("env_var_list"));

                if (!env.getVariableList().isEmpty()) {
                    result.addEnvironment(env);
                }
            }
        }
    }
}
