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
import com.laker.postman.collection.model.CollectionNode;
import com.laker.postman.collection.model.CollectionParseResult;
import com.laker.postman.collection.model.CollectionNodeType;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

import static com.laker.postman.request.model.RequestAuthTypes.AUTH_TYPE_API_KEY;
import static com.laker.postman.request.model.RequestAuthTypes.AUTH_TYPE_BASIC;
import static com.laker.postman.request.model.RequestAuthTypes.AUTH_TYPE_BEARER;
import static com.laker.postman.request.model.RequestAuthTypes.AUTH_TYPE_NONE;
import static com.laker.postman.request.model.RequestBodyTypes.BODY_TYPE_FORM_DATA;
import static com.laker.postman.request.model.RequestBodyTypes.BODY_TYPE_FORM_URLENCODED;
import static com.laker.postman.request.model.RequestBodyTypes.BODY_TYPE_RAW;

/**
 * Swagger 2.0 格式解析器
 */
@Slf4j
@UtilityClass
class Swagger2Parser {

    static CollectionParseResult parse(JSONObject swaggerRoot) {
        JSONObject info = swaggerRoot.getJSONObject("info");
        String title = info != null ? info.getStr("title", "Swagger API") : "Swagger API";
        String version = info != null ? info.getStr("version", "") : "";
        String collectionName = version.isEmpty() ? title : title + " (" + version + ")";

        RequestGroup collectionGroup = new RequestGroup(collectionName);
        CollectionParseResult result = new CollectionParseResult(collectionGroup);

        parseSchemesToEnvironments(swaggerRoot, result, title);
        parseExtensionEnvironments(swaggerRoot, result, title);

        String baseUrl = buildBaseUrl(swaggerRoot);
        String host = swaggerRoot.getStr("host", "");
        if (!host.isEmpty()) {
            baseUrl = "{{baseUrl}}";
            log.debug("检测到 host 配置，请求URL将使用 {{{{baseUrl}}}} 变量");
        }

        JSONObject securityDefsMap = swaggerRoot.getJSONObject("securityDefinitions");
        JSONObject paths = swaggerRoot.getJSONObject("paths");
        if (paths == null || paths.isEmpty()) {
            log.warn("Swagger 2.0 文件中没有定义任何路径");
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
                        swaggerRoot,
                        method.toUpperCase(),
                        path,
                        pathItem,
                        operation,
                        baseUrl,
                        securityDefsMap
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

    private static String buildBaseUrl(JSONObject swaggerRoot) {
        String scheme = "http";
        JSONArray schemes = swaggerRoot.getJSONArray("schemes");
        if (schemes != null && !schemes.isEmpty()) {
            scheme = schemes.getStr(0);
        }

        String host = swaggerRoot.getStr("host", "");
        String basePath = swaggerRoot.getStr("basePath", "");
        if (host.isEmpty()) {
            return "";
        }

        StringBuilder baseUrl = new StringBuilder(scheme);
        baseUrl.append("://").append(host);
        if (!basePath.isEmpty() && !"/".equals(basePath)) {
            if (!basePath.startsWith("/")) {
                baseUrl.append("/");
            }
            baseUrl.append(basePath);
        }
        return baseUrl.toString();
    }

    private static String getTag(JSONObject operation) {
        JSONArray tags = operation.getJSONArray("tags");
        return (tags != null && !tags.isEmpty()) ? tags.getStr(0) : "Default";
    }

    private static CollectionNode getOrCreateTagNode(Map<String, CollectionNode> tagNodes, String tag) {
        return tagNodes.computeIfAbsent(tag, key -> new CollectionNode(CollectionNodeType.GROUP, new RequestGroup(key)));
    }

    private static HttpRequestItem parseOperation(
            JSONObject swaggerRoot,
            String method,
            String path,
            JSONObject pathItem,
            JSONObject operation,
            String baseUrl,
            JSONObject securityDefinitions) {

        HttpRequestItem req = new HttpRequestItem();
        req.setId(UUID.randomUUID().toString());
        req.setMethod(method);

        String summary = operation.getStr("summary", "");
        String operationId = operation.getStr("operationId", "");
        req.setName(summary.isEmpty() ? (operationId.isEmpty() ? method + " " + path : operationId) : summary);
        req.setDescription(operation.getStr("description", ""));
        String urlPath = HttpUrlUtil.toRequestPathTemplate(path);
        req.setUrl(baseUrl.isEmpty() ? urlPath : baseUrl + urlPath);

        parseParameters(swaggerRoot, pathItem, operation, req);
        parseRequestBody(swaggerRoot, pathItem, operation, req);
        parseScripts(operation, req);
        parseSecurity(swaggerRoot, operation, req, securityDefinitions);
        return req;
    }

    private static void parseParameters(JSONObject swaggerRoot, JSONObject pathItem, JSONObject operation, HttpRequestItem req) {
        List<JSONObject> parameters = SwaggerCommonUtil.mergeParameters(swaggerRoot, pathItem, operation);
        if (parameters.isEmpty()) {
            return;
        }

        List<HttpParam> queryParams = new ArrayList<>();
        List<HttpParam> pathVariables = new ArrayList<>();
        List<HttpHeader> headers = req.getHeadersList() == null ? new ArrayList<>() : new ArrayList<>(req.getHeadersList());
        List<HttpFormData> formData = new ArrayList<>();
        List<HttpFormUrlencoded> urlencoded = new ArrayList<>();

        for (JSONObject param : parameters) {
            if (param == null) {
                continue;
            }

            String in = param.getStr("in", "");
            String name = param.getStr("name", "");
            if (name.isBlank()) {
                continue;
            }

            Object defaultValue = param.get("default");
            if (defaultValue == null) {
                defaultValue = param.get("x-example");
            }
            String value = defaultValue == null ? "" : String.valueOf(defaultValue);

            switch (in) {
                case "query":
                    queryParams.add(new HttpParam(true, name, value));
                    break;
                case "header":
                    headers.add(new HttpHeader(true, name, value));
                    break;
                case "formData":
                    if ("file".equalsIgnoreCase(param.getStr("type", ""))) {
                        formData.add(new HttpFormData(true, name, HttpFormData.TYPE_FILE, value));
                    } else {
                        urlencoded.add(new HttpFormUrlencoded(true, name, value));
                    }
                    break;
                case "path":
                    pathVariables.add(new HttpParam(true, name, value));
                    break;
                case "body":
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
        if (!formData.isEmpty()) {
            req.setBodyType(BODY_TYPE_FORM_DATA);
            req.setFormDataList(formData);
        }
        if (!urlencoded.isEmpty()) {
            req.setBodyType(BODY_TYPE_FORM_URLENCODED);
            req.setUrlencodedList(urlencoded);
        }
    }

    private static void parseRequestBody(JSONObject swaggerRoot, JSONObject pathItem, JSONObject operation, HttpRequestItem req) {
        List<JSONObject> parameters = SwaggerCommonUtil.mergeParameters(swaggerRoot, pathItem, operation);
        if (parameters.isEmpty()) {
            return;
        }

        for (JSONObject param : parameters) {
            if (!"body".equals(param.getStr("in"))) {
                continue;
            }

            JSONObject schema = SwaggerCommonUtil.resolveRefObject(swaggerRoot, param.getJSONObject("schema"));
            if (schema == null) {
                return;
            }

            req.setBodyType(BODY_TYPE_RAW);
            req.setBody(SwaggerCommonUtil.generateExampleFromSchema(swaggerRoot, schema));
            List<HttpHeader> headers = req.getHeadersList() == null ? new ArrayList<>() : req.getHeadersList();
            String contentType = resolveConsumesContentType(swaggerRoot, operation);
            SwaggerCommonUtil.upsertHeader(headers, "Content-Type", contentType);
            req.setHeadersList(headers);
            return;
        }
    }

    private static String resolveConsumesContentType(JSONObject swaggerRoot, JSONObject operation) {
        JSONArray consumes = operation.getJSONArray("consumes");
        if (consumes == null || consumes.isEmpty()) {
            consumes = swaggerRoot.getJSONArray("consumes");
        }
        if (consumes != null && !consumes.isEmpty()) {
            return consumes.getStr(0);
        }
        return "application/json";
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

    private static void parseSecurity(JSONObject swaggerRoot, JSONObject operation, HttpRequestItem req, JSONObject securityDefinitions) {
        JSONArray security = operation.getJSONArray("security");
        if (security == null || security.isEmpty() || securityDefinitions == null) {
            req.setAuthType(AUTH_TYPE_NONE);
            return;
        }

        JSONObject firstSecurity = security.getJSONObject(0);
        if (firstSecurity == null || firstSecurity.isEmpty()) {
            req.setAuthType(AUTH_TYPE_NONE);
            return;
        }

        String securityName = firstSecurity.keySet().iterator().next();
        JSONObject securityDef = SwaggerCommonUtil.resolveRefObject(swaggerRoot, securityDefinitions.getJSONObject(securityName));
        if (securityDef == null) {
            req.setAuthType(AUTH_TYPE_NONE);
            return;
        }

        String type = securityDef.getStr("type", "");
        switch (type) {
            case "basic":
                req.setAuthType(AUTH_TYPE_BASIC);
                req.setAuthUsername("");
                req.setAuthPassword("");
                break;

            case "apiKey":
                String in = securityDef.getStr("in", "");
                String name = securityDef.getStr("name", "");
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
                req.setAuthType(AUTH_TYPE_BEARER);
                req.setAuthToken("");
                break;

            default:
                req.setAuthType(AUTH_TYPE_NONE);
                break;
        }
    }

    private static void parseSchemesToEnvironments(JSONObject swaggerRoot, CollectionParseResult result, String apiTitle) {
        String host = swaggerRoot.getStr("host", "");
        if (host.isEmpty()) {
            log.debug("Swagger 2.0 文件中没有定义 host，跳过环境变量创建");
            return;
        }

        String basePath = swaggerRoot.getStr("basePath", "");
        JSONArray schemes = swaggerRoot.getJSONArray("schemes");
        if (schemes == null || schemes.isEmpty()) {
            schemes = new JSONArray();
            schemes.add("http");
        }

        log.info("解析到 {} 个协议方案，将创建对应的环境变量", schemes.size());
        for (int i = 0; i < schemes.size(); i++) {
            String scheme = schemes.getStr(i);
            StringBuilder urlBuilder = new StringBuilder(scheme);
            urlBuilder.append("://").append(host);
            if (!basePath.isEmpty() && !"/".equals(basePath)) {
                if (!basePath.startsWith("/")) {
                    urlBuilder.append("/");
                }
                urlBuilder.append(basePath);
            }

            Environment env = new Environment(apiTitle + " - " + scheme.toUpperCase());
            env.setId(UUID.randomUUID().toString());
            env.addVariable("baseUrl", urlBuilder.toString());
            result.addEnvironment(env);
        }
    }

    private static void parseExtensionEnvironments(JSONObject swaggerRoot, CollectionParseResult result, String apiTitle) {
        for (String key : swaggerRoot.keySet()) {
            if (!key.toLowerCase().startsWith("x-")) {
                continue;
            }
            JSONObject extension = swaggerRoot.getJSONObject(key);
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

                Environment env = new Environment(SwaggerCommonUtil.firstNonBlank(
                        envObj.getStr("name", ""),
                        envObj.getStr("title", ""),
                        apiTitle + " - Env " + (i + 1)
                ));
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
