package com.laker.postman.mcp.server;

import com.laker.postman.util.JsonUtil;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class McpToolSpecifications {
    static final List<String> TOOL_NAMES = List.of(
            "list_workspaces",
            "list_collections",
            "list_environments",
            "list_requests",
            "run_request",
            "run_collection"
    );

    private McpToolSpecifications() {
    }

    static List<McpServerFeatures.SyncToolSpecification> create(EasyPostmanMcpBackend backend) {
        return List.of(
                readOnlyTool(
                        "list_workspaces",
                        "List authorized EasyPostman workspaces",
                        "Lists authorized workspaces. Use a returned workspace ID with downstream tools.",
                        objectSchema(Map.of(), List.of()),
                        ignored -> backend.listWorkspaces(),
                        backend
                ),
                readOnlyTool(
                        "list_collections",
                        "List EasyPostman collections",
                        "Lists collections in an authorized workspace with stable IDs and request counts.",
                        objectSchema(Map.of("workspaceId", workspaceIdProperty()), List.of()),
                        backend::listCollections,
                        backend
                ),
                readOnlyTool(
                        "list_environments",
                        "List EasyPostman environments",
                        "Lists environment IDs, names and variable keys without exposing variable values.",
                        objectSchema(Map.of("workspaceId", workspaceIdProperty()), List.of()),
                        backend::listEnvironments,
                        backend
                ),
                readOnlyTool(
                        "list_requests",
                        "List EasyPostman requests",
                        "Finds saved HTTP requests. Use the returned request ID with run_request.",
                        objectSchema(
                                Map.of(
                                        "workspaceId", workspaceIdProperty(),
                                        "collectionId", stringProperty("Optional collection ID or exact name."),
                                        "query", stringProperty("Optional case-insensitive name, path, URL or method search."),
                                        "limit", integerProperty("Maximum results to return.", 1, 500)
                                ),
                                List.of()
                        ),
                        backend::listRequests,
                        backend
                ),
                actionTool(
                        "run_request",
                        "Run one EasyPostman request",
                        "Executes one saved HTTP request with inheritance, variables, scripts and tests. "
                                + "It can call an external API and may cause side effects.",
                        objectSchema(
                                Map.of(
                                        "workspaceId", workspaceIdProperty(),
                                        "requestId", stringProperty("Stable request ID returned by list_requests."),
                                        "environmentId", stringProperty(
                                                "Optional environment ID or exact name; defaults to the active environment."
                                        ),
                                        "environmentOverrides", environmentOverridesProperty()
                                ),
                                List.of("requestId")
                        ),
                        backend::runRequest,
                        backend
                ),
                actionTool(
                        "run_collection",
                        "Run an EasyPostman collection",
                        "Executes every saved HTTP request in a collection. Requests are serialized and may cause "
                                + "external side effects.",
                        objectSchema(
                                Map.of(
                                        "workspaceId", workspaceIdProperty(),
                                        "collectionId", stringProperty(
                                                "Stable collection ID returned by list_collections."
                                        ),
                                        "environmentId", stringProperty(
                                                "Optional environment ID or exact name; defaults to the active environment."
                                        ),
                                        "environmentOverrides", environmentOverridesProperty(),
                                        "iterations", integerProperty("Number of collection iterations.", 1, 100),
                                        "bail", booleanProperty("Stop after the first failed request or test.")
                                ),
                                List.of("collectionId")
                        ),
                        backend::runCollection,
                        backend
                )
        );
    }

    private static McpServerFeatures.SyncToolSpecification readOnlyTool(String name,
                                                                        String title,
                                                                        String description,
                                                                        Map<String, Object> schema,
                                                                        ToolHandler handler,
                                                                        EasyPostmanMcpBackend backend) {
        McpSchema.ToolAnnotations annotations = McpSchema.ToolAnnotations.builder()
                .title(title)
                .readOnlyHint(true)
                .destructiveHint(false)
                .idempotentHint(true)
                .openWorldHint(false)
                .build();
        return tool(name, title, description, schema, annotations, handler, backend);
    }

    private static McpServerFeatures.SyncToolSpecification actionTool(String name,
                                                                      String title,
                                                                      String description,
                                                                      Map<String, Object> schema,
                                                                      ToolHandler handler,
                                                                      EasyPostmanMcpBackend backend) {
        McpSchema.ToolAnnotations annotations = McpSchema.ToolAnnotations.builder()
                .title(title)
                .readOnlyHint(false)
                .destructiveHint(true)
                .idempotentHint(false)
                .openWorldHint(true)
                .build();
        return tool(name, title, description, schema, annotations, handler, backend);
    }

    private static McpServerFeatures.SyncToolSpecification tool(String name,
                                                                String title,
                                                                String description,
                                                                Map<String, Object> schema,
                                                                McpSchema.ToolAnnotations annotations,
                                                                ToolHandler handler,
                                                                EasyPostmanMcpBackend backend) {
        McpSchema.Tool tool = McpSchema.Tool.builder(name, schema)
                .title(title)
                .description(description)
                .annotations(annotations)
                .build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> invoke(handler, request.arguments(), backend))
                .build();
    }

    private static McpSchema.CallToolResult invoke(ToolHandler handler,
                                                    Map<String, Object> arguments,
                                                    EasyPostmanMcpBackend backend) {
        try {
            Map<String, Object> result = handler.handle(arguments == null ? Map.of() : arguments);
            return McpSchema.CallToolResult.builder()
                    .addTextContent(JsonUtil.toJsonPrettyStr(result))
                    .structuredContent(result)
                    .isError(false)
                    .build();
        } catch (Exception exception) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", backend.sanitizeError(exception));
            error.put("type", exception.getClass().getSimpleName());
            return McpSchema.CallToolResult.builder()
                    .addTextContent(JsonUtil.toJsonPrettyStr(error))
                    .structuredContent(error)
                    .isError(true)
                    .build();
        }
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> stringProperty(String description) {
        return Map.of("type", "string", "description", description, "minLength", 1);
    }

    private static Map<String, Object> workspaceIdProperty() {
        return stringProperty("Workspace ID returned by list_workspaces. Optional when a current or single "
                + "workspace can be selected.");
    }

    private static Map<String, Object> environmentOverridesProperty() {
        return Map.of(
                "type", "object",
                "description", "Optional one-time environment variable values. They are never saved.",
                "maxProperties", 200,
                "additionalProperties", Map.of("type", "string", "maxLength", 65536)
        );
    }

    private static Map<String, Object> integerProperty(String description, int minimum, int maximum) {
        return Map.of(
                "type", "integer",
                "description", description,
                "minimum", minimum,
                "maximum", maximum
        );
    }

    private static Map<String, Object> booleanProperty(String description) {
        return Map.of("type", "boolean", "description", description);
    }

    @FunctionalInterface
    private interface ToolHandler {
        Map<String, Object> handle(Map<String, Object> arguments) throws Exception;
    }
}
