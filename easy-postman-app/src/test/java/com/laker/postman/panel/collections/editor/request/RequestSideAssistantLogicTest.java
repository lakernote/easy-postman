package com.laker.postman.panel.collections.editor.request;

import com.laker.postman.collection.model.CollectionDocument;
import com.laker.postman.collection.model.CollectionNode;
import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.http.request.AppRequestHeaderDefaults;
import com.laker.postman.model.Variable;
import com.laker.postman.request.model.HttpFormUrlencoded;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpParam;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.RequestBodyTypes;
import com.laker.postman.service.collections.CollectionDocumentRegistry;
import com.laker.postman.service.collections.CollectionRequestExecutionScopeResolver;
import com.laker.postman.service.variable.IterationDataVariableService;
import com.laker.postman.service.variable.RequestExecutionContext;
import com.laker.postman.service.variable.RequestExecutionScope;
import com.laker.postman.service.variable.VariableResolver;
import com.laker.postman.service.variable.VariablesService;
import com.laker.postman.variable.VariableType;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class RequestSideAssistantLogicTest {
    @Test
    public void okHttpSnippetShouldCreateEmptyBodyForPostWithoutPayload() {
        HttpRequestItem item = new HttpRequestItem();
        item.setMethod("POST");
        item.setUrl("https://example.com/orders");

        String snippet = RequestCodeSnippetGenerator.generate(item, RequestCodeSnippetLanguage.JAVA_OKHTTP);

        assertTrue(snippet.contains("RequestBody body = RequestBody.create(mediaType, \"\");"));
        assertTrue(snippet.contains(".method(\"POST\", body)"));
    }

    @Test
    public void variableScannerShouldCollectUniqueVariablesFromRequestFields() {
        HttpRequestItem item = new HttpRequestItem();
        item.setUrl("{{baseUrl}}/users/{{userId}}");
        item.setHeadersList(List.of(new HttpHeader(true, "Authorization", "Bearer {{token}}", "")));
        item.setBody("{\"id\":\"{{userId}}\"}");

        List<RequestVariableUsage> usages = RequestVariableUsageScanner.scan(item);

        assertEquals(usages.stream().map(RequestVariableUsage::name).toList(),
                List.of("baseUrl", "userId", "token"));
    }

    @Test
    public void snippetShouldNotDuplicateQueryParamsAlreadyPresentInUrl() {
        HttpRequestItem item = new HttpRequestItem();
        item.setMethod("GET");
        item.setUrl("{{baseUrl}}?q=lakernote");
        item.setParamsList(List.of(
                new HttpParam(true, "q", "lakernote"),
                new HttpParam(true, "page", "1")
        ));

        String snippet = RequestCodeSnippetGenerator.generate(item, RequestCodeSnippetLanguage.PYTHON_REQUESTS);

        assertTrue(snippet.contains("url = '{{baseUrl}}?q=lakernote&page=1'"));
    }

    @Test
    public void snippetShouldEncodeQueryParamsLikeRuntime() {
        HttpRequestItem item = new HttpRequestItem();
        item.setMethod("GET");
        item.setUrl("https://example.com/search");
        item.setParamsList(List.of(new HttpParam(true, "q", "a&b c")));

        String snippet = RequestCodeSnippetGenerator.generate(item, RequestCodeSnippetLanguage.PYTHON_REQUESTS);

        assertTrue(snippet.contains("url = 'https://example.com/search?q=a%26b%20c'"));
    }

    @Test
    public void snippetShouldEncodeUrlencodedBodyLikeRuntime() {
        HttpRequestItem item = new HttpRequestItem();
        item.setMethod("POST");
        item.setUrl("https://example.com/token");
        item.setBodyType(RequestBodyTypes.BODY_TYPE_FORM_URLENCODED);
        item.setUrlencodedList(List.of(new HttpFormUrlencoded(true, "grant type", "a&b=c")));

        String snippet = RequestCodeSnippetGenerator.generate(item, RequestCodeSnippetLanguage.PYTHON_REQUESTS);

        assertTrue(snippet.contains("payload = 'grant+type=a%26b%3Dc'"));
    }

    @Test
    public void curlSnippetShouldUseDataBinaryForBinaryBody() {
        HttpRequestItem item = new HttpRequestItem();
        item.setMethod("PUT");
        item.setUrl("https://example.com/upload");
        item.setBodyType(RequestBodyTypes.BODY_TYPE_BINARY);
        item.setBody("/tmp/upload.bin");

        String snippet = RequestCodeSnippetGenerator.generate(item, RequestCodeSnippetLanguage.CURL);

        assertTrue(snippet.contains("--data-binary '@/tmp/upload.bin'"));
    }

    @Test
    public void curlSnippetShouldDetectBinaryContentTypeFromLocalFile() throws Exception {
        Path file = Files.createTempFile("easy-postman-snippet-binary-", ".png");
        Files.write(file, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
        HttpRequestItem item = new HttpRequestItem();
        item.setMethod("PUT");
        item.setUrl("https://example.com/upload");
        item.setBodyType(RequestBodyTypes.BODY_TYPE_BINARY);
        item.setBody(file.toString());

        String snippet = RequestCodeSnippetGenerator.generate(item, RequestCodeSnippetLanguage.CURL);

        assertTrue(snippet.contains("-H 'Content-Type: image/png'"));
    }

    @Test
    public void snippetShouldHideGeneratedDefaultHeaders() {
        HttpRequestItem item = new HttpRequestItem();
        item.setMethod("GET");
        item.setUrl("https://example.com/users");
        item.setHeadersList(AppRequestHeaderDefaults.generatedHeaders());

        String snippet = RequestCodeSnippetGenerator.generate(item, RequestCodeSnippetLanguage.CURL);

        assertFalse(snippet.contains("-H"));
        assertFalse(snippet.contains("User-Agent"));
        assertFalse(snippet.contains("Accept-Encoding"));
    }

    @Test
    public void variableCatalogShouldIncludeAvailableVariablesBySource() {
        Map<String, String> runtimeVariables = new LinkedHashMap<>();
        runtimeVariables.put("runtimeToken", "abc");
        Map<String, String> iterationData = new LinkedHashMap<>();
        iterationData.put("rowId", "7");

        VariablesService.getInstance().attachContextMap(runtimeVariables);
        IterationDataVariableService.getInstance().attachContextMap(iterationData);
        try {
            Map<VariableType, List<RequestVariableUsage>> catalog = RequestVariableCatalog.allByType();

            assertTrue(catalog.get(VariableType.VARIABLE).stream()
                    .anyMatch(usage -> "runtimeToken".equals(usage.name()) && "abc".equals(usage.value())));
            assertTrue(catalog.get(VariableType.ITERATION_DATA).stream()
                    .anyMatch(usage -> "rowId".equals(usage.name()) && "7".equals(usage.value())));
            assertTrue(catalog.get(VariableType.BUILT_IN).stream()
                    .anyMatch(usage -> "$guid".equals(usage.name())));
        } finally {
            VariablesService.getInstance().detachContext();
            IterationDataVariableService.getInstance().detachContext();
        }
    }

    @Test
    public void variableCatalogShouldScopeGroupVariablesToCurrentRequest() {
        HttpRequestItem item = new HttpRequestItem();
        item.setId("request-1");
        item.setUrl("{{groupToken}}");

        RequestGroup group = new RequestGroup("group");
        group.setVariables(List.of(new Variable(true, "groupToken", "g1")));
        CollectionNode groupNode = CollectionNode.group(group);
        groupNode.addChild(CollectionNode.request(item));
        CollectionDocument document = new CollectionDocument(List.of(groupNode));

        CollectionDocumentRegistry.registerDocumentSupplier(() -> document);
        try {
            Map<VariableType, List<RequestVariableUsage>> catalog = RequestVariableCatalog.allByType(item);
            List<RequestVariableUsage> usages = RequestVariableUsageScanner.scan(item, catalog);

            assertTrue(catalog.get(VariableType.GROUP).stream()
                    .anyMatch(usage -> "groupToken".equals(usage.name()) && "g1".equals(usage.value())));
            assertEquals(usages.size(), 1);
            assertEquals(usages.get(0).name(), "groupToken");
            assertEquals(usages.get(0).value(), "g1");
            assertEquals(usages.get(0).type(), VariableType.GROUP);
        } finally {
            CollectionDocumentRegistry.registerDocumentSupplier(CollectionDocument::empty);
        }
    }

    @Test
    public void variableResolutionScopeSyncShouldUseLatestCollectionGroupVariables() {
        HttpRequestItem item = new HttpRequestItem();
        item.setId("request-scope-sync");
        item.setUrl("{{testname}}");
        RequestExecutionContext.setCurrentScope(RequestExecutionScope.fromGroupVariables(Map.of("testname", "333")));
        registerCollectionRequest(item, "888");
        try {
            assertTrue(CollectionRequestExecutionScopeResolver.syncCurrentScope(item));

            assertEquals(VariableResolver.resolveVariable("testname"), "888");
        } finally {
            RequestExecutionContext.clearCurrentScope();
            CollectionDocumentRegistry.registerDocumentSupplier(CollectionDocument::empty);
        }
    }

    private static void registerCollectionRequest(HttpRequestItem item, String variableValue) {
        RequestGroup group = new RequestGroup("Group");
        group.setVariables(List.of(new Variable(true, "testname", variableValue)));
        CollectionNode groupNode = CollectionNode.group(group);
        groupNode.addChild(CollectionNode.request(item));
        CollectionDocument document = new CollectionDocument(List.of(groupNode));
        CollectionDocumentRegistry.registerDocumentSupplier(() -> document);
    }
}
