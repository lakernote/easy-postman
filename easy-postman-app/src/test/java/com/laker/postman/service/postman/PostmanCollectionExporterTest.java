package com.laker.postman.service.postman;

import com.laker.postman.collection.importer.postman.PostmanCollectionParser;
import com.laker.postman.collection.model.CollectionParseResult;
import com.laker.postman.request.model.AuthApiKeyPlacement;
import com.laker.postman.request.model.AuthType;
import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpFormUrlencoded;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpParam;
import com.laker.postman.request.model.HttpRequestItem;


import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.*;

/**
 * PostmanCollectionExporter 单元测试
 */
public class PostmanCollectionExporterTest {

    @Test
    public void testToPostmanItem_BasicRequest() {
        // 准备测试数据
        HttpRequestItem item = new HttpRequestItem();
        item.setName("Get Users");
        item.setMethod("GET");
        item.setUrl("https://api.example.com/users");

        // 执行导出
        JSONObject postmanItem = PostmanCollectionExporter.toPostmanItem(item);

        // 验证结果
        assertNotNull(postmanItem);
        assertEquals(postmanItem.getStr("name"), "Get Users");

        JSONObject request = postmanItem.getJSONObject("request");
        assertNotNull(request);
        assertEquals(request.getStr("method"), "GET");

        JSONObject url = request.getJSONObject("url");
        assertNotNull(url);
        assertEquals(url.getStr("raw"), "https://api.example.com/users");
    }

    @Test
    public void testToPostmanItem_WithQueryParams() {
        // 准备测试数据
        HttpRequestItem item = new HttpRequestItem();
        item.setName("Search");
        item.setMethod("GET");
        item.setUrl("https://api.example.com/search?q=test&limit=10");

        // 执行导出
        JSONObject postmanItem = PostmanCollectionExporter.toPostmanItem(item);

        // 验证URL解析
        JSONObject request = postmanItem.getJSONObject("request");
        JSONObject url = request.getJSONObject("url");

        assertEquals(url.getStr("protocol"), "https");
        assertNotNull(url.getJSONArray("host"));

        // 验证查询参数
        JSONArray query = url.getJSONArray("query");
        assertNotNull(query);
        assertEquals(query.size(), 2);
    }

    @Test
    public void testToPostmanItem_QueryParamsInUrlAndParamsListExportedOnce() {
        // URL 与 Params 表保持同步，同一组参数只应导出一次
        HttpRequestItem item = new HttpRequestItem();
        item.setName("Search");
        item.setMethod("GET");
        item.setUrl("https://api.example.com/search?q=test&limit=10");
        item.setParamsList(List.of(new HttpParam(true, "q", "test"), new HttpParam(true, "limit", "10")));

        JSONObject postmanItem = PostmanCollectionExporter.toPostmanItem(item);

        JSONArray query = postmanItem.getJSONObject("request").getJSONObject("url").getJSONArray("query");
        assertEquals(query.size(), 2);
        assertEquals(query.getJSONObject(0).getStr("key"), "q");
        assertEquals(query.getJSONObject(1).getStr("key"), "limit");
    }

    @Test
    public void testToPostmanItem_WithPathVariables() {
        HttpRequestItem item = new HttpRequestItem();
        item.setName("Get User");
        item.setMethod("GET");
        item.setUrl("https://api.example.com/users/:userId");
        item.setPathVariablesList(List.of(new HttpParam(true, "userId", "42", "User id")));

        JSONObject postmanItem = PostmanCollectionExporter.toPostmanItem(item);

        JSONObject url = postmanItem.getJSONObject("request").getJSONObject("url");
        assertEquals(url.getStr("raw"), "https://api.example.com/users/:userId");
        JSONArray variables = url.getJSONArray("variable");
        assertNotNull(variables);
        assertEquals(variables.size(), 1);
        JSONObject variable = variables.getJSONObject(0);
        assertEquals(variable.getStr("key"), "userId");
        assertEquals(variable.getStr("value"), "42");
        assertEquals(variable.getStr("description"), "User id");
    }

    @Test
    public void testToPostmanItem_WithHeaders() {
        // 准备测试数据
        HttpRequestItem item = new HttpRequestItem();
        item.setName("API Request");
        item.setMethod("POST");
        item.setUrl("https://api.example.com/data");

        // 通过List添加headers
        List<HttpHeader> reqHeaders = new java.util.ArrayList<>();
        reqHeaders.add(new HttpHeader(true, "Content-Type", "application/json"));
        reqHeaders.add(new HttpHeader(true, "Authorization", "Bearer token123"));
        item.setHeadersList(reqHeaders);

        // 执行导出
        JSONObject postmanItem = PostmanCollectionExporter.toPostmanItem(item);

        // 验证请求头
        JSONObject request = postmanItem.getJSONObject("request");
        JSONArray headers = request.getJSONArray("header");
        assertNotNull(headers);
        assertEquals(headers.size(), 2);

        // 验证Content-Type头
        boolean foundContentType = false;
        boolean foundAuth = false;
        for (Object h : headers) {
            JSONObject header = (JSONObject) h;
            if ("Content-Type".equals(header.getStr("key"))) {
                foundContentType = true;
                assertEquals(header.getStr("value"), "application/json");
            } else if ("Authorization".equals(header.getStr("key"))) {
                foundAuth = true;
                assertEquals(header.getStr("value"), "Bearer token123");
            }
        }
        assertTrue(foundContentType && foundAuth);
    }

    @Test
    public void testToPostmanItem_WithRawBody() {
        // 准备测试数据
        HttpRequestItem item = new HttpRequestItem();
        item.setName("Create User");
        item.setMethod("POST");
        item.setUrl("https://api.example.com/users");
        item.setBody("{\"name\": \"John\", \"email\": \"john@example.com\"}");

        // 执行导出
        JSONObject postmanItem = PostmanCollectionExporter.toPostmanItem(item);

        // 验证Body
        JSONObject request = postmanItem.getJSONObject("request");
        JSONObject body = request.getJSONObject("body");
        assertNotNull(body);
        assertEquals(body.getStr("mode"), "raw");
        assertEquals(body.getStr("raw"), "{\"name\": \"John\", \"email\": \"john@example.com\"}");
    }

    @Test
    public void testToPostmanItem_WithFormData() {
        // 准备测试数据
        HttpRequestItem item = new HttpRequestItem();
        item.setName("Upload");
        item.setMethod("POST");
        item.setUrl("https://api.example.com/upload");

        // 通过List添加formData
        List<HttpFormData> formDataList = new java.util.ArrayList<>();
        formDataList.add(new HttpFormData(true, "field1", com.laker.postman.request.model.HttpFormData.TYPE_TEXT, "value1"));
        formDataList.add(new HttpFormData(true, "field2", com.laker.postman.request.model.HttpFormData.TYPE_TEXT, "value2"));
        formDataList.add(new HttpFormData(true, "file", com.laker.postman.request.model.HttpFormData.TYPE_FILE, "/path/to/file.pdf"));
        item.setFormDataList(formDataList);

        // 执行导出
        JSONObject postmanItem = PostmanCollectionExporter.toPostmanItem(item);

        // 验证FormData
        JSONObject request = postmanItem.getJSONObject("request");
        JSONObject body = request.getJSONObject("body");
        assertNotNull(body);
        assertEquals(body.getStr("mode"), "formdata");

        JSONArray formdata = body.getJSONArray("formdata");
        assertNotNull(formdata);
        assertEquals(formdata.size(), 3); // 2个文本字段 + 1个文件字段
    }

    @Test
    public void testToPostmanItem_WithUrlEncoded() {
        // 准备测试数据
        HttpRequestItem item = new HttpRequestItem();
        item.setName("Login");
        item.setMethod("POST");
        item.setUrl("https://api.example.com/login");

        // 通过List添加urlencoded数据
        List<com.laker.postman.request.model.HttpFormUrlencoded> urlencodedList = new java.util.ArrayList<>();
        urlencodedList.add(new com.laker.postman.request.model.HttpFormUrlencoded(true, "username", "admin"));
        urlencodedList.add(new com.laker.postman.request.model.HttpFormUrlencoded(true, "password", "secret"));
        item.setUrlencodedList(urlencodedList);

        // 执行导出
        JSONObject postmanItem = PostmanCollectionExporter.toPostmanItem(item);

        // 验证URL Encoded
        JSONObject request = postmanItem.getJSONObject("request");
        JSONObject body = request.getJSONObject("body");
        assertNotNull(body);
        assertEquals(body.getStr("mode"), "urlencoded");

        JSONArray urlencoded = body.getJSONArray("urlencoded");
        assertNotNull(urlencoded);
        assertEquals(urlencoded.size(), 2);
    }

    @Test
    public void testToPostmanItem_WithBasicAuth() {
        // 准备测试数据
        HttpRequestItem item = new HttpRequestItem();
        item.setName("Protected");
        item.setMethod("GET");
        item.setUrl("https://api.example.com/protected");
        item.setAuthType("Basic Auth");
        item.setAuthUsername("admin");
        item.setAuthPassword("secret");

        // 执行导出
        JSONObject postmanItem = PostmanCollectionExporter.toPostmanItem(item);

        // 验证认证
        JSONObject request = postmanItem.getJSONObject("request");
        JSONObject auth = request.getJSONObject("auth");
        assertNotNull(auth);
        assertEquals(auth.getStr("type"), "basic");

        JSONArray basic = auth.getJSONArray("basic");
        assertNotNull(basic);
        assertEquals(basic.size(), 2);

        // 验证username和password
        boolean foundUsername = false;
        boolean foundPassword = false;
        for (Object o : basic) {
            JSONObject obj = (JSONObject) o;
            if ("username".equals(obj.getStr("key"))) {
                foundUsername = true;
                assertEquals(obj.getStr("value"), "admin");
            } else if ("password".equals(obj.getStr("key"))) {
                foundPassword = true;
                assertEquals(obj.getStr("value"), "secret");
            }
        }
        assertTrue(foundUsername && foundPassword);
    }

    @Test
    public void testToPostmanItem_WithBearerAuth() {
        // 准备测试数据
        HttpRequestItem item = new HttpRequestItem();
        item.setName("OAuth API");
        item.setMethod("GET");
        item.setUrl("https://api.example.com/oauth");
        item.setAuthType("Bearer Token");
        item.setAuthToken("jwt-token-123");

        // 执行导出
        JSONObject postmanItem = PostmanCollectionExporter.toPostmanItem(item);

        // 验证认证
        JSONObject request = postmanItem.getJSONObject("request");
        JSONObject auth = request.getJSONObject("auth");
        assertNotNull(auth);
        assertEquals(auth.getStr("type"), "bearer");

        JSONArray bearer = auth.getJSONArray("bearer");
        assertNotNull(bearer);
        assertEquals(bearer.size(), 1);

        JSONObject tokenObj = (JSONObject) bearer.get(0);
        assertEquals(tokenObj.getStr("key"), "token");
        assertEquals(tokenObj.getStr("value"), "jwt-token-123");
    }

    @Test
    public void testToPostmanItem_WithApiKeyAuth() {
        HttpRequestItem item = new HttpRequestItem();
        item.setName("API Key API");
        item.setMethod("GET");
        item.setUrl("https://api.example.com/keyed");
        item.setAuthType(AuthType.API_KEY.getConstant());
        item.setAuthApiKeyName("api_key");
        item.setAuthApiKeyValue("secret");
        item.setAuthApiKeyPlacement(AuthApiKeyPlacement.QUERY_PARAMS.getConstant());

        JSONObject postmanItem = PostmanCollectionExporter.toPostmanItem(item);

        JSONObject auth = postmanItem.getJSONObject("request").getJSONObject("auth");
        assertNotNull(auth);
        assertEquals(auth.getStr("type"), "apikey");
        JSONArray apiKey = auth.getJSONArray("apikey");
        assertNotNull(apiKey);
        assertEquals(apiKey.size(), 3);
        assertEquals(((JSONObject) apiKey.get(0)).getStr("key"), "key");
        assertEquals(((JSONObject) apiKey.get(0)).getStr("value"), "api_key");
        assertEquals(((JSONObject) apiKey.get(1)).getStr("key"), "value");
        assertEquals(((JSONObject) apiKey.get(1)).getStr("value"), "secret");
        assertEquals(((JSONObject) apiKey.get(2)).getStr("key"), "in");
        assertEquals(((JSONObject) apiKey.get(2)).getStr("value"), "query");
    }

    @Test
    public void testToPostmanItem_WithDigestAuth() {
        HttpRequestItem item = new HttpRequestItem();
        item.setName("Digest API");
        item.setMethod("GET");
        item.setUrl("https://api.example.com/digest");
        item.setAuthType(AuthType.DIGEST.getConstant());
        item.setAuthUsername("digest-user");
        item.setAuthPassword("digest-pass");

        JSONObject postmanItem = PostmanCollectionExporter.toPostmanItem(item);

        JSONObject auth = postmanItem.getJSONObject("request").getJSONObject("auth");
        assertNotNull(auth);
        assertEquals(auth.getStr("type"), "digest");
        JSONArray digest = auth.getJSONArray("digest");
        assertNotNull(digest);
        assertEquals(digest.size(), 2);
        assertEquals(((JSONObject) digest.get(0)).getStr("key"), "username");
        assertEquals(((JSONObject) digest.get(0)).getStr("value"), "digest-user");
        assertEquals(((JSONObject) digest.get(1)).getStr("key"), "password");
        assertEquals(((JSONObject) digest.get(1)).getStr("value"), "digest-pass");
    }

    @Test
    public void testToPostmanItem_WithScripts() {
        // 准备测试数据
        HttpRequestItem item = new HttpRequestItem();
        item.setName("API With Scripts");
        item.setMethod("GET");
        item.setUrl("https://api.example.com/data");
        item.setPrescript("console.log('before');\npm.environment.set('var', 'value');");
        item.setPostscript("pm.test('Status code is 200', function() {\n    pm.response.to.have.status(200);\n});");

        // 执行导出
        JSONObject postmanItem = PostmanCollectionExporter.toPostmanItem(item);

        // 验证事件
        JSONArray events = postmanItem.getJSONArray("event");
        assertNotNull(events);
        assertEquals(events.size(), 2);

        // 验证前置脚本
        JSONObject preEvent = null;
        JSONObject testEvent = null;
        for (Object e : events) {
            JSONObject event = (JSONObject) e;
            if ("prerequest".equals(event.getStr("listen"))) {
                preEvent = event;
            } else if ("test".equals(event.getStr("listen"))) {
                testEvent = event;
            }
        }

        assertNotNull(preEvent);
        JSONObject preScript = preEvent.getJSONObject("script");
        assertNotNull(preScript);
        assertEquals(preScript.getStr("type"), "text/javascript");
        assertNotNull(preScript.getJSONArray("exec"));

        assertNotNull(testEvent);
        JSONObject testScript = testEvent.getJSONObject("script");
        assertNotNull(testScript);
        assertEquals(testScript.getStr("type"), "text/javascript");
        assertNotNull(testScript.getJSONArray("exec"));
    }

    @Test
    public void testToPostmanItem_NoAuth() {
        // 准备测试数据 - 无认证
        HttpRequestItem item = new HttpRequestItem();
        item.setName("Public API");
        item.setMethod("GET");
        item.setUrl("https://api.example.com/public");
        item.setAuthType("No Auth");

        // 执行导出
        JSONObject postmanItem = PostmanCollectionExporter.toPostmanItem(item);

        // 验证无认证信息
        JSONObject request = postmanItem.getJSONObject("request");
        assertNull(request.getJSONObject("auth"));
    }

    @Test
    public void testToPostmanItem_EmptyBody() {
        // 准备测试数据 - 空Body
        HttpRequestItem item = new HttpRequestItem();
        item.setName("Simple GET");
        item.setMethod("GET");
        item.setUrl("https://api.example.com/data");

        // 执行导出
        JSONObject postmanItem = PostmanCollectionExporter.toPostmanItem(item);

        // 验证无Body
        JSONObject request = postmanItem.getJSONObject("request");
        assertNull(request.getJSONObject("body"));
    }

    @Test
    public void testToPostmanItem_ComplexRequest() {
        // 准备测试数据 - 复杂请求
        HttpRequestItem item = new HttpRequestItem();
        item.setName("Complex Request");
        item.setMethod("POST");
        item.setUrl("https://api.example.com/complex?param1=value1");

        // 通过List添加headers
        List<com.laker.postman.request.model.HttpHeader> headers = new java.util.ArrayList<>();
        headers.add(new com.laker.postman.request.model.HttpHeader(true, "Content-Type", "application/json"));
        headers.add(new com.laker.postman.request.model.HttpHeader(true, "X-Custom", "custom-value"));
        item.setHeadersList(headers);

        item.setBody("{\"data\": \"value\"}");
        item.setAuthType("Bearer Token");
        item.setAuthToken("token123");
        item.setPrescript("console.log('pre');");
        item.setPostscript("console.log('post');");

        // 执行导出
        JSONObject postmanItem = PostmanCollectionExporter.toPostmanItem(item);

        // 验证所有部分都被正确导出
        assertNotNull(postmanItem);
        assertEquals(postmanItem.getStr("name"), "Complex Request");

        JSONObject request = postmanItem.getJSONObject("request");
        assertNotNull(request);
        assertNotNull(request.getJSONObject("url"));
        assertNotNull(request.getJSONArray("header"));
        assertNotNull(request.getJSONObject("body"));
        assertNotNull(request.getJSONObject("auth"));

        JSONArray events = postmanItem.getJSONArray("event");
        assertNotNull(events);
        assertEquals(events.size(), 2);
    }

    @Test
    public void testRoundTrip() {
        // 测试导出后再导入，数据应该保持一致
        HttpRequestItem original = new HttpRequestItem();
        original.setName("Round Trip Test");
        original.setMethod("POST");
        original.setUrl("https://api.example.com/test");
        original.setBody("{\"test\": \"data\"}");

        // 通过List添加headers
        List<com.laker.postman.request.model.HttpHeader> headers = new java.util.ArrayList<>();
        headers.add(new com.laker.postman.request.model.HttpHeader(true, "Content-Type", "application/json"));
        original.setHeadersList(headers);

        // 导出为单个请求
        JSONObject exported = PostmanCollectionExporter.toPostmanItem(original);

        // 构建完整的 Collection JSON 用于测试
        JSONObject collection = new JSONObject();
        JSONObject info = new JSONObject();
        info.set("name", "Test Collection");
        collection.set("info", info);
        JSONArray items = new JSONArray();
        items.add(exported);
        collection.set("item", items);

        // 通过 PostmanCollectionParser 解析完整的 Collection
        CollectionParseResult result = PostmanCollectionParser.parsePostmanCollection(collection.toString());

        // 验证
        assertNotNull(result);
        assertEquals(result.getChildren().size(), 1);

        var requestNode = result.getChildren().get(0);
        assertTrue(requestNode.isRequest());
        HttpRequestItem imported = requestNode.asRequest();

        assertEquals(imported.getName(), original.getName());
        assertEquals(imported.getMethod(), original.getMethod());
        assertEquals(imported.getUrl(), original.getUrl());
        assertEquals(imported.getBody(), original.getBody());
    }

    @Test
    public void testToPostmanItem_UrlParsing() {
        // 测试URL解析（包含protocol, host, path）
        HttpRequestItem item = new HttpRequestItem();
        item.setName("URL Test");
        item.setMethod("GET");
        item.setUrl("https://api.example.com/v1/users/123");

        // 执行导出
        JSONObject postmanItem = PostmanCollectionExporter.toPostmanItem(item);

        // 验证URL解析
        JSONObject request = postmanItem.getJSONObject("request");
        JSONObject url = request.getJSONObject("url");

        assertEquals(url.getStr("protocol"), "https");

        JSONArray host = url.getJSONArray("host");
        assertNotNull(host);
        assertTrue(host.contains("api"));
        assertTrue(host.contains("example"));
        assertTrue(host.contains("com"));

        JSONArray path = url.getJSONArray("path");
        assertNotNull(path);
        assertTrue(path.contains("v1"));
        assertTrue(path.contains("users"));
        assertTrue(path.contains("123"));
    }
}
