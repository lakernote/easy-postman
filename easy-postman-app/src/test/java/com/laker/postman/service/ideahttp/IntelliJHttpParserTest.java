package com.laker.postman.service.ideahttp;

import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.request.model.RequestAuthTypes;
import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.request.model.HttpRequestItem;


import com.laker.postman.collection.model.CollectionParseResult;
import org.testng.annotations.Test;

import java.util.Base64;

import static com.laker.postman.request.model.RequestAuthTypes.AUTH_TYPE_BASIC;
import static com.laker.postman.request.model.RequestAuthTypes.AUTH_TYPE_BEARER;
import static org.testng.Assert.*;

/**
 * HttpFileParser 单元测试类
 * 测试 HTTP 文件解析器的各种功能
 */
public class IntelliJHttpParserTest {

    @Test(description = "测试解析空内容")
    public void testParseEmptyContent() {
        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(null, "test.http");
        assertNull(result, "null 内容应该返回 null");

        result = IntelliJHttpParser.parseHttpFile("", "test.http");
        assertNull(result, "空字符串应该返回 null");

        result = IntelliJHttpParser.parseHttpFile("   ", "test.http");
        assertNull(result, "空白字符串应该返回 null");
    }

    @Test(description = "测试解析简单的 GET 请求")
    public void testParseSimpleGetRequest() {
        String content = "GET https://api.example.com/users";
        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");

        assertNotNull(result, "应该成功解析");
        assertEquals(result.getChildren().size(), 1, "应该有一个请求");

        HttpRequestItem request = result.getChildren().get(0).asRequest();
        assertEquals(request.getMethod(), "GET");
        assertEquals(request.getUrl(), "https://api.example.com/users");
        assertNotNull(request.getName(), "应该有请求名称");
    }

    @Test(description = "测试解析带名称的请求")
    public void testParseRequestWithName() {
        String content = """
                ### 获取用户列表
                GET https://api.example.com/users
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");
        assertNotNull(result);
        assertEquals(result.getChildren().size(), 1);

        HttpRequestItem request = result.getChildren().get(0).asRequest();
        assertEquals(request.getName(), "获取用户列表");
        assertEquals(request.getMethod(), "GET");
        assertEquals(request.getUrl(), "https://api.example.com/users");
    }

    @Test(description = "测试解析多个请求")
    public void testParseMultipleRequests() {
        String content = """
                ### 获取用户列表
                GET https://api.example.com/users
                
                ### 创建用户
                POST https://api.example.com/users
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");
        assertNotNull(result);
        assertEquals(result.getChildren().size(), 2, "应该有两个请求");

        // 验证第一个请求
        HttpRequestItem request1 = result.getChildren().get(0).asRequest();
        assertEquals(request1.getName(), "获取用户列表");
        assertEquals(request1.getMethod(), "GET");

        // 验证第二个请求
        HttpRequestItem request2 = result.getChildren().get(1).asRequest();
        assertEquals(request2.getName(), "创建用户");
        assertEquals(request2.getMethod(), "POST");
    }

    @Test(description = "测试解析 POST 请求带 JSON body")
    public void testParsePostRequestWithJsonBody() {
        String content = """
                ### 创建用户
                POST https://api.example.com/users
                Content-Type: application/json
                
                {
                  "name": "John Doe",
                  "email": "john@example.com"
                }
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");
        assertNotNull(result);
        assertEquals(result.getChildren().size(), 1);

        HttpRequestItem request = getRequestFromResult(result, 0);
        assertEquals(request.getMethod(), "POST");
        assertEquals(request.getUrl(), "https://api.example.com/users");
        assertEquals(request.getBodyType(), "raw");
        assertTrue(request.getBody().contains("John Doe"));
        assertTrue(request.getBody().contains("john@example.com"));

        // 验证 Content-Type 头部
        assertNotNull(request.getHeadersList());
        boolean hasContentType = request.getHeadersList().stream()
                .anyMatch(h -> "Content-Type".equals(h.getKey()) &&
                        h.getValue().contains("application/json"));
        assertTrue(hasContentType, "应该有 Content-Type 头部");
    }

    @Test(description = "测试解析带多个请求头的请求")
    public void testParseRequestWithMultipleHeaders() {
        String content = """
                ### API 请求
                GET https://api.example.com/data
                Accept: application/json
                User-Agent: EasyPostman/1.0
                X-Custom-Header: custom-value
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");
        assertNotNull(result);

        HttpRequestItem request = getRequestFromResult(result, 0);
        assertNotNull(request.getHeadersList());
        assertEquals(request.getHeadersList().size(), 3, "应该有 3 个请求头");

        // 验证请求头
        assertTrue(hasHeader(request, "Accept", "application/json"));
        assertTrue(hasHeader(request, "User-Agent", "EasyPostman/1.0"));
        assertTrue(hasHeader(request, "X-Custom-Header", "custom-value"));
    }

    @Test(description = "测试解析 Basic 认证")
    public void testParseBasicAuth() {
        String content = """
                ### 需要认证的请求
                GET https://api.example.com/protected
                Authorization: Basic dXNlcm5hbWU6cGFzc3dvcmQ=
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");
        assertNotNull(result);

        HttpRequestItem request = getRequestFromResult(result, 0);
        assertEquals(request.getAuthType(), AUTH_TYPE_BASIC);

        // 验证 Base64 解码
        String decoded = new String(Base64.getDecoder().decode("dXNlcm5hbWU6cGFzc3dvcmQ="));
        String[] parts = decoded.split(":", 2);
        assertEquals(request.getAuthUsername(), parts[0]);
        assertEquals(request.getAuthPassword(), parts[1]);
    }

    @Test(description = "测试解析 Basic 认证（变量格式）")
    public void testParseBasicAuthWithVariables() {
        String content = """
                ### 需要认证的请求
                GET https://api.example.com/protected
                Authorization: Basic {{username}} {{password}}
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");
        assertNotNull(result);

        HttpRequestItem request = getRequestFromResult(result, 0);
        assertEquals(request.getAuthType(), AUTH_TYPE_BASIC);
        assertEquals(request.getAuthUsername(), "{{username}}");
        assertEquals(request.getAuthPassword(), "{{password}}");
    }

    @Test(description = "测试解析 Bearer 认证")
    public void testParseBearerAuth() {
        String content = """
                ### 需要 Bearer Token 的请求
                GET https://api.example.com/protected
                Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");
        assertNotNull(result);

        HttpRequestItem request = getRequestFromResult(result, 0);
        assertEquals(request.getAuthType(), AUTH_TYPE_BEARER);
        assertEquals(request.getAuthToken(), "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9");
    }

    @Test(description = "测试解析 urlencoded body")
    public void testParseUrlencodedBody() {
        String content = """
                ### 提交表单
                POST https://api.example.com/submit
                Content-Type: application/x-www-form-urlencoded
                
                name=John+Doe&email=john%40example.com&age=30
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");
        assertNotNull(result);

        HttpRequestItem request = getRequestFromResult(result, 0);
        assertEquals(request.getMethod(), "POST");
        assertEquals(request.getBodyType(), "x-www-form-urlencoded");
        assertNotNull(request.getUrlencodedList());
        assertEquals(request.getUrlencodedList().size(), 3, "应该有 3 个 urlencoded 字段");

        // 验证字段值
        assertTrue(hasUrlencodedField(request, "name", "John+Doe"));
        assertTrue(hasUrlencodedField(request, "email", "john%40example.com"));
        assertTrue(hasUrlencodedField(request, "age", "30"));
    }

    @Test(description = "测试解析 multipart/form-data body")
    public void testParseMultipartFormData() {
        String content = """
                ### Send a form with text and file fields
                POST https://examples.http-client.intellij.net/post
                Content-Type: multipart/form-data; boundary=WebAppBoundary
                
                --WebAppBoundary
                Content-Disposition: form-data; name="element-name"
                Content-Type: text/plain
                
                Name
                --WebAppBoundary
                Content-Disposition: form-data; name="data"; filename="data.json"
                Content-Type: application/json
                
                < ./request-form-data.json
                --WebAppBoundary--
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");
        assertNotNull(result);

        HttpRequestItem request = getRequestFromResult(result, 0);
        assertEquals(request.getMethod(), "POST");
        assertEquals(request.getBodyType(), "form-data");
        assertNotNull(request.getFormDataList());
        assertEquals(request.getFormDataList().size(), 2, "应该有 2 个 form-data 字段");

        // 验证文本字段
        boolean hasTextField = request.getFormDataList().stream()
                .anyMatch(f -> "element-name".equals(f.getKey()) &&
                        "Name".equals(f.getValue()) &&
                        f.isText());
        assertTrue(hasTextField, "应该有名为 element-name 的文本字段");

        // 验证文件字段
        boolean hasFileField = request.getFormDataList().stream()
                .anyMatch(f -> "data".equals(f.getKey()) &&
                        f.getValue().contains("request-form-data.json") &&
                        !f.isText());
        assertTrue(hasFileField, "应该有名为 data 的文件字段");
    }

    @Test(description = "测试解析各种 HTTP 方法")
    public void testParseVariousHttpMethods() {
        String content = """
                ### GET 请求
                GET https://api.example.com/get
                
                ### POST 请求
                POST https://api.example.com/post
                
                ### PUT 请求
                PUT https://api.example.com/put
                
                ### DELETE 请求
                DELETE https://api.example.com/delete
                
                ### PATCH 请求
                PATCH https://api.example.com/patch
                
                ### HEAD 请求
                HEAD https://api.example.com/head
                
                ### OPTIONS 请求
                OPTIONS https://api.example.com/options
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");
        assertNotNull(result);
        assertEquals(result.getChildren().size(), 7, "应该有 7 个请求");

        assertEquals(getRequestFromResult(result, 0).getMethod(), "GET");
        assertEquals(getRequestFromResult(result, 1).getMethod(), "POST");
        assertEquals(getRequestFromResult(result, 2).getMethod(), "PUT");
        assertEquals(getRequestFromResult(result, 3).getMethod(), "DELETE");
        assertEquals(getRequestFromResult(result, 4).getMethod(), "PATCH");
        assertEquals(getRequestFromResult(result, 5).getMethod(), "HEAD");
        assertEquals(getRequestFromResult(result, 6).getMethod(), "OPTIONS");
    }

    @Test(description = "测试解析 POST 请求带多行 body")
    public void testParsePostRequestWithMultilineBody() {
        String content = """
                ### 创建复杂对象
                POST https://api.example.com/complex
                Content-Type: application/json
                
                {
                  "name": "John",
                  "address": {
                    "street": "123 Main St",
                    "city": "New York"
                  },
                  "tags": ["tag1", "tag2"]
                }
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");
        assertNotNull(result);

        HttpRequestItem request = getRequestFromResult(result, 0);
        assertEquals(request.getMethod(), "POST");
        assertEquals(request.getBodyType(), "raw");
        assertNotNull(request.getBody());
        assertTrue(request.getBody().contains("\"name\": \"John\""));
        assertTrue(request.getBody().contains("\"street\": \"123 Main St\""));
        assertTrue(request.getBody().contains("\"tags\":"));
    }

    @Test(description = "测试解析没有名称的请求（使用 URL 作为名称）")
    public void testParseRequestWithoutName() {
        String content = """
                ###
                GET https://api.example.com/users/profile
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");
        assertNotNull(result);

        HttpRequestItem request = getRequestFromResult(result, 0);
        // 如果没有名称，应该使用 URL 的路径部分作为名称
        assertNotNull(request.getName());
        assertFalse(request.getName().isEmpty());
    }

    @Test(description = "测试解析 SSE 请求")
    public void testParseSSERequest() {
        String content = """
                ### SSE 流式请求
                GET https://api.example.com/events
                Accept: text/event-stream
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");
        assertNotNull(result);

        HttpRequestItem request = getRequestFromResult(result, 0);
        assertEquals(request.getProtocol(), RequestItemProtocolEnum.HTTP);
        assertTrue(hasHeader(request, "Accept", "text/event-stream"));
    }

    @Test(description = "测试解析 WebSocket 请求")
    public void testParseWebSocketRequest() {
        String content = """
                ### WebSocket 连接
                GET wss://echo-websocket.hoppscotch.io/
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");
        assertNotNull(result);

        HttpRequestItem request = getRequestFromResult(result, 0);
        assertEquals(request.getProtocol(), RequestItemProtocolEnum.WEBSOCKET);
        assertEquals(request.getUrl(), "wss://echo-websocket.hoppscotch.io/");
    }

    @Test(description = "测试解析包含空行的请求")
    public void testParseRequestWithEmptyLines() {
        String content = """
                ### 带空行的请求
                POST https://api.example.com/data
                Content-Type: application/json
                
                
                {
                  "data": "value"
                }
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");
        assertNotNull(result);

        HttpRequestItem request = getRequestFromResult(result, 0);
        assertEquals(request.getMethod(), "POST");
        assertNotNull(request.getBody());
        assertTrue(request.getBody().contains("\"data\": \"value\""));
    }

    @Test(description = "测试解析没有分隔符的单个请求")
    public void testParseSingleRequestWithoutSeparator() {
        String content = "GET https://api.example.com/users";
        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");

        assertNotNull(result);
        assertEquals(result.getChildren().size(), 1);

        HttpRequestItem request = getRequestFromResult(result, 0);
        assertEquals(request.getMethod(), "GET");
        assertEquals(request.getUrl(), "https://api.example.com/users");
    }

    @Test(description = "测试解析带查询参数的 URL")
    public void testParseRequestWithQueryParams() {
        String content = """
                ### 搜索请求
                GET https://api.example.com/search?q=test&limit=10&page=1
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");
        assertNotNull(result);

        HttpRequestItem request = getRequestFromResult(result, 0);
        assertEquals(request.getUrl(), "https://api.example.com/search?q=test&limit=10&page=1");
    }

    @Test(description = "测试解析分组节点")
    public void testParseGroupNode() {
        String content = """
                ### 第一个请求
                GET https://api.example.com/one
                
                ### 第二个请求
                POST https://api.example.com/two
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");
        assertNotNull(result);

        // 验证分组信息
        RequestGroup group = result.getGroup();
        assertNotNull(group);
        assertNotNull(group.getName());
        assertEquals(result.getChildren().size(), 2);
    }

    @Test(description = "测试解析无效请求（没有 URL）")
    public void testParseInvalidRequestWithoutUrl() {
        String content = """
                ### 无效请求
                GET
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");
        // 没有有效 URL 的请求不应该被添加到结果中
        assertNull(result, "没有有效 URL 的请求应该返回 null");
    }

    @Test(description = "测试解析带特殊字符的请求头值")
    public void testParseHeaderWithSpecialCharacters() {
        String content = """
                ### 特殊字符测试
                GET https://api.example.com/test
                X-Custom: value:with:colons
                X-Another: value with spaces
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");
        assertNotNull(result);

        HttpRequestItem request = getRequestFromResult(result, 0);
        assertTrue(hasHeader(request, "X-Custom", "value:with:colons"));
        assertTrue(hasHeader(request, "X-Another", "value with spaces"));
    }

    @Test(description = "测试解析 DELETE 请求带 body")
    public void testParseDeleteRequestWithBody() {
        String content = """
                ### 删除资源
                DELETE https://api.example.com/resource/123
                Content-Type: application/json
                
                {
                  "reason": "No longer needed"
                }
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");
        assertNotNull(result);

        HttpRequestItem request = getRequestFromResult(result, 0);
        assertEquals(request.getMethod(), "DELETE");
        assertNotNull(request.getBody());
        assertTrue(request.getBody().contains("\"reason\""));
    }

    @Test(description = "测试解析 PUT 请求")
    public void testParsePutRequest() {
        String content = """
                ### 更新资源
                PUT https://api.example.com/resource/123
                Content-Type: application/json
                
                {
                  "name": "Updated Name"
                }
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");
        assertNotNull(result);

        HttpRequestItem request = getRequestFromResult(result, 0);
        assertEquals(request.getMethod(), "PUT");
        assertEquals(request.getBodyType(), "raw");
        assertTrue(request.getBody().contains("Updated Name"));
    }

    @Test(description = "测试解析 urlencoded body 带空值")
    public void testParseUrlencodedWithEmptyValues() {
        String content = """
                ### 表单提交
                POST https://api.example.com/form
                Content-Type: application/x-www-form-urlencoded
                
                name=John&email=&age=30&empty=
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");
        assertNotNull(result);

        HttpRequestItem request = getRequestFromResult(result, 0);
        assertEquals(request.getBodyType(), "x-www-form-urlencoded");
        assertTrue(hasUrlencodedField(request, "name", "John"));
        assertTrue(hasUrlencodedField(request, "email", ""));
        assertTrue(hasUrlencodedField(request, "age", "30"));
        assertTrue(hasUrlencodedField(request, "empty", ""));
    }

    @Test(description = "测试解析复杂场景：多个请求带各种配置")
    public void testParseComplexScenario() {
        String content = """
                ### 获取用户
                GET https://api.example.com/users
                Accept: application/json
                Authorization: Bearer token123
                
                ### 创建用户
                POST https://api.example.com/users
                Content-Type: application/json
                Authorization: Basic dXNlcm5hbWU6cGFzc3dvcmQ=
                
                {
                  "name": "John",
                  "email": "john@example.com"
                }
                
                ### 提交表单
                POST https://api.example.com/form
                Content-Type: application/x-www-form-urlencoded
                
                name=John&email=john@example.com
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");
        assertNotNull(result);
        assertEquals(result.getChildren().size(), 3, "应该有 3 个请求");

        // 验证第一个请求（GET with Bearer）
        HttpRequestItem req1 = getRequestFromResult(result, 0);
        assertEquals(req1.getMethod(), "GET");
        assertEquals(req1.getAuthType(), AUTH_TYPE_BEARER);
        assertEquals(req1.getAuthToken(), "token123");

        // 验证第二个请求（POST with JSON and Basic Auth）
        HttpRequestItem req2 = getRequestFromResult(result, 1);
        assertEquals(req2.getMethod(), "POST");
        assertEquals(req2.getAuthType(), AUTH_TYPE_BASIC);
        assertEquals(req2.getBodyType(), "raw");
        assertTrue(req2.getBody().contains("John"));

        // 验证第三个请求（POST with urlencoded）
        HttpRequestItem req3 = getRequestFromResult(result, 2);
        assertEquals(req3.getMethod(), "POST");
        assertEquals(req3.getBodyType(), "x-www-form-urlencoded");
        assertTrue(hasUrlencodedField(req3, "name", "John"));
    }

    @Test(description = "测试解析带注释的请求（# 开头）")
    public void testParseRequestWithComments() {
        String content = """
                ### 获取认证 Token
                # @no-cookie-jar
                # @no-log
                # @name GetAuthToken
                POST https://api.example.com/auth/token
                Content-Type: application/json
                
                {
                  "username": "testuser",
                  "password": "testpass"
                }
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");
        assertNotNull(result);
        assertEquals(result.getChildren().size(), 1);

        HttpRequestItem request = getRequestFromResult(result, 0);
        assertEquals(request.getName(), "获取认证 Token");
        assertEquals(request.getMethod(), "POST");
        assertEquals(request.getUrl(), "https://api.example.com/auth/token");
    }

    @Test(description = "测试解析多行 urlencoded body")
    public void testParseMultilineUrlencodedBody() {
        String content = """
                ### 提交多行表单
                POST https://api.example.com/form
                Content-Type: application/x-www-form-urlencoded
                
                grant_type = password &
                username = testuser &
                password = testpass &
                scope = read write
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");
        assertNotNull(result);

        HttpRequestItem request = getRequestFromResult(result, 0);
        assertEquals(request.getMethod(), "POST");
        assertEquals(request.getBodyType(), "x-www-form-urlencoded");
        assertNotNull(request.getUrlencodedList());

        // 验证字段
        assertTrue(hasUrlencodedField(request, "grant_type", "password"));
        assertTrue(hasUrlencodedField(request, "username", "testuser"));
        assertTrue(hasUrlencodedField(request, "password", "testpass"));
        assertTrue(hasUrlencodedField(request, "scope", "read write"));
    }

    @Test(description = "测试解析带响应处理脚本的请求")
    public void testParseRequestWithResponseScript() {
        String content = """
                ### 获取 Token
                POST https://api.example.com/token
                Content-Type: application/json
                
                {
                  "username": "test"
                }
                
                > {% client.global.set("token", response.body.token); %}
                
                ### 使用 Token
                GET https://api.example.com/data
                Authorization: Bearer {{token}}
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");
        assertNotNull(result);
        assertEquals(result.getChildren().size(), 2, "应该解析出 2 个请求");

        HttpRequestItem req1 = getRequestFromResult(result, 0);
        assertEquals(req1.getName(), "获取 Token");
        assertEquals(req1.getMethod(), "POST");

        // 验证响应脚本被转换为 Postman 格式
        assertNotNull(req1.getPostscript(), "应该有后置脚本");
        assertFalse(req1.getPostscript().isEmpty(), "后置脚本不应为空");
        assertTrue(req1.getPostscript().contains("pm.environment.set"), "应该转换为 pm.environment.set");
        assertTrue(req1.getPostscript().contains("pm.response.json()"), "应该转换为 pm.response.json()");
        assertFalse(req1.getPostscript().contains("client.global.set"), "不应该包含 client.global.set");

        HttpRequestItem req2 = getRequestFromResult(result, 1);
        assertEquals(req2.getName(), "使用 Token");
        assertEquals(req2.getMethod(), "GET");
    }

    @Test(description = "测试脚本转换的各种场景")
    public void testScriptConversion() {
        String content = """
                ### 测试多种脚本语法
                POST https://api.example.com/test
                
                > {% 
                client.global.set("var1", response.body.value1);
                client.global.set("var2", response.body.data.value2);
                client.global.get("existingVar");
                client.test("Status is 200", function() {
                    return response.status === 200;
                });
                %}
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");
        assertNotNull(result);
        assertEquals(result.getChildren().size(), 1);

        HttpRequestItem req = getRequestFromResult(result, 0);
        String postscript = req.getPostscript();

        // 打印脚本以便调试
        System.out.println("转换后的脚本:");
        System.out.println(postscript);
        System.out.println("---");

        assertNotNull(postscript, "应该有后置脚本");
        assertTrue(postscript.contains("pm.environment.set(\"var1\""), "应该转换 client.global.set");
        assertTrue(postscript.contains("pm.response.json().value1"), "应该转换 response.body.value1");
        assertTrue(postscript.contains("pm.response.json().data.value2"), "应该转换 response.body.data.value2");
        assertTrue(postscript.contains("pm.environment.get(\"existingVar\")"), "应该转换 client.global.get");
        assertTrue(postscript.contains("pm.test(\"Status is 200\""), "应该转换 client.test");
        assertTrue(postscript.contains("pm.response.code"), "应该转换 response.status");
    }

    @Test(description = "测试解析完整的认证流程（实际案例）")
    public void testParseRealWorldAuthFlow() {
        String content = """
                ### STEP 1: 获取 Bearer Token
                # @no-cookie-jar
                # @no-log
                # @name GetBearerToken
                POST https://{{auth_server}}/api/v1/auth/bearer-token
                Content-Type: application/json
                
                {
                  "username": "{{username}}",
                  "password": "{{password}}"
                }
                
                > {% client.global.set("bearerToken", response.body.token); %}
                
                ### STEP 2: 生成 Access Token
                # @no-cookie-jar
                # @no-log
                # @name GenerateAccessToken
                POST https://{{auth_server}}/api/v1/oauth/token
                Content-Type: application/x-www-form-urlencoded
                
                grant_type = {{grant_type}} &
                assertion = {{bearerToken}} &
                scope = {{scope}} &
                include_refresh_token = true &
                client_id = {{client_id}} &
                client_secret = {{client_secret}}
                
                > {% client.global.set("accessToken", response.body.access_token); %}
                
                ### STEP 3: 验证 Access Token
                # @no-cookie-jar
                # @no-log
                # @name ValidateAccessToken
                POST https://{{auth_server}}/api/v1/oauth/validate
                Authorization: Basic {{client_id}} {{client_secret}}
                Content-Type: application/x-www-form-urlencoded
                
                access_token = {{accessToken}}
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");
        assertNotNull(result, "应该成功解析");
        assertEquals(result.getChildren().size(), 3, "应该有 3 个请求");

        // 验证 STEP 1
        HttpRequestItem req1 = getRequestFromResult(result, 0);
        assertEquals(req1.getName(), "STEP 1: 获取 Bearer Token");
        assertEquals(req1.getMethod(), "POST");
        assertTrue(req1.getUrl().contains("bearer-token"));
        assertEquals(req1.getBodyType(), "raw");
        assertTrue(req1.getBody().contains("{{username}}"));

        // 验证 STEP 1 的响应脚本
        assertNotNull(req1.getPostscript(), "STEP 1 应该有后置脚本");
        assertTrue(req1.getPostscript().contains("pm.environment.set(\"bearerToken\""), "应该设置 bearerToken");
        assertTrue(req1.getPostscript().contains("pm.response.json().token"), "应该从响应中获取 token");

        // 验证 STEP 2
        HttpRequestItem req2 = getRequestFromResult(result, 1);
        assertEquals(req2.getName(), "STEP 2: 生成 Access Token");
        assertEquals(req2.getMethod(), "POST");
        assertEquals(req2.getBodyType(), "x-www-form-urlencoded");
        assertTrue(hasUrlencodedField(req2, "grant_type", "{{grant_type}}"));
        assertTrue(hasUrlencodedField(req2, "assertion", "{{bearerToken}}"));
        assertTrue(hasUrlencodedField(req2, "scope", "{{scope}}"));

        // 验证 STEP 2 的响应脚本
        assertNotNull(req2.getPostscript(), "STEP 2 应该有后置脚本");
        assertTrue(req2.getPostscript().contains("pm.environment.set(\"accessToken\""), "应该设置 accessToken");
        assertTrue(req2.getPostscript().contains("pm.response.json().access_token"), "应该从响应中获取 access_token");

        // 验证 STEP 3
        HttpRequestItem req3 = getRequestFromResult(result, 2);
        assertEquals(req3.getName(), "STEP 3: 验证 Access Token");
        assertEquals(req3.getMethod(), "POST");
        assertEquals(req3.getAuthType(), AUTH_TYPE_BASIC);
        assertEquals(req3.getAuthUsername(), "{{client_id}}");
        assertEquals(req3.getAuthPassword(), "{{client_secret}}");
        assertEquals(req3.getBodyType(), "x-www-form-urlencoded");
        assertTrue(hasUrlencodedField(req3, "access_token", "{{accessToken}}"));
    }

    // 辅助方法：从解析结果获取 HttpRequestItem
    private HttpRequestItem getRequestFromResult(CollectionParseResult result, int index) {
        return result.getChildren().get(index).asRequest();
    }

    // 辅助方法：检查请求是否有指定的请求头
    private boolean hasHeader(HttpRequestItem request, String key, String value) {
        if (request.getHeadersList() == null) {
            return false;
        }
        return request.getHeadersList().stream()
                .anyMatch(h -> key.equals(h.getKey()) && value.equals(h.getValue()));
    }

    // 辅助方法：检查请求是否有指定的 urlencoded 字段
    private boolean hasUrlencodedField(HttpRequestItem request, String key, String value) {
        if (request.getUrlencodedList() == null) {
            return false;
        }
        return request.getUrlencodedList().stream()
                .anyMatch(f -> key.equals(f.getKey()) && value.equals(f.getValue()));
    }

    // 辅助方法：检查分组是否有指定的变量
    private boolean hasVariable(RequestGroup group, String key, String value) {
        if (group.getVariables() == null) {
            return false;
        }
        return group.getVariables().stream()
                .anyMatch(v -> key.equals(v.getKey()) && value.equals(v.getValue()));
    }

    @Test(description = "测试解析环境变量定义（@ 开头）")
    public void testParseEnvironmentVariables() {
        String content = """
                ##############################################################################################################################################
                ### CI Service Integration
                ##############################################################################################################################################
                @ciUserId=test-user-uuid-12345
                @orgId=test-org-uuid-67890
                @displayName=Test User
                @userEmail=test@example.com
                
                ### get user by uuid
                GET {{ciUrl}}/identity/scim/v1/Users?startIndex=0&filter=id eq "{{ciUserId}}"
                Content-Type: application/json
                Authorization: Bearer {{accessToken}}
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "ci-service.http");
        assertNotNull(result, "应该成功解析");

        // 验证分组级别的变量
        RequestGroup group = result.getGroup();
        assertNotNull(group, "应该有分组对象");
        assertNotNull(group.getVariables(), "分组应该有变量列表");
        assertFalse(group.getVariables().isEmpty(), "变量列表不应为空");
        assertEquals(group.getVariables().size(), 4, "应该有4个变量");

        // 验证变量值
        assertTrue(hasVariable(group, "ciUserId", "test-user-uuid-12345"));
        assertTrue(hasVariable(group, "orgId", "test-org-uuid-67890"));
        assertTrue(hasVariable(group, "displayName", "Test User"));
        assertTrue(hasVariable(group, "userEmail", "test@example.com"));

        // 验证请求被正确解析
        assertEquals(result.getChildren().size(), 1, "应该有一个请求");
        HttpRequestItem request = getRequestFromResult(result, 0);
        assertEquals(request.getName(), "get user by uuid");
        assertTrue(request.getUrl().contains("{{ciUserId}}"), "URL 应该包含变量引用");
    }

    @Test(description = "测试解析多个请求与环境变量")
    public void testParseMultipleRequestsWithVariables() {
        String content = """
                @ciUrl=https://api.example.com
                @accessToken=test-token-123
                
                ### get user by email
                GET {{ciUrl}}/users?email={{userEmail}}
                Authorization: Bearer {{accessToken}}
                
                ### create user
                POST {{ciUrl}}/users
                Content-Type: application/json
                Authorization: Bearer {{accessToken}}
                
                {
                  "email": "{{userEmail}}",
                  "name": "{{displayName}}"
                }
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "test.http");
        assertNotNull(result);

        // 验证分组级别的变量
        RequestGroup group = result.getGroup();
        assertNotNull(group.getVariables());
        assertEquals(group.getVariables().size(), 2, "应该有2个变量");
        assertTrue(hasVariable(group, "ciUrl", "https://api.example.com"));
        assertTrue(hasVariable(group, "accessToken", "test-token-123"));

        // 验证两个请求
        assertEquals(result.getChildren().size(), 2);
    }

    @Test(description = "测试解析 JetBrains 官方 # @name、pre-request 和内联 response handler")
    public void testParseJetBrainsOfficialScriptSyntax() {
        String content = """
                # @name CreatePet
                < {% request.variables.set("petName", "Bella") %}
                POST https://example.org/pets/{{petName}} HTTP/2 > {% client.global.set("createdPet", response.body.id); %}
                Content-Type: application/json
                
                {
                  "name": "{{petName}}"
                }
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "pets.http");
        assertNotNull(result);
        assertEquals(result.getChildren().size(), 1);

        HttpRequestItem request = getRequestFromResult(result, 0);
        assertEquals(request.getName(), "CreatePet");
        assertEquals(request.getHttpVersion(), HttpRequestItem.HTTP_VERSION_HTTP_2);
        assertEquals(request.getPrescript(), "pm.variables.set(\"petName\", \"Bella\")");
        assertTrue(request.getPostscript().contains("pm.environment.set(\"createdPet\""));
        assertTrue(request.getPostscript().contains("pm.response.json().id"));
        assertTrue(request.getUrl().contains("{{petName}}"));
    }

    @Test(description = "测试解析多行 pre-request 脚本")
    public void testParseMultilinePreRequestScript() {
        String content = """
                # @name Login
                < {%
                request.variables.set("user", "demo");
                client.global.set("token", "abc");
                %}
                POST https://example.org/login HTTP/1.1
                Content-Type: application/json
                
                {
                  "user": "{{user}}"
                }
                """;

        CollectionParseResult result = IntelliJHttpParser.parseHttpFile(content, "login.http");
        assertNotNull(result);

        HttpRequestItem request = getRequestFromResult(result, 0);
        assertEquals(request.getHttpVersion(), HttpRequestItem.HTTP_VERSION_HTTP_1_1);
        assertTrue(request.getPrescript().contains("pm.variables.set(\"user\", \"demo\")"));
        assertTrue(request.getPrescript().contains("pm.environment.set(\"token\", \"abc\")"));
    }

}
