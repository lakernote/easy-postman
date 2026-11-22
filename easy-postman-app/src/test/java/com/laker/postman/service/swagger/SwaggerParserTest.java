package com.laker.postman.service.swagger;

import com.laker.postman.http.request.PreparedRequestFactory;
import com.laker.postman.http.request.PreparedRequestFinalizer;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.model.Environment;
import com.laker.postman.collection.model.RequestGroup;
import com.laker.postman.request.model.RequestAuthTypes;
import com.laker.postman.request.model.RequestBodyTypes;
import com.laker.postman.request.model.HttpRequestItem;


import com.laker.postman.collection.model.CollectionParseResult;
import com.laker.postman.service.common.TreeNodeBuilder;
import com.laker.postman.service.variable.RequestExecutionContext;
import com.laker.postman.service.variable.RequestExecutionScope;
import org.testng.annotations.Test;

import javax.swing.tree.DefaultMutableTreeNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.laker.postman.request.model.RequestAuthTypes.AUTH_TYPE_BEARER;
import static com.laker.postman.request.model.RequestBodyTypes.BODY_TYPE_RAW;
import static org.testng.Assert.*;


/**
 * Swagger解析器测试
 */
public class SwaggerParserTest {

    @Test
    public void testParseSwagger2() {
        String swagger2Json = """
                {
                  "swagger": "2.0",
                  "info": {
                    "title": "Pet Store API",
                    "version": "1.0.0"
                  },
                  "host": "petstore.swagger.io",
                  "basePath": "/v2",
                  "schemes": ["https"],
                  "paths": {
                    "/pets": {
                      "get": {
                        "tags": ["Pets"],
                        "summary": "List all pets",
                        "operationId": "listPets",
                        "parameters": [
                          {
                            "name": "limit",
                            "in": "query",
                            "type": "integer",
                            "default": 10
                          }
                        ],
                        "responses": {
                          "200": {
                            "description": "Success"
                          }
                        }
                      },
                      "post": {
                        "tags": ["Pets"],
                        "summary": "Create a pet",
                        "operationId": "createPet",
                        "parameters": [
                          {
                            "name": "body",
                            "in": "body",
                            "schema": {
                              "type": "object",
                              "properties": {
                                "name": {
                                  "type": "string"
                                },
                                "age": {
                                  "type": "integer"
                                }
                              }
                            }
                          }
                        ],
                        "responses": {
                          "201": {
                            "description": "Created"
                          }
                        }
                      }
                    }
                  }
                }
                """;

        CollectionParseResult parseResult = SwaggerParser.parseSwagger(swagger2Json);
        assertNotNull(parseResult);

        DefaultMutableTreeNode collectionNode = TreeNodeBuilder.buildFromParseResult(parseResult);

        assertNotNull(collectionNode);
        Object[] userObject = (Object[]) collectionNode.getUserObject();
        assertEquals(userObject[0], "group");

        RequestGroup group = (RequestGroup) userObject[1];
        assertEquals(group.getName(), "Pet Store API (1.0.0)");

        // 应该有一个Pets分组
        assertEquals(collectionNode.getChildCount(), 1);
        DefaultMutableTreeNode petsGroup = (DefaultMutableTreeNode) collectionNode.getChildAt(0);
        Object[] petsUserObject = (Object[]) petsGroup.getUserObject();
        assertEquals(petsUserObject[0], "group");

        // Pets分组应该有2个请求
        assertEquals(petsGroup.getChildCount(), 2);

        // 验证第一个请求 (GET /pets)
        DefaultMutableTreeNode getRequest = (DefaultMutableTreeNode) petsGroup.getChildAt(0);
        Object[] getRequestUserObject = (Object[]) getRequest.getUserObject();
        assertEquals(getRequestUserObject[0], "request");

        HttpRequestItem getItem = (HttpRequestItem) getRequestUserObject[1];
        assertEquals(getItem.getName(), "List all pets");
        assertEquals(getItem.getMethod(), "GET");
        assertTrue(getItem.getUrl().contains("/pets"));
        assertNotNull(getItem.getParamsList());
        assertEquals(getItem.getParamsList().size(), 1);
        assertEquals(getItem.getParamsList().get(0).getKey(), "limit");

        // 验证第二个请求 (POST /pets)
        DefaultMutableTreeNode postRequest = (DefaultMutableTreeNode) petsGroup.getChildAt(1);
        Object[] postRequestUserObject = (Object[]) postRequest.getUserObject();
        assertEquals(postRequestUserObject[0], "request");

        HttpRequestItem postItem = (HttpRequestItem) postRequestUserObject[1];
        assertEquals(postItem.getName(), "Create a pet");
        assertEquals(postItem.getMethod(), "POST");
        assertTrue(postItem.getUrl().contains("/pets"));
        assertNotNull(postItem.getBody());
    }

    @Test
    void testParseOpenAPI3() {
        String openapi3Json = """
                {
                  "openapi": "3.0.0",
                  "info": {
                    "title": "User API",
                    "version": "2.0.0"
                  },
                  "servers": [
                    {
                      "url": "https://api.example.com/v1"
                    }
                  ],
                  "paths": {
                    "/users": {
                      "get": {
                        "tags": ["Users"],
                        "summary": "Get all users",
                        "parameters": [
                          {
                            "name": "page",
                            "in": "query",
                            "schema": {
                              "type": "integer",
                              "default": 1
                            }
                          }
                        ],
                        "responses": {
                          "200": {
                            "description": "Success"
                          }
                        }
                      },
                      "post": {
                        "tags": ["Users"],
                        "summary": "Create user",
                        "requestBody": {
                          "content": {
                            "application/json": {
                              "schema": {
                                "type": "object",
                                "properties": {
                                  "username": {
                                    "type": "string"
                                  },
                                  "email": {
                                    "type": "string",
                                    "format": "email"
                                  }
                                }
                              }
                            }
                          }
                        },
                        "responses": {
                          "201": {
                            "description": "Created"
                          }
                        }
                      }
                    }
                  }
                }
                """;

        CollectionParseResult parseResult = SwaggerParser.parseSwagger(openapi3Json);
        assertNotNull(parseResult);

        DefaultMutableTreeNode collectionNode = TreeNodeBuilder.buildFromParseResult(parseResult);

        assertNotNull(collectionNode);
        Object[] userObject = (Object[]) collectionNode.getUserObject();
        assertEquals(userObject[0], "group");

        RequestGroup group = (RequestGroup) userObject[1];
        assertEquals(group.getName(), "User API (2.0.0)");

        // 应该有一个Users分组
        assertEquals(collectionNode.getChildCount(), 1);
        DefaultMutableTreeNode usersGroup = (DefaultMutableTreeNode) collectionNode.getChildAt(0);

        // Users分组应该有2个请求
        assertEquals(usersGroup.getChildCount(), 2);

        // 验证第一个请求 (GET /users)
        DefaultMutableTreeNode getRequest = (DefaultMutableTreeNode) usersGroup.getChildAt(0);
        Object[] getRequestUserObject = (Object[]) getRequest.getUserObject();
        HttpRequestItem getItem = (HttpRequestItem) getRequestUserObject[1];
        assertEquals(getItem.getName(), "Get all users");
        assertEquals(getItem.getMethod(), "GET");
        // 验证 URL 使用了 {{baseUrl}} 变量引用
        assertTrue(getItem.getUrl().contains("{{baseUrl}}"), "URL 应该使用 {{baseUrl}} 变量");
        assertTrue(getItem.getUrl().contains("/users"));

        // 验证第二个请求 (POST /users)
        DefaultMutableTreeNode postRequest = (DefaultMutableTreeNode) usersGroup.getChildAt(1);
        Object[] postRequestUserObject = (Object[]) postRequest.getUserObject();
        HttpRequestItem postItem = (HttpRequestItem) postRequestUserObject[1];
        assertEquals(postItem.getName(), "Create user");
        assertEquals(postItem.getMethod(), "POST");
        assertNotNull(postItem.getBody());
    }

    @Test
    void testPathParametersBecomePathVariables() {
        String openapi3Json = """
                {
                  "openapi": "3.0.0",
                  "info": {"title": "Pets", "version": "1.0.0"},
                  "paths": {
                    "/pets/{petId}": {
                      "get": {
                        "summary": "Get pet",
                        "parameters": [{"name": "petId", "in": "path", "required": true, "example": "42"}]
                      }
                    }
                  }
                }
                """;
        String swagger2Json = """
                {
                  "swagger": "2.0",
                  "info": {"title": "Pets", "version": "1.0.0"},
                  "paths": {
                    "/pets/{petId}": {
                      "get": {
                        "summary": "Get pet",
                        "parameters": [{"name": "petId", "in": "path", "required": true, "type": "string", "default": "42"}]
                      }
                    }
                  }
                }
                """;

        for (String spec : List.of(openapi3Json, swagger2Json)) {
            DefaultMutableTreeNode root = TreeNodeBuilder.buildFromParseResult(SwaggerParser.parseSwagger(spec));
            DefaultMutableTreeNode request = (DefaultMutableTreeNode) root.getChildAt(0).getChildAt(0);
            HttpRequestItem item = (HttpRequestItem) ((Object[]) request.getUserObject())[1];

            assertTrue(item.getUrl().endsWith("/pets/:petId"), item.getUrl());
            assertEquals(item.getPathVariablesList().size(), 1);
            assertEquals(item.getPathVariablesList().get(0).getKey(), "petId");
            assertEquals(item.getPathVariablesList().get(0).getValue(), "42");
        }
    }

    @Test
    void testPathParameterBoundariesArePreservedBeforeSending() {
        String swagger2Spec = """
                {
                  "swagger": "2.0",
                  "info": {"title": "Files", "version": "1.0"},
                  "host": "api.example.com",
                  "schemes": ["https"],
                  "paths": {
                    "/files/{name}-latest/{file.name}.json": {
                      "get": {
                        "parameters": [
                          {"name": "name", "in": "path", "required": true, "type": "string", "x-example": "report"},
                          {"name": "file.name", "in": "path", "required": true, "type": "string", "x-example": "photo"}
                        ],
                        "responses": {"200": {"description": "OK"}}
                      }
                    }
                  }
                }
                """;
        String openApi3Spec = """
                {
                  "openapi": "3.0.3",
                  "info": {"title": "Files", "version": "1.0"},
                  "servers": [{"url": "https://api.example.com"}],
                  "paths": {
                    "/files/{name}-latest/{file.name}.json": {
                      "get": {
                        "parameters": [
                          {"name": "name", "in": "path", "required": true,
                           "schema": {"type": "string"}, "example": "report"},
                          {"name": "file.name", "in": "path", "required": true,
                           "schema": {"type": "string"}, "example": "photo"}
                        ],
                        "responses": {"200": {"description": "OK"}}
                      }
                    }
                  }
                }
                """;

        String expectedUrl = "https://api.example.com/files/report-latest/photo.json";
        assertEquals(finalizeImportedUrl(swagger2Spec), expectedUrl, "Swagger 2.0");
        assertEquals(finalizeImportedUrl(openApi3Spec), expectedUrl, "OpenAPI 3.0");
    }

    @Test
    void testOpenApi3PathParametersFallBackToSchemaExamples() {
        String spec = """
                {
                  "openapi": "3.0.3",
                  "info": {"title": "Files", "version": "1.0"},
                  "servers": [{"url": "https://api.example.com"}],
                  "components": {"schemas": {"FileId": {"type": "string", "example": "photo"}}},
                  "paths": {
                    "/files/{folderId}/{fileId}/{format}/{revision}": {
                      "get": {
                        "parameters": [
                          {"name": "folderId", "in": "path", "required": true,
                           "schema": {"type": "integer", "example": 42}},
                          {"name": "fileId", "in": "path", "required": true,
                           "schema": {"$ref": "#/components/schemas/FileId"}},
                          {"name": "format", "in": "path", "required": true,
                           "schema": {"type": "string", "example": "png"}, "example": "jpeg"},
                          {"name": "revision", "in": "path", "required": true,
                           "schema": {"type": "integer", "default": 0, "example": 1}, "example": 2}
                        ],
                        "responses": {"200": {"description": "OK"}}
                      }
                    }
                  }
                }
                """;

        assertEquals(finalizeImportedUrl(spec), "https://api.example.com/files/42/photo/jpeg/0");
    }

    private static String finalizeImportedUrl(String spec) {
        CollectionParseResult result = SwaggerParser.parseSwagger(spec);
        assertNotNull(result);
        HttpRequestItem item = result.getChildren().get(0).getChildren().get(0).asRequest();
        RequestExecutionScope previousScope = RequestExecutionContext.captureCurrentScope();
        RequestExecutionContext.setCurrentScope(
                RequestExecutionScope.fromVariables(result.getEnvironments().get(0).getVariableList()));
        try {
            PreparedRequest request = PreparedRequestFactory.buildWithoutInheritance(item);
            PreparedRequestFinalizer.finalizeForSend(request);
            return request.url;
        } finally {
            if (previousScope == null) {
                RequestExecutionContext.clearCurrentScope();
            } else {
                RequestExecutionContext.setCurrentScope(previousScope);
            }
        }
    }

    @Test
    void testParseInvalidSwagger() {
        String invalidJson = """
                {
                  "invalid": "format"
                }
                """;

        CollectionParseResult parseResult = SwaggerParser.parseSwagger(invalidJson);
        assertNull(parseResult);
    }

    @Test
    void testParseSwagger2WithAuth() {
        String swagger2Json = """
                {
                  "swagger": "2.0",
                  "info": {
                    "title": "Secure API",
                    "version": "1.0.0"
                  },
                  "host": "api.example.com",
                  "basePath": "/v1",
                  "securityDefinitions": {
                    "bearerAuth": {
                      "type": "apiKey",
                      "name": "Authorization",
                      "in": "header"
                    }
                  },
                  "paths": {
                    "/protected": {
                      "get": {
                        "tags": ["Secure"],
                        "summary": "Protected endpoint",
                        "security": [
                          {
                            "bearerAuth": []
                          }
                        ],
                        "responses": {
                          "200": {
                            "description": "Success"
                          }
                        }
                      }
                    }
                  }
                }
                """;

        CollectionParseResult parseResult = SwaggerParser.parseSwagger(swagger2Json);
        assertNotNull(parseResult);

        DefaultMutableTreeNode collectionNode = TreeNodeBuilder.buildFromParseResult(parseResult);
        assertNotNull(collectionNode);

        DefaultMutableTreeNode secureGroup = (DefaultMutableTreeNode) collectionNode.getChildAt(0);
        DefaultMutableTreeNode getRequest = (DefaultMutableTreeNode) secureGroup.getChildAt(0);
        Object[] requestUserObject = (Object[]) getRequest.getUserObject();
        HttpRequestItem item = (HttpRequestItem) requestUserObject[1];

        // 应该设置了Bearer认证
        assertEquals(item.getAuthType(), AUTH_TYPE_BEARER);
    }

    /**
     * 测试解析服务并保存到环境
     */
    @Test
    void testParseServersOpenAPI3() {
        // 从resources目录读取zfile.openapi.json文件
        String openapiJson;
        try (var stream = getClass().getResourceAsStream("/zfile.openapi.json")) {
            if (stream == null) {
                throw new RuntimeException("无法找到资源文件: zfile.openapi.json");
            }
            openapiJson = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("读取zfile.openapi.json文件时发生错误", e);
        }

        CollectionParseResult parseResult = SwaggerParser.parseSwagger(openapiJson);
        assertNotNull(parseResult);

        DefaultMutableTreeNode collectionNode = TreeNodeBuilder.buildFromParseResult(parseResult);
        assertNotNull(collectionNode);

        // 验证解析结果中创建了环境，并且每个环境都包含 baseUrl 变量
        List<Environment> environments = parseResult.getEnvironments();
        assertNotNull(environments);
        assertFalse(environments.isEmpty(), "Expected environments parsed from OpenAPI servers");
        for (Environment env : environments) {
            assertNotNull(env.getName());
            assertFalse(env.getName().isBlank());
            assertNotNull(env.getVariableList());
            assertFalse(env.getVariableList().isEmpty());
            assertTrue(
                    env.getVariableList().stream().anyMatch(v -> "baseUrl".equals(v.getKey())),
                    "Environment should contain baseUrl variable"
            );
        }
    }

    @Test
    void testOpenAPI3WithEnvironments() {
        String openapi3Json = """
                {
                  "openapi": "3.0.1",
                  "info": {
                    "title": "Test API",
                    "version": "1.0.0"
                  },
                  "servers": [
                    {
                      "url": "http://localhost:8080",
                      "description": "本地环境"
                    },
                    {
                      "url": "http://dev.example.com",
                      "description": "开发环境"
                    },
                    {
                      "url": "http://prod.example.com",
                      "description": "生产环境"
                    }
                  ],
                  "paths": {
                    "/users": {
                      "get": {
                        "tags": ["Users"],
                        "summary": "Get users",
                        "responses": {
                          "200": {
                            "description": "Success"
                          }
                        }
                      }
                    }
                  }
                }
                """;

        CollectionParseResult parseResult = SwaggerParser.parseSwagger(openapi3Json);
        assertNotNull(parseResult);

        // 验证解析出了3个环境
        assertEquals(parseResult.getEnvironments().size(), 3);

        // 验证第一个环境
        Environment env1 = parseResult.getEnvironments().get(0);
        assertEquals(env1.getName(), "Test API - 本地环境");
        assertEquals(env1.getVariableList().size(), 1);
        assertEquals(env1.getVariableList().get(0).getKey(), "baseUrl");
        assertEquals(env1.getVariableList().get(0).getValue(), "http://localhost:8080");

        // 验证第二个环境
        Environment env2 = parseResult.getEnvironments().get(1);
        assertEquals(env2.getName(), "Test API - 开发环境");
        assertEquals(env2.getVariableList().get(0).getValue(), "http://dev.example.com");

        // 验证第三个环境
        Environment env3 = parseResult.getEnvironments().get(2);
        assertEquals(env3.getName(), "Test API - 生产环境");
        assertEquals(env3.getVariableList().get(0).getValue(), "http://prod.example.com");

        // 验证请求 URL 使用了 {{baseUrl}} 变量引用
        DefaultMutableTreeNode collectionNode = TreeNodeBuilder.buildFromParseResult(parseResult);
        DefaultMutableTreeNode usersGroup = (DefaultMutableTreeNode) collectionNode.getChildAt(0);
        DefaultMutableTreeNode getUserRequest = (DefaultMutableTreeNode) usersGroup.getChildAt(0);
        Object[] requestUserObject = (Object[]) getUserRequest.getUserObject();
        HttpRequestItem requestItem = (HttpRequestItem) requestUserObject[1];

        // 验证 URL 格式为 {{baseUrl}}/users
        assertEquals(requestItem.getUrl(), "{{baseUrl}}/users");
        System.out.println("✅ URL 使用变量引用: " + requestItem.getUrl());
    }

    @Test
    void testOpenAPI3ServerVariablesUseDefaultValuesInBaseUrl() {
        String openapi3Json = """
                {
                  "openapi": "3.0.1",
                  "info": {"title": "Variable Server API", "version": "1.0"},
                  "servers": [{
                    "url": "https://{region}.example.com/{basePath}",
                    "variables": {
                      "region": {"default": "api"},
                      "basePath": {"default": "v1"}
                    }
                  }],
                  "paths": {"/items": {"get": {"responses": {"200": {"description": "ok"}}}}}
                }
                """;

        CollectionParseResult parseResult = SwaggerParser.parseSwagger(openapi3Json);

        assertNotNull(parseResult);
        Environment environment = parseResult.getEnvironments().get(0);
        assertEquals(environment.getVariableList().get(0).getKey(), "baseUrl");
        assertEquals(environment.getVariableList().get(0).getValue(), "https://api.example.com/v1");
    }

    @Test
    void testParseOpenAPI3RefBodyAndScripts() {
        String openapi3Json = """
                {
                  "openapi": "3.0.0",
                  "info": {
                    "title": "ApiPost Export",
                    "version": "1.0.0"
                  },
                  "paths": {
                    "/users": {
                      "post": {
                        "summary": "Create user",
                        "x-apipost-pre-script": "pm.environment.set('token', '123');",
                        "requestBody": {
                          "content": {
                            "application/json": {
                              "schema": {
                                "$ref": "#/components/schemas/CreateUserRequest"
                              }
                            }
                          }
                        },
                        "responses": {
                          "200": {
                            "description": "ok"
                          }
                        }
                      }
                    }
                  },
                  "components": {
                    "schemas": {
                      "CreateUserRequest": {
                        "type": "object",
                        "properties": {
                          "name": {
                            "type": "string",
                            "example": "Alice"
                          },
                          "age": {
                            "type": "integer",
                            "example": 18
                          }
                        }
                      }
                    }
                  }
                }
                """;

        CollectionParseResult parseResult = SwaggerParser.parseSwagger(openapi3Json);
        assertNotNull(parseResult);

        DefaultMutableTreeNode collectionNode = TreeNodeBuilder.buildFromParseResult(parseResult);
        DefaultMutableTreeNode groupNode = (DefaultMutableTreeNode) collectionNode.getChildAt(0);
        DefaultMutableTreeNode requestNode = (DefaultMutableTreeNode) groupNode.getChildAt(0);
        HttpRequestItem request = (HttpRequestItem) ((Object[]) requestNode.getUserObject())[1];

        assertEquals(request.getBodyType(), BODY_TYPE_RAW);
        assertTrue(request.getBody().contains("\"Alice\""));
        assertTrue(request.getBody().contains("\"age\":18"));
        assertEquals(request.getPrescript(), "pm.environment.set('token', '123');");
        assertTrue(request.getHeadersList().stream().anyMatch(h -> "Content-Type".equalsIgnoreCase(h.getKey())
                && "application/json".equals(h.getValue())));
    }

    @Test
    void testParseOpenAPI3SelfReferencingRequestBody() {
        String openapi3Json = """
                {
                  "openapi": "3.0.1",
                  "info": {"title": "Recursive API", "version": "1.0"},
                  "paths": {
                    "/items": {
                      "post": {
                        "requestBody": {
                          "content": {
                            "application/json": {
                              "schema": {"$ref": "#/components/schemas/Item"}
                            }
                          }
                        },
                        "responses": {"200": {"description": "ok"}}
                      }
                    }
                  },
                  "components": {
                    "schemas": {
                      "Item": {
                        "type": "object",
                        "properties": {
                          "name": {"type": "string"},
                          "child": {"$ref": "#/components/schemas/Item"},
                          "children": {
                            "type": "array",
                            "items": {"$ref": "#/components/schemas/Item"}
                          }
                        }
                      }
                    }
                  }
                }
                """;

        CollectionParseResult parseResult = SwaggerParser.parseSwagger(openapi3Json);

        assertNotNull(parseResult);
        DefaultMutableTreeNode collectionNode = TreeNodeBuilder.buildFromParseResult(parseResult);
        DefaultMutableTreeNode groupNode = (DefaultMutableTreeNode) collectionNode.getChildAt(0);
        DefaultMutableTreeNode requestNode = (DefaultMutableTreeNode) groupNode.getChildAt(0);
        HttpRequestItem request = (HttpRequestItem) ((Object[]) requestNode.getUserObject())[1];
        assertEquals(request.getBody(), "{\"name\":\"string\",\"child\":{},\"children\":[{}]}");
    }

    @Test
    void testParseOpenAPI3IndirectlySelfReferencingRequestBody() {
        String openapi3Json = """
                {
                  "openapi": "3.0.1",
                  "info": {"title": "Mutually Recursive API", "version": "1.0"},
                  "paths": {
                    "/items": {
                      "post": {
                        "requestBody": {
                          "content": {
                            "application/json": {
                              "schema": {"$ref": "#/components/schemas/A"}
                            }
                          }
                        },
                        "responses": {"200": {"description": "ok"}}
                      }
                    }
                  },
                  "components": {
                    "schemas": {
                      "A": {
                        "type": "object",
                        "properties": {"b": {"$ref": "#/components/schemas/B"}}
                      },
                      "B": {
                        "type": "object",
                        "properties": {"a": {"$ref": "#/components/schemas/A"}}
                      }
                    }
                  }
                }
                """;

        CollectionParseResult parseResult = SwaggerParser.parseSwagger(openapi3Json);

        assertNotNull(parseResult);
        DefaultMutableTreeNode collectionNode = TreeNodeBuilder.buildFromParseResult(parseResult);
        DefaultMutableTreeNode groupNode = (DefaultMutableTreeNode) collectionNode.getChildAt(0);
        DefaultMutableTreeNode requestNode = (DefaultMutableTreeNode) groupNode.getChildAt(0);
        HttpRequestItem request = (HttpRequestItem) ((Object[]) requestNode.getUserObject())[1];
        assertEquals(request.getBody(), "{\"b\":{\"a\":{}}}");
    }

    @Test
    void testParseOpenAPI3GlobalSecurity() {
        String openapi3Json = """
                {
                  "openapi": "3.0.1",
                  "info": {"title": "Secure API", "version": "1.0"},
                  "security": [{"bearerAuth": []}],
                  "components": {
                    "securitySchemes": {
                      "bearerAuth": {"type": "http", "scheme": "bearer"}
                    }
                  },
                  "paths": {
                    "/items": {
                      "get": {"responses": {"200": {"description": "ok"}}}
                    }
                  }
                }
                """;

        CollectionParseResult parseResult = SwaggerParser.parseSwagger(openapi3Json);

        assertNotNull(parseResult);
        DefaultMutableTreeNode collectionNode = TreeNodeBuilder.buildFromParseResult(parseResult);
        DefaultMutableTreeNode groupNode = (DefaultMutableTreeNode) collectionNode.getChildAt(0);
        DefaultMutableTreeNode requestNode = (DefaultMutableTreeNode) groupNode.getChildAt(0);
        HttpRequestItem request = (HttpRequestItem) ((Object[]) requestNode.getUserObject())[1];
        assertEquals(request.getAuthType(), AUTH_TYPE_BEARER);
    }

    @Test
    void testParseSwagger2RefBodyAndScripts() {
        String swagger2Json = """
                {
                  "swagger": "2.0",
                  "info": {
                    "title": "ApiPost Swagger",
                    "version": "1.0.0"
                  },
                  "paths": {
                    "/orders": {
                      "post": {
                        "summary": "Create order",
                        "x-apipost-pre-script": "pm.environment.set('orderId', 'demo');",
                        "consumes": ["application/json"],
                        "parameters": [
                          {
                            "name": "body",
                            "in": "body",
                            "schema": {
                              "$ref": "#/definitions/CreateOrderRequest"
                            }
                          }
                        ],
                        "responses": {
                          "200": {
                            "description": "ok"
                          }
                        }
                      }
                    }
                  },
                  "definitions": {
                    "CreateOrderRequest": {
                      "type": "object",
                      "properties": {
                        "id": {
                          "type": "string",
                          "example": "O-1"
                        },
                        "amount": {
                          "type": "number",
                          "example": 10.5
                        }
                      }
                    }
                  }
                }
                """;

        CollectionParseResult parseResult = SwaggerParser.parseSwagger(swagger2Json);
        assertNotNull(parseResult);

        DefaultMutableTreeNode collectionNode = TreeNodeBuilder.buildFromParseResult(parseResult);
        DefaultMutableTreeNode groupNode = (DefaultMutableTreeNode) collectionNode.getChildAt(0);
        DefaultMutableTreeNode requestNode = (DefaultMutableTreeNode) groupNode.getChildAt(0);
        HttpRequestItem request = (HttpRequestItem) ((Object[]) requestNode.getUserObject())[1];

        assertEquals(request.getBodyType(), BODY_TYPE_RAW);
        assertTrue(request.getBody().contains("\"O-1\""));
        assertTrue(request.getBody().contains("\"amount\":10.5"));
        assertEquals(request.getPrescript(), "pm.environment.set('orderId', 'demo');");
        assertTrue(request.getHeadersList().stream().anyMatch(h -> "Content-Type".equalsIgnoreCase(h.getKey())
                && "application/json".equals(h.getValue())));
    }

    @Test
    void testParseOpenAPI3ComposedRequestBody() {
        String openapi3Json = """
                {
                  "openapi": "3.0.0",
                  "info": {
                    "title": "Composed API",
                    "version": "1.0.0"
                  },
                  "paths": {
                    "/pets": {
                      "post": {
                        "tags": ["Pets"],
                        "summary": "Create pet",
                        "requestBody": {
                          "content": {
                            "application/json": {
                              "schema": {
                                "oneOf": [
                                  {
                                    "type": "object",
                                    "properties": {
                                      "name": {
                                        "type": "string",
                                        "example": "Bella"
                                      },
                                      "age": {
                                        "type": "integer",
                                        "example": 2
                                      }
                                    }
                                  }
                                ]
                              }
                            }
                          }
                        },
                        "responses": {
                          "200": {
                            "description": "ok"
                          }
                        }
                      }
                    }
                  }
                }
                """;

        CollectionParseResult parseResult = SwaggerParser.parseSwagger(openapi3Json);
        assertNotNull(parseResult);

        DefaultMutableTreeNode collectionNode = TreeNodeBuilder.buildFromParseResult(parseResult);
        DefaultMutableTreeNode groupNode = (DefaultMutableTreeNode) collectionNode.getChildAt(0);
        DefaultMutableTreeNode requestNode = (DefaultMutableTreeNode) groupNode.getChildAt(0);
        HttpRequestItem request = (HttpRequestItem) ((Object[]) requestNode.getUserObject())[1];

        assertEquals(request.getBodyType(), BODY_TYPE_RAW);
        assertTrue(request.getBody().contains("\"name\":\"Bella\""));
        assertTrue(request.getBody().contains("\"age\":2"));
        assertFalse("\"string\"".equals(request.getBody()));
    }

    @Test
    void testParseOpenAPI3SecuritySchemeRefAndParameterOverride() {
        String openapi3Json = """
                {
                  "openapi": "3.0.0",
                  "info": {
                    "title": "Secure API",
                    "version": "1.0.0"
                  },
                  "components": {
                    "securitySchemes": {
                      "BearerAuth": {
                        "$ref": "#/components/securitySchemes/BearerBase"
                      },
                      "BearerBase": {
                        "type": "http",
                        "scheme": "bearer"
                      }
                    }
                  },
                  "paths": {
                    "/secure": {
                      "parameters": [
                        {
                          "name": "X-Trace-Id",
                          "in": "header",
                          "schema": {
                            "type": "string",
                            "default": "path-level"
                          }
                        }
                      ],
                      "get": {
                        "tags": ["Secure"],
                        "summary": "Get secure data",
                        "parameters": [
                          {
                            "name": "X-Trace-Id",
                            "in": "header",
                            "schema": {
                              "type": "string",
                              "default": "operation-level"
                            }
                          }
                        ],
                        "security": [
                          {
                            "BearerAuth": []
                          }
                        ],
                        "responses": {
                          "200": {
                            "description": "ok"
                          }
                        }
                      }
                    }
                  }
                }
                """;

        CollectionParseResult parseResult = SwaggerParser.parseSwagger(openapi3Json);
        assertNotNull(parseResult);

        DefaultMutableTreeNode collectionNode = TreeNodeBuilder.buildFromParseResult(parseResult);
        DefaultMutableTreeNode groupNode = (DefaultMutableTreeNode) collectionNode.getChildAt(0);
        DefaultMutableTreeNode requestNode = (DefaultMutableTreeNode) groupNode.getChildAt(0);
        HttpRequestItem request = (HttpRequestItem) ((Object[]) requestNode.getUserObject())[1];

        assertEquals(request.getAuthType(), AUTH_TYPE_BEARER);
        long traceHeaderCount = request.getHeadersList().stream()
                .filter(h -> "X-Trace-Id".equalsIgnoreCase(h.getKey()))
                .count();
        assertEquals(traceHeaderCount, 1L);
        assertEquals(
                request.getHeadersList().stream()
                        .filter(h -> "X-Trace-Id".equalsIgnoreCase(h.getKey()))
                        .findFirst()
                        .orElseThrow()
                        .getValue(),
                "operation-level"
        );
    }

    @Test
    void testParseSwagger2SecurityDefinitionRef() {
        String swagger2Json = """
                {
                  "swagger": "2.0",
                  "info": {
                    "title": "Secure Swagger API",
                    "version": "1.0.0"
                  },
                  "securityDefinitions": {
                    "bearerAuth": {
                      "$ref": "#/securityDefinitions/bearerBase"
                    },
                    "bearerBase": {
                      "type": "apiKey",
                      "name": "Authorization",
                      "in": "header"
                    }
                  },
                  "paths": {
                    "/protected": {
                      "get": {
                        "tags": ["Secure"],
                        "summary": "Protected endpoint",
                        "security": [
                          {
                            "bearerAuth": []
                          }
                        ],
                        "responses": {
                          "200": {
                            "description": "Success"
                          }
                        }
                      }
                    }
                  }
                }
                """;

        CollectionParseResult parseResult = SwaggerParser.parseSwagger(swagger2Json);
        assertNotNull(parseResult);

        DefaultMutableTreeNode collectionNode = TreeNodeBuilder.buildFromParseResult(parseResult);
        DefaultMutableTreeNode groupNode = (DefaultMutableTreeNode) collectionNode.getChildAt(0);
        DefaultMutableTreeNode requestNode = (DefaultMutableTreeNode) groupNode.getChildAt(0);
        HttpRequestItem request = (HttpRequestItem) ((Object[]) requestNode.getUserObject())[1];

        assertEquals(request.getAuthType(), AUTH_TYPE_BEARER);
    }
}
