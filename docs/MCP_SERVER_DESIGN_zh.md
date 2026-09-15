# EasyPostman MCP Server

EasyPostman 内置标准 MCP stdio Server，让 Codex、Claude Desktop、Cursor 等客户端发现工作区，并在每次工具调用中选择工作区和环境、执行已保存的 HTTP 请求。MCP 直接复用无头执行链，不远程操作 Swing 页面，也不修改桌面端当前选择。实现对应 [Issue #169](https://github.com/lakernote/easy-postman/issues/169)。

## 安装与配置

### 1. 获取 EasyPostman

MCP 已包含在主程序中，不发布单独的 MCP 安装包：

| 下载方式 | MCP 使用方式 | 是否需要另装 Java |
|---|---|---|
| macOS / Windows / Linux 原生安装包 | 使用包内的 `EasyPostmanMCP` 启动器 | 否，已包含 JRE |
| Windows 便携版 ZIP | 解压后使用 `EasyPostmanMCP.exe` | 否，已包含 JRE |
| 跨平台 JAR | `java -jar ... mcp serve` | 是，需要 Java 17+ |

推荐从 [GitHub Releases](https://github.com/lakernote/easy-postman/releases) 下载原生包。Windows 便携版无需安装，解压后即可使用桌面端和 MCP。

### 2. 找到原生启动器

| 安装方式 | `command` |
|---|---|
| macOS | `/Applications/EasyPostman.app/Contents/MacOS/EasyPostmanMCP` |
| Windows 安装版 | `C:\Program Files\EasyPostman\EasyPostmanMCP.exe` |
| Windows 便携版 | 解压目录中的 `EasyPostmanMCP.exe`，例如 `D:\Tools\EasyPostman\EasyPostmanMCP.exe` |
| Linux DEB / RPM | `/opt/easypostman/bin/EasyPostmanMCP` |

Windows 便携版解压后大致如下。两个 EXE 共享同一个 JAR 和运行时，因此不要只把 `EasyPostmanMCP.exe` 单独复制出去：

```text
EasyPostman/
├── EasyPostman.exe          # 桌面应用
├── EasyPostmanMCP.exe       # MCP stdio 启动器
├── app/
│   ├── easy-postman.jar
│   ├── EasyPostman.cfg
│   └── EasyPostmanMCP.cfg
├── runtime/                 # 内置 JRE
└── .portable                # 仅便携版存在
```

macOS 安装包采用同样的共享结构：

```text
EasyPostman.app/Contents/
├── MacOS/EasyPostman
├── MacOS/EasyPostmanMCP
├── app/easy-postman.jar
├── app/EasyPostmanMCP.cfg
└── runtime/Contents/Home/
```

### 3. 配置 MCP 客户端

原生启动器会自动补上 `mcp serve`，所以默认配置的 `args` 必须为空，不要再写 `"mcp", "serve"`。

Codex CLI：

```bash
codex mcp add easy-postman -- /Applications/EasyPostman.app/Contents/MacOS/EasyPostmanMCP
```

也可以写入 Codex 的 `config.toml`：

```toml
[mcp_servers.easy-postman]
command = "/Applications/EasyPostman.app/Contents/MacOS/EasyPostmanMCP"
args = []
```

Claude Desktop、Claude Code、Cursor 等使用 JSON 的客户端可以配置完整的 `mcpServers` 对象：

```json
{
  "mcpServers": {
    "easy-postman": {
      "command": "/Applications/EasyPostman.app/Contents/MacOS/EasyPostmanMCP",
      "args": []
    }
  }
}
```

Windows JSON 路径中的反斜杠需要写成双反斜杠，例如 `"D:\\Tools\\EasyPostman\\EasyPostmanMCP.exe"`。

Windows 便携版的完整 JSON 示例：

```json
{
  "mcpServers": {
    "easy-postman": {
      "command": "D:\\Tools\\EasyPostman\\EasyPostmanMCP.exe",
      "args": []
    }
  }
}
```

保存后重启客户端并新建对话。建议先做只读验证：

```text
请使用 EasyPostman MCP 列出所有工作区，不要执行任何接口。
```

Codex、Claude 和 Cursor 的具体配置入口可能随客户端版本变化，请以各自官方文档为准：[Codex MCP](https://developers.openai.com/codex/mcp/)、[Claude MCP](https://docs.anthropic.com/en/docs/claude-code/mcp)、[Cursor MCP](https://docs.cursor.com/context/model-context-protocol)。

### 4. 默认工作区与进程模型

默认不需要配置工作区目录。MCP 进程会读取 EasyPostman 已登记的工作区，客户端先调用 `list_workspaces`，再为后续 Tool 传入 `workspaceId` 即可选择工作区。

MCP 客户端为一次连接启动一个 `EasyPostmanMCP` 进程，并在多次 Tool 调用间复用；不是每执行一个请求就重新启动一次。连接结束后 stdin 关闭，MCP 进程随之退出，不需要用户维护常驻服务。

桌面应用和 MCP 是两个独立进程，可以同时运行并读取同一批工作区。MCP 的工作区和环境选择只影响本次 Tool 调用，不会模拟点击页面，也不会改变桌面端当前选择。桌面端新增或删除工作区后，需要重启 MCP 客户端连接以刷新目录。

### 5. 可选：只授权一个工作区

如需缩小授权范围，在原生启动器的 `args` 中传入工作区绝对路径：

```json
{
  "command": "/Applications/EasyPostman.app/Contents/MacOS/EasyPostmanMCP",
  "args": ["/absolute/path/to/my-api-workspace"]
}
```

该目录应包含 `collections.json`，`environments.json` 可选。

### 6. 跨平台 JAR 备用方式

只下载 JAR 时仍可使用下面的配置，但需要 Java 17+：

```json
{
  "command": "java",
  "args": ["-jar", "/absolute/path/to/easy-postman-{version}.jar", "mcp", "serve"]
}
```

JAR 方式如需只授权一个工作区，在 `"serve"` 后追加目录。原生启动器路径在应用升级后通常保持不变；版本化 JAR 文件名变化后则需要同步修改客户端配置。

可选参数 `--max-response-bytes <n>` 控制一次 Tool 调用返回的响应体总量，默认为 64 KiB，可设置为 1 KiB～10 MiB。原生启动器把它直接放入 `args`；JAR 方式放在 `"serve"` 之后。

### 7. 安装排查

- 先直接运行 `EasyPostmanMCP --help`；能显示帮助信息表示启动器和内置 JRE 完整。
- 配置中的 `command` 建议使用绝对路径。Windows JSON 路径需要转义反斜杠，命令行中有空格的路径需要加引号。
- Windows 便携版必须完整解压，不能只复制 EXE；升级或移动解压目录后要同步更新 MCP 配置。
- Codex 可执行 `codex mcp get easy-postman --json` 检查最终保存的命令；其他客户端使用各自的 MCP Server 状态页检查。
- 修改配置或桌面端工作区列表后，重启 MCP 客户端或重建对话。正常运行时 stdout 只有 JSON-RPC，常规日志默认关闭；必要错误会脱敏后写入 stderr。

## 工具

| Tool | 主要输入 | 用途 |
|---|---|---|
| `list_workspaces` | 无 | 列出本进程已授权的工作区及稳定 ID |
| `list_collections` | `workspaceId?` | 列出集合、描述和请求数量 |
| `list_environments` | `workspaceId?` | 列出环境和变量名，不返回变量值 |
| `list_requests` | `workspaceId?`、`collectionId?`、`query?`、`limit?` | 查找已保存请求 |
| `run_request` | `workspaceId?`、`requestId`、`environmentId?`、`environmentOverrides?` | 执行一个 HTTP 请求 |
| `run_collection` | `workspaceId?`、`collectionId`、`environmentId?`、`environmentOverrides?`、`iterations?`、`bail?` | 执行整个集合 |

### 工作区和环境选择语义

这里的“选择”是调用级路由，不是模拟人工点击桌面页面：

- 传入工作区目录启动时，只授权该目录，所有 Tool 都可以省略 `workspaceId`。
- 省略启动目录时，MCP 进程在启动时读取 EasyPostman 已登记工作区。客户端先调用 `list_workspaces`，再把返回的 `workspaceId` 传给后续 Tool，即可在不同工作区之间执行请求。
- 多工作区模式下省略 `workspaceId`，会使用 MCP 进程启动时记录的 EasyPostman 当前工作区；如果不能唯一确定，则要求显式传入 `workspaceId`。
- 选择 `workspaceId` 不会调用桌面端的“切换工作区”，不会保存新的当前工作区，也不会让已打开的 Swing 页面跟随切换。桌面端新增、删除或切换工作区后，应重启 MCP 客户端进程以刷新目录；始终显式传入 `workspaceId` 最稳定。
- `list_environments` 用于发现环境；`run_request` / `run_collection` 的 `environmentId` 可以传环境 ID 或精确名称。省略时读取该工作区 `environments.json` 中的激活环境，没有激活项时使用第一项。
- `environmentId` 只决定本次执行使用哪个环境，不会修改激活状态或保存 `environments.json`。

Tool 参数不接受任意本机目录作为 `workspaceId`，只接受本 MCP 进程启动时已授权的 ID。

`environmentId` 选择保存的环境，省略时使用激活环境。`environmentOverrides` 接收字符串键值，例如：

```json
{
  "environmentId": "development",
  "environmentOverrides": {
    "baseUrl": "http://127.0.0.1:8080",
    "apiToken": "temporary-value"
  }
}
```

覆盖值只在本次运行的内存环境中生效，不修改 `environments.json`，结果只返回被覆盖的变量名，不回显输入值。

### 可以调用哪些 API

- `list_requests` 可以按 `collectionId` 和关键词查找工作区内已保存的请求；结果包含稳定 `requestId`、method、脱敏 URL 和 protocol。
- `run_request` 只执行指定 `requestId` 对应的一个已保存 HTTP 请求；不能直接传入任意 URL、method、header 或 body 临时拼装请求。
- `run_collection` 执行指定顶层集合内的全部已保存请求。MCP v1 只支持 HTTP；单个请求或集合中包含 SSE/WebSocket 等协议时会拒绝执行。
- 当前授权粒度是“工作区”，不是 collection/request 白名单。只要某个工作区被授权，客户端就可以发现并执行其中任意已保存 HTTP 请求；需要严格限制时，应使用启动参数只授权一个专用工作区，并只在其中保留允许调用的 API。
- `environmentOverrides` 可能改变 `{{baseUrl}}` 等 URL 变量，因此它不是域名白名单。应只把生产工作区授权给可信 MCP 客户端。
- binary / multipart 上传只允许读取当前授权工作区内的真实文件；绝对路径、`..` 或符号链接都不能逃逸到工作区之外。

典型调用链：

```text
list_workspaces
  → list_collections / list_environments
  → list_requests
  → run_request / run_collection
```

## 架构

```text
MCP Client
  │ stdio JSON-RPC
  ▼
easy-postman-mcp
  ├─ official MCP Java SDK
  ├─ stdio lifecycle
  ├─ 六个 tool schema + annotations
  └─ EasyPostmanMcpBackend
                │
                ▼
easy-postman-app
  ├─ mcp serve CLI
  ├─ authorized workspace catalog
  ├─ collection/environment adapter
  ├─ response redaction and size limit
  └─ WorkspaceRunExecutor
       ├─ inheritance and variables
       ├─ pre/post scripts and tests
       └─ HTTP runtime
```

`easy-postman-mcp` 只拥有协议层，不依赖 app、Swing、工作区存储或具体请求执行。app 实现 backend 并复用现有无头执行链，因此不会复制 EasyPostman 的变量、脚本、Cookie、TLS 和 HTTP 语义。本次没有改变插件 SPI，也不需要提升 `plugin.platform.version`。

## 安全边界

- 单工作区模式只授权启动参数中的目录；多工作区模式只授权 EasyPostman 注册表中的工作区。
- MCP v1 不写集合或环境文件，不提供导入、保存和删除工具。
- 环境值不通过枚举工具返回；一次性覆盖不落盘。但目标 API 可能在响应体中回显输入值，响应体本身也可能包含个人或业务数据，因此只能连接可信 MCP 客户端。
- 常见认证响应头、自定义 token/key/password/secret 头、URL user-info、敏感查询参数，以及响应体中常见的敏感键值会脱敏；通用脱敏无法识别所有业务字段，不能替代服务端的数据分级和最小权限。
- 响应体有字节上限，并返回是否截断和原始字节数；每次调用最多返回 100 条请求/响应明细，超出时返回省略数量，汇总计数仍覆盖完整运行。
- 同一进程内的执行调用串行化；每次 Tool 执行前后清空 MCP 进程 Cookie，集合内部仍可共享 Cookie，但不同调用和工作区之间不会继承会话。
- stdio 输入遇到 EOF、非法 JSON、超大消息或 I/O 错误时都会结束 MCP 进程，不会留下失去通信能力的挂起进程。
- stdout 只输出 MCP JSON-RPC。MCP 模式默认关闭应用、插件和 HTTP Logback 日志，仅将必要的 CLI/协议错误写 stderr；排障时可用 JVM 系统属性显式恢复日志级别。

`run_request` 和 `run_collection` 会访问外部 API，可能产生真实业务副作用，因此 MCP annotations 标记为非只读、可能破坏、开放世界操作。生产工作区只应授权给可信客户端。

## 构建与验证

```bash
mvn -pl easy-postman-app -am -DskipTests clean package
```

产物：`easy-postman-app/target/easy-postman-{version}.jar`。

协议模块可独立测试：

```bash
mvn -pl easy-postman-mcp -am test
```

参考：[MCP 规范](https://modelcontextprotocol.io/specification/2026-07-28)、[官方 Java SDK](https://github.com/modelcontextprotocol/java-sdk)。
