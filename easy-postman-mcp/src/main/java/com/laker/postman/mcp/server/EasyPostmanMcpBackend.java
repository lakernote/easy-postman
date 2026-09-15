package com.laker.postman.mcp.server;

import java.util.Map;

/**
 * Host-neutral backend consumed by the MCP protocol module.
 * Implementations own workspace authorization, persistence and request execution.
 */
public interface EasyPostmanMcpBackend {

    Map<String, Object> listWorkspaces() throws Exception;

    Map<String, Object> listCollections(Map<String, Object> arguments) throws Exception;

    Map<String, Object> listEnvironments(Map<String, Object> arguments) throws Exception;

    Map<String, Object> listRequests(Map<String, Object> arguments) throws Exception;

    Map<String, Object> runRequest(Map<String, Object> arguments) throws Exception;

    Map<String, Object> runCollection(Map<String, Object> arguments) throws Exception;

    default String sanitizeError(Throwable failure) {
        if (failure == null) {
            return "unknown error";
        }
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
