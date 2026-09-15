package com.laker.postman.mcp;

import com.laker.postman.collection.model.CollectionDocument;
import com.laker.postman.collection.model.CollectionNode;
import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.functional.execution.FunctionalRequestExecutionResult;
import com.laker.postman.functional.model.AssertionResult;
import com.laker.postman.http.runtime.app.AppHttpRuntimeBootstrap;
import com.laker.postman.http.runtime.config.HttpRuntimeSettingsProvider;
import com.laker.postman.http.runtime.cookie.HttpCookieStore;
import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.okhttp.OkHttpClientManager;
import com.laker.postman.model.Environment;
import com.laker.postman.model.Variable;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.RequestBodyTypes;
import com.laker.postman.service.collections.CollectionDocumentJsonCodec;
import com.laker.postman.workspace.cli.WorkspaceRequestCatalog;
import com.laker.postman.workspace.cli.WorkspaceRunExecutor;
import com.laker.postman.workspace.cli.WorkspaceRunOptions;
import com.laker.postman.workspace.cli.WorkspaceRunPlan;
import com.laker.postman.workspace.cli.WorkspaceRunReport;
import com.laker.postman.workspace.cli.WorkspaceRunSelectedRequest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class McpWorkspaceToolServiceTest {
    private MockWebServer server;

    @BeforeMethod
    public void setUpRuntime() {
        HttpRuntimeSettingsProvider.reset();
        OkHttpClientManager.clearClientCache();
        AppHttpRuntimeBootstrap.configure();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (server != null) {
            server.shutdown();
        }
        OkHttpClientManager.clearClientCache();
        HttpRuntimeSettingsProvider.reset();
        HttpCookieStore.clearAllCookies();
    }

    @Test
    public void shouldDiscoverWorkspaceWithoutExposingEnvironmentValues() throws Exception {
        Path workspace = Files.createTempDirectory("easy-postman-mcp-catalog-");
        HttpRequestItem request = request(
                "request-1",
                "List users",
                "https://example.test/users?api_key=url-secret"
        );
        writeWorkspace(workspace, "collection-1", "Users API", request);
        writeEnvironment(workspace, "env-1", "Development", "very-secret-token");
        McpWorkspaceToolService service = new McpWorkspaceToolService(new McpServeOptions(workspace.toString(), 1024));

        Map<String, Object> collections = service.listCollections(Map.of());
        Map<String, Object> requests = service.listRequests(Map.of("query", "users", "limit", 10));
        Map<String, Object> environments = service.listEnvironments(Map.of());

        assertEquals(service.listWorkspaces().get("count"), 1);
        assertEquals(collections.get("count"), 1);
        assertEquals(requests.get("matched"), 1);
        assertEquals(environments.get("count"), 1);
        assertFalse(environments.toString().contains("very-secret-token"));
        assertTrue(environments.toString().contains("apiToken"));
        assertFalse(requests.toString().contains("url-secret"));
    }

    @Test
    public void shouldSelectOnlyAuthorizedWorkspaceIds() throws Exception {
        Path first = Files.createTempDirectory("easy-postman-mcp-first-");
        Path second = Files.createTempDirectory("easy-postman-mcp-second-");
        writeWorkspace(first, "collection-first", "First API", request("request-first", "First", "https://first.test"));
        writeWorkspace(second, "collection-second", "Second API", request("request-second", "Second", "https://second.test"));
        McpWorkspaceCatalog catalog = new McpWorkspaceCatalog(List.of(
                new McpAuthorizedWorkspace("workspace-first", "First", "", "LOCAL", first, true),
                new McpAuthorizedWorkspace("workspace-second", "Second", "", "GIT", second, false)
        ));
        McpWorkspaceToolService service = new McpWorkspaceToolService(catalog, 1024, null);

        assertEquals(service.listWorkspaces().get("count"), 2);
        assertEquals(service.listCollections(Map.of()).get("workspaceId"), "workspace-first");
        assertEquals(service.listCollections(Map.of("workspaceId", "workspace-second")).get("workspaceId"),
                "workspace-second");
        IllegalArgumentException error = expectThrows(
                IllegalArgumentException.class,
                () -> service.listCollections(Map.of("workspaceId", second.toString()))
        );
        assertTrue(error.getMessage().contains("not authorized"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldRunRequestTruncateBodyAndRedactSensitiveHeaders() throws Exception {
        server = new MockWebServer();
        server.start();
        server.enqueue(new MockResponse()
                .setResponseCode(201)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .addHeader("Set-Cookie", "session=very-secret")
                .addHeader("X-Auth-Token", "custom-header-secret")
                .addHeader("Location", "https://callback.test?refresh_token=redirect-secret")
                .setBody("{\"access_token\":\"one-time-secret\",\"data\":\""
                        + "好".repeat(600) + "\"}"));
        Path workspace = Files.createTempDirectory("easy-postman-mcp-run-");
        HttpRequestItem request = request(
                "request-run",
                "Create user",
                server.url("/users").toString() + "?access_token={{apiToken}}"
        );
        writeWorkspace(workspace, "collection-run", "Users API", request);
        writeEnvironment(workspace, "env-run", "Development", "saved-secret");
        McpWorkspaceToolService service = new McpWorkspaceToolService(new McpServeOptions(workspace.toString(), 1024));

        Map<String, Object> result = service.runRequest(Map.of(
                "requestId", "request-run",
                "environmentOverrides", Map.of("apiToken", "one-time-secret")
        ));
        RecordedRequest recordedRequest = server.takeRequest();

        Map<String, Object> run = (Map<String, Object>) result.get("run");
        List<Map<String, Object>> responses = (List<Map<String, Object>>) result.get("responses");
        Map<String, Object> response = responses.get(0);
        Map<String, List<String>> headers = (Map<String, List<String>>) response.get("headers");
        assertEquals(run.get("status"), "SUCCESS");
        assertEquals(response.get("statusCode"), 201);
        assertEquals(response.get("bodyTruncated"), true);
        assertEquals(recordedRequest.getRequestUrl().queryParameter("access_token"), "one-time-secret");
        assertEquals(result.get("environmentOverridesApplied"), List.of("apiToken"));
        assertTrue(((String) response.get("body")).getBytes(StandardCharsets.UTF_8).length <= 1024);
        assertTrue(headers.entrySet().stream()
                .anyMatch(entry -> "set-cookie".equalsIgnoreCase(entry.getKey())
                        && entry.getValue().equals(List.of("<redacted>"))));
        assertFalse(response.toString().contains("very-secret"));
        assertFalse(response.toString().contains("custom-header-secret"));
        assertFalse(response.toString().contains("redirect-secret"));
        assertFalse(result.toString().contains("one-time-secret"));
        String persistedEnvironments = Files.readString(workspace.resolve("environments.json"));
        assertTrue(persistedEnvironments.contains("saved-secret"));
        assertFalse(persistedEnvironments.contains("one-time-secret"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldSelectEnvironmentPerRunWithoutChangingPersistedActiveEnvironment() throws Exception {
        server = new MockWebServer();
        server.start();
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
        Path workspace = Files.createTempDirectory("easy-postman-mcp-environment-");
        HttpRequestItem request = request("request-environment", "Environment request", "{{baseUrl}}/users");
        writeWorkspace(workspace, "collection-environment", "Environment API", request);
        Environment development = environment(
                "env-development",
                "Development",
                true,
                "baseUrl",
                server.url("/development").toString()
        );
        Environment production = environment(
                "env-production",
                "Production",
                false,
                "baseUrl",
                server.url("/production").toString()
        );
        writeEnvironments(workspace, development, production);
        McpWorkspaceToolService service = new McpWorkspaceToolService(new McpServeOptions(workspace.toString(), 1024));

        Map<String, Object> result = service.runRequest(Map.of(
                "requestId", "request-environment",
                "environmentId", "env-production"
        ));
        RecordedRequest recordedRequest = server.takeRequest();
        Map<String, Object> run = (Map<String, Object>) result.get("run");
        List<Environment> persisted = cn.hutool.json.JSONUtil.toList(
                cn.hutool.json.JSONUtil.readJSONArray(
                        workspace.resolve("environments.json").toFile(),
                        StandardCharsets.UTF_8
                ),
                Environment.class
        );

        assertEquals(recordedRequest.getPath(), "/production/users");
        assertEquals(run.get("environment"), "Production");
        assertTrue(persisted.get(0).isActive());
        assertFalse(persisted.get(1).isActive());
    }

    @Test
    public void shouldClearCookiesBetweenMcpToolCalls() throws Exception {
        server = new MockWebServer();
        server.start();
        server.enqueue(new MockResponse().setResponseCode(200).addHeader("Set-Cookie", "session=first-call"));
        server.enqueue(new MockResponse().setResponseCode(200));
        server.enqueue(new MockResponse().setResponseCode(200));
        Path workspace = Files.createTempDirectory("easy-postman-mcp-cookie-");
        writeWorkspace(
                workspace,
                "collection-cookie",
                "Cookie API",
                request("request-login", "Login request", server.url("/login").toString()),
                request("request-cookie", "Cookie request", server.url("/cookie").toString())
        );
        McpWorkspaceToolService service = new McpWorkspaceToolService(new McpServeOptions(workspace.toString(), 1024));

        service.runCollection(Map.of("collectionId", "collection-cookie"));
        service.runRequest(Map.of("requestId", "request-cookie"));

        server.takeRequest();
        RecordedRequest requestInsideCollectionRun = server.takeRequest();
        RecordedRequest requestInNextToolCall = server.takeRequest();
        assertTrue(requestInsideCollectionRun.getHeader("Cookie").contains("session=first-call"));
        assertEquals(requestInNextToolCall.getHeader("Cookie"), null);
    }

    @Test
    public void shouldRejectUploadFilesOutsideAuthorizedWorkspace() throws Exception {
        server = new MockWebServer();
        server.start();
        Path workspace = Files.createTempDirectory("easy-postman-mcp-upload-");
        Path outsideFile = Files.createTempFile("easy-postman-mcp-secret-", ".txt");
        Files.writeString(outsideFile, "local-secret", StandardCharsets.UTF_8);
        HttpRequestItem upload = request(
                "request-upload",
                "Upload request",
                server.url("/upload").toString()
        );
        upload.setMethod("POST");
        upload.setBodyType(RequestBodyTypes.BODY_TYPE_BINARY);
        upload.setBody("{{uploadPath}}");
        writeWorkspace(workspace, "collection-upload", "Upload API", upload);
        McpWorkspaceToolService service = new McpWorkspaceToolService(new McpServeOptions(workspace.toString(), 1024));

        IllegalArgumentException error = expectThrows(
                IllegalArgumentException.class,
                () -> service.runRequest(Map.of(
                        "requestId", "request-upload",
                        "environmentOverrides", Map.of("uploadPath", outsideFile.toString())
                ))
        );

        assertTrue(error.getMessage().contains("outside the authorized workspace"));
        assertEquals(server.getRequestCount(), 0);
    }

    @Test
    public void shouldBoundCapturedResponseDetails() {
        HttpRequestItem request = request("request-capture", "Capture", "https://example.test");
        WorkspaceRunSelectedRequest selected = new WorkspaceRunSelectedRequest(request, List.of(), "Capture");
        HttpResponse response = new HttpResponse();
        response.code = 200;
        response.body = "ok";
        FunctionalRequestExecutionResult execution = new FunctionalRequestExecutionResult(
                null,
                response,
                1L,
                "200",
                null,
                AssertionResult.NO_TESTS,
                List.of()
        );
        WorkspaceRunReport.RequestResult report = new WorkspaceRunReport.RequestResult(
                1,
                "Capture",
                "Capture",
                "GET",
                "https://example.test",
                "200",
                1L,
                true,
                "",
                List.of()
        );
        McpResponseCapture capture = new McpResponseCapture(1024, 1);

        capture.onRequestCompleted(1, selected, execution, report);
        capture.onRequestCompleted(2, selected, execution, report);

        assertEquals(capture.responses().size(), 1);
        assertEquals(capture.omittedResponses(), 1);
    }

    @Test
    public void shouldBoundRetainedRunReportDetailsWithoutChangingTotals() throws Exception {
        server = new MockWebServer();
        server.start();
        server.enqueue(new MockResponse().setResponseCode(200));
        server.enqueue(new MockResponse().setResponseCode(200));
        Path workspace = Files.createTempDirectory("easy-postman-mcp-report-limit-");
        writeWorkspace(
                workspace,
                "collection-report-limit",
                "Report Limit API",
                request("request-report-1", "First", server.url("/first").toString()),
                request("request-report-2", "Second", server.url("/second").toString())
        );
        WorkspaceRunOptions options = WorkspaceRunOptions.builder()
                .workspace(workspace.toString())
                .maxRequestReportEntries(1)
                .build();

        WorkspaceRunReport report = new WorkspaceRunExecutor().execute(
                options,
                (resolvedWorkspace, document) -> {
                    List<WorkspaceRunSelectedRequest> requests = WorkspaceRequestCatalog.flatten(document.getRoots());
                    return new WorkspaceRunPlan(
                            requests,
                            WorkspaceRequestCatalog.collectionNames(requests),
                            List.of(),
                            "TEST",
                            "<none>"
                    );
                },
                null
        );

        assertEquals(report.totalRequests(), 2);
        assertEquals(report.requests().size(), 1);
    }

    private static void writeWorkspace(Path workspace,
                                       String collectionId,
                                       String collectionName,
                                       HttpRequestItem... requests) throws Exception {
        RequestGroup group = new RequestGroup(collectionName);
        group.setId(collectionId);
        CollectionNode root = CollectionNode.group(group);
        for (HttpRequestItem request : requests) {
            root.addChild(CollectionNode.request(request));
        }
        CollectionDocumentJsonCodec.write(
                workspace.resolve("collections.json").toFile(),
                new CollectionDocument(List.of(root))
        );
    }

    private static void writeEnvironment(Path workspace,
                                         String environmentId,
                                         String name,
                                         String value) throws Exception {
        writeEnvironments(workspace, environment(environmentId, name, true, "apiToken", value));
    }

    private static Environment environment(String id,
                                           String name,
                                           boolean active,
                                           String key,
                                           String value) {
        Environment environment = new Environment(name);
        environment.setId(id);
        environment.setActive(active);
        environment.setVariableList(List.of(new Variable(true, key, value)));
        return environment;
    }

    private static void writeEnvironments(Path workspace, Environment... environments) throws Exception {
        Files.writeString(
                workspace.resolve("environments.json"),
                cn.hutool.json.JSONUtil.toJsonPrettyStr(List.of(environments)),
                StandardCharsets.UTF_8
        );
    }

    private static HttpRequestItem request(String id, String name, String url) {
        HttpRequestItem request = new HttpRequestItem();
        request.setId(id);
        request.setName(name);
        request.setMethod("GET");
        request.setUrl(url);
        request.setBodyType(RequestBodyTypes.BODY_TYPE_NONE);
        return request;
    }
}
