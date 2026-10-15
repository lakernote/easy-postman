package com.laker.postman.panel.collections.editor.request;

import com.laker.postman.collection.model.CollectionDocument;
import com.laker.postman.collection.model.CollectionNode;
import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.model.Variable;
import com.laker.postman.request.model.AuthApiKeyPlacement;
import com.laker.postman.request.model.AuthType;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpFormUrlencoded;
import com.laker.postman.request.model.HttpParam;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.RequestBodyTypes;
import com.laker.postman.service.collections.CollectionDocumentRegistry;
import com.laker.postman.service.variable.VariablesService;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class RequestCodeSnippetGeneratorTest {

    @DataProvider
    public Object[][] languages() {
        return java.util.Arrays.stream(RequestCodeSnippetLanguage.values())
                .map(language -> new Object[]{language}).toArray(Object[][]::new);
    }

    @Test(dataProvider = "languages")
    public void shouldGenerateInheritedAuthAndHeadersWithOptionalVariableResolution(RequestCodeSnippetLanguage language) {
        HttpRequestItem item = request();
        item.setMethod("POST");
        item.setUrl("{{baseUrl}}/users/:id");
        item.setPathVariablesList(List.of(new HttpParam(true, "id", "{{userId}}")));
        item.setBodyType(RequestBodyTypes.BODY_TYPE_RAW);
        item.setBody("{\"user\":\"{{userId}}\"}");
        RequestGroup group = new RequestGroup("Collection");
        group.setAuthType(AuthType.BEARER.getConstant());
        group.setAuthToken("{{token}}");
        group.setHeaders(List.of(new HttpHeader(true, "X-Tenant", "{{tenant}}")));
        group.setVariables(List.of(
                new Variable(true, "baseUrl", "https://example.com"),
                new Variable(true, "userId", "42"),
                new Variable(true, "token", "collection-token"),
                new Variable(true, "tenant", "team-1")));
        CollectionNode groupNode = CollectionNode.group(group);
        groupNode.addChild(CollectionNode.request(item));
        CollectionDocument document = new CollectionDocument(List.of(groupNode));
        CollectionDocumentRegistry.registerDocumentSupplier(() -> document);
        try {
            String template = RequestCodeSnippetGenerator.generate(item, language, false);
            assertTrue(template.contains("{{baseUrl}}/users/{{userId}}"), template);
            assertTrue(template.contains("Bearer {{token}}"), template);
            assertTrue(template.contains("{{tenant}}"), template);

            String resolved = RequestCodeSnippetGenerator.generate(item, language, true);
            assertTrue(resolved.contains("https://example.com/users/42"), resolved);
            assertTrue(resolved.contains("Bearer collection-token"), resolved);
            assertTrue(resolved.contains("team-1"), resolved);
            assertTrue(resolved.contains("42"), resolved);
            assertFalse(resolved.contains("{{"), resolved);
        } finally {
            CollectionDocumentRegistry.registerDocumentSupplier(CollectionDocument::empty);
        }
    }

    @Test(dataProvider = "languages")
    public void shouldGenerateNormalizedJsonWithoutRemovingCommentLikeStringValues(RequestCodeSnippetLanguage language) {
        HttpRequestItem item = request();
        item.setMethod("POST");
        item.setBodyType(RequestBodyTypes.BODY_TYPE_RAW);
        item.setBody("{/* remove_me */\"url\":\"http://body.example/path\",\"text\":\"/* literal */\"}");

        String code = RequestCodeSnippetGenerator.generate(item, language, true);

        assertFalse(code.contains("remove_me"), code);
        assertTrue(code.contains("http://body.example/path"), code);
        assertTrue(code.contains("/* literal */"), code);
        assertTrue(item.getBody().contains("remove_me"));
    }

    @Test
    public void curlFormShouldEncodeBothFieldNamesAndLiteralValues() {
        HttpRequestItem item = request();
        item.setMethod("POST");
        item.setBodyType(RequestBodyTypes.BODY_TYPE_FORM_URLENCODED);
        item.setUrlencodedList(List.of(new HttpFormUrlencoded(true, "a&b=c", "a+b%20 c")));

        String code = RequestCodeSnippetGenerator.generate(item, RequestCodeSnippetLanguage.CURL);

        assertTrue(code.contains("--data-raw 'a%26b%3Dc=a%2Bb%2520+c'"), code);
    }

    @Test(dataProvider = "languages")
    public void shouldResolveBasicCredentialsBeforeEncodingAndKeepTemplatesVisible(RequestCodeSnippetLanguage language) {
        HttpRequestItem item = request();
        item.setAuthType(AuthType.BASIC.getConstant());
        item.setAuthUsername("{{username}}");
        item.setAuthPassword("{{password}}");
        VariablesService.getInstance().attachContextMap(Map.of("username", "用户", "password", "p:a'ss"));
        try {
            String template = RequestCodeSnippetGenerator.generate(item, language, false);
            assertTrue(template.contains("{{username}}"), template);
            assertTrue(template.contains("{{password}}"), template);
            String encoded = Base64.getEncoder().encodeToString("用户:p:a'ss".getBytes(StandardCharsets.UTF_8));

            String resolved = RequestCodeSnippetGenerator.generate(item, language, true);
            assertTrue(resolved.contains("Basic " + encoded), resolved);
            assertFalse(resolved.contains("{{"), resolved);
        } finally {
            VariablesService.getInstance().attachContextMap(null);
        }
    }

    @Test(dataProvider = "languages")
    public void queryApiKeyShouldRespectExplicitUrlAndEnabledParams(RequestCodeSnippetLanguage language) {
        HttpRequestItem item = request();
        item.setAuthType(AuthType.API_KEY.getConstant());
        item.setAuthApiKeyPlacement(AuthApiKeyPlacement.QUERY_PARAMS.getConstant());
        item.setAuthApiKeyName("api_key");
        item.setAuthApiKeyValue("a&b c");

        String generated = RequestCodeSnippetGenerator.generate(item, language);
        assertTrue(generated.contains("?api_key=a%26b%20c"), generated);

        item.setUrl("https://example.com?api_key=from-url#details");
        generated = RequestCodeSnippetGenerator.generate(item, language);
        assertTrue(generated.contains("?api_key=from-url#details"), generated);
        assertFalse(generated.contains("a%26b"), generated);

        item.setUrl("https://example.com");
        item.setParamsList(List.of(new HttpParam(true, "api_key", "from-params")));
        generated = RequestCodeSnippetGenerator.generate(item, language);
        assertTrue(generated.contains("?api_key=from-params"), generated);
        assertFalse(generated.contains("a%26b"), generated);

        item.setParamsList(List.of(new HttpParam(false, "api_key", "disabled")));
        generated = RequestCodeSnippetGenerator.generate(item, language);
        assertTrue(generated.contains("?api_key=a%26b%20c"), generated);
    }

    @Test
    public void pythonBasicTemplateShouldEncodeUnicodeCredentialsAsUtf8() {
        HttpRequestItem item = request();
        item.setAuthType(AuthType.BASIC.getConstant());
        item.setAuthUsername("{{username}}");
        item.setAuthPassword("密码");

        String generated = RequestCodeSnippetGenerator.generate(item, RequestCodeSnippetLanguage.PYTHON_REQUESTS);

        assertTrue(generated.contains("auth=('{{username}}'.encode('utf-8'), '密码'.encode('utf-8'))"), generated);
    }

    @Test(dataProvider = "languages")
    public void headerApiKeyShouldResolveNamesAndHonorExplicitHeader(RequestCodeSnippetLanguage language) {
        HttpRequestItem item = request();
        item.setAuthType(AuthType.API_KEY.getConstant());
        item.setAuthApiKeyName("{{keyName}}");
        item.setAuthApiKeyValue("{{keyValue}}");
        item.setHeadersList(List.of(new HttpHeader(true, "x-api-key", "explicit-key")));
        VariablesService.getInstance().attachContextMap(Map.of("keyName", "X-API-Key", "keyValue", "auth-tab-key"));
        try {
            String generated = RequestCodeSnippetGenerator.generate(item, language, true);
            assertTrue(generated.contains("explicit-key"), generated);
            assertFalse(generated.contains("auth-tab-key"), generated);
        } finally {
            VariablesService.getInstance().attachContextMap(null);
        }
    }

    @Test(dataProvider = "languages")
    public void digestShouldUseSupportedClientAuthOrExplainLimitation(RequestCodeSnippetLanguage language) {
        HttpRequestItem item = request();
        item.setAuthType(AuthType.DIGEST.getConstant());
        item.setAuthUsername("{{username}}");
        item.setAuthPassword("{{password}}");
        String generated = RequestCodeSnippetGenerator.generate(item, language);
        switch (language) {
            case CURL -> {
                assertTrue(generated.contains("--digest"), generated);
                assertTrue(generated.contains("--user '{{username}}:{{password}}'"), generated);
            }
            case PYTHON_REQUESTS -> {
                assertTrue(generated.contains("from requests.auth import HTTPDigestAuth"), generated);
                assertTrue(generated.contains("auth=HTTPDigestAuth('{{username}}', '{{password}}')"), generated);
            }
            case CSHARP_HTTP_CLIENT -> {
                assertTrue(generated.contains("CredentialCache"), generated);
                assertTrue(generated.contains("\"Digest\""), generated);
                assertTrue(generated.contains("{{username}}"), generated);
                assertTrue(generated.contains("{{password}}"), generated);
            }
            case JAVA_OKHTTP, JAVASCRIPT_FETCH, GO_NET_HTTP -> assertTrue(generated.startsWith("// "
                    + I18nUtil.getMessage(MessageKeys.REQUEST_ASSISTANT_CODE_DIGEST_UNSUPPORTED)), generated);
        }

        item.setHeadersList(List.of(new HttpHeader(true, "authorization", "Custom explicit-auth")));
        generated = RequestCodeSnippetGenerator.generate(item, language);
        assertTrue(generated.contains("Custom explicit-auth"), generated);
        assertFalse(generated.contains("{{username}}"), generated);
        assertFalse(generated.contains("HTTPDigestAuth"), generated);
        assertFalse(generated.contains("--digest"), generated);
        assertFalse(generated.startsWith("//"), generated);
    }

    private static HttpRequestItem request() {
        HttpRequestItem item = new HttpRequestItem();
        item.setId("snippet-request");
        item.setUrl("https://example.com");
        return item;
    }
}
