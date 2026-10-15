package com.laker.postman.http.request;

import com.laker.postman.collection.model.CollectionDocument;
import com.laker.postman.collection.model.CollectionNode;
import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.model.Environment;
import com.laker.postman.model.Variable;
import com.laker.postman.request.model.AuthApiKeyPlacement;
import com.laker.postman.request.model.AuthType;
import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpFormUrlencoded;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpParam;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.service.collections.CollectionDocumentRegistry;
import com.laker.postman.service.variable.IterationDataVariableService;
import com.laker.postman.service.variable.RequestExecutionContext;
import com.laker.postman.service.variable.RequestExecutionScope;
import com.laker.postman.service.variable.RunScopedVariableContext;
import com.laker.postman.service.variable.VariablesService;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class RequestCodeSnippetPreparerTest {

    @BeforeMethod
    @AfterMethod
    public void clearContext() {
        CollectionDocumentRegistry.registerDocumentSupplier(CollectionDocument::empty);
        RequestExecutionContext.clearCurrentScope();
        VariablesService.getInstance().attachContextMap(null);
        IterationDataVariableService.getInstance().attachContextMap(null);
    }

    @Test
    public void shouldUseNearestInheritedAuthAndMergeHeadersWithoutChangingParents() {
        HttpRequestItem draft = request("https://example.com/users");
        draft.setHeadersList(List.of(new HttpHeader(true, "x-shared", "request")));

        RequestGroup collection = new RequestGroup("Collection");
        collection.setAuthType(AuthType.BEARER.getConstant());
        collection.setAuthToken("outer-token");
        collection.setHeaders(List.of(new HttpHeader(true, "X-Shared", "collection")));

        RequestGroup folder = new RequestGroup("Folder");
        folder.setAuthType(AuthType.BASIC.getConstant());
        folder.setAuthUsername("{{__snippet_user__}}");
        folder.setAuthPassword("{{__snippet_password__}}");
        HttpHeader parentHeader = new HttpHeader(true, "X-Folder", "{{__snippet_user__}}");
        folder.setHeaders(List.of(parentHeader));
        folder.setVariables(List.of(
                new Variable(true, "__snippet_user__", "alice"),
                new Variable(true, "__snippet_password__", "secret")
        ));
        register(draft, collection, folder);

        HttpRequestItem prepared = RequestCodeSnippetPreparer.prepare(draft, true);

        assertEquals(prepared.getAuthType(), AuthType.BASIC.getConstant());
        assertEquals(prepared.getAuthUsername(), "alice");
        assertEquals(prepared.getAuthPassword(), "secret");
        assertEquals(prepared.getHeadersList().get(0).getValue(), "request");
        assertEquals(prepared.getHeadersList().get(1).getValue(), "alice");
        assertNotSame(prepared.getHeadersList().get(1), parentHeader);
        assertEquals(parentHeader.getValue(), "{{__snippet_user__}}");
        assertEquals(draft.getAuthType(), AuthType.INHERIT.getConstant());
        assertEquals(draft.getHeadersList().size(), 1);
        assertNull(RequestExecutionContext.getCurrentScope());
    }

    @Test
    public void shouldHonorNoAuthAndExplicitRequestAuth() {
        HttpRequestItem draft = request("https://example.com/users");
        RequestGroup collection = new RequestGroup("Collection");
        collection.setAuthType(AuthType.BEARER.getConstant());
        collection.setAuthToken("collection-token");
        RequestGroup folder = new RequestGroup("Folder");
        folder.setAuthType(AuthType.NONE.getConstant());
        register(draft, collection, folder);

        assertEquals(RequestCodeSnippetPreparer.prepare(draft, false).getAuthType(),
                AuthType.NONE.getConstant());

        draft.setAuthType(AuthType.API_KEY.getConstant());
        draft.setAuthApiKeyName("api_key");
        draft.setAuthApiKeyValue("request-key");
        draft.setAuthApiKeyPlacement(AuthApiKeyPlacement.QUERY_PARAMS.getConstant());

        HttpRequestItem explicit = RequestCodeSnippetPreparer.prepare(draft, false);

        assertEquals(explicit.getAuthType(), AuthType.API_KEY.getConstant());
        assertEquals(explicit.getAuthApiKeyName(), "api_key");
        assertEquals(explicit.getAuthApiKeyValue(), "request-key");
        assertEquals(explicit.getAuthApiKeyPlacement(), AuthApiKeyPlacement.QUERY_PARAMS.getConstant());

        draft.setAuthType(AuthType.NONE.getConstant());
        folder.setAuthType(AuthType.BEARER.getConstant());
        assertEquals(RequestCodeSnippetPreparer.prepare(draft, false).getAuthType(),
                AuthType.NONE.getConstant());
    }

    @Test
    public void shouldResolveNestedVariablesAcrossRequestFieldsBeforeAuthEncoding() {
        HttpRequestItem draft = request("http://{{__snippet_host__}}/users/:id");
        draft.setBody("{{__snippet_nested__}}/{{__snippet_missing__}}");
        draft.setAuthType(AuthType.BASIC.getConstant());
        draft.setAuthUsername("{{__snippet_nested__}}");
        draft.setAuthPassword("{{__snippet_value__}}");
        draft.setAuthToken("{{__snippet_value__}}");
        draft.setAuthApiKeyName("{{__snippet_key__}}");
        draft.setAuthApiKeyValue("{{__snippet_value__}}");
        draft.setHeadersList(Arrays.asList(null,
                new HttpHeader(true, "{{__snippet_key__}}", "{{__snippet_nested__}}"),
                new HttpHeader(false, "{{__snippet_key__}}", "{{__snippet_value__}}")));
        draft.setParamsList(List.of(
                new HttpParam(true, "{{__snippet_key__}}", "{{__snippet_value__}}"),
                new HttpParam(false, "disabled", "{{__snippet_value__}}")));
        draft.setPathVariablesList(List.of(new HttpParam(true, "id", "{{__snippet_value__}}")));
        draft.setFormDataList(List.of(
                new HttpFormData(true, "{{__snippet_key__}}", HttpFormData.TYPE_FILE, "/{{__snippet_nested__}}.txt"),
                new HttpFormData(false, "disabled", HttpFormData.TYPE_TEXT, "{{__snippet_value__}}")));
        draft.setUrlencodedList(List.of(
                new HttpFormUrlencoded(true, "{{__snippet_key__}}", "{{__snippet_nested__}}"),
                new HttpFormUrlencoded(false, "disabled", "{{__snippet_value__}}")));

        RequestGroup group = new RequestGroup("Group");
        group.setVariables(List.of(new Variable(true, "__snippet_nested__", "{{__snippet_value__}}")));
        register(draft, group);
        Environment environment = new Environment("test-environment");
        environment.set("__snippet_host__", "{{__snippet_global_host__}}");
        environment.set("__snippet_value__", "resolved");
        environment.set("__snippet_key__", "field");
        Environment globals = new Environment("test-globals");
        globals.set("__snippet_global_host__", "::1");

        HttpRequestItem prepared;
        try (RunScopedVariableContext ignored = RunScopedVariableContext.open(environment, globals)) {
            prepared = RequestCodeSnippetPreparer.prepare(draft, true);
        }

        assertEquals(prepared.getUrl(), "http://[::1]/users/resolved");
        assertEquals(prepared.getBody(), "resolved/{{__snippet_missing__}}");
        assertEquals(prepared.getAuthUsername(), "resolved");
        assertEquals(prepared.getAuthPassword(), "resolved");
        assertEquals(prepared.getAuthToken(), "resolved");
        assertEquals(prepared.getAuthApiKeyName(), "field");
        assertEquals(prepared.getAuthApiKeyValue(), "resolved");
        assertEquals(prepared.getHeadersList().get(1).getKey(), "field");
        assertEquals(prepared.getHeadersList().get(1).getValue(), "resolved");
        assertEquals(prepared.getParamsList().get(0).getKey(), "field");
        assertEquals(prepared.getParamsList().get(0).getValue(), "resolved");
        assertEquals(prepared.getPathVariablesList().get(0).getValue(), "resolved");
        assertEquals(prepared.getFormDataList().get(0).getKey(), "field");
        assertEquals(prepared.getFormDataList().get(0).getValue(), "/resolved.txt");
        assertEquals(prepared.getUrlencodedList().get(0).getKey(), "field");
        assertEquals(prepared.getUrlencodedList().get(0).getValue(), "resolved");
        assertEquals(prepared.getHeadersList().get(2).getValue(), "{{__snippet_value__}}");
        assertEquals(prepared.getParamsList().get(1).getValue(), "{{__snippet_value__}}");
        assertEquals(prepared.getFormDataList().get(1).getValue(), "{{__snippet_value__}}");
        assertEquals(prepared.getUrlencodedList().get(1).getValue(), "{{__snippet_value__}}");
        assertEquals(draft.getUrl(), "http://{{__snippet_host__}}/users/:id");
        assertEquals(draft.getBody(), "{{__snippet_nested__}}/{{__snippet_missing__}}");
        assertEquals(draft.getAuthUsername(), "{{__snippet_nested__}}");
        assertEquals(draft.getHeadersList().get(1).getKey(), "{{__snippet_key__}}");
    }

    @Test
    public void shouldPreservePlaceholdersWhenResolutionIsDisabledAndStillExpandPathParameters() {
        HttpRequestItem draft = request("http://::1/users/:id");
        draft.setPathVariablesList(List.of(new HttpParam(true, "id", "{{__snippet_value__}}")));
        draft.setBody("{{__snippet_value__}}");
        RequestGroup group = new RequestGroup("Group");
        group.setAuthType(AuthType.BEARER.getConstant());
        group.setAuthToken("{{__snippet_value__}}");
        group.setVariables(List.of(new Variable(true, "__snippet_value__", "actual-value")));
        register(draft, group);

        HttpRequestItem prepared = RequestCodeSnippetPreparer.prepare(draft, false);

        assertEquals(prepared.getUrl(), "http://[::1]/users/{{__snippet_value__}}");
        assertEquals(prepared.getBody(), "{{__snippet_value__}}");
        assertEquals(prepared.getAuthToken(), "{{__snippet_value__}}");
        assertEquals(draft.getUrl(), "http://::1/users/:id");
        assertNull(RequestExecutionContext.getCurrentScope());
    }

    @Test
    public void shouldRestoreExistingScopeAndPreserveExecutionVariablePriority() {
        RequestExecutionScope previous = RequestExecutionScope.fromGroupVariables(Map.of("__old_scope__", "old"));
        RequestExecutionContext.setCurrentScope(previous);
        Map<String, String> executionVariables = new LinkedHashMap<>(Map.of("__snippet_value__", "runtime"));
        Map<String, String> iterationVariables = new LinkedHashMap<>(Map.of("__snippet_value__", "iteration"));
        VariablesService.getInstance().attachContextMap(executionVariables);
        IterationDataVariableService.getInstance().attachContextMap(iterationVariables);

        HttpRequestItem draft = request("https://example.com/{{__snippet_value__}}");
        RequestGroup group = new RequestGroup("Group");
        group.setVariables(List.of(new Variable(true, "__snippet_value__", "group")));
        register(draft, group);

        assertEquals(RequestCodeSnippetPreparer.prepare(draft, true).getUrl(), "https://example.com/runtime");
        assertSame(RequestExecutionContext.getCurrentScope(), previous);
        assertSame(VariablesService.getInstance().getCurrentContextMap(), executionVariables);
        assertSame(IterationDataVariableService.getInstance().getCurrentContextMap(), iterationVariables);
        assertEquals(executionVariables.get("__snippet_value__"), "runtime");
        assertEquals(iterationVariables.get("__snippet_value__"), "iteration");
    }

    @Test
    public void shouldUseEmptyGroupScopeForRequestsWithoutParentsAndRestorePreviousScope() {
        RequestExecutionScope previous = RequestExecutionScope.fromGroupVariables(
                Map.of("__snippet_stale_group__", "stale"));
        RequestExecutionContext.setCurrentScope(previous);
        HttpRequestItem draft = request("https://example.com/{{__snippet_stale_group__}}");

        HttpRequestItem prepared = RequestCodeSnippetPreparer.prepare(draft, true);

        assertEquals(prepared.getUrl(), "https://example.com/{{__snippet_stale_group__}}");
        assertNotSame(prepared, draft);
        assertSame(RequestExecutionContext.getCurrentScope(), previous);
    }

    @Test
    public void shouldNotUseStaleGroupVariablesWhenCollectionLookupFails() {
        RequestExecutionScope previous = RequestExecutionScope.fromGroupVariables(
                Map.of("__snippet_stale_group__", "stale"));
        RequestExecutionContext.setCurrentScope(previous);
        CollectionDocumentRegistry.registerDocumentSupplier(() -> {
            throw new IllegalStateException("collection unavailable");
        });
        HttpRequestItem draft = request("https://example.com/{{__snippet_stale_group__}}");

        HttpRequestItem prepared = RequestCodeSnippetPreparer.prepare(draft, true);

        assertEquals(prepared.getUrl(), "https://example.com/{{__snippet_stale_group__}}");
        assertSame(RequestExecutionContext.getCurrentScope(), previous);
    }

    @Test
    public void shouldNotExecuteRequestOrInheritedScripts() {
        HttpRequestItem draft = request("https://example.com/users");
        draft.setPrescript("throw new Error('request script must not run');");
        RequestGroup group = new RequestGroup("Group");
        group.setPrescript("throw new Error('group script must not run');");
        register(draft, group);

        HttpRequestItem prepared = RequestCodeSnippetPreparer.prepare(draft, true);

        assertEquals(prepared.getUrl(), draft.getUrl());
        assertTrue(prepared.getPrescript().contains("request script must not run"));
        assertTrue(prepared.getPrescript().contains("group script must not run"));
        assertNull(RequestExecutionContext.getCurrentScope());
    }

    private static HttpRequestItem request(String url) {
        HttpRequestItem item = new HttpRequestItem();
        item.setId("snippet-request");
        item.setUrl(url);
        return item;
    }

    private static void register(HttpRequestItem item, RequestGroup... groups) {
        CollectionNode node = CollectionNode.request(item);
        for (int index = groups.length - 1; index >= 0; index--) {
            CollectionNode parent = CollectionNode.group(groups[index]);
            parent.addChild(node);
            node = parent;
        }
        CollectionDocument document = new CollectionDocument(List.of(node));
        CollectionDocumentRegistry.registerDocumentSupplier(() -> document);
    }
}
