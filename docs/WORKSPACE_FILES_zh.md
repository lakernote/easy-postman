# EasyPostman 工作区文件格式

EasyPostman 工作区就是一个目录，里面是几个 JSON 文件。目录可以独立成一个 Git 仓库，也可以放在已有代码仓库的任意子目录中（例如 `order-service/easy-postman/`）。本文说明这些文件的格式，方便人或 AI agent 直接编辑。

新建工作区时，EasyPostman 会在目录中生成一份简短的 `AGENTS.md`，供 Codex、Claude Code、Cursor 等 agent 读取；已有的 `AGENTS.md` 不会被覆盖。在代码仓库中使用时，建议在仓库根目录的 `AGENTS.md` 加一句：

```markdown
接口测试数据在 easy-postman/，修改前阅读 easy-postman/AGENTS.md。
```

## 1. 目录结构

```text
easy-postman/
├── AGENTS.md                 # 给 AI agent 的修改规则（自动生成，可自行补充）
├── collections.json          # 集合、文件夹、请求、脚本和示例响应
├── environments.json         # 环境和变量
├── functional_config.json    # 功能测试：选中的请求和顺序
├── performance_config.json   # 性能测试计划
└── mock_servers.json         # Mock 服务
```

只有 `collections.json` 是必需的，其他文件缺失时按空处理，EasyPostman 不会自动补建。

桌面端在窗口重新获得焦点时检测外部修改：没有未保存编辑时自动重新加载；JSON 不合法时提示错误，保留当前界面和磁盘文件。

## 2. 通用规则

- 文件使用 UTF-8 编码，4 空格缩进的 JSON。
- `id` 在整个文件内唯一，新增节点使用新的 UUID；复制节点时必须换新 `id`。
- 引用其他文件时用 `id`（例如功能测试引用请求 `id`），不要用名称。
- 变量写成 `{{name}}`，可用于 URL、Header、参数、请求体和认证字段。
- 密钥、Token、密码不要写真实值，使用 `{{token}}` 这类变量，真实值在本机环境或 CI 中注入。
- 本文只列出常用字段。修改已有节点时保留现有字段，不认识的字段原样保留；新增节点可省略未列出的字段，使用默认值。

## 3. `collections.json`

顶层是集合数组。集合和文件夹都是 `group` 节点，请求是 `request` 节点：

```json
[
    {
        "type": "group",
        "id": "c7a1f6a2-0d1e-4b8e-9f51-6f0a3b6f2c11",
        "name": "Order Service",
        "description": "订单服务接口",
        "authType": "Inherit auth from parent",
        "prescript": "",
        "postscript": "",
        "variables": [
            {"enabled": true, "key": "orderPath", "value": "/api/orders"}
        ],
        "children": [
            {
                "type": "group",
                "id": "5a7b3c90-3b52-4c3e-8f6b-2d9f4e0e7a21",
                "name": "Orders",
                "children": [
                    {
                        "type": "request",
                        "data": {
                            "id": "0f2e6c1a-8b7d-4a55-9c3e-1d2f3a4b5c6d",
                            "name": "Create order",
                            "method": "POST",
                            "url": "{{baseUrl}}{{orderPath}}",
                            "headersList": [
                                {"enabled": true, "key": "Content-Type", "value": "application/json"}
                            ],
                            "paramsList": [],
                            "bodyType": "raw",
                            "body": "{\n    \"sku\": \"A-100\",\n    \"quantity\": 1\n}",
                            "authType": "Bearer Token",
                            "authToken": "{{token}}",
                            "postscript": "pm.test('status is 201', function () {\n    pm.response.to.have.status(201);\n});"
                        }
                    }
                ]
            }
        ]
    }
]
```

### group 节点（集合、文件夹）

| 字段 | 说明 |
|---|---|
| `type` | 固定为 `group` |
| `id`、`name`、`description` | 标识、名称、Markdown 描述 |
| `children` | 子文件夹和请求，按数组顺序显示和运行 |
| `headers` | 公共请求头，元素格式同 `headersList`，子请求继承 |
| `variables` | 集合/文件夹变量：`{"enabled", "key", "value"}` |
| `authType` 及 `auth*` | 认证，取值见下文；子请求设为继承时生效 |
| `prescript`、`postscript` | 前置脚本、后置脚本，对其下所有请求执行 |

### request 节点

请求内容在 `data` 中：

| 字段 | 说明 |
|---|---|
| `id`、`name`、`description` | 标识、名称、Markdown 描述 |
| `method` | `GET`、`POST`、`PUT`、`PATCH`、`DELETE`、`HEAD`、`OPTIONS`、`TRACE` |
| `url` | 完整地址，可含 `{{变量}}` 和 `:pathVar` 路径变量；查询参数建议写在 `paramsList` |
| `protocol` | `HTTP`（默认）或 `WEBSOCKET` |
| `headersList` | 请求头：`{"enabled", "key", "value", "description"}` |
| `paramsList` | 查询参数，格式同上 |
| `pathVariablesList` | 路径变量（对应 URL 中的 `:name`），格式同上 |
| `bodyType` | `none`、`raw`、`form-data`、`x-www-form-urlencoded`、`binary` |
| `body` | `raw` 时的请求体文本；JSON 体同时设置 `Content-Type: application/json` 请求头 |
| `formDataList` | `form-data` 字段：`{"enabled", "key", "type", "value"}`，`type` 为 `Text` 或 `File`（`value` 为文件路径） |
| `urlencodedList` | `x-www-form-urlencoded` 字段：`{"enabled", "key", "value"}` |
| `authType` | 见下表，默认 `Inherit auth from parent` |
| `prescript`、`postscript` | 前置脚本、后置脚本（JavaScript，`pm.*` API） |
| `response` | 保存的示例响应，Mock 服务据此返回数据；建议在桌面端生成 |

认证字段：

| `authType` | 需要的字段 |
|---|---|
| `Inherit auth from parent` | 无，继承上级文件夹/集合 |
| `No Auth` | 无 |
| `Bearer Token` | `authToken` |
| `Basic Auth`、`Digest Auth` | `authUsername`、`authPassword` |
| `API Key` | `authApiKeyName`、`authApiKeyValue`、`authApiKeyPlacement`（`Header` 或 `Query Params`） |

脚本 API 参见 [脚本 API 参考](SCRIPT_API_REFERENCE_zh.md)。常用断言：

```javascript
pm.test('status is 200', function () {
    pm.response.to.have.status(200);
});
var body = pm.response.json();
pm.environment.set('orderId', body.id);
```

## 4. `environments.json`

```json
[
    {
        "id": "4e1b9d2c-6a7f-4f0e-8b3d-9c2a1e5f7b80",
        "name": "Dev",
        "active": true,
        "variableList": [
            {"enabled": true, "key": "baseUrl", "value": "http://localhost:8080"},
            {"enabled": true, "key": "token", "value": ""}
        ]
    }
]
```

- `active: true` 表示当前环境，最多一个。
- 需要保密的变量值留空或写占位值，真实值在桌面端本机填写，CI 中按 [Collection CLI](COLLECTION_CLI_zh.md#7-环境全局变量和-secret) 注入。

## 5. `functional_config.json`

功能测试按 `rows` 顺序执行 `selected: true` 的请求，`requestItemId` 必须是 `collections.json` 中存在的请求 `id`：

```json
{
    "version": "1.0",
    "rows": [
        {"selected": true, "requestItemId": "0f2e6c1a-8b7d-4a55-9c3e-1d2f3a4b5c6d"}
    ]
}
```

可选的 `csvState` 内嵌多轮数据，详见 [Functional CLI](FUNCTIONAL_CLI_zh.md#3-functional_configjson)。

## 6. `performance_config.json` 和 `mock_servers.json`

- `performance_config.json`：性能测试计划，结构较复杂，建议在桌面端编辑。
- `mock_servers.json`：Mock 服务数组。常用字段为 `id`、`name`、`host`、`port`、`collectionSources`（引用集合 `{"id", "name"}`，路由来自其中请求的示例响应）。建议在桌面端编辑。

## 7. 修改后验证

```bash
# 运行集合（可用 -c 指定集合，-e 指定环境）
java -jar easy-postman.jar collection run ./easy-postman -c "Order Service" -e "Dev"

# 按 functional_config.json 运行功能测试
java -jar easy-postman.jar functional run ./easy-postman

# 启动 Mock 服务
java -jar easy-postman.jar mock run ./easy-postman
```

退出码为 0 表示全部通过。也可以配置 [MCP Server](MCP_SERVER_DESIGN_zh.md)，让 agent 直接执行已保存的请求。
