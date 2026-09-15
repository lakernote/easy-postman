package com.laker.postman.mcp;

import cn.hutool.json.JSONUtil;
import com.laker.postman.collection.model.CollectionDocument;
import com.laker.postman.collection.model.CollectionNode;
import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.http.runtime.cookie.HttpCookieStore;
import com.laker.postman.mcp.server.EasyPostmanMcpBackend;
import com.laker.postman.model.Environment;
import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.service.collections.CollectionDocumentJsonCodec;
import com.laker.postman.workspace.cli.WorkspaceRequestCatalog;
import com.laker.postman.workspace.cli.WorkspaceRunExecutor;
import com.laker.postman.workspace.cli.WorkspaceRunOptions;
import com.laker.postman.workspace.cli.WorkspaceRunPlan;
import com.laker.postman.workspace.cli.WorkspaceRunReport;
import com.laker.postman.workspace.cli.WorkspaceRunSelectedRequest;
import com.laker.postman.workspace.cli.WorkspaceRunWorkspace;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

final class McpWorkspaceToolService implements EasyPostmanMcpBackend {
    private static final int DEFAULT_LIST_LIMIT = 100;
    private static final int MAX_LIST_LIMIT = 500;
    private static final int MAX_ITERATIONS = 100;
    private static final int MAX_EXECUTION_DETAIL_ENTRIES = 100;
    private static final int MAX_TEST_DETAILS_PER_REQUEST = 20;
    private static final int MAX_DETAIL_TEXT_CHARACTERS = 1024;

    private final McpWorkspaceCatalog workspaceCatalog;
    private final int maxResponseBytes;
    private final WorkspaceRunExecutor runExecutor;
    private final ReentrantLock executionLock = new ReentrantLock();

    McpWorkspaceToolService(McpServeOptions options) {
        this(options, new WorkspaceRunExecutor());
    }

    McpWorkspaceToolService(McpServeOptions options, WorkspaceRunExecutor runExecutor) {
        this(new McpWorkspaceCatalog(options), options.maxResponseBytes(), runExecutor);
    }

    McpWorkspaceToolService(McpWorkspaceCatalog workspaceCatalog,
                            int maxResponseBytes,
                            WorkspaceRunExecutor runExecutor) {
        this.workspaceCatalog = workspaceCatalog;
        this.maxResponseBytes = maxResponseBytes;
        this.runExecutor = runExecutor == null ? new WorkspaceRunExecutor() : runExecutor;
    }

    void validate() {
        List<String> failures = new ArrayList<>();
        for (McpAuthorizedWorkspace workspace : workspaceCatalog.workspaces()) {
            try {
                readDocument(workspace.runWorkspace());
                readEnvironments(workspace.runWorkspace());
                return;
            } catch (IllegalArgumentException exception) {
                failures.add(workspace.name() + ": " + describe(exception));
            }
        }
        throw new IllegalArgumentException("No runnable EasyPostman workspace found. " + String.join("; ", failures));
    }

    @Override
    public Map<String, Object> listWorkspaces() {
        List<Map<String, Object>> items = workspaceCatalog.workspaces().stream()
                .map(this::workspaceSummary)
                .toList();
        Map<String, Object> result = resultList("workspaces", items, false);
        result.put("selection", "Pass workspaceId to downstream tools. The current workspace is used when omitted.");
        return result;
    }

    @Override
    public Map<String, Object> listCollections(Map<String, Object> arguments) {
        McpAuthorizedWorkspace authorized = workspaceCatalog.resolve(arguments);
        CollectionDocument document = readDocument(authorized.runWorkspace());
        List<Map<String, Object>> collections = new ArrayList<>();
        for (CollectionNode root : document.getRoots()) {
            if (root == null || !root.isGroup() || root.getGroup() == null) {
                continue;
            }
            RequestGroup group = root.getGroup();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", safe(group.getId()));
            item.put("name", safe(group.getName()));
            item.put("description", safe(group.getDescription()));
            item.put("requestCount", WorkspaceRequestCatalog.flatten(List.of(root)).size());
            collections.add(item);
        }
        Map<String, Object> result = resultList("collections", collections, false);
        result.put("workspaceId", authorized.id());
        return result;
    }

    @Override
    public Map<String, Object> listEnvironments(Map<String, Object> arguments) {
        McpAuthorizedWorkspace authorized = workspaceCatalog.resolve(arguments);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Environment environment : readEnvironments(authorized.runWorkspace())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", safe(environment.getId()));
            item.put("name", safe(environment.getName()));
            item.put("active", environment.isActive());
            item.put("variableCount", environment.getVariableList() == null
                    ? 0
                    : environment.getVariableList().size());
            item.put("variableKeys", environment.getVariableList() == null
                    ? List.of()
                    : environment.getVariableList().stream()
                    .filter(variable -> variable != null && variable.isEnabled())
                    .map(variable -> safe(variable.getKey()))
                    .filter(key -> !key.isBlank())
                    .toList());
            items.add(item);
        }
        Map<String, Object> result = resultList("environments", items, false);
        result.put("workspaceId", authorized.id());
        result.put("note", "Variable names are listed, but values are intentionally not exposed. "
                + "Use environmentOverrides on a run tool for one-time values.");
        return result;
    }

    @Override
    public Map<String, Object> listRequests(Map<String, Object> arguments) {
        McpAuthorizedWorkspace authorized = workspaceCatalog.resolve(arguments);
        String collectionSelector = McpToolArguments.optionalString(arguments, "collectionId");
        String query = McpToolArguments.optionalString(arguments, "query");
        int limit = McpToolArguments.integer(
                arguments,
                "limit",
                DEFAULT_LIST_LIMIT,
                1,
                MAX_LIST_LIMIT
        );
        String normalizedQuery = query == null ? null : query.toLowerCase(Locale.ROOT);
        List<WorkspaceRunSelectedRequest> matching = WorkspaceRequestCatalog.flatten(
                        readDocument(authorized.runWorkspace()).getRoots()
                )
                .stream()
                .filter(request -> collectionSelector == null || belongsToCollection(request, collectionSelector))
                .filter(request -> normalizedQuery == null || matchesQuery(request, normalizedQuery))
                .toList();

        List<Map<String, Object>> items = matching.stream()
                .limit(limit)
                .map(McpWorkspaceToolService::requestSummary)
                .toList();
        Map<String, Object> result = resultList("requests", items, matching.size() > limit);
        result.put("workspaceId", authorized.id());
        result.put("matched", matching.size());
        return result;
    }

    @Override
    public Map<String, Object> runRequest(Map<String, Object> arguments) throws Exception {
        McpAuthorizedWorkspace authorized = workspaceCatalog.resolve(arguments);
        WorkspaceRunWorkspace workspace = authorized.runWorkspace();
        String requestId = McpToolArguments.requiredString(arguments, "requestId");
        String environment = McpToolArguments.optionalString(arguments, "environmentId");
        Map<String, String> environmentOverrides = McpToolArguments.stringMap(arguments, "environmentOverrides");
        WorkspaceRunSelectedRequest selected = WorkspaceRequestCatalog.flatten(readDocument(workspace).getRoots())
                .stream()
                .filter(request -> requestId.equals(request.request().getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));
        requireHttpRequest(selected);
        Map<String, Object> result = execute(
                workspace,
                environment,
                environmentOverrides,
                1,
                true,
                (resolvedWorkspace, document) -> {
                    WorkspaceRunSelectedRequest currentRequest = findRequest(document, requestId);
                    return new WorkspaceRunPlan(
                            List.of(currentRequest),
                            WorkspaceRequestCatalog.collectionNames(List.of(currentRequest)),
                            List.of(),
                            "MCP_REQUEST",
                            "<none>"
                    );
                }
        );
        result.put("workspaceId", authorized.id());
        return result;
    }

    @Override
    public Map<String, Object> runCollection(Map<String, Object> arguments) throws Exception {
        McpAuthorizedWorkspace authorized = workspaceCatalog.resolve(arguments);
        WorkspaceRunWorkspace workspace = authorized.runWorkspace();
        String collectionId = McpToolArguments.requiredString(arguments, "collectionId");
        String environment = McpToolArguments.optionalString(arguments, "environmentId");
        Map<String, String> environmentOverrides = McpToolArguments.stringMap(arguments, "environmentOverrides");
        int iterations = McpToolArguments.integer(arguments, "iterations", 1, 1, MAX_ITERATIONS);
        boolean bail = McpToolArguments.bool(arguments, "bail", false);
        CollectionNode selectedCollection = findCollection(readDocument(workspace), collectionId);
        List<WorkspaceRunSelectedRequest> selectedRequests = WorkspaceRequestCatalog.flatten(List.of(selectedCollection));
        requireHttpRequests(selectedRequests);
        Map<String, Object> result = execute(
                workspace,
                environment,
                environmentOverrides,
                iterations,
                bail,
                (resolvedWorkspace, document) -> {
                    CollectionNode currentCollection = findCollection(document, collectionId);
                    List<WorkspaceRunSelectedRequest> requests = WorkspaceRequestCatalog.flatten(
                            List.of(currentCollection)
                    );
                    return new WorkspaceRunPlan(
                            requests,
                            List.of(currentCollection.getGroup().getName()),
                            List.of(),
                            "MCP_COLLECTION",
                            "<none>"
                    );
                }
        );
        result.put("workspaceId", authorized.id());
        return result;
    }

    private Map<String, Object> execute(WorkspaceRunWorkspace workspace,
                                        String environment,
                                        Map<String, String> environmentOverrides,
                                        int iterations,
                                        boolean bail,
                                        com.laker.postman.workspace.cli.WorkspaceRunPlanner planner) throws Exception {
        WorkspaceRunOptions options = WorkspaceRunOptions.builder()
                .workspace(workspace.directory().toString())
                .environment(environment)
                .environmentOverrides(environmentOverrides)
                .iterationCount(iterations)
                .workingDirectory(workspace.directory())
                .bail(bail)
                .uploadFileRoot(workspace.directory())
                .maxRequestReportEntries(MAX_EXECUTION_DETAIL_ENTRIES)
                .build();
        List<String> sensitiveValues = sensitiveValues(workspace, environment, environmentOverrides);
        McpResponseCapture capture = new McpResponseCapture(
                maxResponseBytes,
                MAX_EXECUTION_DETAIL_ENTRIES,
                sensitiveValues
        );
        executionLock.lockInterruptibly();
        try {
            HttpCookieStore.clearAllCookies();
            WorkspaceRunReport report = runExecutor.execute(options, planner, null, capture);
            Map<String, Object> result = new LinkedHashMap<>();
            Map<String, Object> run = reportMap(report, sensitiveValues);
            run.put("workspaceName", workspace.name());
            result.put("run", run);
            result.put("responses", capture.responses());
            int returnedRequestDetails = report.requests().size();
            int omittedRequestDetails = Math.max(0, report.totalRequests() - returnedRequestDetails);
            result.put("detailsTruncated", omittedRequestDetails > 0 || capture.omittedResponses() > 0);
            result.put("omittedRequestDetails", omittedRequestDetails);
            result.put("omittedResponses", capture.omittedResponses());
            result.put("environmentOverridesApplied", environmentOverrides.keySet().stream().sorted().toList());
            return result;
        } finally {
            HttpCookieStore.clearAllCookies();
            executionLock.unlock();
        }
    }

    private Map<String, Object> workspaceSummary(McpAuthorizedWorkspace authorized) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", authorized.id());
        item.put("name", authorized.name());
        item.put("description", authorized.description());
        item.put("type", authorized.type());
        item.put("current", authorized.current());
        try {
            WorkspaceRunWorkspace workspace = authorized.runWorkspace();
            CollectionDocument document = readDocument(workspace);
            item.put("available", true);
            item.put("collectionCount", document.getRoots().size());
            item.put("requestCount", WorkspaceRequestCatalog.flatten(document.getRoots()).size());
            item.put("environmentCount", readEnvironments(workspace).size());
        } catch (IllegalArgumentException exception) {
            item.put("available", false);
            item.put("error", McpSensitiveDataRedactor.message(describe(exception)));
        }
        return item;
    }

    private CollectionDocument readDocument(WorkspaceRunWorkspace workspace) {
        if (!Files.isRegularFile(workspace.collectionsFile()) || !Files.isReadable(workspace.collectionsFile())) {
            throw new IllegalArgumentException("collections.json does not exist or is not readable");
        }
        try {
            CollectionDocument document = CollectionDocumentJsonCodec.read(workspace.collectionsFile().toFile());
            if (document.getRoots().isEmpty()) {
                throw new IllegalArgumentException("No EasyPostman collections found");
            }
            return document;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Unable to read EasyPostman collections: " + describe(exception),
                    exception
            );
        }
    }

    private List<Environment> readEnvironments(WorkspaceRunWorkspace workspace) {
        if (!Files.isRegularFile(workspace.environmentsFile()) || !Files.isReadable(workspace.environmentsFile())) {
            return List.of();
        }
        try {
            return List.copyOf(JSONUtil.toList(
                    JSONUtil.readJSONArray(workspace.environmentsFile().toFile(), StandardCharsets.UTF_8),
                    Environment.class
            ));
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Unable to read EasyPostman environments: " + describe(exception),
                    exception
            );
        }
    }

    @Override
    public String sanitizeError(Throwable failure) {
        return McpSensitiveDataRedactor.message(describe(failure));
    }

    private static WorkspaceRunSelectedRequest findRequest(CollectionDocument document, String requestId) {
        return WorkspaceRequestCatalog.flatten(document.getRoots()).stream()
                .filter(request -> requestId.equals(request.request().getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));
    }

    private static CollectionNode findCollection(CollectionDocument document, String collectionId) {
        return document.getRoots().stream()
                .filter(node -> node != null && node.isGroup() && node.getGroup() != null)
                .filter(node -> collectionId.equals(node.getGroup().getId())
                        || collectionId.equals(node.getGroup().getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Collection not found: " + collectionId));
    }

    private static boolean belongsToCollection(WorkspaceRunSelectedRequest request, String selector) {
        if (request.groupChain().isEmpty()) {
            return false;
        }
        RequestGroup collection = request.groupChain().get(0);
        return selector.equals(collection.getId()) || selector.equals(collection.getName());
    }

    private static boolean matchesQuery(WorkspaceRunSelectedRequest request, String query) {
        return safe(request.request().getName()).toLowerCase(Locale.ROOT).contains(query)
                || safe(request.path()).toLowerCase(Locale.ROOT).contains(query)
                || safe(request.request().getUrl()).toLowerCase(Locale.ROOT).contains(query)
                || safe(request.request().getMethod()).toLowerCase(Locale.ROOT).contains(query);
    }

    private static Map<String, Object> requestSummary(WorkspaceRunSelectedRequest request) {
        Map<String, Object> item = new LinkedHashMap<>();
        RequestGroup collection = request.groupChain().isEmpty() ? null : request.groupChain().get(0);
        item.put("id", safe(request.request().getId()));
        item.put("name", safe(request.request().getName()));
        item.put("path", request.path());
        item.put("collectionId", collection == null ? "" : safe(collection.getId()));
        item.put("collectionName", collection == null ? "" : safe(collection.getName()));
        item.put("method", safe(request.request().getMethod()));
        item.put("url", McpSensitiveDataRedactor.url(request.request().getUrl()));
        item.put("protocol", request.request().getProtocol() == null
                ? RequestItemProtocolEnum.HTTP.getProtocol()
                : request.request().getProtocol().getProtocol());
        return item;
    }

    private static Map<String, Object> reportMap(WorkspaceRunReport report, List<String> sensitiveValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", report.schemaVersion());
        result.put("status", report.status());
        result.put("workspaceName", truncateDetail(report.workspaceName()));
        result.put("collections", report.collections());
        result.put("environment", report.environment());
        result.put("selectionMode", report.selectionMode());
        result.put("elapsedTimeMs", report.elapsedTimeMs());
        result.put("iterations", report.iterations());
        result.put("totalRequests", report.totalRequests());
        result.put("passedRequests", report.passedRequests());
        result.put("failedRequests", report.failedRequests());
        result.put("totalTests", report.totalTests());
        result.put("passedTests", report.passedTests());
        result.put("failedTests", report.failedTests());
        result.put("requests", report.requests().stream()
                .map(request -> requestReportMap(request, sensitiveValues))
                .toList());
        return result;
    }

    private static Map<String, Object> requestReportMap(WorkspaceRunReport.RequestResult request,
                                                         List<String> sensitiveValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("iteration", request.iteration());
        result.put("name", truncateDetail(request.name()));
        result.put("path", truncateDetail(request.path()));
        result.put("method", request.method());
        result.put("url", truncateDetail(McpSensitiveDataRedactor.url(request.url(), sensitiveValues)));
        result.put("status", request.status());
        result.put("durationMs", request.durationMs());
        result.put("passed", request.passed());
        result.put("error", truncateDetail(McpSensitiveDataRedactor.message(request.error(), sensitiveValues)));
        result.put("tests", request.tests().stream()
                .limit(MAX_TEST_DETAILS_PER_REQUEST)
                .map(test -> testCaseMap(test, sensitiveValues))
                .toList());
        result.put("testsTruncated", request.tests().size() > MAX_TEST_DETAILS_PER_REQUEST);
        result.put("omittedTests", Math.max(0, request.tests().size() - MAX_TEST_DETAILS_PER_REQUEST));
        return result;
    }

    private static Map<String, Object> testCaseMap(WorkspaceRunReport.TestCase test,
                                                    List<String> sensitiveValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", truncateDetail(McpSensitiveDataRedactor.message(test.name(), sensitiveValues)));
        result.put("passed", test.passed());
        result.put("message", truncateDetail(McpSensitiveDataRedactor.message(test.message(), sensitiveValues)));
        return result;
    }

    private List<String> sensitiveValues(WorkspaceRunWorkspace workspace,
                                         String environmentSelector,
                                         Map<String, String> environmentOverrides) {
        List<Environment> environments = readEnvironments(workspace);
        Environment selected = null;
        if (environmentSelector != null) {
            selected = environments.stream()
                    .filter(environment -> environmentSelector.equals(environment.getId())
                            || environmentSelector.equals(environment.getName()))
                    .findFirst()
                    .orElse(null);
        } else {
            selected = environments.stream()
                    .filter(Environment::isActive)
                    .findFirst()
                    .orElseGet(() -> environments.stream().findFirst().orElse(null));
        }

        List<String> values = new ArrayList<>();
        if (selected != null && selected.getVariableList() != null) {
            selected.getVariableList().stream()
                    .filter(variable -> variable != null && variable.isEnabled())
                    .filter(variable -> McpSensitiveDataRedactor.isSensitiveName(variable.getKey()))
                    .map(variable -> variable.getValue())
                    .filter(value -> value != null && !value.isBlank())
                    .forEach(values::add);
        }
        environmentOverrides.entrySet().stream()
                .filter(entry -> McpSensitiveDataRedactor.isSensitiveName(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(value -> value != null && !value.isBlank())
                .forEach(values::add);
        return values.stream()
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }

    private static String truncateDetail(String value) {
        String safeValue = safe(value);
        if (safeValue.length() <= MAX_DETAIL_TEXT_CHARACTERS) {
            return safeValue;
        }
        int end = MAX_DETAIL_TEXT_CHARACTERS;
        if (Character.isHighSurrogate(safeValue.charAt(end - 1))) {
            end--;
        }
        return safeValue.substring(0, end);
    }

    private static Map<String, Object> resultList(String name, List<Map<String, Object>> items, boolean truncated) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(name, items);
        result.put("count", items.size());
        result.put("truncated", truncated);
        return result;
    }

    private static void requireHttpRequests(List<WorkspaceRunSelectedRequest> requests) {
        if (requests.isEmpty()) {
            throw new IllegalArgumentException("No runnable requests found in collection");
        }
        requests.forEach(McpWorkspaceToolService::requireHttpRequest);
    }

    private static void requireHttpRequest(WorkspaceRunSelectedRequest request) {
        RequestItemProtocolEnum protocol = request.request().getProtocol();
        if (protocol != null && !protocol.isHttpProtocol()) {
            throw new IllegalArgumentException(
                    "MCP v1 can run HTTP requests only; unsupported protocol at " + request.path() + ": "
                            + protocol.getProtocol()
            );
        }
    }

    private static String describe(Throwable failure) {
        if (failure == null) {
            return "unknown error";
        }
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
