package com.laker.postman.service.postman;

import com.laker.postman.collection.importer.postman.PostmanCollectionParser;
import com.laker.postman.collection.model.CollectionParseResult;
import com.laker.postman.request.model.AuthApiKeyPlacement;
import com.laker.postman.request.model.AuthType;
import com.laker.postman.request.model.HttpRequestItem;
import com.laker.postman.request.model.RequestBodyTypes;
import org.testng.annotations.Test;

import static com.laker.postman.request.model.RequestBodyTypes.BODY_TYPE_FORM_DATA;
import static com.laker.postman.request.model.RequestBodyTypes.BODY_TYPE_RAW;
import static org.testng.Assert.*;

/**
 * PostmanCollectionParser 单元测试
 * 测试完整的 Postman Collection 解析功能
 */
public class PostmanCollectionParserTest {

    @Test
    public void testParsePostmanCollection_BasicCollection() {
        // 准备测试数据 - 基本的 Collection
        String json = """
                {
                    "info": {
                        "name": "Test Collection",
                        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
                    },
                    "item": [
                        {
                            "name": "Get Users",
                            "request": {
                                "method": "GET",
                                "url": "https://api.example.com/users"
                            }
                        }
                    ]
                }
                """;

        // 执行解析
        CollectionParseResult result = PostmanCollectionParser.parsePostmanCollection(json);

        // 验证结果
        assertNotNull(result);
        assertNotNull(result.getGroup());
        assertEquals(result.getGroup().getName(), "Test Collection");
        assertEquals(result.getChildren().size(), 1);
    }

    @Test
    public void testParsePostmanCollection_WithFolder() {
        // 准备测试数据 - 包含文件夹的 Collection
        String json = """
                {
                    "info": {
                        "name": "API Collection",
                        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
                    },
                    "item": [
                        {
                            "name": "User API",
                            "item": [
                                {
                                    "name": "Get User",
                                    "request": {
                                        "method": "GET",
                                        "url": "https://api.example.com/user/1"
                                    }
                                },
                                {
                                    "name": "Create User",
                                    "request": {
                                        "method": "POST",
                                        "url": "https://api.example.com/user"
                                    }
                                }
                            ]
                        }
                    ]
                }
                """;

        // 执行解析
        CollectionParseResult result = PostmanCollectionParser.parsePostmanCollection(json);

        // 验证结果
        assertNotNull(result);
        assertEquals(result.getChildren().size(), 1);

        // 验证文件夹
        var folderNode = result.getChildren().get(0);
        assertTrue(folderNode.isGroup());
        assertEquals(folderNode.asGroup().getName(), "User API");
        assertEquals(folderNode.getChildren().size(), 2);
    }

    @Test
    public void testParsePostmanCollection_WithDigestAuth() {
        String json = """
                {
                    "info": {
                        "name": "Digest Collection"
                    },
                    "item": [
                        {
                            "name": "Digest Request",
                            "request": {
                                "method": "GET",
                                "url": "https://api.example.com/digest",
                                "auth": {
                                    "type": "digest",
                                    "digest": [
                                        {"key": "username", "value": "digest-user"},
                                        {"key": "password", "value": "digest-pass"}
                                    ]
                                }
                            }
                        }
                    ]
                }
                """;

        CollectionParseResult result = PostmanCollectionParser.parsePostmanCollection(json);

        assertNotNull(result);
        assertEquals(result.getChildren().size(), 1);
        var request = result.getChildren().get(0).asRequest();
        assertEquals(request.getAuthType(), AuthType.DIGEST.getConstant());
        assertEquals(request.getAuthUsername(), "digest-user");
        assertEquals(request.getAuthPassword(), "digest-pass");
    }

    @Test
    public void testParsePostmanCollection_WithApiKeyAuth() {
        String json = """
                {
                    "info": {
                        "name": "API Key Collection"
                    },
                    "item": [
                        {
                            "name": "API Key Request",
                            "request": {
                                "method": "GET",
                                "url": "https://api.example.com/keyed",
                                "auth": {
                                    "type": "apikey",
                                    "apikey": [
                                        {"key": "key", "value": "X-API-Key"},
                                        {"key": "value", "value": "secret"},
                                        {"key": "in", "value": "header"}
                                    ]
                                }
                            }
                        }
                    ]
                }
                """;

        CollectionParseResult result = PostmanCollectionParser.parsePostmanCollection(json);

        assertNotNull(result);
        assertEquals(result.getChildren().size(), 1);
        var request = result.getChildren().get(0).asRequest();
        assertEquals(request.getAuthType(), AuthType.API_KEY.getConstant());
        assertEquals(request.getAuthApiKeyName(), "X-API-Key");
        assertEquals(request.getAuthApiKeyValue(), "secret");
        assertEquals(request.getAuthApiKeyPlacement(), AuthApiKeyPlacement.HEADER.getConstant());
    }

    @Test
    public void testParsePostmanCollection_ComplexStructure() {
        // 准备测试数据 - 复杂的嵌套结构
        String json = """
                {
                    "info": {
                        "name": "Complex Collection"
                    },
                    "item": [
                        {
                            "name": "Auth",
                            "item": [
                                {
                                    "name": "Login",
                                    "request": {
                                        "method": "POST",
                                        "url": "https://api.example.com/login",
                                        "auth": {
                                            "type": "basic",
                                            "basic": [
                                                {"key": "username", "value": "admin"},
                                                {"key": "password", "value": "secret"}
                                            ]
                                        }
                                    }
                                }
                            ]
                        },
                        {
                            "name": "Direct Request",
                            "request": {
                                "method": "GET",
                                "url": "https://api.example.com/public"
                            }
                        }
                    ]
                }
                """;

        // 执行解析
        CollectionParseResult result = PostmanCollectionParser.parsePostmanCollection(json);

        // 验证结果
        assertNotNull(result);
        assertEquals(result.getChildren().size(), 2);

        // 验证第一个子节点（文件夹）
        var authFolder = result.getChildren().get(0);
        assertTrue(authFolder.isGroup());
        assertEquals(authFolder.asGroup().getName(), "Auth");
        assertEquals(authFolder.getChildren().size(), 1);

        // 验证第二个子节点（请求）
        var directRequest = result.getChildren().get(1);
        assertTrue(directRequest.isRequest());
    }

    @Test
    public void testParsePostmanCollection_WithQueryParams() {
        // 准备测试数据 - 带查询参数的请求
        String json = """
                {
                    "info": {
                        "name": "Query Test"
                    },
                    "item": [
                        {
                            "name": "Search API",
                            "request": {
                                "method": "GET",
                                "url": {
                                    "raw": "https://api.example.com/search?q=test&limit=10",
                                    "query": [
                                        {"key": "q", "value": "test", "disabled": false},
                                        {"key": "limit", "value": "10", "disabled": false}
                                    ]
                                }
                            }
                        }
                    ]
                }
                """;

        // 执行解析
        CollectionParseResult result = PostmanCollectionParser.parsePostmanCollection(json);

        // 验证结果
        assertNotNull(result);
        assertEquals(result.getChildren().size(), 1);

        var requestNode = result.getChildren().get(0);
        assertTrue(requestNode.isRequest());

        // 验证 HttpRequestItem
        var req = requestNode.asRequest();
        assertEquals(req.getName(), "Search API");
        assertEquals(req.getMethod(), "GET");
        assertEquals(req.getUrl(), "https://api.example.com/search?q=test&limit=10");
        assertNotNull(req.getParamsList());
        assertEquals(req.getParamsList().size(), 2);
    }

    @Test
    public void testParsePostmanCollection_WithPathVariables() {
        String json = """
                {
                    "info": {
                        "name": "Path Variable Test"
                    },
                    "item": [
                        {
                            "name": "Get User",
                            "request": {
                                "method": "GET",
                                "url": {
                                    "raw": "https://api.example.com/users/:userId/orders/:orderId",
                                    "host": ["api", "example", "com"],
                                    "path": ["users", ":userId", "orders", ":orderId"],
                                    "variable": [
                                        {"key": "userId", "value": "42", "description": "User id"},
                                        {"key": "orderId", "value": "A-100"}
                                    ]
                                }
                            }
                        }
                    ]
                }
                """;

        CollectionParseResult result = PostmanCollectionParser.parsePostmanCollection(json);

        assertNotNull(result);
        HttpRequestItem req = result.getChildren().get(0).asRequest();
        assertEquals(req.getUrl(), "https://api.example.com/users/:userId/orders/:orderId");
        assertEquals(req.getPathVariablesList().size(), 2);
        assertEquals(req.getPathVariablesList().get(0).getKey(), "userId");
        assertEquals(req.getPathVariablesList().get(0).getValue(), "42");
        assertEquals(req.getPathVariablesList().get(0).getDescription(), "User id");
        assertEquals(req.getPathVariablesList().get(1).getKey(), "orderId");
    }

    @Test
    public void testParsePostmanCollection_WithHeaders() {
        // 准备测试数据 - 带请求头的请求
        String json = """
                {
                    "info": {
                        "name": "Header Test"
                    },
                    "item": [
                        {
                            "name": "API Request",
                            "request": {
                                "method": "POST",
                                "url": "https://api.example.com/data",
                                "header": [
                                    {"key": "Content-Type", "value": "application/json", "disabled": false},
                                    {"key": "Authorization", "value": "Bearer token", "disabled": false}
                                ]
                            }
                        }
                    ]
                }
                """;

        // 执行解析
        CollectionParseResult result = PostmanCollectionParser.parsePostmanCollection(json);

        // 验证结果
        assertNotNull(result);
        var requestNode = result.getChildren().get(0);
        var req = requestNode.asRequest();

        assertNotNull(req.getHeadersList());
        assertEquals(req.getHeadersList().size(), 2);
        assertEquals(req.getHeadersList().get(0).getKey(), "Content-Type");
        assertEquals(req.getHeadersList().get(0).getValue(), "application/json");
    }

    @Test
    public void testParsePostmanCollection_WithScripts() {
        // 准备测试数据 - 带脚本的请求
        String json = """
                {
                    "info": {
                        "name": "Script Test"
                    },
                    "item": [
                        {
                            "name": "API With Scripts",
                            "request": {
                                "method": "GET",
                                "url": "https://api.example.com/data"
                            },
                            "event": [
                                {
                                    "listen": "prerequest",
                                    "script": {
                                        "exec": ["console.log('before');"]
                                    }
                                },
                                {
                                    "listen": "test",
                                    "script": {
                                        "exec": ["pm.test('Status is 200', function() {", "    pm.response.to.have.status(200);", "});"]
                                    }
                                }
                            ]
                        }
                    ]
                }
                """;

        // 执行解析
        CollectionParseResult result = PostmanCollectionParser.parsePostmanCollection(json);

        // 验证结果
        assertNotNull(result);
        var requestNode = result.getChildren().get(0);
        var req = requestNode.asRequest();

        assertNotNull(req.getPrescript());
        assertTrue(req.getPrescript().contains("console.log('before')"));

        assertNotNull(req.getPostscript());
        assertTrue(req.getPostscript().contains("Status is 200"));
    }

    @Test
    public void testParsePostmanCollection_InvalidJson() {
        // 准备测试数据 - 无效的 JSON
        String json = "invalid json";

        // 执行解析
        CollectionParseResult result = PostmanCollectionParser.parsePostmanCollection(json);

        // 验证结果 - 应该返回 null
        assertNull(result);
    }

    @Test
    public void testParsePostmanCollection_MissingInfo() {
        // 准备测试数据 - 缺少 info 字段
        String json = """
                {
                    "item": [
                        {
                            "name": "Test",
                            "request": {
                                "method": "GET",
                                "url": "https://api.example.com"
                            }
                        }
                    ]
                }
                """;

        // 执行解析
        CollectionParseResult result = PostmanCollectionParser.parsePostmanCollection(json);

        // 验证结果 - 应该返回 null
        assertNull(result);
    }

    @Test
    public void testParsePostmanCollection_MissingItem() {
        // 准备测试数据 - 缺少 item 字段
        String json = """
                {
                    "info": {
                        "name": "Test Collection"
                    }
                }
                """;

        // 执行解析
        CollectionParseResult result = PostmanCollectionParser.parsePostmanCollection(json);

        // 验证结果 - 应该返回 null
        assertNull(result);
    }

    @Test
    public void testParsePostmanCollection_EmptyCollection() {
        // 准备测试数据 - 空的 Collection
        String json = """
                {
                    "info": {
                        "name": "Empty Collection"
                    },
                    "item": []
                }
                """;

        // 执行解析
        CollectionParseResult result = PostmanCollectionParser.parsePostmanCollection(json);

        // 验证结果
        assertNotNull(result);
        assertEquals(result.getGroup().getName(), "Empty Collection");
        assertEquals(result.getChildren().size(), 0);
    }

    @Test
    public void testParsePostmanCollection_NoCollectionName() {
        // 准备测试数据 - 无 collection 名称
        String json = """
                {
                    "info": {
                        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
                    },
                    "item": [
                        {
                            "name": "Test",
                            "request": {
                                "method": "GET",
                                "url": "https://api.example.com"
                            }
                        }
                    ]
                }
                """;

        // 执行解析
        CollectionParseResult result = PostmanCollectionParser.parsePostmanCollection(json);

        // 验证结果 - 应该使用默认名称
        assertNotNull(result);
        assertEquals(result.getGroup().getName(), "Postman");
    }

    @Test
    public void testParsePostmanCollection_RequestStringAndFolderVariables() {
        String json = """
                {
                  "info": {
                    "name": "String Request Collection",
                    "schema": "https://schema.getpostman.com/collection/v2.1.0/collection.json"
                  },
                  "item": [
                    {
                      "name": "Folder A",
                      "variable": [
                        {"key": "baseUrl", "value": "https://api.example.com"}
                      ],
                      "item": [
                        {
                          "name": "Ping",
                          "request": "https://api.example.com/ping"
                        }
                      ]
                    }
                  ]
                }
                """;

        CollectionParseResult result = PostmanCollectionParser.parsePostmanCollection(json);
        assertNotNull(result);
        assertEquals(result.getChildren().size(), 1);

        var folder = result.getChildren().get(0);
        assertTrue(folder.isGroup());
        assertEquals(folder.asGroup().getVariables().size(), 1);
        assertEquals(folder.asGroup().getVariables().get(0).getKey(), "baseUrl");
        assertEquals(folder.getChildren().get(0).asRequest().getUrl(), "https://api.example.com/ping");
    }

    @Test
    public void testParsePostmanCollection_ScriptStringAndHeaderString() {
        String json = """
                {
                  "info": {
                    "name": "Header String Collection",
                    "schema": "https://schema.getpostman.com/collection/v2.1.0/collection.json"
                  },
                  "item": [
                    {
                      "name": "Create User",
                      "event": [
                        {
                          "listen": "prerequest",
                          "script": {
                            "exec": "console.log('single line');"
                          }
                        }
                      ],
                      "request": {
                        "method": "POST",
                        "header": "Content-Type: application/json\\nX-Trace: abc",
                        "url": {
                          "protocol": "https",
                          "host": ["api", "example", "com"],
                          "path": ["users"]
                        },
                        "body": {
                          "mode": "raw",
                          "raw": "{\\"name\\":\\"Alice\\"}"
                        }
                      }
                    }
                  ]
                }
                """;

        CollectionParseResult result = PostmanCollectionParser.parsePostmanCollection(json);
        assertNotNull(result);

        var request = result.getChildren().get(0).asRequest();
        assertEquals(request.getUrl(), "https://api.example.com/users");
        assertEquals(request.getBodyType(), BODY_TYPE_RAW);
        assertEquals(request.getPrescript(), "console.log('single line');\n");
        assertTrue(request.getHeadersList().stream().anyMatch(h -> "Content-Type".equals(h.getKey())));
        assertTrue(request.getHeadersList().stream().anyMatch(h -> "X-Trace".equals(h.getKey()) && "abc".equals(h.getValue())));
    }

    @Test
    public void testParsePostmanCollection_FormDataFileArrayAndGraphql() {
        String json = """
                {
                  "info": {
                    "name": "Advanced Body Collection",
                    "schema": "https://schema.getpostman.com/collection/v2.1.0/collection.json"
                  },
                  "item": [
                    {
                      "name": "Upload Avatar",
                      "request": {
                        "method": "POST",
                        "url": "https://api.example.com/upload",
                        "body": {
                          "mode": "formdata",
                          "formdata": [
                            {"key": "avatar", "type": "file", "src": ["./front.png", "./back.png"]}
                          ]
                        }
                      }
                    },
                    {
                      "name": "GraphQL Query",
                      "request": {
                        "method": "POST",
                        "url": "https://api.example.com/graphql",
                        "body": {
                          "mode": "graphql",
                          "graphql": {
                            "query": "query Ping { ping }",
                            "variables": "{\\"id\\":1}"
                          }
                        }
                      }
                    }
                  ]
                }
                """;

        CollectionParseResult result = PostmanCollectionParser.parsePostmanCollection(json);
        assertNotNull(result);
        assertEquals(result.getChildren().size(), 2);

        var upload = result.getChildren().get(0).asRequest();
        assertEquals(upload.getBodyType(), BODY_TYPE_FORM_DATA);
        assertEquals(upload.getFormDataList().size(), 2);
        assertEquals(upload.getFormDataList().get(0).getKey(), "avatar");
        assertEquals(upload.getFormDataList().get(0).getValue(), "./front.png");
        assertEquals(upload.getFormDataList().get(1).getKey(), "avatar");
        assertEquals(upload.getFormDataList().get(1).getValue(), "./back.png");

        var graphql = result.getChildren().get(1).asRequest();
        assertEquals(graphql.getBodyType(), BODY_TYPE_RAW);
        assertTrue(graphql.getBody().contains("query Ping"));
    }

    @Test
    public void testParsePostmanCollection_ExamplesGetUniqueIds() {
        String json = """
                {
                    "info": {"name": "Examples", "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
                    "item": [{
                        "name": "Get User",
                        "request": {"method": "GET", "url": "https://api.example.com/user"},
                        "response": [
                            {"name": "OK", "code": 200, "_postman_previewlanguage": "json", "body": "{}"},
                            {"name": "Not Found", "code": 404, "_postman_previewlanguage": "json", "body": "{}"}
                        ]
                    }]
                }
                """;

        CollectionParseResult result = PostmanCollectionParser.parsePostmanCollection(json);

        var responses = result.getChildren().get(0).asRequest().getResponse();
        assertEquals(responses.size(), 2);
        assertNotEquals(responses.get(0).getId(), "json");
        assertNotEquals(responses.get(0).getId(), responses.get(1).getId());
        assertEquals(responses.get(0).getPreviewLanguage(), "json");
    }
}
