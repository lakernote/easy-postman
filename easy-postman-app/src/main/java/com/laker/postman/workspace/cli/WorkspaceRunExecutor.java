package com.laker.postman.workspace.cli;

import com.laker.postman.collection.CollectionInheritance;
import com.laker.postman.collection.model.CollectionDocument;
import com.laker.postman.common.constants.ConfigPathConstants;
import com.laker.postman.functional.execution.FunctionalRequestExecutionResult;
import com.laker.postman.functional.execution.FunctionalRequestExecutor;
import com.laker.postman.functional.model.AssertionResult;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.model.Environment;
import com.laker.postman.model.Variable;
import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.RequestBodyTypes;
import com.laker.postman.script.model.TestResult;
import com.laker.postman.service.collections.CollectionDocumentJsonCodec;
import com.laker.postman.service.js.JsScriptExecutor;
import com.laker.postman.service.variable.ExecutionVariableContext;
import com.laker.postman.service.variable.IterationDataRuntimeSupport;
import com.laker.postman.service.variable.RequestExecutionScope;
import com.laker.postman.service.variable.RunScopedVariableContext;
import com.laker.postman.util.CsvDataUtil;
import com.laker.postman.util.JsonUtil;
import tools.jackson.databind.JsonNode;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WorkspaceRunExecutor {
    private final FunctionalRequestExecutor requestExecutor = new FunctionalRequestExecutor(null);

    public WorkspaceRunReport execute(WorkspaceRunOptions options,
                                      WorkspaceRunPlanner planner,
                                      PrintStream out) throws Exception {
        return execute(options, planner, out, WorkspaceRunObserver.NO_OP);
    }

    public WorkspaceRunReport execute(WorkspaceRunOptions options,
                                      WorkspaceRunPlanner planner,
                                      PrintStream out,
                                      WorkspaceRunObserver observer) throws Exception {
        if (planner == null) {
            throw new IllegalArgumentException("Workspace run planner is required");
        }
        WorkspaceRunObserver completionObserver = observer == null ? WorkspaceRunObserver.NO_OP : observer;
        WorkspaceRunWorkspace workspace = WorkspaceRunWorkspaceResolver.resolve(options.getWorkspace());
        Path collectionPath = requireFile(workspace.collectionsFile(), "EasyPostman collections file");
        Path workingDirectory = resolveWorkingDirectory(options, workspace.directory());
        CollectionDocument document = parseCollections(collectionPath);
        WorkspaceRunPlan plan = planner.plan(workspace, document);
        if (plan == null || plan.requests().isEmpty()) {
            throw new IllegalArgumentException("No runnable requests selected");
        }

        Path iterationDataPath = resolveIterationDataPath(
                options.getIterationDataPath(),
                workspace.directory()
        );
        List<Map<String, String>> dataRows = iterationDataPath == null
                ? plan.embeddedIterationData()
                : loadIterationData(iterationDataPath);
        String iterationDataSource = iterationDataPath == null
                ? plan.embeddedIterationDataSource()
                : iterationDataPath.toString();
        int iterationCount = resolveIterationCount(options.getIterationCount(), dataRows);
        Environment environment = loadEnvironment(workspace.environmentsFile(), options.getEnvironment());
        options.getEnvironmentOverrides().forEach(environment::set);
        Environment globals = loadGlobals(Path.of(ConfigPathConstants.GLOBAL_VARIABLES));
        Path uploadFileRoot = resolveUploadFileRoot(options.getUploadFileRoot());
        int maxRequestReportEntries = options.getMaxRequestReportEntries() == null
                ? Integer.MAX_VALUE
                : options.getMaxRequestReportEntries();

        if (out != null) {
            out.printf("Workspace: %s (%s)%n", workspace.name(), workspace.directory());
            out.printf("Environment: %s | Collections: %s%n",
                    environment.getName(),
                    String.join(", ", plan.collectionNames()));
            out.printf("Selection: %s | Iteration data: %s%n",
                    plan.selectionMode(),
                    iterationDataSource);
        }

        long startTimeMs = System.currentTimeMillis();
        List<WorkspaceRunReport.RequestResult> requestReports = new ArrayList<>();
        int passedRequests = 0;
        int failedRequests = 0;
        int passedTests = 0;
        int failedTests = 0;
        int startedIterations = 0;
        boolean stoppedByBail = false;
        ExecutionVariableContext runContext = new ExecutionVariableContext();

        try (RunScopedVariableContext ignored = RunScopedVariableContext.open(environment, globals)) {
            for (int iteration = 0; iteration < iterationCount && !stoppedByBail; iteration++) {
                startedIterations++;
                runContext.setIterationInfo(iteration, iterationCount);
                runContext.replaceIterationData(IterationDataRuntimeSupport.prepare(
                        dataRows.isEmpty() ? Map.of() : dataRows.get(iteration % dataRows.size())
                ));
                if (out != null && iterationCount > 1) {
                    out.printf("Iteration %d/%d%n", iteration + 1, iterationCount);
                }

                for (WorkspaceRunSelectedRequest selected : plan.requests()) {
                    HttpRequestItem effectiveItem = CollectionInheritance.apply(
                            selected.request(),
                            selected.groupChain()
                    );
                    RequestExecutionScope requestScope = RequestExecutionScope.fromVariables(
                            CollectionInheritance.mergeEnabledGroupVariables(selected.groupChain())
                    );

                    if (out != null) {
                        out.printf("→ %s %s%n", effectiveItem.getMethod(), selected.path());
                    }
                    FunctionalRequestExecutionResult result = requestExecutor.executeEffective(
                            effectiveItem,
                            runContext,
                            () -> true,
                            () -> environment,
                            requestScope,
                            scriptOutput(out),
                            request -> resolveFilePaths(request, workingDirectory, uploadFileRoot)
                    );
                    WorkspaceRunReport.RequestResult requestReport = toRequestReport(
                            iteration + 1,
                            selected,
                            result
                    );
                    completionObserver.onRequestCompleted(iteration + 1, selected, result, requestReport);
                    if (requestReports.size() < maxRequestReportEntries) {
                        requestReports.add(requestReport);
                    }
                    if (requestReport.passed()) {
                        passedRequests++;
                    } else {
                        failedRequests++;
                    }
                    for (WorkspaceRunReport.TestCase test : requestReport.tests()) {
                        if (test.passed()) {
                            passedTests++;
                        } else {
                            failedTests++;
                        }
                    }
                    printRequestResult(out, requestReport);
                    if (options.isBail() && !requestReport.passed()) {
                        stoppedByBail = true;
                        break;
                    }
                }
            }
        }

        long endTimeMs = System.currentTimeMillis();
        return new WorkspaceRunReport(
                "2.1",
                failedRequests == 0 ? WorkspaceRunReport.STATUS_SUCCESS : WorkspaceRunReport.STATUS_FAILED,
                workspace.name(),
                workspace.directory().toString(),
                plan.collectionNames(),
                environment.getName(),
                plan.selectionMode(),
                iterationDataSource,
                startTimeMs,
                endTimeMs,
                Math.max(0L, endTimeMs - startTimeMs),
                startedIterations,
                passedRequests + failedRequests,
                passedRequests,
                failedRequests,
                passedTests + failedTests,
                passedTests,
                failedTests,
                requestReports
        );
    }

    private static CollectionDocument parseCollections(Path collectionPath) {
        try {
            CollectionDocument document = CollectionDocumentJsonCodec.read(collectionPath.toFile());
            if (document.getRoots().isEmpty()) {
                throw new IllegalArgumentException("No EasyPostman collections found: " + collectionPath);
            }
            return document;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Unable to read EasyPostman collections: " + collectionPath + ": " + describe(ex),
                    ex
            );
        }
    }

    private static Environment loadEnvironment(Path path, String selector) {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            if (selector != null && !selector.isBlank()) {
                throw new IllegalArgumentException("EasyPostman environments file does not exist: " + path);
            }
            return new Environment("<none>");
        }

        List<Environment> environments;
        try {
            cn.hutool.json.JSONArray array = cn.hutool.json.JSONUtil.readJSONArray(
                    path.toFile(),
                    StandardCharsets.UTF_8
            );
            environments = cn.hutool.json.JSONUtil.toList(array, Environment.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Unable to read EasyPostman environments: " + path + ": " + describe(ex),
                    ex
            );
        }

        if (selector != null && !selector.isBlank()) {
            return environments.stream()
                    .filter(environment -> selector.equals(environment.getName())
                            || selector.equals(environment.getId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Environment not found: " + selector + ". Available environments: "
                                    + availableEnvironmentNames(environments)
                    ));
        }
        return environments.stream()
                .filter(Environment::isActive)
                .findFirst()
                .orElseGet(() -> environments.stream().findFirst().orElseGet(() -> new Environment("<none>")));
    }

    private static String availableEnvironmentNames(List<Environment> environments) {
        return environments.stream()
                .map(Environment::getName)
                .filter(name -> name != null && !name.isBlank())
                .reduce((left, right) -> left + ", " + right)
                .orElse("<none>");
    }

    private static Environment loadGlobals(Path path) {
        Environment empty = new Environment("Globals");
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            return empty;
        }
        try {
            Object parsed = cn.hutool.json.JSONUtil.parse(
                    Files.readString(path, StandardCharsets.UTF_8)
            );
            if (parsed instanceof cn.hutool.json.JSONObject object) {
                return cn.hutool.json.JSONUtil.toBean(object, Environment.class);
            }
            if (parsed instanceof cn.hutool.json.JSONArray array) {
                empty.setVariableList(cn.hutool.json.JSONUtil.toList(array, Variable.class));
            }
            return empty;
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Unable to read EasyPostman globals: " + path + ": " + describe(ex),
                    ex
            );
        }
    }

    private static List<Map<String, String>> loadIterationData(Path path) throws Exception {
        Path dataFile = requireFile(path, "Iteration data file");
        String lowerName = dataFile.getFileName().toString().toLowerCase();
        List<Map<String, String>> rows;
        if (lowerName.endsWith(".json")) {
            rows = readJsonData(dataFile);
        } else if (lowerName.endsWith(".csv")) {
            rows = CsvDataUtil.readCsvData(dataFile.toFile());
        } else {
            throw new IllegalArgumentException("Iteration data file must use .csv or .json: " + dataFile);
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Iteration data contains no rows: " + dataFile);
        }
        return rows;
    }

    private static List<Map<String, String>> readJsonData(Path dataFile) throws Exception {
        JsonNode root;
        try {
            root = JsonUtil.readTree(Files.readString(dataFile, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Unable to read JSON iteration data: " + dataFile + ": " + describe(ex),
                    ex
            );
        }
        if (root == null || !root.isArray()) {
            throw new IllegalArgumentException("JSON iteration data must be an array of objects: " + dataFile);
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (JsonNode rowNode : root) {
            if (!rowNode.isObject()) {
                throw new IllegalArgumentException("JSON iteration data rows must be objects: " + dataFile);
            }
            Map<String, String> row = new LinkedHashMap<>();
            rowNode.properties().forEach(entry -> row.put(
                    entry.getKey(),
                    jsonValue(entry.getValue())
            ));
            rows.add(row);
        }
        return rows;
    }

    private static String jsonValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return "";
        }
        return value.isValueNode() ? value.asText() : value.toString();
    }

    private static int resolveIterationCount(Integer configuredCount, List<Map<String, String>> dataRows) {
        if (configuredCount != null) {
            return configuredCount;
        }
        return dataRows.isEmpty() ? 1 : dataRows.size();
    }

    private static Path resolveIterationDataPath(Path path, Path workspaceDirectory) {
        if (path == null) {
            return null;
        }
        Path resolved = path.isAbsolute() ? path : workspaceDirectory.resolve(path);
        return resolved.toAbsolutePath().normalize();
    }

    private static Path resolveWorkingDirectory(WorkspaceRunOptions options, Path workspaceDirectory) {
        Path workingDirectory = options.getWorkingDirectory();
        if (workingDirectory == null) {
            workingDirectory = workspaceDirectory;
        } else if (!workingDirectory.isAbsolute()) {
            workingDirectory = workspaceDirectory.resolve(workingDirectory);
        }
        workingDirectory = workingDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(workingDirectory)) {
            throw new IllegalArgumentException("Working directory does not exist: " + workingDirectory);
        }
        return workingDirectory;
    }

    private static Path resolveUploadFileRoot(Path configuredRoot) {
        if (configuredRoot == null) {
            return null;
        }
        try {
            Path realRoot = configuredRoot.toRealPath();
            if (!Files.isDirectory(realRoot)) {
                throw new IllegalArgumentException("Upload file root is not a directory: " + configuredRoot);
            }
            return realRoot;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Unable to resolve upload file root: " + configuredRoot + ": " + describe(exception),
                    exception
            );
        }
    }

    private static void resolveFilePaths(PreparedRequest request,
                                         Path workingDirectory,
                                         Path uploadFileRoot) {
        if (request == null) {
            return;
        }
        if (RequestBodyTypes.BODY_TYPE_BINARY.equals(request.bodyType)) {
            request.body = resolveFilePath(request.body, workingDirectory);
            validateUploadFile(request.body, uploadFileRoot);
        }
        if (request.formDataList != null) {
            for (HttpFormData part : request.formDataList) {
                if (part != null && part.isEnabled() && part.isFile()) {
                    part.setValue(resolveFilePath(part.getValue(), workingDirectory));
                    validateUploadFile(part.getValue(), uploadFileRoot);
                }
            }
        }
    }

    private static String resolveFilePath(String value, Path workingDirectory) {
        if (value == null || value.isBlank()) {
            return value;
        }
        Path path = Path.of(value);
        return (path.isAbsolute() ? path : workingDirectory.resolve(path))
                .normalize()
                .toString();
    }

    private static void validateUploadFile(String value, Path uploadFileRoot) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Upload file path is required");
        }
        if (value.contains("{{")) {
            throw new IllegalArgumentException("Upload file path contains unresolved variables: " + value);
        }
        Path path = Path.of(value);
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalArgumentException("Upload file does not exist or is not readable: " + path);
        }
        if (uploadFileRoot != null) {
            try {
                Path realFile = path.toRealPath();
                if (!realFile.startsWith(uploadFileRoot)) {
                    throw new IllegalArgumentException(
                            "Upload file is outside the authorized workspace: " + path
                    );
                }
            } catch (IllegalArgumentException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalArgumentException(
                        "Unable to validate upload file: " + path + ": " + describe(exception),
                        exception
                );
            }
        }
    }

    private static Path requireFile(Path path, String label) {
        if (path == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized) || !Files.isReadable(normalized)) {
            throw new IllegalArgumentException(label + " does not exist or is not readable: " + normalized);
        }
        return normalized;
    }

    private static String describe(Throwable failure) {
        String message = failure == null ? null : failure.getMessage();
        return message == null || message.isBlank()
                ? failure == null ? "unknown error" : failure.getClass().getSimpleName()
                : message;
    }

    private static JsScriptExecutor.OutputCallback scriptOutput(PrintStream out) {
        return output -> {
            if (out == null || output == null || output.isBlank()) {
                return;
            }
            out.println(output.stripTrailing());
            out.flush();
        };
    }

    private static WorkspaceRunReport.RequestResult toRequestReport(
            int iteration,
            WorkspaceRunSelectedRequest selected,
            FunctionalRequestExecutionResult result) {
        List<WorkspaceRunReport.TestCase> tests = new ArrayList<>();
        if (result.getTestResults() != null) {
            for (TestResult test : result.getTestResults()) {
                if (test != null) {
                    tests.add(new WorkspaceRunReport.TestCase(test.name, test.passed, safe(test.message)));
                }
            }
        }
        boolean passed = result.getAssertion() != AssertionResult.FAIL
                && (result.getErrorMessage() == null || result.getErrorMessage().isBlank());
        String url = result.getRequest() == null
                ? selected.request().getUrl()
                : firstNonBlank(result.getRequest().sentUrl, result.getRequest().url);
        return new WorkspaceRunReport.RequestResult(
                iteration,
                selected.request().getName(),
                selected.path(),
                selected.request().getMethod(),
                url,
                result.getStatus(),
                result.getCost(),
                passed,
                safe(result.getErrorMessage()),
                tests
        );
    }

    private static void printRequestResult(PrintStream out, WorkspaceRunReport.RequestResult result) {
        if (out == null) {
            return;
        }
        out.printf("  %s %dms %s%n", result.status(), result.durationMs(), result.passed() ? "PASS" : "FAIL");
        for (WorkspaceRunReport.TestCase test : result.tests()) {
            out.printf("    %s %s", test.passed() ? "✓" : "✗", test.name());
            if (!test.passed() && !test.message().isBlank()) {
                out.printf(": %s", test.message());
            }
            out.println();
        }
        if (!result.error().isBlank()) {
            out.printf("    Error: %s%n", result.error());
        }
        out.flush();
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : safe(second);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
