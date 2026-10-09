package com.laker.postman.panel.collections.editor.request;

import com.laker.postman.http.runtime.error.DownloadCancelledException;
import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.transport.HttpExchangeTerminalResponseFactory;
import com.laker.postman.stream.MessageType;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.script.model.TestResult;
import com.laker.postman.panel.collections.editor.request.sub.ResponsePanel;
import com.laker.postman.panel.sidebar.ConsolePanel;
import com.laker.postman.http.runtime.error.NetworkErrorMessageResolver;
import com.laker.postman.http.request.HttpRequestProtocol;
import com.laker.postman.http.runtime.redirect.HttpRedirectExecutor;
import com.laker.postman.http.runtime.sse.SseResponseCallback;
import com.laker.postman.service.js.ScriptExecutionPipeline;
import com.laker.postman.util.FileSizeDisplayUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.common.component.notification.NotificationCenter;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.io.InterruptedIOException;
import java.util.List;
import java.util.Locale;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class HttpRequestExecutor {
    private final ResponsePanel responsePanel;
    private final RequestExecutionUiUpdater requestExecutionUiUpdater;
    private final RequestStreamUiAppender requestStreamUiAppender;
    private final RequestResponseHandler requestResponseHandler;
    private final HttpRedirectExecutor redirectExecutor = new HttpRedirectExecutor();
    private final Runnable convertCurrentRequestToSse;
    private final RequestExecutionState executionState;

    SwingWorker<Void, Void> createWorker(PreparedRequest req, ScriptExecutionPipeline pipeline, int maxRedirectCount) {
        return new SwingWorker<>() {
            HttpResponse resp;
            boolean userCanceled;
            final boolean expectedSse = HttpRequestProtocol.isSse(req);
            final StringBuilder sseBodyBuilder = new StringBuilder();
            final long requestStartMs = System.currentTimeMillis();

            @Override
            protected Void doInBackground() {
                try {
                    responsePanel.setResponseTabButtonsEnable(true);
                    responsePanel.switchTabButtonHttpOrSse(expectedSse ? "sse" : "http");
                    resp = redirectExecutor.executeWithRedirects(req, maxRedirectCount, new SseResponseCallback() {
                        @Override
                        public void onOpen(HttpResponse response) {
                            SwingUtilities.invokeLater(() -> {
                                if (executionState.isDisposed()) {
                                    return;
                                }
                                executionState.markAutoDetectedHttpSseOpen();
                                convertCurrentRequestToSse.run();
                                responsePanel.switchTabButtonHttpOrSse("sse");
                                requestExecutionUiUpdater.updateUIForResponse(response);
                                responsePanel.setRequestDetails(req);
                                responsePanel.setResponseDetails(response);
                                requestStreamUiAppender.appendSseMessage(MessageType.CONNECTED, null, "open", null,
                                        I18nUtil.getMessage(MessageKeys.SSE_STREAM_CONNECTED), null);
                            });
                        }

                        @Override
                        public void onEvent(String id, String type, String data) {
                            requestStreamUiAppender.appendSseRawEvent(sseBodyBuilder, id, type, data);
                            SwingUtilities.invokeLater(() -> {
                                if (executionState.isDisposed()) {
                                    return;
                                }
                                List<TestResult> testResults = requestResponseHandler.handleStreamMessage(pipeline, data);
                                requestStreamUiAppender.appendSseMessage(MessageType.RECEIVED, id, type, null, data, testResults);
                            });
                        }

                        @Override
                        public void onRetryChange(long retryMs) {
                            SwingUtilities.invokeLater(() -> {
                                if (executionState.isDisposed()) {
                                    return;
                                }
                                requestStreamUiAppender.appendSseMessage(MessageType.INFO, null, "retry", retryMs,
                                        I18nUtil.getMessage(MessageKeys.SSE_RETRY_UPDATED, retryMs), null);
                            });
                        }

                        @Override
                        public void onClosed(HttpResponse response) {
                            requestStreamUiAppender.finalizeSseResponse(response, sseBodyBuilder, requestStartMs);
                            SwingUtilities.invokeLater(() -> {
                                if (executionState.isDisposed()) {
                                    return;
                                }
                                executionState.clearAutoDetectedHttpSseOpen();
                                requestExecutionUiUpdater.updateUIForResponse(response);
                                responsePanel.setRequestDetails(req);
                                responsePanel.setResponseDetails(response);
                                requestStreamUiAppender.appendSseMessage(MessageType.CLOSED, null, "closed", null,
                                        I18nUtil.getMessage(MessageKeys.SSE_STREAM_CLOSED), null);
                            });
                        }

                        @Override
                        public void onFailure(String errorMsg, HttpResponse response) {
                            requestStreamUiAppender.finalizeSseResponse(response, sseBodyBuilder, requestStartMs);
                            SwingUtilities.invokeLater(() -> {
                                if (executionState.isDisposed()) {
                                    return;
                                }
                                executionState.clearAutoDetectedHttpSseOpen();
                                NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.SSE_FAILED, errorMsg));
                                requestExecutionUiUpdater.updateUIForResponse(response);
                                responsePanel.setRequestDetails(req);
                                responsePanel.setResponseDetails(response);
                                requestStreamUiAppender.appendSseMessage(MessageType.WARNING, null, "failure", null,
                                        I18nUtil.getMessage(MessageKeys.SSE_STREAM_FAILED, errorMsg), null);
                            });
                        }
                    }, executionState);
                } catch (DownloadCancelledException ex) {
                    log.info("User canceled download for request: {} {}", req.method, req.url);
                    userCanceled = true;
                    resp = HttpExchangeTerminalResponseFactory.fromCancellation(req, requestStartMs, System.currentTimeMillis());
                    resp.bodySize = ex.getReceivedBytes();
                    resp.body = I18nUtil.getMessage(MessageKeys.DOWNLOAD_PROGRESS_CANCELLED_BODY,
                            FileSizeDisplayUtil.formatSize(ex.getReceivedBytes()));
                } catch (InterruptedIOException ex) {
                    log.warn("Request interrupted: {} {} - {}", req.method, req.url, ex.getMessage());
                    if (isCancelled()) {
                        userCanceled = true;
                        resp = HttpExchangeTerminalResponseFactory.fromCancellation(req, requestStartMs, System.currentTimeMillis());
                    } else {
                        resp = HttpExchangeTerminalResponseFactory.fromFailure(req, ex, requestStartMs, System.currentTimeMillis());
                        String userMessage = toInterruptedRequestUserMessage(ex, resp);
                        ConsolePanel.appendLog("[Error] " + userMessage, ConsolePanel.LogType.ERROR);
                        if (!executionState.isDisposed()) {
                            NotificationCenter.showError(userMessage);
                        }
                    }
                } catch (Exception ex) {
                    String logMessage = NetworkErrorMessageResolver.toLogMessage(ex);
                    if (ex instanceof java.net.UnknownHostException) {
                        log.error("Error executing HTTP request: {} {} - {}", req.method, req.url, logMessage);
                    } else {
                        log.error("Error executing HTTP request: {} {} - {}", req.method, req.url, logMessage, ex);
                    }
                    if (isCancelled()) {
                        userCanceled = true;
                        resp = HttpExchangeTerminalResponseFactory.fromCancellation(req, requestStartMs, System.currentTimeMillis());
                        return null;
                    }
                    resp = HttpExchangeTerminalResponseFactory.fromFailure(req, ex, requestStartMs, System.currentTimeMillis());
                    String userFriendlyMessage = resolveResponseErrorMessage(resp, ex);
                    ConsolePanel.appendLog("[Error] " + userFriendlyMessage, ConsolePanel.LogType.ERROR);
                    if (!executionState.isDisposed()) {
                        NotificationCenter.showError(userFriendlyMessage);
                    }
                }
                return null;
            }

            private String toInterruptedRequestUserMessage(InterruptedIOException ex, HttpResponse response) {
                String rawMessage = ex.getMessage();
                if (rawMessage != null && rawMessage.toLowerCase(Locale.ROOT).contains("timeout")) {
                    return I18nUtil.getMessage(MessageKeys.ERROR_NETWORK_TIMEOUT);
                }
                if (response != null && response.httpEventInfo != null
                        && response.httpEventInfo.getErrorMessage() != null
                        && !response.httpEventInfo.getErrorMessage().isBlank()) {
                    return response.httpEventInfo.getErrorMessage();
                }
                return NetworkErrorMessageResolver.toUserFriendlyMessage(ex);
            }

            private String resolveResponseErrorMessage(HttpResponse response, Exception ex) {
                if (response != null && response.httpEventInfo != null
                        && response.httpEventInfo.getErrorMessage() != null
                        && !response.httpEventInfo.getErrorMessage().isBlank()) {
                    return response.httpEventInfo.getErrorMessage();
                }
                return NetworkErrorMessageResolver.toUserFriendlyMessage(ex);
            }

            @Override
            protected void done() {
                try {
                    if (executionState.isDisposed()) {
                        executionState.clearAutoDetectedHttpSseOpen();
                        return;
                    }
                    boolean workerCanceled = isCancelled();
                    boolean missingResponseAfterCancel = resp == null && workerCanceled;
                    boolean keepSseView = (resp != null && resp.isSse)
                            || (workerCanceled && executionState.isAutoDetectedHttpSseOpen());
                    responsePanel.switchTabButtonHttpOrSse(keepSseView ? "sse" : "http");
                    resp = HttpRequestCancellationResponseSupport.resolveTerminalResponse(
                            req, resp, workerCanceled, requestStartMs, System.currentTimeMillis());
                    if (missingResponseAfterCancel && resp != null) {
                        userCanceled = true;
                    }
                    requestExecutionUiUpdater.updateUIForResponse(resp);
                    if (resp != null && userCanceled) {
                        responsePanel.setRequestDetails(req);
                        responsePanel.setResponseDetails(resp);
                    } else if (resp != null && !resp.isSse) {
                        requestResponseHandler.handleResponse(pipeline, req, resp);
                    } else if (resp != null) {
                        requestResponseHandler.recordExchange(req, resp);
                        responsePanel.setRequestDetails(req);
                        responsePanel.setResponseDetails(resp);
                        requestResponseHandler.saveHistory(req, resp, "SSE request");
                    }
                    if (!keepSseView) {
                        executionState.clearAutoDetectedHttpSseOpen();
                    }
                } finally {
                    requestExecutionUiUpdater.resetSendButton();
                    executionState.clearCurrentWorkerIf(this);
                }
            }
        };
    }
}
