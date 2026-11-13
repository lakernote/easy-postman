package com.laker.postman.performance.execution;

import com.laker.postman.performance.core.plan.PerformanceAssertionElement;
import com.laker.postman.performance.core.plan.PerformanceExtractorElement;


import cn.hutool.core.text.CharSequenceUtil;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.performance.plan.PerformanceRequestSampler;

import java.util.List;
import java.util.regex.Pattern;

record PerformanceResponseCapturePlan(PreparedRequest.ResponseBodyMode httpResponseBodyMode,
                                      boolean runPostScript,
                                      boolean postScriptNeedsResponseBody,
                                      boolean retainStreamResponseBody,
                                      boolean retainWebSocketReadPayloads,
                                      boolean trackStreamResponseBodySize,
                                      boolean evaluatesResponseBody) {

    static PerformanceResponseCapturePlan resolve(boolean efficientMode,
                                                  PerformanceRequestSampler requestSampler,
                                                  boolean sseRequest,
                                                  boolean webSocketRequest,
                                                  String postscript) {
        List<PerformanceAssertionElement> assertionElements =
                PerformanceAssertionRunner.collectAssertionElements(requestSampler, sseRequest, webSocketRequest);
        List<PerformanceExtractorElement> extractorElements =
                PerformanceExtractorRunner.collectExtractorElements(requestSampler, sseRequest, webSocketRequest);
        boolean assertionNeedsResponseBody = PerformanceAssertionRunner.requiresResponseBodyElements(assertionElements);
        boolean extractorNeedsResponseBody = PerformanceExtractorRunner.requiresResponseBodyElements(extractorElements);
        boolean runPostScript = CharSequenceUtil.isNotBlank(postscript);
        boolean postScriptNeedsResponseBody = PerformancePostScriptResponseUsage.requiresResponseBody(postscript);
        boolean postScriptNeedsResponseSize = PerformancePostScriptResponseUsage.requiresResponseSize(postscript);
        boolean readStepNeedsResponseBody = webSocketRequest
                && WebSocketScenarioStepSupport.hasReadStepWithResponseBodyNode(requestSampler);
        boolean retainWebSocketReadPayloads = webSocketRequest
                && WebSocketScenarioStepSupport.hasReadStepRequiringPayload(requestSampler);
        boolean retainStreamResponseBody = !efficientMode
                || assertionNeedsResponseBody
                || extractorNeedsResponseBody
                || postScriptNeedsResponseBody
                || readStepNeedsResponseBody;
        boolean trackStreamResponseBodySize = retainStreamResponseBody || postScriptNeedsResponseSize;

        return new PerformanceResponseCapturePlan(
                resolveHttpResponseBodyMode(efficientMode, assertionElements, extractorElements, postscript),
                runPostScript,
                postScriptNeedsResponseBody,
                retainStreamResponseBody,
                retainWebSocketReadPayloads,
                trackStreamResponseBodySize,
                assertionNeedsResponseBody || extractorNeedsResponseBody || postScriptNeedsResponseBody
        );
    }

    static PreparedRequest.ResponseBodyMode resolveHttpResponseBodyMode(boolean efficientMode,
                                                                        List<PerformanceAssertionElement> assertionElements,
                                                                        List<PerformanceExtractorElement> extractorElements,
                                                                        String postscript) {
        if (!efficientMode) {
            // Result rows keep the body for inspection. PREVIEW reads in memory (no per-request temp files);
            // the executor bounds what is retained after assertions/extractors have seen the body.
            return PreparedRequest.ResponseBodyMode.PREVIEW;
        }
        boolean requiresResponseBody = PerformanceAssertionRunner.requiresResponseBodyElements(assertionElements)
                || PerformanceExtractorRunner.requiresResponseBodyElements(extractorElements)
                || PerformancePostScriptResponseUsage.requiresResponseBody(postscript);
        if (requiresResponseBody) {
            return PreparedRequest.ResponseBodyMode.PREVIEW;
        }
        return PreparedRequest.ResponseBodyMode.METADATA_ONLY;
    }

    private static final class PerformancePostScriptResponseUsage {
        private static final List<Pattern> RESPONSE_BODY_ACCESS_PATTERNS = List.of(
                Pattern.compile("\\bpm\\s*\\.\\s*response\\s*\\.\\s*(?:text|json)\\s*\\("),
                Pattern.compile("\\bpm\\s*\\.\\s*response\\s*\\.\\s*to\\s*\\.\\s*have\\s*\\.\\s*body\\b"),
                Pattern.compile("\\bresponseBody\\b")
        );
        private static final List<Pattern> RESPONSE_SIZE_ACCESS_PATTERNS = List.of(
                Pattern.compile("\\bpm\\s*\\.\\s*response\\s*\\.\\s*size\\s*\\(")
        );

        private PerformancePostScriptResponseUsage() {
        }

        private static boolean requiresResponseBody(String script) {
            if (CharSequenceUtil.isBlank(script)) {
                return false;
            }
            for (Pattern pattern : RESPONSE_BODY_ACCESS_PATTERNS) {
                if (pattern.matcher(script).find()) {
                    return true;
                }
            }
            return false;
        }

        private static boolean requiresResponseSize(String script) {
            if (CharSequenceUtil.isBlank(script)) {
                return false;
            }
            for (Pattern pattern : RESPONSE_SIZE_ACCESS_PATTERNS) {
                if (pattern.matcher(script).find()) {
                    return true;
                }
            }
            return false;
        }
    }
}
