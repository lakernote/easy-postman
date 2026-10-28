package com.laker.postman.service.curl;

import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.http.runtime.model.HttpEventInfo;
import com.laker.postman.http.request.PreparedRequestFactory;
import com.laker.postman.request.model.AuthType;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpParam;
import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpFormUrlencoded;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.RequestBodyTypes;
import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.request.model.TransportAuth;
import com.laker.postman.request.util.HttpUrlUtil;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * CurlParser 单元测试类
 * 测试 cURL 命令解析器的各种功能（仅支持 Bash 格式）
 */
public class CurlParserTest {

    @Test(description = "解析简单的GET请求")
    public void testParseSimpleGetRequest() {
        String curl = "curl https://api.example.com/users";
        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.url, "https://api.example.com/users");
        assertEquals(result.method, "GET");
        assertTrue(result.headersList == null || result.headersList.isEmpty());
        assertNull(result.body);
        assertTrue(result.paramsList == null || result.paramsList.isEmpty());
        assertFalse(result.followRedirects);
    }

    @Test(description = "解析带查询参数的GET请求")
    public void testParseGetRequestWithQueryParams() {
        String curl = "curl 'https://api.example.com/users?page=1&limit=10&sort=name'";
        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.url, "https://api.example.com/users?page=1&limit=10&sort=name");
        assertEquals(result.method, "GET");
        assertEquals(findParamValue(result.paramsList, "page"), "1");
        assertEquals(findParamValue(result.paramsList, "limit"), "10");
        assertEquals(findParamValue(result.paramsList, "sort"), "name");
    }

    @Test(description = "解析POST请求带JSON数据")
    public void testParsePostRequestWithJsonData() {
        String curl = "curl -X POST https://api.example.com/users " +
                "-H 'Content-Type: application/json' " +
                "-d '{\"name\":\"John\",\"email\":\"john@example.com\"}'";
        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.url, "https://api.example.com/users");
        assertEquals(result.method, "POST");
        assertEquals(findHeaderValue(result.headersList, "Content-Type"), "application/json");
        assertEquals(result.body, "{\"name\":\"John\",\"email\":\"john@example.com\"}");
    }

    @Test(description = "解析带多个请求头的请求")
    public void testParseRequestWithMultipleHeaders() {
        String curl = "curl -X GET https://api.example.com/users " +
                "-H 'Authorization: Bearer token123' " +
                "-H 'Accept: application/json' " +
                "-H 'User-Agent: MyApp/1.0'";
        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.url, "https://api.example.com/users");
        assertEquals(result.method, "GET");
        assertEquals(findHeaderValue(result.headersList, "Authorization"), "Bearer token123");
        assertEquals(findHeaderValue(result.headersList, "Accept"), "application/json");
        assertEquals(findHeaderValue(result.headersList, "User-Agent"), "MyApp/1.0");
    }

    @Test(description = "解析带Cookie的请求")
    public void testParseRequestWithCookie() {
        String curl = "curl https://api.example.com/users -b 'session=abc123; lang=en'";
        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.url, "https://api.example.com/users");
        assertEquals(findHeaderValue(result.headersList, "Cookie"), "session=abc123; lang=en");
    }

    @Test(description = "解析带重定向标志的请求")
    public void testParseRequestWithRedirectFlag() {
        String curl = "curl -L https://api.example.com/users";
        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.url, "https://api.example.com/users");
        assertTrue(result.followRedirects);

        // 测试长格式
        curl = "curl --location https://api.example.com/users";
        result = CurlParser.parse(curl);
        assertTrue(result.followRedirects);
    }

    @Test(description = "解析表单数据请求")
    public void testParseFormDataRequest() {
        String curl = "curl -X POST https://api.example.com/upload " +
                "-F 'name=John' " +
                "-F 'email=john@example.com' " +
                "-F 'file=@/path/to/file.txt'";
        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.url, "https://api.example.com/upload");
        assertEquals(result.method, "POST");
        assertEquals(findHeaderValue(result.headersList, "Content-Type"), "multipart/form-data");
        assertEquals(findFormDataValue(result.formDataList, "name"), "John");
        assertEquals(findFormDataValue(result.formDataList, "email"), "john@example.com");
        assertEquals(findFormDataFileValue(result.formDataList, "file"), "/path/to/file.txt");
    }

    @Test(description = "解析带转义字符的数据")
    public void testParseDataWithEscapeCharacters() {
        String curl = "curl -X POST https://api.example.com/data " +
                "-d \"{\\\"message\\\":\\\"Hello\\nWorld\\t!\\\",\\\"path\\\":\\\"/usr/local\\\"}\"";
        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.method, "POST");
        assertTrue(result.body.contains("Hello\\nWorld\\t!"));
        assertFalse(result.body.contains("Hello\nWorld\t!"));
        assertTrue(result.body.contains("/usr/local"));
    }

    @Test(description = "解析多行cURL命令（Bash格式）")
    public void testParseMultiLineCurlCommand() {
        String curl = """
                curl -X POST \\
                  https://api.example.com/users \\
                  -H 'Content-Type: application/json' \\
                  -d '{"name":"John"}'
                """;
        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.url, "https://api.example.com/users");
        assertEquals(result.method, "POST");
        assertEquals(findHeaderValue(result.headersList, "Content-Type"), "application/json");
        assertEquals(result.body, "{\"name\":\"John\"}");
    }

    @Test(description = "批量 cURL 文本应拆分并逐条解析")
    public void testParseMultipleCurlCommands() {
        String curls = """
                curl 'https://api.example.com/orders/123' \\
                  -H 'Accept: application/json' \\
                  -H 'Cookie: session=abc; lang=zh-CN' \\
                  --insecure ;
                curl 'https://api.example.com/orders/search' \\
                  -H 'Content-Type: application/json' \\
                  --data-raw '{"orderNo":"","pageNum":1,"pageSize":20}' \\
                  --insecure ;
                curl 'https://api.example.com/orders/stats?date=&status=' \\
                  -H 'Accept: application/json' \\
                  --insecure
                """;

        java.util.List<CurlRequest> results = CurlParser.parseMany(curls);

        assertEquals(results.size(), 3);
        assertEquals(results.get(0).url, "https://api.example.com/orders/123");
        assertEquals(results.get(0).method, "GET");
        assertEquals(findHeaderValue(results.get(0).headersList, "Cookie"), "session=abc; lang=zh-CN");
        assertNull(results.get(0).body);

        assertEquals(results.get(1).url, "https://api.example.com/orders/search");
        assertEquals(results.get(1).method, "POST");
        assertEquals(findHeaderValue(results.get(1).headersList, "Content-Type"), "application/json");
        assertEquals(results.get(1).body, "{\"orderNo\":\"\",\"pageNum\":1,\"pageSize\":20}");

        assertEquals(results.get(2).url, "https://api.example.com/orders/stats?date=&status=");
        assertEquals(results.get(2).method, "GET");
    }

    @Test(description = "单条解析入口遇到批量 cURL 文本时只解析第一条")
    public void testParseSingleCommandFromMultipleCurlText() {
        String curls = """
                curl 'https://api.example.com/first' -H 'X-First: true' ;
                curl 'https://api.example.com/second' -H 'X-Second: true'
                """;

        CurlRequest result = CurlParser.parse(curls);

        assertEquals(result.url, "https://api.example.com/first");
        assertEquals(findHeaderValue(result.headersList, "X-First"), "true");
        assertNull(findHeaderValue(result.headersList, "X-Second"));
    }

    @Test(description = "批量 cURL 拆分不能被引号中的分号干扰")
    public void testSplitMultipleCurlCommandsIgnoresQuotedSemicolon() {
        String curls = """
                curl 'https://api.example.com/a' -H 'Cookie: a=1; b=2'
                curl 'https://api.example.com/b' --data-raw '{"note":"a;b"}'
                """;

        java.util.List<String> commands = CurlParser.splitCommands(curls);

        assertEquals(commands.size(), 2);
        assertTrue(commands.get(0).contains("Cookie: a=1; b=2"));
        assertTrue(commands.get(1).contains("{\"note\":\"a;b\"}"));
    }

    @Test(description = "解析带URL参数的请求")
    public void testParseRequestWithUrlParameter() {
        String curl = "curl --url https://api.example.com/users -X GET";
        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.url, "https://api.example.com/users");
        assertEquals(result.method, "GET");
    }

    @Test(description = "长选项应支持 --opt=value 形式")
    public void testParseLongOptionsWithInlineValue() {
        String curl = "curl --url=https://api.example.com/users " +
                "--request=POST " +
                "--header='Accept: application/json' " +
                "--data-raw='{\"ok\":true}'";

        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.url, "https://api.example.com/users");
        assertEquals(result.method, "POST");
        assertEquals(findHeaderValue(result.headersList, "Accept"), "application/json");
        assertEquals(result.body, "{\"ok\":true}");
    }

    @Test(description = "元数据表应消费不支持选项的参数，避免把代理地址误当请求 URL")
    public void testOptionMetadataConsumesIgnoredOptionValues() {
        String curl = "curl https://api.example.com/users " +
                "--proxy http://proxy.example.com:8080 " +
                "--connect-timeout 10 " +
                "--resolve api.example.com:443:127.0.0.1";

        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.url, "https://api.example.com/users");
        assertEquals(result.method, "GET");
    }

    @Test(description = "短选项聚合应按 curl 语义展开")
    public void testParseShortOptionCluster() {
        String curl = "curl -LkXPOST https://api.example.com/users";

        CurlRequest result = CurlParser.parse(curl);

        assertTrue(result.followRedirects);
        assertEquals(result.method, "POST");
        assertEquals(result.url, "https://api.example.com/users");
    }

    @Test(description = "解析不同的数据参数格式")
    public void testParseDataParameterVariants() {
        // 测试 -d
        String curl1 = "curl -d 'test data' https://api.example.com";
        CurlRequest result1 = CurlParser.parse(curl1);
        assertEquals(result1.method, "POST");
        assertEquals(result1.body, "test data");

        // 测试 --data
        String curl2 = "curl --data 'test data' https://api.example.com";
        CurlRequest result2 = CurlParser.parse(curl2);
        assertEquals(result2.method, "POST");
        assertEquals(result2.body, "test data");

        // 测试 --data-raw
        String curl3 = "curl --data-raw 'test data' https://api.example.com";
        CurlRequest result3 = CurlParser.parse(curl3);
        assertEquals(result3.method, "POST");
        assertEquals(result3.body, "test data");

        // 测试 --data-binary
        String curl4 = "curl --data-binary 'test data' https://api.example.com";
        CurlRequest result4 = CurlParser.parse(curl4);
        assertEquals(result4.method, "POST");
        assertEquals(result4.body, "test data");
    }

    @Test(description = "解析带引号的复杂命令")
    public void testParseComplexQuotedCommand() {
        String curl = "curl -X POST 'https://api.example.com/users' " +
                "-H \"Content-Type: application/json\" " +
                "-H 'Authorization: Bearer token' " +
                "-d $'{\"data\": \"test\"}'";
        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.url, "https://api.example.com/users");
        assertEquals(result.method, "POST");
        assertEquals(findHeaderValue(result.headersList, "Content-Type"), "application/json");
        assertEquals(findHeaderValue(result.headersList, "Authorization"), "Bearer token");
        assertEquals(result.body, "{\"data\": \"test\"}");
    }

    @Test(description = "测试默认方法推断")
    public void testDefaultMethodInference() {
        // 没有数据和方法的请求应该默认为GET
        String curl1 = "curl https://api.example.com/users";
        CurlRequest result1 = CurlParser.parse(curl1);
        assertEquals(result1.method, "GET");

        // 有数据但没有指定方法的请求应该默认为POST
        String curl2 = "curl https://api.example.com/users -d 'data'";
        CurlRequest result2 = CurlParser.parse(curl2);
        assertEquals(result2.method, "POST");

        // 有表单数据但没有指定方法的请求应该默认为POST
        String curl3 = "curl https://api.example.com/users -F 'name=test'";
        CurlRequest result3 = CurlParser.parse(curl3);
        assertEquals(result3.method, "POST");
    }

    @Test(description = "测试空值和边界情况")
    public void testNullAndEdgeCases() {
        // 空字符串
        CurlRequest result1 = CurlParser.parse("");
        assertEquals(result1.method, "GET");
        assertNull(result1.url);

        // 只有curl命令
        CurlRequest result2 = CurlParser.parse("curl");
        assertEquals(result2.method, "GET");
        assertNull(result2.url);

        // null输入
        CurlRequest result3 = CurlParser.parse(null);
        assertEquals(result3.method, "GET");
        assertNull(result3.url);
    }

    @Test(description = "测试PreparedRequest转cURL功能")
    public void testToCurlMethod() {
        PreparedRequest preparedRequest = new PreparedRequest();
        preparedRequest.method = "POST";
        preparedRequest.url = "https://api.example.com/users";
        preparedRequest.headersList = new java.util.ArrayList<>();
        preparedRequest.headersList.add(new HttpHeader(true, "Content-Type", "application/json"));
        preparedRequest.headersList.add(new HttpHeader(true, "Authorization", "Bearer token"));
        preparedRequest.body = "{\"name\":\"John\"}";

        String curlCommand = CurlParser.toCurl(preparedRequest);

        assertTrue(curlCommand.startsWith("curl"));
        assertTrue(curlCommand.contains("-X POST"));
        assertTrue(curlCommand.contains("'https://api.example.com/users'"));
        assertTrue(curlCommand.contains("-H 'Content-Type: application/json'"));
        assertTrue(curlCommand.contains("-H 'Authorization: Bearer token'"));
        assertTrue(curlCommand.contains("--data"));
        assertTrue(curlCommand.contains("'{\"name\":\"John\"}'"));
    }

    @Test(description = "测试PreparedRequest转cURL - 表单数据")
    public void testToCurlWithFormData() {
        PreparedRequest preparedRequest = new PreparedRequest();
        preparedRequest.method = "POST";
        preparedRequest.url = "https://api.example.com/upload";
        preparedRequest.formDataList = new java.util.ArrayList<>();
        preparedRequest.formDataList.add(new HttpFormData(true, "name", HttpFormData.TYPE_TEXT, "John"));
        preparedRequest.formDataList.add(new HttpFormData(true, "email", HttpFormData.TYPE_TEXT, "john@example.com"));
        preparedRequest.formDataList.add(new HttpFormData(true, "file", HttpFormData.TYPE_FILE, "/path/to/file.txt"));

        String curlCommand = CurlParser.toCurl(preparedRequest);

        assertTrue(curlCommand.contains("-F 'name=John'"));
        assertTrue(curlCommand.contains("-F 'email=john@example.com'"));
        assertTrue(curlCommand.contains("-F 'file=@/path/to/file.txt'"));
    }

    @Test(description = "测试PreparedRequest转cURL - Digest认证")
    public void testToCurlWithDigestAuth() {
        PreparedRequest preparedRequest = new PreparedRequest();
        preparedRequest.method = "GET";
        preparedRequest.url = "https://api.example.com/digest";
        preparedRequest.transportAuth = new TransportAuth(AuthType.DIGEST.getConstant(), "digest-user", "digest pass");

        String curlCommand = CurlParser.toCurl(preparedRequest);

        assertTrue(curlCommand.contains(" --digest"));
        assertTrue(curlCommand.contains(" --user 'digest-user:digest pass'"));
    }

    @Test(description = "测试Digest认证cURL导出后可重新导入")
    public void testDigestCurlRoundTripImport() {
        PreparedRequest preparedRequest = new PreparedRequest();
        preparedRequest.method = "GET";
        preparedRequest.url = "https://api.example.com/digest";
        preparedRequest.transportAuth = new TransportAuth(AuthType.DIGEST.getConstant(), "digest-user", "digest pass");

        String curlCommand = CurlParser.toCurl(preparedRequest);
        HttpRequestItem item = CurlImportUtil.fromCurl(curlCommand);

        assertNotNull(item);
        assertEquals(item.getAuthType(), AuthType.DIGEST.getConstant());
        assertEquals(item.getAuthUsername(), "digest-user");
        assertEquals(item.getAuthPassword(), "digest pass");
    }

    @Test(description = "测试PreparedRequest转cURL - GET请求")
    public void testToCurlGetRequest() {
        PreparedRequest preparedRequest = new PreparedRequest();
        preparedRequest.method = "GET";
        preparedRequest.url = "https://api.example.com/users";
        preparedRequest.headersList = new java.util.ArrayList<>();
        preparedRequest.headersList.add(new HttpHeader(true, "Accept", "application/json"));

        String curlCommand = CurlParser.toCurl(preparedRequest);

        assertTrue(curlCommand.startsWith("curl"));
        assertFalse(curlCommand.contains("-X GET")); // GET方法不应该显式指定
        assertTrue(curlCommand.contains("'https://api.example.com/users'"));
        assertTrue(curlCommand.contains("-H 'Accept: application/json'"));
    }

    @Test(description = "测试Bash Shell参数转义")
    public void testBashShellArgumentEscaping() {
        PreparedRequest preparedRequest = new PreparedRequest();
        preparedRequest.method = "POST";
        preparedRequest.url = "https://api.example.com/users";
        preparedRequest.body = "data with 'single quotes' and \"double quotes\"";

        String curlCommand = CurlParser.toCurl(preparedRequest);

        assertFalse(curlCommand.contains("--data \""));
        assertTrue(curlCommand.contains("'\\''single quotes'\\''"));

        CurlRequest parsed = CurlParser.parse(curlCommand);
        assertEquals(parsed.body, preparedRequest.body);
    }

    @Test(description = "导出 cURL 时 URL 和请求头应使用 shell 安全引用")
    public void testToCurlShellQuotesUrlAndHeaders() {
        String userAgentClientHint = "\"Google Chrome\";v=\"149\", \"Chromium\";v=\"149\"";
        PreparedRequest preparedRequest = new PreparedRequest();
        preparedRequest.method = "GET";
        preparedRequest.url = "https://example.com/search?q=\"quoted\"&lang=zh-CN";
        preparedRequest.headersList = new java.util.ArrayList<>();
        preparedRequest.headersList.add(new HttpHeader(true, "sec-ch-ua", userAgentClientHint));

        String curlCommand = CurlParser.toCurl(preparedRequest);

        assertTrue(curlCommand.contains("'https://example.com/search?q=\"quoted\"&lang=zh-CN'"));
        assertTrue(curlCommand.contains("-H 'sec-ch-ua: " + userAgentClientHint + "'"));

        CurlRequest parsed = CurlParser.parse(curlCommand);
        assertEquals(parsed.url, preparedRequest.url);
        assertEquals(findHeaderValue(parsed.headersList, "sec-ch-ua"), userAgentClientHint);
    }

    @Test(description = "导出 cURL 时包含单引号的 body 不应退回到双引号")
    public void testToCurlEscapesSingleQuotesWithoutDoubleQuoteFallback() {
        PreparedRequest preparedRequest = new PreparedRequest();
        preparedRequest.method = "POST";
        preparedRequest.url = "https://example.com/api";
        preparedRequest.body = "prefix 'quoted' $(uname) $HOME `id` \\ tail";

        String curlCommand = CurlParser.toCurl(preparedRequest);

        assertFalse(curlCommand.contains("--data \""));
        assertTrue(curlCommand.contains("'\\''quoted'\\''"));

        CurlRequest parsed = CurlParser.parse(curlCommand);
        assertEquals(parsed.body, preparedRequest.body);
    }

    @Test(description = "复制实际发送 cURL 应使用网络层请求快照")
    public void testToActualCurlUsesSentSnapshot() {
        PreparedRequest preparedRequest = new PreparedRequest();
        preparedRequest.method = "POST";
        preparedRequest.url = "https://api.example.com/final";
        preparedRequest.headersList = new java.util.ArrayList<>();
        preparedRequest.headersList.add(new HttpHeader(true, "Authorization", "Bearer configured"));
        preparedRequest.body = "{\"configured\":true}";
        preparedRequest.sentHeadersList = new java.util.ArrayList<>();
        preparedRequest.sentHeadersList.add(new HttpHeader(true, "Host", "api.example.com"));
        preparedRequest.sentHeadersList.add(new HttpHeader(true, "Authorization", "Bearer actual"));
        preparedRequest.sentHeadersList.add(new HttpHeader(true, "Cookie", "session=jar; theme=dark"));
        preparedRequest.sentHeadersList.add(new HttpHeader(true, "Content-Length", "17"));
        preparedRequest.sentRequestBody = "{\"actual\":true}";
        preparedRequest.sentRequestBodyReplayable = true;

        String curlCommand = CurlParser.toActualCurl(preparedRequest);

        assertTrue(curlCommand.contains("-H 'Authorization: Bearer actual'"));
        assertTrue(curlCommand.contains("-H 'Cookie: session=jar; theme=dark'"));
        assertTrue(curlCommand.contains("-H 'Content-Length: 17'"));
        assertTrue(curlCommand.contains("'{\"actual\":true}'"));
        assertFalse(curlCommand.contains("Bearer configured"));
        assertFalse(curlCommand.contains("{\"configured\":true}"));
    }

    @Test(description = "复制实际发送 cURL 应保留网络层协议版本")
    public void testToActualCurlIncludesNegotiatedProtocol() {
        PreparedRequest preparedRequest = new PreparedRequest();
        preparedRequest.method = "GET";
        preparedRequest.url = "https://api.example.com/h2";
        preparedRequest.sentHeadersList = new java.util.ArrayList<>();
        preparedRequest.sentHeadersList.add(new HttpHeader(true, "Host", "api.example.com"));
        preparedRequest.exchangeEventInfo = new HttpEventInfo();
        preparedRequest.exchangeEventInfo.setProtocol("h2");

        String curlCommand = CurlParser.toActualCurl(preparedRequest);

        assertTrue(curlCommand.contains(" --http2 "));
        assertFalse(curlCommand.contains("-X GET"));
    }

    @Test(description = "复制实际发送 cURL 应优先使用网络层实际 URL 和方法")
    public void testToActualCurlUsesSentUrlAndMethod() {
        PreparedRequest preparedRequest = new PreparedRequest();
        preparedRequest.method = "POST";
        preparedRequest.url = "https://api.example.com/start";
        preparedRequest.sentMethod = "GET";
        preparedRequest.sentUrl = "https://api.example.com/target";
        preparedRequest.sentHeadersList = new java.util.ArrayList<>();
        preparedRequest.sentHeadersList.add(new HttpHeader(true, "Host", "api.example.com"));

        String curlCommand = CurlParser.toActualCurl(preparedRequest);

        assertTrue(curlCommand.contains("'https://api.example.com/target'"));
        assertFalse(curlCommand.contains("https://api.example.com/start"));
        assertFalse(curlCommand.contains("-X POST"));
    }

    @Test(description = "复制实际发送 cURL 在 GET 有请求体快照时应显式保留 GET 方法")
    public void testToActualCurlPreservesGetMethodWhenBodySnapshotExists() {
        PreparedRequest preparedRequest = new PreparedRequest();
        preparedRequest.method = "GET";
        preparedRequest.url = "https://api.example.com/search";
        preparedRequest.sentMethod = "GET";
        preparedRequest.sentUrl = "https://api.example.com/search";
        preparedRequest.sentHeadersList = new java.util.ArrayList<>();
        preparedRequest.sentHeadersList.add(new HttpHeader(true, "Host", "api.example.com"));
        preparedRequest.sentRequestBody = "{\"query\":\"hello\"}";
        preparedRequest.sentRequestBodyReplayable = true;

        String curlCommand = CurlParser.toActualCurl(preparedRequest);

        assertTrue(curlCommand.contains(" -X GET "));
        assertTrue(curlCommand.contains("--data-raw '{\"query\":\"hello\"}'"));
    }

    @Test(description = "复制实际发送 cURL 不应把截断的诊断请求体当作真实 payload")
    public void testToActualCurlOmitsTruncatedBodyPreview() {
        PreparedRequest preparedRequest = new PreparedRequest();
        preparedRequest.method = "POST";
        preparedRequest.url = "https://api.example.com/upload";
        preparedRequest.sentMethod = "POST";
        preparedRequest.sentUrl = "https://api.example.com/upload";
        preparedRequest.sentHeadersList = new java.util.ArrayList<>();
        preparedRequest.sentHeadersList.add(new HttpHeader(true, "Content-Type", "application/json"));
        preparedRequest.sentRequestBody = "{\"partial\":true}\n\n[Truncated request body: 1048576 bytes, showing first 64KB]";
        preparedRequest.sentRequestBodyReplayable = false;

        String curlCommand = CurlParser.toActualCurl(preparedRequest);

        assertFalse(curlCommand.contains("--data-raw"));
        assertFalse(curlCommand.contains("Truncated request body"));
        assertTrue(curlCommand.startsWith("# Request body omitted:"));
        assertTrue(curlCommand.contains("-X POST"));
    }

    @Test(description = "复制实际发送 cURL 在省略不可复现请求体时不应保留请求体 framing 头")
    public void testToActualCurlOmitsBodyFramingHeadersWhenBodyIsNotReplayable() {
        PreparedRequest preparedRequest = new PreparedRequest();
        preparedRequest.method = "POST";
        preparedRequest.url = "https://api.example.com/upload";
        preparedRequest.sentMethod = "POST";
        preparedRequest.sentUrl = "https://api.example.com/upload";
        preparedRequest.sentHeadersList = new java.util.ArrayList<>();
        preparedRequest.sentHeadersList.add(new HttpHeader(true, "Content-Type", "application/octet-stream"));
        preparedRequest.sentHeadersList.add(new HttpHeader(true, "Content-Length", "1048576"));
        preparedRequest.sentHeadersList.add(new HttpHeader(true, "Transfer-Encoding", "chunked"));
        preparedRequest.sentRequestBody = "[binary/octet-stream]";
        preparedRequest.sentRequestBodyReplayable = false;

        String curlCommand = CurlParser.toActualCurl(preparedRequest);

        assertFalse(curlCommand.contains("--data-raw"));
        assertFalse(curlCommand.contains("Content-Length"));
        assertFalse(curlCommand.contains("Transfer-Encoding"));
        assertTrue(curlCommand.contains("-H 'Content-Type: application/octet-stream'"));
        assertTrue(curlCommand.startsWith("# Request body omitted:"));
    }

    @Test(description = "复制实际发送 cURL 不应把 multipart 占位文本当作真实 payload")
    public void testToActualCurlOmitsMultipartPlaceholderBodyPreview() {
        PreparedRequest preparedRequest = new PreparedRequest();
        preparedRequest.method = "POST";
        preparedRequest.url = "https://api.example.com/form";
        preparedRequest.sentMethod = "POST";
        preparedRequest.sentUrl = "https://api.example.com/form";
        preparedRequest.sentHeadersList = new java.util.ArrayList<>();
        preparedRequest.sentHeadersList.add(new HttpHeader(true, "Content-Type", "multipart/form-data; boundary=abc"));
        preparedRequest.sentRequestBody = "[multipart/form-data] (see form files)";
        preparedRequest.sentRequestBodyReplayable = false;

        String curlCommand = CurlParser.toActualCurl(preparedRequest);

        assertFalse(curlCommand.contains("--data-raw"));
        assertFalse(curlCommand.contains("[multipart/form-data]"));
        assertTrue(curlCommand.startsWith("# Request body omitted:"));
    }

    @DataProvider(name = "getRequestFormats")
    public Object[][] getRequestFormats() {
        return new Object[][]{
                {"curl https://example.com"},
                {"curl -X GET https://example.com"},
                {"curl --request GET https://example.com"},
                {"curl https://example.com -X GET"}
        };
    }

    @Test(description = "测试不同格式的GET请求", dataProvider = "getRequestFormats")
    public void testVariousGetRequestFormats(String curlCommand) {
        CurlRequest result = CurlParser.parse(curlCommand);
        assertEquals(result.url, "https://example.com");
        assertEquals(result.method, "GET");
    }

    @Test(description = "测试请求头分割逻辑")
    public void testHeaderSplitting() {
        String curl = "curl https://api.example.com -H 'Content-Type:application/json' " +
                "-H 'Authorization: Bearer token:with:colons'";
        CurlRequest result = CurlParser.parse(curl);

        assertEquals(findHeaderValue(result.headersList, "Content-Type"), "application/json");
        assertEquals(findHeaderValue(result.headersList, "Authorization"), "Bearer token:with:colons");
    }

    @Test(description = "测试查询参数解析边界情况")
    public void testQueryParamEdgeCases() {
        // 没有值的参数
        String curl1 = "curl 'https://api.example.com?flag&key=value'";
        CurlRequest result1 = CurlParser.parse(curl1);
        assertEquals(findParamValue(result1.paramsList, "flag"), "");
        assertEquals(findParamValue(result1.paramsList, "key"), "value");

        // 空值参数
        String curl2 = "curl 'https://api.example.com?empty=&key=value'";
        CurlRequest result2 = CurlParser.parse(curl2);
        assertEquals(findParamValue(result2.paramsList, "empty"), "");
        assertEquals(findParamValue(result2.paramsList, "key"), "value");
    }

    @Test(description = "测试复杂的表单字段解析")
    public void testComplexFormFieldParsing() {
        String curl = "curl -X POST https://api.example.com/upload " +
                "-F 'field_without_value' " +
                "-F 'normal_field=value' " +
                "-F 'file_field=@/path/file.txt'";
        CurlRequest result = CurlParser.parse(curl);

        // 没有等号的字段应该被忽略
        assertNull(findFormDataValue(result.formDataList, "field_without_value"));
        assertEquals(findFormDataValue(result.formDataList, "normal_field"), "value");
        assertEquals(findFormDataFileValue(result.formDataList, "file_field"), "/path/file.txt");
    }

    @Test(description = "测试URL和方法的不同位置")
    public void testUrlAndMethodPositions() {
        // URL在前，方法在后
        String curl1 = "curl https://api.example.com -X POST";
        CurlRequest result1 = CurlParser.parse(curl1);
        assertEquals(result1.url, "https://api.example.com");
        assertEquals(result1.method, "POST");

        // 方法在前，URL在后
        String curl2 = "curl -X POST https://api.example.com";
        CurlRequest result2 = CurlParser.parse(curl2);
        assertEquals(result2.url, "https://api.example.com");
        assertEquals(result2.method, "POST");
    }

    @Test(description = "测试Cookie参数的不同格式")
    public void testCookieParameterFormats() {
        // 短格式
        String curl1 = "curl -b 'session=123' https://api.example.com";
        CurlRequest result1 = CurlParser.parse(curl1);
        assertEquals(findHeaderValue(result1.headersList, "Cookie"), "session=123");

        // 长格式
        String curl2 = "curl --cookie 'session=123' https://api.example.com";
        CurlRequest result2 = CurlParser.parse(curl2);
        assertEquals(findHeaderValue(result2.headersList, "Cookie"), "session=123");
    }

    @Test(description = "测试请求头参数的不同格式")
    public void testHeaderParameterFormats() {
        // 短格式
        String curl1 = "curl -H 'Content-Type: application/json' https://api.example.com";
        CurlRequest result1 = CurlParser.parse(curl1);
        assertEquals(findHeaderValue(result1.headersList, "Content-Type"), "application/json");

        // 长格式
        String curl2 = "curl --header 'Content-Type: application/json' https://api.example.com";
        CurlRequest result2 = CurlParser.parse(curl2);
        assertEquals(findHeaderValue(result2.headersList, "Content-Type"), "application/json");
    }

    @Test(description = "测试 -H 分号格式导入空值头")
    public void testHeaderSemicolonImportsEmptyHeaderValue() {
        String curl = "curl -H 'X-Empty;' https://api.example.com";

        CurlRequest result = CurlParser.parse(curl);

        assertEquals(findHeaderValue(result.headersList, "X-Empty"), "");
    }

    @Test(description = "测试 -A/--user-agent 导入为 User-Agent 头")
    public void testParseUserAgentOptionAsHeader() {
        String curl = "curl -A 'Agent/1.0' --user-agent 'Agent/2.0' https://api.example.com";

        CurlRequest result = CurlParser.parse(curl);

        assertEquals(findHeaderValue(result.headersList, "User-Agent"), "Agent/2.0");
    }

    @Test(description = "测试 Windows CMD caret 转义的 URL 和请求头")
    public void testParseWindowsCmdCaretEscapedUrlAndHeader() {
        String curl = "curl \"https://example.com/api?first=1^&second=2\" " +
                "-H \"X-Token: a^&b\"";

        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.url, "https://example.com/api?first=1&second=2");
        assertEquals(findParamValue(result.paramsList, "first"), "1");
        assertEquals(findParamValue(result.paramsList, "second"), "2");
        assertEquals(findHeaderValue(result.headersList, "X-Token"), "a&b");
    }

    @Test(description = "测试HTTP方法大小写转换")
    public void testHttpMethodCaseConversion() {
        String curl = "curl -X post https://api.example.com";
        CurlRequest result = CurlParser.parse(curl);
        assertEquals(result.method, "POST"); // 应该转换为大写
    }

    @Test(description = "测试Bash $'...'格式的转义字符处理")
    public void testBashDollarQuoteEscapeCharacterHandling() {
        // 使用 $'...' 格式来支持转义字符（ANSI-C quoting）
        String curl = "curl -d $'line1\\nline2\\tindented\\\\backslash\\\"quote' https://api.example.com";
        CurlRequest result = CurlParser.parse(curl);

        String expectedBody = "line1\nline2\tindented\\backslash\"quote";
        assertEquals(result.body, expectedBody);
    }

    @Test(description = "测试PreparedRequest为null的情况")
    public void testToCurlWithNullPreparedRequest() {
        // 测试各种null情况
        PreparedRequest preparedRequest = new PreparedRequest();

        String curlCommand = CurlParser.toCurl(preparedRequest);
        assertEquals(curlCommand, "curl");

        // 测试部分null
        preparedRequest.url = "https://api.example.com";
        curlCommand = CurlParser.toCurl(preparedRequest);
        assertTrue(curlCommand.contains("'https://api.example.com'"));
    }

    @Test(description = "测试复合场景 - 完整的Bash cURL命令")
    public void testCompleteScenario() {
        String curl = "curl -L -X POST 'https://api.example.com/users?active=true' " +
                "-H 'Content-Type: application/json' " +
                "-H 'Authorization: Bearer eyJhbGciOiJIUzI1NiJ9' " +
                "-H 'User-Agent: EasyPostman/2.1.0' " +
                "-b 'session=abc123; csrf_token=xyz789' " +
                "-d '{\"name\":\"测试用户\",\"age\":25,\"active\":true}'";

        CurlRequest result = CurlParser.parse(curl);

        // 验证基本属性
        assertEquals(result.url, "https://api.example.com/users?active=true");
        assertEquals(result.method, "POST");
        assertTrue(result.followRedirects);

        // 验证请求头
        assertEquals(findHeaderValue(result.headersList, "Content-Type"), "application/json");
        assertEquals(findHeaderValue(result.headersList, "Authorization"), "Bearer eyJhbGciOiJIUzI1NiJ9");
        assertEquals(findHeaderValue(result.headersList, "User-Agent"), "EasyPostman/2.1.0");
        assertEquals(findHeaderValue(result.headersList, "Cookie"), "session=abc123; csrf_token=xyz789");

        // 验证请求体
        assertEquals(result.body, "{\"name\":\"测试用户\",\"age\":25,\"active\":true}");

        // 验证查询参数
        assertEquals(findParamValue(result.paramsList, "active"), "true");
    }

    @Test(description = "测试往返转换一致性")
    public void testRoundTripConsistency() {
        // 创建一个PreparedRequest
        PreparedRequest original = new PreparedRequest();
        original.method = "PUT";
        original.url = "https://api.example.com/users/123";
        original.headersList = new java.util.ArrayList<>();
        original.headersList.add(new HttpHeader(true, "Content-Type", "application/json"));
        original.headersList.add(new HttpHeader(true, "Accept", "application/json"));
        original.body = "{\"name\":\"Updated Name\"}";

        // 转换为cURL命令
        String curlCommand = CurlParser.toCurl(original);

        // 再解析回CurlRequest
        CurlRequest parsed = CurlParser.parse(curlCommand);

        // 验证往返转换的一致性
        assertEquals(parsed.method, original.method);
        assertEquals(parsed.url, original.url);
        assertEquals(findHeaderValue(parsed.headersList, "Content-Type"), findHeaderValue(original.headersList, "Content-Type"));
        assertEquals(findHeaderValue(parsed.headersList, "Accept"), findHeaderValue(original.headersList, "Accept"));
        assertEquals(parsed.body, original.body);
    }

    @Test(description = "解析WebSocket连接的curl命令")
    public void testParseWebSocketCurlCommand() {
        String curl = "curl 'wss://echo-websocket.hoppscotch.io/' " +
                "-H 'Upgrade: websocket' " +
                "-H 'Origin: https://hoppscotch.io' " +
                "-H 'Cache-Control: no-cache' " +
                "-H 'Accept-Language: zh-CN,zh;q=0.9' " +
                "-H 'Pragma: no-cache' " +
                "-H 'Connection: Upgrade' " +
                "-H 'Sec-WebSocket-Key: ISD3FRCDNdAPiU+0dTuSXg==' " +
                "-H 'User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36' " +
                "-H 'Sec-WebSocket-Version: 13' " +
                "-H 'Sec-WebSocket-Extensions: permessage-deflate; client_max_window_bits'";

        CurlRequest result = CurlParser.parse(curl);

        // 验证URL和基本信息解析正确
        assertEquals(result.url, "wss://echo-websocket.hoppscotch.io/");
        assertEquals(result.method, "GET"); // WebSocket握手使用GET方法

        // 验证受限制的WebSocket头部已被过滤掉
        assertNull(findHeaderValue(result.headersList, "Upgrade"));
        assertNull(findHeaderValue(result.headersList, "Connection"));
        assertNull(findHeaderValue(result.headersList, "Sec-WebSocket-Key"));
        assertNull(findHeaderValue(result.headersList, "Sec-WebSocket-Version"));
        assertNull(findHeaderValue(result.headersList, "Sec-WebSocket-Extensions"));

        // 验证非受限制的头部正常保留
        assertEquals(findHeaderValue(result.headersList, "Origin"), "https://hoppscotch.io");
        assertEquals(findHeaderValue(result.headersList, "Cache-Control"), "no-cache");
        assertEquals(findHeaderValue(result.headersList, "Accept-Language"), "zh-CN,zh;q=0.9");
        assertEquals(findHeaderValue(result.headersList, "Pragma"), "no-cache");
        assertEquals(findHeaderValue(result.headersList, "User-Agent"), "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36");
    }

    @Test(description = "测试WebSocket请求的受限头部过滤")
    public void testWebSocketRestrictedHeaderFiltering() {
        String curl = "curl 'wss://echo-websocket.hoppscotch.io/' " +
                "-H 'Upgrade: websocket' " +
                "-H 'Origin: https://hoppscotch.io' " +
                "-H 'Connection: Upgrade' " +
                "-H 'Sec-WebSocket-Key: ISD3FRCDNdAPiU+0dTuSXg==' " +
                "-H 'User-Agent: Mozilla/5.0' " +
                "-H 'Sec-WebSocket-Version: 13' " +
                "-H 'Sec-WebSocket-Extensions: permessage-deflate' " +
                "-H 'Accept-Language: zh-CN,zh;q=0.9'";

        CurlRequest result = CurlParser.parse(curl);

        // 验证URL和基本信息解析正确
        assertEquals(result.url, "wss://echo-websocket.hoppscotch.io/");
        assertEquals(result.method, "GET");

        // 验证受限制的WebSocket头部已被过滤掉
        assertNull(findHeaderValue(result.headersList, "Upgrade"));
        assertNull(findHeaderValue(result.headersList, "Connection"));
        assertNull(findHeaderValue(result.headersList, "Sec-WebSocket-Key"));
        assertNull(findHeaderValue(result.headersList, "Sec-WebSocket-Version"));
        assertNull(findHeaderValue(result.headersList, "Sec-WebSocket-Extensions"));

        // 验证非受限制的头部正常保留
        assertEquals(findHeaderValue(result.headersList, "Origin"), "https://hoppscotch.io");
        assertEquals(findHeaderValue(result.headersList, "User-Agent"), "Mozilla/5.0");
        assertEquals(findHeaderValue(result.headersList, "Accept-Language"), "zh-CN,zh;q=0.9");
    }

    @Test(description = "测试普通HTTP请求不受WebSocket头部过滤影响")
    public void testHttpRequestWithWebSocketHeaders() {
        String curl = "curl 'https://api.example.com/test' " +
                "-H 'Sec-WebSocket-Key: test-key' " +
                "-H 'Sec-WebSocket-Version: 13' " +
                "-H 'Connection: keep-alive' " +
                "-H 'User-Agent: TestAgent'";

        CurlRequest result = CurlParser.parse(curl);

        // 验证普通HTTP请求允许设置这些头部
        assertEquals(result.url, "https://api.example.com/test");
        assertEquals(result.method, "GET");
        assertEquals(findHeaderValue(result.headersList, "Sec-WebSocket-Key"), "test-key");
        assertEquals(findHeaderValue(result.headersList, "Sec-WebSocket-Version"), "13");
        assertEquals(findHeaderValue(result.headersList, "Connection"), "keep-alive");
        assertEquals(findHeaderValue(result.headersList, "User-Agent"), "TestAgent");
    }

    @Test(description = "解析复杂的Bash多行curl示例")
    public void testParseComplexBashCurlExample() {
        var curl = """
                curl 'http://127.0.0.1:8801/app-api/v1/ChangeClothes/getChangeClothesList' \\
                  -H 'Accept: */*' \\
                  -H 'Accept-Language: zh-CN,zh;q=0.9' \\
                  -H 'Authorization: bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9' \\
                  -H 'Cache-Control: no-cache' \\
                  -H 'Connection: keep-alive' \\
                  -H 'Content-Type: application/json' \\
                  -H 'Origin: http://127.0.0.1:8801' \\
                  -H 'Pragma: no-cache' \\
                  -H 'Referer: http://127.0.0.1:8801/doc.html' \\
                  -H 'Request-Origion: Knife4j' \\
                  -H 'Sec-Fetch-Dest: empty' \\
                  -H 'Sec-Fetch-Mode: cors' \\
                  -H 'Sec-Fetch-Site: same-origin' \\
                  -H 'User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36' \\
                  -H 'knife4j-gateway-code: ROOT' \\
                  -H 'sec-ch-ua: "Google Chrome";v="141", "Not?A_Brand";v="8", "Chromium";v="141"' \\
                  -H 'sec-ch-ua-mobile: ?0' \\
                  -H 'sec-ch-ua-platform: "Windows"' \\
                  --data-raw $'{\\n  "id": "",\\n  "memberId": "",\\n  "pageNum": 1,\\n  "pageSize": 10\\n}'
                """;

        var req = CurlParser.parse(curl);

        assertNotNull(req);
        assertEquals(req.url, "http://127.0.0.1:8801/app-api/v1/ChangeClothes/getChangeClothesList");
        assertEquals(req.method, "POST");
        assertEquals(findHeaderValue(req.headersList, "Content-Type"), "application/json");
        assertNotNull(findHeaderValue(req.headersList, "Authorization"));
        assertEquals(findHeaderValue(req.headersList, "Request-Origion"), "Knife4j");

        assertNotNull(req.body);
        assertTrue(req.body.contains("\"pageNum\": 1"));
        assertTrue(req.body.contains("\"pageSize\": 10"));
        assertTrue(req.body.contains("\"id\": \"\""));
        assertTrue(req.body.contains("\"memberId\": \"\""));
    }

    @Test(description = "测试Bash $'...'格式的真实cURL命令")
    public void testParseRealBashCurlWithDollarQuote() {
        String curl = "curl 'http://127.0.0.1:8801/app-api/v1/ChangeClothes/getChangeClothesList' \\\n" +
                "  -H 'Accept: */*' \\\n" +
                "  -H 'Accept-Language: zh-CN,zh;q=0.9' \\\n" +
                "  -H 'Authorization: bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9' \\\n" +
                "  -H 'Cache-Control: no-cache' \\\n" +
                "  -H 'Connection: keep-alive' \\\n" +
                "  -H 'Content-Type: application/json' \\\n" +
                "  -H 'Origin: http://127.0.0.1:8801' \\\n" +
                "  -H 'Pragma: no-cache' \\\n" +
                "  -H 'Referer: http://127.0.0.1:8801/doc.html' \\\n" +
                "  -H 'Request-Origion: Knife4j' \\\n" +
                "  -H 'User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36' \\\n" +
                "  --data-raw $'{\\n  \"id\": \"\",\\n  \"memberId\": \"\",\\n  \"pageNum\": 1,\\n  \"pageSize\": 10\\n}'";

        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.url, "http://127.0.0.1:8801/app-api/v1/ChangeClothes/getChangeClothesList");
        assertEquals(result.method, "POST");
        assertEquals(findHeaderValue(result.headersList, "Accept"), "*/*");
        assertEquals(findHeaderValue(result.headersList, "Content-Type"), "application/json");
        assertEquals(findHeaderValue(result.headersList, "Authorization"), "bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9");

        assertNotNull(result.body);
        assertTrue(result.body.contains("\n"), "Body should contain newline characters");
        assertTrue(result.body.contains("\"id\":"), "Body should contain id field");
        assertTrue(result.body.contains("\"memberId\":"), "Body should contain memberId field");
        assertTrue(result.body.contains("\"pageNum\": 1"), "Body should contain pageNum field");
        assertTrue(result.body.contains("\"pageSize\": 10"), "Body should contain pageSize field");
        assertFalse(result.body.contains("\\n"), "Body should not contain literal \\n");
    }

    @Test(description = "测试真实的Bash curl命令")
    public void testActualUserBashCurl() {
        String curl = "curl 'https://www.doubao.com/samantha/chat/completion?aid=497858' \\\n" +
                "  -H 'content-type: application/json' \\\n" +
                "  --data-raw '{\"messages\":[{\"content\":\"{\\\"text\\\":\\\"1\\\"}\",\"content_type\":2001}],\"conversation_id\":\"24642138855619074\"}'";
        CurlRequest result = CurlParser.parse(curl);
        assertEquals(result.body, "{\"messages\":[{\"content\":\"{\\\"text\\\":\\\"1\\\"}\",\"content_type\":2001}],\"conversation_id\":\"24642138855619074\"}");
    }

    @Test(description = "导入 cURL 时不压缩引号内 raw body 空白")
    public void testParsePreservesQuotedRawBodyWhitespace() {
        String body = "line  one\nline\tthree";
        String curl = "curl -X POST 'https://example.com/text' " +
                "-H 'Content-Type: text/plain' " +
                "--data-raw '" + body + "'";

        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.body, body);
    }


    @Test(description = "测试 -G 选项：GET 请求与 data-urlencode")
    public void testForceGetWithDataUrlencode() {
        // 测试您提供的第一个 curl 命令
        String curl = "curl -G 'http://localhost:8086/query?db=mydb' " +
                "--data-urlencode 'q=SELECT * FROM my_measurement LIMIT 5'";
        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.method, "GET", "使用 -G 选项时应该强制使用 GET 方法");
        assertEquals(result.url, "http://localhost:8086/query?db=mydb");
        assertEquals(findParamValue(result.paramsList, "q"), "SELECT * FROM my_measurement LIMIT 5",
                "使用 -G 时，data 参数应该被添加到查询参数中");
        assertNull(result.body, "使用 -G 时，不应该有请求体");
    }

    @Test(description = "测试 -XPOST 与 data-urlencode")
    public void testPostWithDataUrlencode() {
        // 测试您提供的第二个 curl 命令
        String curl = "curl -XPOST 'http://localhost:8086/query?db=mydb' " +
                "--data-urlencode 'q=SELECT * FROM my_measurement LIMIT 5'";
        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.method, "POST", "使用 -XPOST 时应该使用 POST 方法");
        assertEquals(result.url, "http://localhost:8086/query?db=mydb");
        // 查询参数应该只包含 URL 中的参数
        assertEquals(findParamValue(result.paramsList, "db"), "mydb");
        assertNull(findParamValue(result.paramsList, "q"), "不使用 -G 时，data 参数不应该在查询参数中");
        // 应该自动设置 Content-Type
        assertEquals(findHeaderValue(result.headersList, "Content-Type"), "application/x-www-form-urlencoded",
                "使用 data-urlencode 时应该自动设置 Content-Type");
        // data-urlencode 的数据应该解析到 urlencoded list 中
        assertEquals(findUrlencodedValue(result.urlencodedList, "q"), "SELECT * FROM my_measurement LIMIT 5",
                "使用 data-urlencode 时，数据应该解析到 urlencoded list 中");
        // body 应该为 null，因为数据在 urlencoded list 中
        assertNull(result.body, "当使用 urlencoded 时，body 应该为 null");
    }

    @Test(description = "测试 data-urlencode 单个参数中的 & 是 value 内容")
    public void testDataUrlencodeTreatsAmpersandAsValueContentForPost() {
        String curl = "curl -XPOST 'https://example.com/search' " +
                "--data-urlencode 'q=a&b' " +
                "--data-urlencode 'limit=10'";

        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.method, "POST");
        assertEquals(findHeaderValue(result.headersList, "Content-Type"), "application/x-www-form-urlencoded");
        assertEquals(result.urlencodedList.size(), 2);
        assertEquals(findUrlencodedValue(result.urlencodedList, "q"), "a&b");
        assertEquals(findUrlencodedValue(result.urlencodedList, "limit"), "10");
        assertNull(findUrlencodedValue(result.urlencodedList, "b"));
        assertNull(result.body);
    }

    @Test(description = "测试 -G 下普通 -d 按 & 拆，data-urlencode 不按 & 拆")
    public void testForceGetSplitsDataAmpersandButKeepsDataUrlencodeAmpersand() {
        String curl = "curl -G 'https://example.com/search' " +
                "-d 'page=1&size=20' " +
                "--data-urlencode 'q=a&b'";

        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.method, "GET");
        assertEquals(findParamValue(result.paramsList, "page"), "1");
        assertEquals(findParamValue(result.paramsList, "size"), "20");
        assertEquals(findParamValue(result.paramsList, "q"), "a&b");
        assertNull(findParamValue(result.paramsList, "b"));
        assertNull(result.body);
    }

    @Test(description = "测试 POST 与多个 data-urlencode 参数")
    public void testPostWithMultipleDataUrlencode() {
        String curl = "curl -XPOST 'http://localhost:8086/query' " +
                "--data-urlencode 'db=mydb' " +
                "--data-urlencode 'q=SELECT * FROM my_measurement'";
        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.method, "POST");
        assertEquals(findHeaderValue(result.headersList, "Content-Type"), "application/x-www-form-urlencoded");
        assertEquals(findUrlencodedValue(result.urlencodedList, "db"), "mydb",
                "多个 data-urlencode 参数应该都解析到 urlencoded list 中");
        assertEquals(findUrlencodedValue(result.urlencodedList, "q"), "SELECT * FROM my_measurement",
                "多个 data-urlencode 参数应该都解析到 urlencoded list 中");
        assertNull(result.body, "当使用 urlencoded 时，body 应该为 null");
    }

    @Test(description = "测试 -G 选项与多个 data 参数")
    public void testForceGetWithMultipleDataParams() {
        String curl = "curl -G 'http://localhost:8086/query' " +
                "--data-urlencode 'db=mydb' " +
                "--data-urlencode 'q=SELECT * FROM my_measurement'";
        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.method, "GET");
        assertEquals(findParamValue(result.paramsList, "db"), "mydb");
        assertEquals(findParamValue(result.paramsList, "q"), "SELECT * FROM my_measurement");
        assertNull(result.body);
    }

    @Test(description = "测试 --get 长格式选项")
    public void testGetLongFormatOption() {
        String curl = "curl --get 'http://localhost:8086/query' " +
                "-d 'key=value' " +
                "-d 'param=data'";
        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.method, "GET");
        assertEquals(findParamValue(result.paramsList, "key"), "value");
        assertEquals(findParamValue(result.paramsList, "param"), "data");
        assertNull(result.body);
    }

    @Test(description = "测试没有 -G 的普通 POST 请求")
    public void testPostWithoutForceGet() {
        String curl = "curl -X POST 'http://localhost:8086/query' " +
                "-d 'key=value' " +
                "-d 'param=data'";
        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.method, "POST");
        assertEquals(findHeaderValue(result.headersList, "Content-Type"), "application/x-www-form-urlencoded",
                "使用 -d 时应该自动设置 Content-Type 为 application/x-www-form-urlencoded");
        // 多个 -d 参数应该解析到 urlencoded list 中
        assertEquals(findUrlencodedValue(result.urlencodedList, "key"), "value");
        assertEquals(findUrlencodedValue(result.urlencodedList, "param"), "data");
        assertNull(result.body, "当使用 urlencoded 时，body 应该为 null");
        assertTrue(result.paramsList == null || result.paramsList.isEmpty());
    }

    @Test(description = "测试没有等号的 data 参数")
    public void testDataParamWithoutEquals() {
        String curl = "curl -X POST 'http://localhost:8086/query' " +
                "-d 'rawdata'";
        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.method, "POST");
        assertEquals(result.body, "rawdata", "没有等号的 data 参数应该作为原始 body");
    }

    @Test(description = "测试混合 JSON Content-Type 和 data 参数")
    public void testJsonContentTypeWithDataParams() {
        String curl = "curl -X POST 'http://localhost:8086/api' " +
                "-H 'Content-Type: application/json' " +
                "-d '{\"key\":\"value\"}'";
        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.method, "POST");
        assertEquals(findHeaderValue(result.headersList, "Content-Type"), "application/json");
        assertEquals(result.body, "{\"key\":\"value\"}", "JSON body 应该保持原样");
        assertTrue(result.urlencodedList == null || result.urlencodedList.isEmpty(), "JSON 格式不应该解析为 urlencoded");
    }

    @Test(description = "测试 -G 与无值参数")
    public void testForceGetWithEmptyValueParam() {
        String curl = "curl -G 'http://localhost:8086/query' " +
                "-d 'flag'";
        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.method, "GET");
        assertEquals(findParamValue(result.paramsList, "flag"), "", "无值参数应该设置为空字符串");
    }

    @Test(description = "测试 ws:// 协议的 WebSocket URL")
    public void testWebSocketWithWsProtocol() {
        String curl = "curl 'ws://localhost:8080/socket' " +
                "-H 'Sec-WebSocket-Key: test123' " +
                "-H 'Custom-Header: value'";
        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.url, "ws://localhost:8080/socket");
        assertNull(findHeaderValue(result.headersList, "Sec-WebSocket-Key"), "ws:// 协议也应该过滤 WebSocket 头部");
        assertEquals(findHeaderValue(result.headersList, "Custom-Header"), "value", "自定义头部应该保留");
    }

    @Test(description = "测试 Host 头部在 WebSocket 中被过滤")
    public void testWebSocketHostHeaderFiltering() {
        String curl = "curl 'wss://example.com/socket' " +
                "-H 'Host: example.com' " +
                "-H 'Custom-Header: value'";
        CurlRequest result = CurlParser.parse(curl);

        assertNull(findHeaderValue(result.headersList, "Host"), "Host 头部应该在 WebSocket 中被过滤");
        assertEquals(findHeaderValue(result.headersList, "Custom-Header"), "value");
    }

    @Test(description = "WebSocket 受限头过滤不应依赖 URL 出现顺序")
    public void testWebSocketRestrictedHeaderFilteringAfterUrlIsKnown() {
        String curl = "curl -H 'Upgrade: websocket' " +
                "-H 'Connection: Upgrade' " +
                "-H 'sec-websocket-key: abc123' " +
                "-H 'Custom-Header: value' " +
                "'wss://example.com/socket'";

        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.url, "wss://example.com/socket");
        assertNull(findHeaderValue(result.headersList, "Upgrade"));
        assertNull(findHeaderValue(result.headersList, "Connection"));
        assertNull(findHeaderValue(result.headersList, "sec-websocket-key"));
        assertEquals(findHeaderValue(result.headersList, "Custom-Header"), "value");
    }

    @Test(description = "测试双引号包含单引号")
    public void testDoubleQuoteWithSingleQuote() {
        String curl = "curl -d \"data with 'single' quotes\" https://example.com";
        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.body, "data with 'single' quotes");
    }

    @Test(description = "测试多个 URL 参数（取最后一个）")
    public void testMultipleUrlParameters() {
        String curl = "curl https://first.com --url https://second.com";
        CurlRequest result = CurlParser.parse(curl);

        // 应该使用最后一个 URL
        assertEquals(result.url, "https://second.com");
    }

    @Test(description = "测试 PreparedRequest 转 cURL 时的空值处理")
    public void testToCurlWithEmptyBody() {
        PreparedRequest req = new PreparedRequest();
        req.method = "GET";
        req.url = "https://example.com";
        req.body = "";

        String curl = CurlParser.toCurl(req);

        assertFalse(curl.contains("--data"), "空 body 不应该生成 --data 参数");
    }

    @Test(description = "测试转义字符在单引号中不生效")
    public void testEscapeInSingleQuote() {
        String curl = "curl -d 'test\\ndata' https://example.com";
        CurlRequest result = CurlParser.parse(curl);

        // 单引号中的转义字符不应该被处理
        assertEquals(result.body, "test\\ndata");
    }

    @Test(description = "测试带 boundary 的 multipart/form-data")
    public void testMultipartFormDataWithBoundary() {
        String curl = "curl -X POST 'http://example.com/upload' " +
                "-H 'Content-Type: multipart/form-data; boundary=----WebKitFormBoundary' " +
                "-d '------WebKitFormBoundary\r\n" +
                "Content-Disposition: form-data; name=\"field1\"\r\n\r\n" +
                "value1\r\n" +
                "------WebKitFormBoundary--'";

        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.method, "POST");
        assertEquals(findHeaderValue(result.headersList, "Content-Type"), "multipart/form-data; boundary=----WebKitFormBoundary");
        // 由于 parseMultipartFormData 会被调用
        assertNotNull(result.body);
    }

    @Test(description = "测试 -XPOST 连写格式")
    public void testDashXMethodCombinedFormat() {
        String curl1 = "curl -XPOST 'http://example.com/api'";
        CurlRequest result1 = CurlParser.parse(curl1);
        assertEquals(result1.method, "POST", "-XPOST 应该被正确解析为 POST");

        String curl2 = "curl -XGET 'http://example.com/api'";
        CurlRequest result2 = CurlParser.parse(curl2);
        assertEquals(result2.method, "GET", "-XGET 应该被正确解析为 GET");

        String curl3 = "curl -XPUT 'http://example.com/api'";
        CurlRequest result3 = CurlParser.parse(curl3);
        assertEquals(result3.method, "PUT", "-XPUT 应该被正确解析为 PUT");

        String curl4 = "curl -XDELETE 'http://example.com/api'";
        CurlRequest result4 = CurlParser.parse(curl4);
        assertEquals(result4.method, "DELETE", "-XDELETE 应该被正确解析为 DELETE");
    }

    @Test(description = "测试多个 URL 参数（应该使用最后一个）")
    public void testMultipleUrlsUsesLast() {
        String curl = "curl https://first.com https://second.com";
        CurlRequest result = CurlParser.parse(curl);

        // curl 实际行为是使用最后一个 URL
        assertEquals(result.url, "https://second.com", "应该使用最后一个出现的 URL（符合 curl 实际行为）");
    }

    @Test(description = "测试 toCurl 支持 urlencoded 字段")
    public void testToCurlWithUrlencoded() {
        PreparedRequest req = new PreparedRequest();
        req.method = "POST";
        req.url = "https://example.com/api";
        req.urlencodedList = new java.util.ArrayList<>();
        req.urlencodedList.add(new com.laker.postman.request.model.HttpFormUrlencoded(true, "username", "admin"));
        req.urlencodedList.add(new com.laker.postman.request.model.HttpFormUrlencoded(true, "password", "secret123"));

        String curl = CurlParser.toCurl(req);

        assertTrue(curl.contains("--data-urlencode 'username=admin'"),
                "应该包含 urlencoded 参数");
        assertTrue(curl.contains("--data-urlencode 'password=secret123'"),
                "应该包含 urlencoded 参数");
    }

    @Test(description = "测试转义字符 \\a \\v \\0 在 $'...' 格式中")
    public void testAdditionalEscapeCharacters() {
        String curl = "curl -d $'line1\\aline2\\vline3' https://example.com";
        CurlRequest result = CurlParser.parse(curl);

        assertTrue(result.body.contains("\u0007"), "应该包含 alert 字符");
        assertTrue(result.body.contains("\u000B"), "应该包含 vertical tab 字符");
    }

    @Test(description = "测试往返转换：urlencoded 字段")
    public void testRoundTripWithUrlencoded() {
        PreparedRequest original = new PreparedRequest();
        original.method = "POST";
        original.url = "https://example.com/api";
        original.urlencodedList = new java.util.ArrayList<>();
        original.urlencodedList.add(new com.laker.postman.request.model.HttpFormUrlencoded(true, "key1", "value1"));
        original.urlencodedList.add(new com.laker.postman.request.model.HttpFormUrlencoded(true, "key2", "value2"));

        String curlCommand = CurlParser.toCurl(original);
        CurlRequest parsed = CurlParser.parse(curlCommand);

        assertEquals(parsed.method, original.method);
        assertEquals(parsed.url, original.url);
        assertEquals(findUrlencodedValue(parsed.urlencodedList, "key1"), "value1");
        assertEquals(findUrlencodedValue(parsed.urlencodedList, "key2"), "value2");
    }

    @Test(description = "测试空值和 null 的边界情况")
    public void testEdgeCasesWithNullValues() {
        // 测试没有参数的 -H
        String curl1 = "curl https://example.com -H";
        CurlRequest result1 = CurlParser.parse(curl1);
        assertEquals(result1.url, "https://example.com");

        // 测试没有参数的 -d
        String curl2 = "curl https://example.com -d";
        CurlRequest result2 = CurlParser.parse(curl2);
        assertEquals(result2.url, "https://example.com");
    }

    @Test(description = "测试 URL 中特殊字符的处理")
    public void testUrlWithSpecialCharacters() {
        String curl = "curl 'https://example.com/api?name=John%20Doe&age=30&tags=a,b,c'";
        CurlRequest result = CurlParser.parse(curl);

        assertEquals(result.url, "https://example.com/api?name=John%20Doe&age=30&tags=a,b,c");
        assertEquals(findParamValue(result.paramsList, "name"), "John Doe");
        assertEquals(findParamValue(result.paramsList, "age"), "30");
        assertEquals(findParamValue(result.paramsList, "tags"), "a,b,c");
    }

    @Test(description = "测试大小写混合的 HTTP 方法")
    public void testMixedCaseHttpMethods() {
        String curl = "curl -X pOsT https://example.com";
        CurlRequest result = CurlParser.parse(curl);
        assertEquals(result.method, "POST", "HTTP 方法应该转换为大写");
    }

    @Test(description = "测试 followRedirects 标志的各种格式")
    public void testFollowRedirectsVariations() {
        // 短格式
        String curl1 = "curl -L https://example.com";
        CurlRequest result1 = CurlParser.parse(curl1);
        assertTrue(result1.followRedirects);

        // 长格式
        String curl2 = "curl --location https://example.com";
        CurlRequest result2 = CurlParser.parse(curl2);
        assertTrue(result2.followRedirects);

        // 没有重定向标志
        String curl3 = "curl https://example.com";
        CurlRequest result3 = CurlParser.parse(curl3);
        assertFalse(result3.followRedirects);
    }

    @Test(description = "测试复杂的查询参数解析")
    public void testComplexQueryParameterParsing() {
        String curl = "curl 'https://api.example.com/search?q=java+programming&sort=date&filter=&limit=10'";
        CurlRequest result = CurlParser.parse(curl);

        assertEquals(findParamValue(result.paramsList, "q"), "java+programming");
        assertEquals(findParamValue(result.paramsList, "sort"), "date");
        assertEquals(findParamValue(result.paramsList, "filter"), "");
        assertEquals(findParamValue(result.paramsList, "limit"), "10");
    }

    @Test(description = "解析 cURL URL 中用于关闭 glob 的方括号转义")
    public void testParseUrlWithCurlEscapedGlobBrackets() {
        String curl = "curl 'https://www.google.com/httpservice/web/AimThreadsService/ListThreads?aep=48&reqpld=\\[null,null,1\\]&msc=gwsclient'";

        CurlRequest result = CurlParser.parse(curl);

        assertEquals(
                result.url,
                "https://www.google.com/httpservice/web/AimThreadsService/ListThreads?aep=48&reqpld=[null,null,1]&msc=gwsclient"
        );
        assertEquals(findParamValue(result.paramsList, "reqpld"), "[null,null,1]");
    }

    @Test(description = "测试同时使用 body 和 urlencoded（body 优先）")
    public void testBodyAndUrlencodedConflict() {
        PreparedRequest req = new PreparedRequest();
        req.method = "POST";
        req.url = "https://example.com/api";
        req.body = "{\"raw\":\"json\"}";
        req.urlencodedList = new java.util.ArrayList<>();
        req.urlencodedList.add(new com.laker.postman.request.model.HttpFormUrlencoded(true, "key", "value"));

        String curl = CurlParser.toCurl(req);

        // body 应该优先
        assertTrue(curl.contains("--data"));
        assertTrue(curl.contains("{\"raw\":\"json\"}"));
    }

    // ==================== 辅助方法 ====================

    /**
     * 从 headersList 中查找指定 key 的 value
     */
    private String findHeaderValue(java.util.List<HttpHeader> list, String key) {
        if (list == null) return null;
        for (HttpHeader item : list) {
            if (item.getKey() != null && item.getKey().equals(key)) {
                return item.getValue();
            }
        }
        return null;
    }

    /**
     * 从 paramsList 中查找指定 key 的 value
     */
    private String findParamValue(java.util.List<HttpParam> list, String key) {
        if (list == null) return null;
        for (HttpParam item : list) {
            if (item.getKey() != null && item.getKey().equals(key)) {
                return item.getValue();
            }
        }
        return null;
    }

    /**
     * 从 formDataList 中查找指定 key 的 Text 类型 value
     */
    private String findFormDataValue(java.util.List<HttpFormData> list, String key) {
        if (list == null) return null;
        for (HttpFormData item : list) {
            if (item.getKey() != null && item.getKey().equals(key) && HttpFormData.TYPE_TEXT.equals(item.getType())) {
                return item.getValue();
            }
        }
        return null;
    }

    /**
     * 从 formDataList 中查找指定 key 的 File 类型 value
     */
    private String findFormDataFileValue(java.util.List<HttpFormData> list, String key) {
        if (list == null) return null;
        for (HttpFormData item : list) {
            if (item.getKey() != null && item.getKey().equals(key) && HttpFormData.TYPE_FILE.equals(item.getType())) {
                return item.getValue();
            }
        }
        return null;
    }

    /**
     * 从 urlencodedList 中查找指定 key 的 value
     */
    private String findUrlencodedValue(java.util.List<com.laker.postman.request.model.HttpFormUrlencoded> list, String key) {
        if (list == null) return null;
        for (com.laker.postman.request.model.HttpFormUrlencoded item : list) {
            if (item.getKey() != null && item.getKey().equals(key)) {
                return item.getValue();
            }
        }
        return null;
    }

    @Test(description = "测试 --data-raw 配合 application/x-www-form-urlencoded 解析完整的 urlencoded 字符串")
    public void testDataRawWithUrlencodedContentType() {
        String curl = """
                curl 'https://xxxxx/xxxx.json' \\
                  -H 'Content-Type: application/x-www-form-urlencoded' \\
                  --data-raw 'page_no=1&page_size=10&province_code=13&start_date=&end_date=&status=4&start_card_date=&end_card_date=&card_no=&loss_type=&base_line=20251201'
                """;

        CurlRequest result = CurlParser.parse(curl);

        assertNotNull(result, "应该成功解析");
        assertEquals(result.method, "POST", "应该自动设置为 POST 方法");
        assertEquals(result.url, "https://xxxxx/xxxx.json");
        assertEquals(findHeaderValue(result.headersList, "Content-Type"), "application/x-www-form-urlencoded");

        // 验证 urlencoded 字段被正确解析
        assertNotNull(result.urlencodedList, "应该有 urlencodedList");
        assertEquals(result.urlencodedList.size(), 11, "应该有 11 个 urlencoded 字段");

        // 验证各个字段
        assertEquals(findUrlencodedValue(result.urlencodedList, "page_no"), "1");
        assertEquals(findUrlencodedValue(result.urlencodedList, "page_size"), "10");
        assertEquals(findUrlencodedValue(result.urlencodedList, "province_code"), "13");
        assertEquals(findUrlencodedValue(result.urlencodedList, "start_date"), "");
        assertEquals(findUrlencodedValue(result.urlencodedList, "end_date"), "");
        assertEquals(findUrlencodedValue(result.urlencodedList, "status"), "4");
        assertEquals(findUrlencodedValue(result.urlencodedList, "start_card_date"), "");
        assertEquals(findUrlencodedValue(result.urlencodedList, "end_card_date"), "");
        assertEquals(findUrlencodedValue(result.urlencodedList, "card_no"), "");
        assertEquals(findUrlencodedValue(result.urlencodedList, "loss_type"), "");
        assertEquals(findUrlencodedValue(result.urlencodedList, "base_line"), "20251201");

        // 验证 body 应该为 null（因为数据在 urlencodedList 中）
        assertNull(result.body, "当使用 urlencoded 时，body 应该为 null");
    }

    @Test(description = "测试 --data-raw 配合 application/x-www-form-urlencoded 解析多个 data 参数")
    public void testMultipleDataRawWithUrlencodedContentType() {
        String curl = """
                curl 'https://api.example.com/submit' \\
                  -H 'Content-Type: application/x-www-form-urlencoded' \\
                  --data-raw 'name=John' \\
                  --data-raw 'email=john@example.com' \\
                  --data-raw 'age=30'
                """;

        CurlRequest result = CurlParser.parse(curl);

        assertNotNull(result);
        assertEquals(result.method, "POST");
        assertEquals(findHeaderValue(result.headersList, "Content-Type"), "application/x-www-form-urlencoded");

        // 验证 urlencoded 字段被正确解析
        assertNotNull(result.urlencodedList);
        assertEquals(result.urlencodedList.size(), 3);

        assertEquals(findUrlencodedValue(result.urlencodedList, "name"), "John");
        assertEquals(findUrlencodedValue(result.urlencodedList, "email"), "john@example.com");
        assertEquals(findUrlencodedValue(result.urlencodedList, "age"), "30");

        assertNull(result.body);
    }

    @Test(description = "测试 --data-raw 配合 application/x-www-form-urlencoded 解析包含特殊字符的值")
    public void testDataRawWithUrlencodedSpecialCharacters() {
        String curl = """
                curl 'https://api.example.com/submit' \\
                  -H 'Content-Type: application/x-www-form-urlencoded' \\
                  --data-raw 'key1=value%20with%20spaces&key2=value%3Dwith%3Dequals&key3=normal'
                """;

        CurlRequest result = CurlParser.parse(curl);

        assertNotNull(result);
        assertEquals(result.method, "POST");
        assertEquals(findHeaderValue(result.headersList, "Content-Type"), "application/x-www-form-urlencoded");

        assertNotNull(result.urlencodedList);
        assertEquals(result.urlencodedList.size(), 3);

        assertEquals(findUrlencodedValue(result.urlencodedList, "key1"), "value with spaces");
        assertEquals(findUrlencodedValue(result.urlencodedList, "key2"), "value=with=equals");
        assertEquals(findUrlencodedValue(result.urlencodedList, "key3"), "normal");

        assertNull(result.body);
    }

    @Test(description = "测试 curl 导入时对中文 query 和 urlencoded 参数做解码")
    public void testDecodeChineseParametersFromCurlImport() {
        String curl = """
                curl 'https://example.com/api?name=%E5%BC%A0%E4%B8%89&city=%E5%8C%97%E4%BA%AC' \\
                  -H 'Content-Type: application/x-www-form-urlencoded' \\
                  --data-raw 'keyword=%E4%B8%AD%E6%96%87%E5%85%B3%E9%94%AE%E8%AF%8D&note=%E6%B5%8B%E8%AF%95'
                """;

        CurlRequest result = CurlParser.parse(curl);

        assertEquals(findParamValue(result.paramsList, "name"), "张三");
        assertEquals(findParamValue(result.paramsList, "city"), "北京");
        assertEquals(findUrlencodedValue(result.urlencodedList, "keyword"), "中文关键词");
        assertEquals(findUrlencodedValue(result.urlencodedList, "note"), "测试");
    }

    @Test(description = "测试导入请求对象时 URL query 以可读文本显示")
    public void testCurlImportUtilDecodesUrlQueryForDisplay() {
        String curl = "curl 'https://example.com/api?keyword=%E4%B8%AD%E6%96%87&name=%E5%BC%A0%E4%B8%89'";

        HttpRequestItem item = CurlImportUtil.fromCurl(curl);

        assertNotNull(item);
        assertEquals(item.getUrl(), "https://example.com/api?keyword=中文&name=张三");
        assertEquals(item.getParamsList().size(), 2);
        assertEquals(item.getParamsList().get(0).getValue(), "中文");
        assertEquals(item.getParamsList().get(1).getValue(), "张三");
        assertEquals(item.getBody(), "");
    }

    @Test(description = "导入 cURL 时保留 query value 中嵌套 URL 的 reserved percent-encoding")
    public void testCurlImportUtilPreservesReservedEncodingInsideQueryValues() {
        String originalUrl = "https://www.google.com/async/folif?" +
                "async=_fmt:adl,_snbasecss:https%3A%2F%2Fwww.gstatic.com%2F_%2Fmss%2Fsearch-next%2F_%2Fss%2Fk%3Dsearch-next.aim%2Fam%3DgBA%2Fd%3D1,_xsrf:token%3A123" +
                "&keyword=%E4%B8%AD%E6%96%87";
        String curl = "curl '" + originalUrl + "'";

        HttpRequestItem item = CurlImportUtil.fromCurl(curl);

        assertNotNull(item);
        assertTrue(item.getUrl().contains("keyword=中文"), "普通中文 query 仍应可读显示");
        assertTrue(item.getUrl().contains("https%3A%2F%2Fwww.gstatic.com%2F_"),
                "嵌套 URL 的 :// 不应被 display decode 成 raw reserved 字符");
        assertTrue(item.getUrl().contains("%2Fk%3Dsearch-next.aim%2Fam%3DgBA%2Fd%3D1"),
                "嵌套路径和值分隔符应保持原始 percent-encoding");
        assertEquals(HttpUrlUtil.buildEncodedUrl(item.getUrl(), item.getParamsList()), originalUrl);
    }

    @Test(description = "导入 raw body cURL 时应设置 bodyType")
    public void testCurlImportUtilSetsRawBodyType() {
        String curl = "curl -X POST 'https://example.com/text' --data-raw 'plain body'";

        HttpRequestItem item = CurlImportUtil.fromCurl(curl);

        assertNotNull(item);
        assertEquals(item.getBody(), "plain body");
        assertEquals(item.getBodyType(), RequestBodyTypes.BODY_TYPE_RAW);
    }

    @Test(description = "导入 --data-binary @file cURL 时应设置 binary bodyType")
    public void testCurlImportUtilSetsBinaryBodyTypeForDataBinaryFile() {
        String curl = "curl -X PUT 'https://example.com/upload' --data-binary '@/tmp/upload.bin'";

        HttpRequestItem item = CurlImportUtil.fromCurl(curl);

        assertNotNull(item);
        assertEquals(item.getBody(), "/tmp/upload.bin");
        assertEquals(item.getBodyType(), RequestBodyTypes.BODY_TYPE_BINARY);
    }

    @Test(description = "导入解析出 form-data 的 multipart cURL 时 bodyType 不应被 raw body 覆盖")
    public void testCurlImportUtilKeepsFormDataBodyTypeWhenMultipartBodyIsParsed() {
        String curl = "curl -X POST 'http://example.com/upload' " +
                "-H 'Content-Type: multipart/form-data; boundary=----WebKitFormBoundary' " +
                "-d '------WebKitFormBoundary\r\n" +
                "Content-Disposition: form-data; name=\"field1\"\r\n\r\n" +
                "value1\r\n" +
                "------WebKitFormBoundary--'";

        HttpRequestItem item = CurlImportUtil.fromCurl(curl);

        assertNotNull(item);
        assertEquals(item.getBodyType(), RequestBodyTypes.BODY_TYPE_FORM_DATA);
        assertEquals(item.getFormDataList().size(), 1);
    }

    @Test(description = "导入带 -L 的 cURL 时应保留跟随重定向设置")
    public void testCurlImportUtilPreservesFollowRedirectsFlag() {
        HttpRequestItem item = CurlImportUtil.fromCurl("curl -L 'https://example.com/redirect'");

        assertNotNull(item);
        assertEquals(item.getFollowRedirects(), Boolean.TRUE);
    }

    @Test(description = "测试 curl 导入请求对象时保留 JSON body 原始内容")
    public void testCurlImportUtilPreservesJsonBodyFromCurl() {
        String curl = "curl -X POST 'https://example.com/api' " +
                "-H 'Content-Type: application/json' " +
                "--data-raw '{\"name\":\"张三\",\"roles\":[\"admin\",\"user\"]}'";

        HttpRequestItem item = CurlImportUtil.fromCurl(curl);

        assertNotNull(item);
        assertEquals(item.getBody(), "{\"name\":\"张三\",\"roles\":[\"admin\",\"user\"]}");
    }

    @Test(description = "测试 ChatGPT SSE cURL 导入后保留 cookie、SSE 协议和 raw JSON body")
    public void testChatGptSseCurlImportPreservesSendShape() {
        String body = "{\"action\":\"next\",\"messages\":[{\"content\":{\"parts\":[\"1\"]}}]}";
        String curl = "curl 'https://chatgpt.com/backend-api/f/conversation' " +
                "-H 'Content-Type: application/json' " +
                "-H 'accept: text/event-stream' " +
                "-H 'Authorization: Bearer token' " +
                "-H 'OpenAI-Sentinel-Chat-Requirements-Token: challenge-token' " +
                "-b 'cf_clearance=clearance; __Secure-oai-is=ois; __cf_bm=bm' " +
                "--data-raw '" + body + "'";

        HttpRequestItem item = CurlImportUtil.fromCurl(curl);

        assertNotNull(item);
        assertEquals(item.getProtocol(), RequestItemProtocolEnum.HTTP);
        assertEquals(item.getMethod(), "POST");
        assertEquals(item.getBody(), body);
        assertEquals(findHeaderValue(item.getHeadersList(), "accept"), "text/event-stream");
        assertEquals(findHeaderValue(item.getHeadersList(), "Cookie"),
                "cf_clearance=clearance; __Secure-oai-is=ois; __cf_bm=bm");

        PreparedRequest preparedRequest = PreparedRequestFactory.buildWithoutInheritance(item);

        assertEquals(preparedRequest.body, body);
        assertEquals(findHeaderValue(preparedRequest.headersList, "Content-Type"), "application/json");
        assertEquals(findHeaderValue(preparedRequest.headersList, "Cookie"),
                "cf_clearance=clearance; __Secure-oai-is=ois; __cf_bm=bm");
    }
}
