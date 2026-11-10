package com.laker.postman.collection.importer.postman;

import com.laker.postman.collection.importer.AuthParserUtil;
import com.laker.postman.collection.model.CollectionNode;
import com.laker.postman.collection.model.CollectionNodeType;
import com.laker.postman.collection.model.CollectionParseResult;
import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.model.Variable;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpParam;
import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpFormUrlencoded;
import com.laker.postman.request.model.SavedResponse;
import com.laker.postman.request.model.HttpRequestItem;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.laker.postman.request.model.RequestAuthTypes.AUTH_TYPE_BASIC;
import static com.laker.postman.request.model.RequestAuthTypes.AUTH_TYPE_API_KEY;
import static com.laker.postman.request.model.RequestAuthTypes.AUTH_TYPE_BEARER;
import static com.laker.postman.request.model.RequestAuthTypes.AUTH_TYPE_DIGEST;
import static com.laker.postman.request.model.RequestAuthTypes.AUTH_TYPE_NONE;
import static com.laker.postman.request.model.RequestBodyTypes.BODY_TYPE_BINARY;
import static com.laker.postman.request.model.RequestBodyTypes.BODY_TYPE_FORM_DATA;
import static com.laker.postman.request.model.RequestBodyTypes.BODY_TYPE_FORM_URLENCODED;
import static com.laker.postman.request.model.RequestBodyTypes.BODY_TYPE_RAW;

/**
 * Postman Collection解析器
 * 负责解析Postman Collection格式的请求（v2.1格式）
 * 此类只负责解析，不涉及 UI 层的 TreeNode 组装
 */
@Slf4j
@UtilityClass
public class PostmanCollectionParser {

    // 常量定义
    private static final String KEY_EVENT = "event";
    private static final String KEY_DISABLED = "disabled";
    private static final String KEY_VALUE = "value";
    private static final String KEY_HEADER = "header";
    private static final String MODE_FORMDATA = "formdata";
    private static final String MODE_URLENCODED = "urlencoded";


    /**
     * 解析完整的 Postman Collection JSON，返回解析结果
     *
     * @param json Postman Collection JSON 字符串
     * @return 解析结果，如果解析失败返回 null
     */
    public static CollectionParseResult parsePostmanCollection(String json) {
        try {
            JSONObject postmanRoot = JSONUtil.parseObj(json);
            if (postmanRoot.containsKey("info") && postmanRoot.containsKey("item")) {
                // 解析 collection 名称和描述
                JSONObject info = postmanRoot.getJSONObject("info");
                String collectionName = info.getStr("name", "Postman");
                RequestGroup collectionGroup = new RequestGroup(collectionName);

                // 解析集合描述
                String description = extractDescription(info.get("description"));
                if (description != null && !description.isEmpty()) {
                    collectionGroup.setDescription(description);
                }

                // 解析集合级别的认证
                if (postmanRoot.containsKey("auth")) {
                    AuthParserUtil.parsePostmanAuthToGroup(postmanRoot.getJSONObject("auth"), collectionGroup);
                }

                // 解析集合级别的脚本
                if (postmanRoot.containsKey(KEY_EVENT)) {
                    parseEventsToGroup(postmanRoot.getJSONArray(KEY_EVENT), collectionGroup);
                }

                // 解析集合级别的变量
                if (postmanRoot.containsKey("variable")) {
                    collectionGroup.setVariables(parseVariables(postmanRoot.getJSONArray("variable")));
                }

                JSONArray items = postmanRoot.getJSONArray("item");
                CollectionParseResult result = new CollectionParseResult(collectionGroup);

                // 递归解析树结构
                List<CollectionNode> children = parsePostmanItems(items);
                for (CollectionNode child : children) {
                    result.addChild(child);
                }
                return result;
            }
        } catch (Exception e) {
            // 返回 null 表示解析失败
            log.error("解析Postman Collection失败", e);
            return null;
        }
        return null;
    }

    /**
     * 递归解析Postman集合，返回节点列表
     *
     * @param items Postman collection 的 item 数组
     * @return 节点列表
     */
    private static List<CollectionNode> parsePostmanItems(JSONArray items) {
        List<CollectionNode> nodeList = new ArrayList<>();
        for (Object obj : items) {
            JSONObject item = (JSONObject) obj;

            // 判断是文件夹还是请求
            // 文件夹：有 item 字段
            // 请求：有 request 字段
            if (item.containsKey("item")) {
                // 文件夹节点
                String folderName = item.getStr("name", "default group");
                RequestGroup group = new RequestGroup(folderName);

                // 解析文件夹描述
                String description = extractDescription(item.get("description"));
                if (description != null && !description.isEmpty()) {
                    group.setDescription(description);
                }

                if (item.containsKey("variable")) {
                    group.setVariables(parseVariables(item.getJSONArray("variable")));
                }

                // 解析分组级别的认证
                JSONObject auth = item.getJSONObject("auth");
                if (auth != null) {
                    AuthParserUtil.parsePostmanAuthToGroup(auth, group);
                }

                // 解析分组级别的脚本
                JSONArray events = item.getJSONArray("event");
                if (events != null && !events.isEmpty()) {
                    parseEventsToGroup(events, group);
                }

                CollectionNode folderNode = new CollectionNode(CollectionNodeType.GROUP, group);

                // 递归处理子节点
                JSONArray children = item.getJSONArray("item");
                List<CollectionNode> childNodes = parsePostmanItems(children);
                for (CollectionNode child : childNodes) {
                    folderNode.addChild(child);
                }
                nodeList.add(folderNode);
            } else if (item.containsKey("request")) {
                // 纯请求节点
                HttpRequestItem req = parsePostmanSingleItem(item);
                CollectionNode requestNode = new CollectionNode(CollectionNodeType.REQUEST, req);

                nodeList.add(requestNode);
            }
        }
        return nodeList;
    }


    /**
     * 解析Postman的events到RequestGroup
     */
    private static void parseEventsToGroup(JSONArray events, RequestGroup group) {
        if (events == null || events.isEmpty()) {
            return;
        }

        ScriptPair scripts = parseEventScripts(events);
        if (scripts.preScript != null) {
            group.setPrescript(scripts.preScript);
        }
        if (scripts.postScript != null) {
            group.setPostscript(scripts.postScript);
        }
    }

    /**
     * 从events数组中解析前置和后置脚本
     */
    private static ScriptPair parseEventScripts(JSONArray events) {
        StringBuilder testScript = new StringBuilder();
        StringBuilder preScript = new StringBuilder();

        for (Object e : events) {
            JSONObject eObj = (JSONObject) e;
            String listen = eObj.getStr("listen");
            JSONObject script = eObj.getJSONObject("script");
            if (script != null) {
                Object execObj = script.get("exec");
                if (execObj instanceof JSONArray exec) {
                    for (Object line : exec) {
                        if ("test".equals(listen)) {
                            testScript.append(line).append("\n");
                        } else if ("prerequest".equals(listen)) {
                            preScript.append(line).append("\n");
                        }
                    }
                } else if (execObj instanceof String exec) {
                    if ("test".equals(listen)) {
                        testScript.append(exec).append("\n");
                    } else if ("prerequest".equals(listen)) {
                        preScript.append(exec).append("\n");
                    }
                }
            }
        }

        return new ScriptPair(
                preScript.isEmpty() ? null : preScript.toString(),
                testScript.isEmpty() ? null : testScript.toString()
        );
    }

    /**
     * 脚本对，包含前置和后置脚本
     */
    private static class ScriptPair {
        final String preScript;
        final String postScript;

        ScriptPair(String preScript, String postScript) {
            this.preScript = preScript;
            this.postScript = postScript;
        }
    }

    /**
     * 解析Postman的query参数数组
     */
    private static List<HttpParam> parseQueryParams(JSONArray queryArr) {
        if (queryArr == null || queryArr.isEmpty()) {
            return new ArrayList<>();
        }

        List<HttpParam> paramsList = new ArrayList<>();
        for (Object q : queryArr) {
            JSONObject qObj = (JSONObject) q;
            boolean enabled = !qObj.getBool(KEY_DISABLED, false);
            paramsList.add(new HttpParam(
                    enabled,
                    qObj.getStr("key", ""),
                    qObj.getStr(KEY_VALUE, ""),
                    qObj.getStr("description", "")
            ));
        }
        return paramsList;
    }

    private static List<HttpParam> parseUrlVariables(JSONArray variableArr) {
        if (variableArr == null || variableArr.isEmpty()) {
            return new ArrayList<>();
        }

        List<HttpParam> pathVariables = new ArrayList<>();
        for (Object variable : variableArr) {
            if (!(variable instanceof JSONObject variableObj)) {
                continue;
            }
            String key = variableObj.getStr("key", "");
            if (key.isBlank()) {
                continue;
            }
            boolean enabled = !variableObj.getBool(KEY_DISABLED, false);
            pathVariables.add(new HttpParam(
                    enabled,
                    key,
                    variableObj.getStr(KEY_VALUE, ""),
                    variableObj.getStr("description", "")
            ));
        }
        return pathVariables;
    }

    /**
     * 解析Postman的header数组
     */
    private static List<HttpHeader> parseHeaders(JSONArray headers) {
        if (headers == null || headers.isEmpty()) {
            return new ArrayList<>();
        }

        List<HttpHeader> headersList = new ArrayList<>();
        for (Object h : headers) {
            JSONObject hObj = (JSONObject) h;
            boolean enabled = !hObj.getBool(KEY_DISABLED, false);
            headersList.add(new HttpHeader(
                    enabled,
                    hObj.getStr("key", ""),
                    hObj.getStr(KEY_VALUE, ""),
                    hObj.getStr("description", "")
            ));
        }
        return headersList;
    }

    /**
     * 解析 Postman 的 header 原始字符串
     */
    private static List<HttpHeader> parseHeaders(String rawHeaders) {
        if (rawHeaders == null || rawHeaders.isBlank()) {
            return new ArrayList<>();
        }

        List<HttpHeader> headersList = new ArrayList<>();
        String[] lines = rawHeaders.split("\\r?\\n");
        for (String line : lines) {
            int separatorIndex = line.indexOf(':');
            if (separatorIndex <= 0) {
                continue;
            }
            String key = line.substring(0, separatorIndex).trim();
            String value = line.substring(separatorIndex + 1).trim();
            if (!key.isEmpty()) {
                headersList.add(new HttpHeader(true, key, value));
            }
        }
        return headersList;
    }

    /**
     * 解析Postman的variable数组
     */
    private static List<Variable> parseVariables(JSONArray variables) {
        if (variables == null || variables.isEmpty()) {
            return new ArrayList<>();
        }

        List<Variable> variableList = new ArrayList<>();
        for (Object v : variables) {
            JSONObject vObj = (JSONObject) v;
            boolean enabled = !vObj.getBool(KEY_DISABLED, false);
            String key = vObj.getStr("key", "");
            String value = vObj.getStr(KEY_VALUE, "");
            variableList.add(new Variable(enabled, key, value));
        }
        return variableList;
    }

    /**
     * 解析Postman的formdata数组
     */
    private static List<HttpFormData> parseFormData(JSONArray arr) {
        if (arr == null || arr.isEmpty()) {
            return new ArrayList<>();
        }

        List<HttpFormData> formDataList = new ArrayList<>();
        for (Object o : arr) {
            JSONObject oObj = (JSONObject) o;
            String formType = oObj.getStr("type", "text");
            String key = oObj.getStr("key", "");
            boolean enabled = !oObj.getBool(KEY_DISABLED, false);
            String description = oObj.getStr("description", "");
            if ("file".equals(formType)) {
                Object srcObj = oObj.get("src");
                if (srcObj instanceof JSONArray srcArray && !srcArray.isEmpty()) {
                    for (Object src : srcArray) {
                        formDataList.add(new HttpFormData(
                                enabled,
                                key,
                                HttpFormData.TYPE_FILE,
                                src == null ? "" : String.valueOf(src),
                                description
                        ));
                    }
                } else {
                    String src = srcObj == null ? "" : String.valueOf(srcObj);
                    formDataList.add(new HttpFormData(enabled, key, HttpFormData.TYPE_FILE, src, description));
                }
            } else {
                formDataList.add(new HttpFormData(
                        enabled,
                        key,
                        HttpFormData.TYPE_TEXT,
                        oObj.getStr(KEY_VALUE, ""),
                        description
                ));
            }
        }
        return formDataList;
    }

    /**
     * 解析Postman的urlencoded数组
     */
    private static List<HttpFormUrlencoded> parseUrlencoded(JSONArray arr) {
        if (arr == null || arr.isEmpty()) {
            return new ArrayList<>();
        }

        List<HttpFormUrlencoded> urlencodedList = new ArrayList<>();
        for (Object o : arr) {
            JSONObject oObj = (JSONObject) o;
            boolean enabled = !oObj.getBool(KEY_DISABLED, false);
            urlencodedList.add(new HttpFormUrlencoded(
                    enabled,
                    oObj.getStr("key", ""),
                    oObj.getStr(KEY_VALUE, ""),
                    oObj.getStr("description", "")
            ));
        }
        return urlencodedList;
    }

    /**
     * 解析单个Postman请求item为HttpRequestItem
     */
    private static HttpRequestItem parsePostmanSingleItem(JSONObject item) {
        HttpRequestItem req = new HttpRequestItem();
        req.setId(UUID.randomUUID().toString());
        req.setName(item.getStr("name", "未命名请求"));
        Object requestObj = item.get("request");
        if (requestObj instanceof String requestUrl) {
            req.setMethod("GET");
            req.setUrl(requestUrl);
        } else if (requestObj instanceof JSONObject request) {
            // 解析请求描述
            String description = extractDescription(request.get("description"));
            if (description != null && !description.isEmpty()) {
                req.setDescription(description);
            }

            req.setMethod(request.getStr("method", "GET"));
            // url
            Object urlObj = request.get("url");
            if (urlObj instanceof JSONObject urlJson) {
                req.setUrl(buildRawUrl(urlJson));
                List<HttpParam> pathVariables = parseUrlVariables(urlJson.getJSONArray("variable"));
                if (!pathVariables.isEmpty()) {
                    req.setPathVariablesList(pathVariables);
                }
                // 解析query参数
                JSONArray queryArr = urlJson.getJSONArray("query");
                List<HttpParam> params = parseQueryParams(queryArr);
                if (!params.isEmpty()) {
                    req.setParamsList(params);
                }
            } else if (urlObj instanceof String urlStr) {
                req.setUrl(urlStr);
            }
            // headers
            List<HttpHeader> headersList = new ArrayList<>();
            Object headersObj = request.get(KEY_HEADER);
            if (headersObj instanceof JSONArray headers) {
                headersList = parseHeaders(headers);
            } else if (headersObj instanceof String rawHeaders) {
                headersList = parseHeaders(rawHeaders);
            }
            if (headersList != null && !headersList.isEmpty()) {
                req.setHeadersList(headersList);
            }
            // auth
            JSONObject auth = request.getJSONObject("auth");
            if (auth != null) {
                String authType = auth.getStr("type", "");
                if ("basic".equals(authType)) {
                    req.setAuthType(AUTH_TYPE_BASIC);
                    JSONArray basicArr = auth.getJSONArray("basic");
                    String username = null, password = null;
                    if (basicArr != null) {
                        for (Object o : basicArr) {
                            JSONObject oObj = (JSONObject) o;
                            if ("username".equals(oObj.getStr("key"))) username = oObj.getStr("value", "");
                            if ("password".equals(oObj.getStr("key"))) password = oObj.getStr("value", "");
                        }
                    }
                    req.setAuthUsername(username);
                    req.setAuthPassword(password);
                } else if ("apikey".equals(authType)) {
                    req.setAuthType(AUTH_TYPE_API_KEY);
                    AuthParserUtil.applyPostmanApiKeyAuth(
                            auth.getJSONArray("apikey"),
                            req::setAuthApiKeyName,
                            req::setAuthApiKeyValue,
                            req::setAuthApiKeyPlacement
                    );
                } else if ("bearer".equals(authType)) {
                    req.setAuthType(AUTH_TYPE_BEARER);
                    JSONArray bearerArr = auth.getJSONArray("bearer");
                    if (bearerArr != null && !bearerArr.isEmpty()) {
                        for (Object o : bearerArr) {
                            JSONObject oObj = (JSONObject) o;
                            if ("token".equals(oObj.getStr("key"))) {
                                req.setAuthToken(oObj.getStr("value", ""));
                            }
                        }
                    }
                } else if ("digest".equals(authType)) {
                    req.setAuthType(AUTH_TYPE_DIGEST);
                    JSONArray digestArr = auth.getJSONArray("digest");
                    if (digestArr != null) {
                        for (Object o : digestArr) {
                            JSONObject oObj = (JSONObject) o;
                            if ("username".equals(oObj.getStr("key"))) {
                                req.setAuthUsername(oObj.getStr("value", ""));
                            }
                            if ("password".equals(oObj.getStr("key"))) {
                                req.setAuthPassword(oObj.getStr("value", ""));
                            }
                        }
                    }
                } else if ("noauth".equals(authType)) {
                    req.setAuthType(AUTH_TYPE_NONE);
                } else {
                    req.setAuthType(AUTH_TYPE_NONE);
                }
            }
            // body
            JSONObject body = request.getJSONObject("body");
            if (body != null) {
                String mode = body.getStr("mode", "");
                if ("raw".equals(mode)) {
                    req.setBodyType(BODY_TYPE_RAW);
                    req.setBody(body.getStr("raw", ""));
                } else if (MODE_FORMDATA.equals(mode)) {
                    req.setBodyType(BODY_TYPE_FORM_DATA);
                    List<HttpFormData> formDataList = parseFormData(body.getJSONArray(MODE_FORMDATA));
                    if (!formDataList.isEmpty()) {
                        req.setFormDataList(formDataList);
                    }
                } else if (MODE_URLENCODED.equals(mode)) {
                    req.setBodyType(BODY_TYPE_FORM_URLENCODED);
                    List<HttpFormUrlencoded> urlencodedList = parseUrlencoded(body.getJSONArray(MODE_URLENCODED));
                    if (!urlencodedList.isEmpty()) {
                        req.setUrlencodedList(urlencodedList);
                    }
                } else if ("graphql".equals(mode)) {
                    req.setBodyType(BODY_TYPE_RAW);
                    JSONObject graphql = body.getJSONObject("graphql");
                    if (graphql != null) {
                        req.setBody(graphql.toString());
                    }
                } else if ("file".equals(mode)) {
                    req.setBodyType(BODY_TYPE_BINARY);
                    JSONObject file = body.getJSONObject("file");
                    if (file != null) {
                        req.setBody(file.getStr("src", ""));
                    }
                }
            }
        }
        // event（如test脚本、pre-request脚本）
        JSONArray events = item.getJSONArray("event");
        if (events != null && !events.isEmpty()) {
            ScriptPair scripts = parseEventScripts(events);
            if (scripts.preScript != null) {
                req.setPrescript(scripts.preScript);
            }
            if (scripts.postScript != null) {
                req.setPostscript(scripts.postScript);
            }
        }

        // 解析 response（Postman 的 Examples/Saved Responses）
        JSONArray responses = item.getJSONArray("response");
        if (responses != null && !responses.isEmpty()) {
            List<SavedResponse> savedResponsesList = new ArrayList<>();
            for (Object respObj : responses) {
                JSONObject respJson = (JSONObject) respObj;
                SavedResponse savedResponse = parsePostmanResponse(respJson);
                if (savedResponse != null) {
                    savedResponsesList.add(savedResponse);
                }
            }
            req.setResponse(savedResponsesList);
        }

        return req;
    }

    private static String extractDescription(Object descriptionObj) {
        if (descriptionObj instanceof String description) {
            return description;
        }
        if (descriptionObj instanceof JSONObject description) {
            String content = description.getStr("content", "");
            if (!content.isEmpty()) {
                return content;
            }
        }
        return null;
    }

    private static String buildRawUrl(JSONObject urlJson) {
        String raw = urlJson.getStr("raw", "");
        if (!raw.isBlank()) {
            return raw;
        }

        StringBuilder url = new StringBuilder();
        String protocol = urlJson.getStr("protocol", "");
        if (!protocol.isBlank()) {
            url.append(protocol).append("://");
        }

        Object hostObj = urlJson.get("host");
        if (hostObj instanceof JSONArray hostArray) {
            for (int i = 0; i < hostArray.size(); i++) {
                if (i > 0) {
                    url.append(".");
                }
                url.append(hostArray.getStr(i));
            }
        } else if (hostObj instanceof String host) {
            url.append(host);
        }

        String port = urlJson.getStr("port", "");
        if (!port.isBlank()) {
            url.append(":").append(port);
        }

        Object pathObj = urlJson.get("path");
        if (pathObj instanceof JSONArray pathArray) {
            for (Object segment : pathArray) {
                url.append("/");
                if (segment instanceof JSONObject pathVar) {
                    String key = pathVar.getStr("key", "");
                    String type = pathVar.getStr("type", "");
                    if ("param".equals(type) && !key.isBlank()) {
                        url.append(":").append(key);
                    } else {
                        url.append(pathVar.getStr("value", ""));
                    }
                } else {
                    url.append(String.valueOf(segment));
                }
            }
        } else if (pathObj instanceof String path) {
            if (!path.startsWith("/")) {
                url.append("/");
            }
            url.append(path);
        }

        JSONArray query = urlJson.getJSONArray("query");
        if (query != null && !query.isEmpty()) {
            boolean first = true;
            for (Object q : query) {
                JSONObject qObj = (JSONObject) q;
                if (qObj.getBool(KEY_DISABLED, false)) {
                    continue;
                }
                url.append(first ? "?" : "&");
                first = false;
                url.append(qObj.getStr("key", ""));
                if (qObj.containsKey(KEY_VALUE)) {
                    url.append("=").append(qObj.getStr(KEY_VALUE, ""));
                }
            }
        }

        return url.toString();
    }

    /**
     * 解析 Postman 的单个响应（Example/Saved Response）
     */
    private static SavedResponse parsePostmanResponse(JSONObject respJson) {
        SavedResponse savedResponse = new SavedResponse();

        // 基本信息
        savedResponse.setId(UUID.randomUUID().toString());
        savedResponse.setName(respJson.getStr("name", "Response"));
        savedResponse.setTimestamp(System.currentTimeMillis());

        // 状态信息
        savedResponse.setCode(respJson.getInt("code", 200));
        savedResponse.setStatus(respJson.getStr("status", "OK"));
        savedResponse.setPreviewLanguage(respJson.getStr("_postman_previewlanguage", "text"));

        // 响应头
        JSONArray headers = respJson.getJSONArray(KEY_HEADER);
        List<HttpHeader> headersList = parseHeaders(headers);
        if (!headersList.isEmpty()) {
            savedResponse.setHeaders(headersList);
        }

        // 响应体
        savedResponse.setBody(respJson.getStr("body", ""));

        // 原始请求信息
        JSONObject originalRequest = respJson.getJSONObject("originalRequest");
        if (originalRequest != null) {
            SavedResponse.OriginalRequest origReq = new SavedResponse.OriginalRequest();
            origReq.setMethod(originalRequest.getStr("method", "GET"));

            // 解析 URL
            Object urlObj = originalRequest.get("url");
            if (urlObj instanceof JSONObject urlJson) {
                origReq.setUrl(urlJson.getStr("raw", ""));

                List<HttpParam> pathVariables = parseUrlVariables(urlJson.getJSONArray("variable"));
                if (!pathVariables.isEmpty()) {
                    origReq.setPathVariables(pathVariables);
                }

                // 解析 query 参数
                List<HttpParam> params = parseQueryParams(urlJson.getJSONArray("query"));
                if (!params.isEmpty()) {
                    origReq.setParams(params);
                }
            } else if (urlObj instanceof String urlStr) {
                origReq.setUrl(urlStr);
            }

            // 解析请求头
            List<HttpHeader> reqHeaders = parseHeaders(originalRequest.getJSONArray(KEY_HEADER));
            if (!reqHeaders.isEmpty()) {
                origReq.setHeaders(reqHeaders);
            }

            // 解析请求体
            JSONObject body = originalRequest.getJSONObject("body");
            if (body != null) {
                String mode = body.getStr("mode", "");
                origReq.setBodyType("file".equals(mode) ? BODY_TYPE_BINARY : mode);

                if ("raw".equals(mode)) {
                    origReq.setBody(body.getStr("raw", ""));
                } else if ("file".equals(mode)) {
                    JSONObject file = body.getJSONObject("file");
                    if (file != null) {
                        origReq.setBody(file.getStr("src", ""));
                    }
                } else if (MODE_FORMDATA.equals(mode)) {
                    List<HttpFormData> formDataList = parseFormData(body.getJSONArray(MODE_FORMDATA));
                    if (!formDataList.isEmpty()) {
                        origReq.setFormDataList(formDataList);
                    }
                } else if (MODE_URLENCODED.equals(mode)) {
                    List<HttpFormUrlencoded> urlencodedList = parseUrlencoded(body.getJSONArray(MODE_URLENCODED));
                    if (!urlencodedList.isEmpty()) {
                        origReq.setUrlencodedList(urlencodedList);
                    }
                }
            }

            savedResponse.setOriginalRequest(origReq);
        }

        return savedResponse;
    }
}
