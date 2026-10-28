package com.laker.postman.functional.execution;

import com.laker.postman.functional.model.AssertionResult;
import com.laker.postman.functional.model.RunnerRowData;
import com.laker.postman.http.request.PreparedRequestFactory;
import com.laker.postman.http.runtime.model.HttpCaptureProfile;
import com.laker.postman.http.runtime.model.HttpCaptureProfiles;
import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.http.runtime.transport.DefaultHttpTransport;
import com.laker.postman.http.runtime.transport.HttpExchangeOptions;
import com.laker.postman.http.runtime.transport.HttpTransport;
import com.laker.postman.http.runtime.sse.SseResponseCallback;
import com.laker.postman.model.Environment;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.script.model.TestResult;
import com.laker.postman.service.js.JsScriptExecutor;
import com.laker.postman.service.js.ScriptExecutionPipeline;
import com.laker.postman.service.js.ScriptExecutionResult;
import com.laker.postman.service.variable.ExecutionVariableContext;
import com.laker.postman.service.variable.RequestExecutionScope;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
public final class FunctionalRequestExecutor {
    public static final String ERROR = "Error";

    private final Consumer<String> requestErrorConsumer;
    private final HttpTransport httpTransport;

    public FunctionalRequestExecutor(Consumer<String> requestErrorConsumer) {
        this(requestErrorConsumer, new DefaultHttpTransport());
    }

    FunctionalRequestExecutor(Consumer<String> requestErrorConsumer, HttpTransport httpTransport) {
        this.requestErrorConsumer = requestErrorConsumer;
        this.httpTransport = httpTransport == null ? new DefaultHttpTransport() : httpTransport;
    }

    public FunctionalRequestExecutionResult execute(RunnerRowData row,
                                                    ExecutionVariableContext iterationContext,
                                                    BooleanSupplier executionActiveSupplier) {
        return execute(
                row.requestItem,
                iterationContext,
                executionActiveSupplier,
                true,
                false,
                null,
                null,
                null,
                null
        );
    }

    /**
     * Executes an effective request and lets a headless caller adjust the prepared request after
     * pre-scripts and variable substitution, but before the HTTP transport reads it.
     */
    public FunctionalRequestExecutionResult executeEffective(HttpRequestItem item,
                                                             ExecutionVariableContext iterationContext,
                                                             BooleanSupplier executionActiveSupplier,
                                                             Supplier<Environment> environmentSupplier,
                                                             RequestExecutionScope requestExecutionScope,
                                                             JsScriptExecutor.OutputCallback outputCallback,
                                                             Consumer<PreparedRequest> finalizedRequestConsumer) {
        return execute(
                item,
                iterationContext,
                executionActiveSupplier,
                false,
                true,
                environmentSupplier,
                requestExecutionScope,
                outputCallback,
                finalizedRequestConsumer
        );
    }

    private FunctionalRequestExecutionResult execute(HttpRequestItem item,
                                                     ExecutionVariableContext iterationContext,
                                                     BooleanSupplier executionActiveSupplier,
                                                     boolean applyActiveCollectionInheritance,
                                                     boolean collectionRunnerSemantics,
                                                     Supplier<Environment> environmentSupplier,
                                                     RequestExecutionScope requestExecutionScope,
                                                     JsScriptExecutor.OutputCallback outputCallback,
                                                     Consumer<PreparedRequest> finalizedRequestConsumer) {
        if (executionActiveSupplier != null && !executionActiveSupplier.getAsBoolean()) {
            return FunctionalRequestExecutionResult.skipped();
        }

        long start = System.currentTimeMillis();
        PreparedRequest request = applyActiveCollectionInheritance
                ? PreparedRequestFactory.build(item)
                : PreparedRequestFactory.buildWithoutInheritance(item);
        HttpCaptureProfiles.apply(request, HttpCaptureProfile.FUNCTIONAL_DIAGNOSTIC);

        ScriptExecutionPipeline pipeline = applyActiveCollectionInheritance
                ? ScriptExecutionPipeline.forRequestExecution(item, request, iterationContext)
                : ScriptExecutionPipeline.forEffectiveRequestExecution(
                        item,
                        request,
                        iterationContext,
                        outputCallback,
                        environmentSupplier,
                        requestExecutionScope
                );

        boolean includePreRequestTests = collectionRunnerSemantics;
        ScriptExecutionResult preResult = includePreRequestTests
                ? pipeline.executePreScriptWithTests()
                : pipeline.executePreScript();
        if (preResult.isSuccess()) {
            pipeline.finalizeRequest();
            if (finalizedRequestConsumer != null) {
                finalizedRequestConsumer.accept(request);
            }
        }

        HttpResponse response = null;
        String status;
        AssertionResult assertion = AssertionResult.NO_TESTS;
        String errorMessage = null;
        ScriptExecutionResult postResult = null;
        List<TestResult> streamTestResults = new ArrayList<>();
        StringBuilder streamResponseBody = new StringBuilder();
        boolean[] streamScriptsSucceeded = {true};
        String[] streamScriptError = {null};
        HttpResponse[] streamResponseRef = {null};

        if (!preResult.isSuccess()) {
            errorMessage = preResult.getErrorMessage();
            status = ERROR;
        } else {
            try {
                response = httpTransport.execute(request, HttpExchangeOptions.builder()
                        .callback(new SseResponseCallback() {
                            @Override
                            public void onOpen(HttpResponse streamResponse) {
                                streamResponse.isSse = true;
                                streamResponseRef[0] = streamResponse;
                            }

                            @Override
                            public void onEvent(String id, String type, String data) {
                                appendSseEvent(streamResponseBody, id, type, data);
                                HttpResponse eventResponse = new HttpResponse();
                                eventResponse.code = streamResponseRef[0] == null ? 0 : streamResponseRef[0].code;
                                eventResponse.headers = streamResponseRef[0] == null ? null : streamResponseRef[0].headers;
                                eventResponse.isSse = true;
                                eventResponse.body = data;
                                eventResponse.bodySize = data == null ? 0 : data.getBytes(StandardCharsets.UTF_8).length;
                                ScriptExecutionResult eventResult = pipeline.executePostScript(eventResponse);
                                if (eventResult.getTestResults() != null) {
                                    streamTestResults.addAll(eventResult.getTestResults());
                                }
                                if (!eventResult.isSuccess()) {
                                    streamScriptsSucceeded[0] = false;
                                    if (streamScriptError[0] == null) {
                                        streamScriptError[0] = eventResult.getErrorMessage();
                                    }
                                }
                            }

                            @Override
                            public void onFailure(String errorMsg, HttpResponse streamResponse) {
                                streamScriptsSucceeded[0] = false;
                                streamScriptError[0] = errorMsg;
                            }
                        })
                        .build());
                if (response.isSse) {
                    response.body = streamResponseBody.toString();
                    response.bodySize = streamResponseBody.toString().getBytes(StandardCharsets.UTF_8).length;
                }
                status = String.valueOf(response.code);
                postResult = response.isSse
                        ? ScriptExecutionResult.builder()
                        .success(streamScriptsSucceeded[0])
                        .testResults(streamTestResults)
                        .errorMessage(streamScriptError[0])
                        .build()
                        : pipeline.executePostScript(response);
                if (collectionRunnerSemantics && !postResult.isSuccess()) {
                    assertion = AssertionResult.FAIL;
                    errorMessage = postResult.getErrorMessage();
                } else if (includePreRequestTests
                        && (preResult.hasTestResults() || postResult.hasTestResults())) {
                    assertion = preResult.allTestsPassed() && postResult.allTestsPassed()
                            ? AssertionResult.PASS
                            : AssertionResult.FAIL;
                } else if (postResult.hasTestResults()) {
                    assertion = postResult.allTestsPassed() ? AssertionResult.PASS : AssertionResult.FAIL;
                }
            } catch (Exception ex) {
                log.error("请求执行失败", ex);
                if (requestErrorConsumer != null) {
                    requestErrorConsumer.accept(ex.getMessage());
                }
                assertion = AssertionResult.FAIL;
                errorMessage = ex.getMessage();
                status = ERROR;
            }
        }

        long elapsedMs = System.currentTimeMillis() - start;
        return new FunctionalRequestExecutionResult(
                request,
                response,
                response == null ? elapsedMs : response.costMs,
                status,
                errorMessage,
                assertion,
                reportedTests(preResult, postResult, includePreRequestTests)
        );
    }

    private static List<TestResult> reportedTests(ScriptExecutionResult preResult,
                                                  ScriptExecutionResult postResult,
                                                  boolean includePreRequestTests) {
        if (!includePreRequestTests) {
            return postResult == null ? null : postResult.getTestResults();
        }
        List<TestResult> tests = new ArrayList<>();
        if (preResult != null && preResult.getTestResults() != null) {
            tests.addAll(preResult.getTestResults());
        }
        if (postResult != null && postResult.getTestResults() != null) {
            tests.addAll(postResult.getTestResults());
        }
        return tests;
    }

    private static void appendSseEvent(StringBuilder body, String id, String type, String data) {
        if (id != null && !id.isBlank()) {
            body.append("id: ").append(id).append('\n');
        }
        if (type != null && !type.isBlank()) {
            body.append("event: ").append(type).append('\n');
        }
        if (data != null) {
            for (String line : data.split("\\R", -1)) {
                body.append("data: ").append(line).append('\n');
            }
        }
        body.append('\n');
    }
}
