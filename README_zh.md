<div align="center">

<img src="docs/icon.png" alt="EasyPostman Logo" width="100" />

# EasyPostman

**开源 Postman 风格接口调试 + JMeter 风格性能测试桌面工具**<br>
*本地优先 · Git 工作区 · 无头 CLI · 内置 MCP Server*

[![GitHub license](https://img.shields.io/github/license/lakernote/easy-postman?style=flat-square)](https://github.com/lakernote/easy-postman/blob/main/LICENSE)
[![GitHub release](https://img.shields.io/github/v/release/lakernote/easy-postman?style=flat-square&color=brightgreen)](https://github.com/lakernote/easy-postman/releases)
[![GitHub downloads](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Flakernote%2Feasy-postman%2Fbadges%2Fgithub-downloads.json&style=flat-square&cacheSeconds=3600)](https://github.com/lakernote/easy-postman/releases)
[![GitHub stars](https://img.shields.io/github/stars/lakernote/easy-postman?style=flat-square&color=yellow)](https://github.com/lakernote/easy-postman/stargazers)
[![Java](https://img.shields.io/badge/Java-17+-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20macOS%20%7C%20Linux-0078D4?style=flat-square)](https://github.com/lakernote/easy-postman/releases)

[English](README.md) · [📦 下载](#-下载与安装) · [🤖 安装 MCP](#-mcp-server) · [📖 文档](#-文档) · [💬 讨论区](https://github.com/lakernote/easy-postman/discussions) · 微信：`lakernote`

</div>

---

## EasyPostman 是什么

EasyPostman 把 **Postman 风格的接口调试**和 **JMeter 风格的性能测试**放进同一个 Java 17 桌面应用。集合、环境和测试数据默认保存在本地，也可以用 Git 工作区协作；同一份工作区还能通过 CLI 在 CI 中运行，或通过 MCP 交给 Codex、Claude Desktop、Cursor 等 AI 客户端调用。

| 核心能力 | 说明 |
|---|---|
| 🔌 API 调试 | HTTP/HTTPS、SSE、WebSocket、认证、Cookie、多种 Body、响应查看与网络日志 |
| 🧩 集合与脚本 | Postman v2.1 / cURL 导入、环境变量、前后置脚本、断言、请求链路 |
| ⚡ 性能测试 | 线程组、定时器、提取器、实时指标、报告和 master/worker 分布式执行 |
| 🏢 本地与 Git 工作区 | 每个工作区独立保存集合、环境和设置，数据由用户自己掌控 |
| 🤖 MCP 与无头 CLI | AI 调用已保存的 API；CI 运行集合、功能测试和压测计划 |

📖 [查看完整功能列表](docs/FEATURES_zh.md)

## 🖼️ 界面预览

<table>
  <tr>
    <th width="50%">Postman 风格接口调试</th>
    <th width="50%">JMeter 风格性能测试</th>
  </tr>
  <tr>
    <td><a href="docs/collections.png"><img src="docs/collections.png" alt="接口集合和响应查看器" width="100%"></a></td>
    <td><a href="docs/performance-trend.png"><img src="docs/performance-trend.png" alt="性能趋势面板" width="100%"></a></td>
  </tr>
  <tr>
    <th width="50%">脚本、断言与代码片段</th>
    <th width="50%">Git 工作区协作</th>
  </tr>
  <tr>
    <td><a href="docs/script-snippets.png"><img src="docs/script-snippets.png" alt="脚本代码片段和编辑器支持" width="100%"></a></td>
    <td><a href="docs/workspaces-gitcommit.png"><img src="docs/workspaces-gitcommit.png" alt="Git 工作区管理" width="100%"></a></td>
  </tr>
</table>

📸 [查看完整截图集](docs/SCREENSHOTS_zh.md)

## 📦 下载与安装

从 **[GitHub Releases](https://github.com/lakernote/easy-postman/releases)** 下载最新版本；国内用户也可以使用 **[Gitee Releases](https://gitee.com/lakernote/easy-postman/releases)**。原生安装包和 Windows 便携版已包含运行时，桌面端和 MCP 都不用另装 Java；只有跨平台 JAR 需要 Java 17+。

### Windows WinGet

```powershell
winget install --id Laker.EasyPostman --exact
```

升级：

```powershell
winget upgrade --id Laker.EasyPostman --exact
```

### 如何选择安装包

| 平台 | 文件 |
|---|---|
| macOS Apple Silicon | `EasyPostman-{版本号}-macos-arm64.dmg` |
| macOS Intel | `EasyPostman-{版本号}-macos-x86_64.dmg` |
| Windows 安装版 / 便携版 | `EasyPostman-{版本号}-windows-x64.exe` / `-portable.zip` |
| Debian / Ubuntu x64 | `EasyPostman-{版本号}-linux-amd64.deb` |
| Debian / Ubuntu ARM64 | `EasyPostman-{版本号}-linux-arm64.deb`；旧版 `dpkg` 不兼容时使用 `-compat.deb` |
| RHEL / Rocky / CentOS / Fedora | `EasyPostman-{版本号}-1.x86_64.rpm` 或 `-1.aarch64.rpm` |
| 跨平台 JAR | `easy-postman-{版本号}.jar`，需要 Java 17+ |

首次启动若遇到 Windows SmartScreen 或 macOS Gatekeeper 提示，请选择“仍要运行”或右键应用后选择“打开”。项目未购买商业代码签名证书，所有源代码均可公开审查。

## 🤖 MCP Server

EasyPostman 的 MCP 已包含在全部发布包中，**不用单独下载 MCP，也不用手工启动服务**。推荐使用原生安装包或 Windows 便携版：其中已经包含 `EasyPostmanMCP` 和 JRE，用户不用另装 Java。

### 推荐安装方式

1. 从 [Releases](https://github.com/lakernote/easy-postman/releases) 安装原生包，或解压 Windows 便携版。
2. 在 Codex、Claude Desktop 或 Cursor 中添加本地 STDIO MCP Server，`command` 使用对应启动器：

| 平台 | `command` |
|---|---|
| macOS | `/Applications/EasyPostman.app/Contents/MacOS/EasyPostmanMCP` |
| Windows 安装版 | `C:\Program Files\EasyPostman\EasyPostmanMCP.exe` |
| Windows 便携版 | 解压目录中的 `EasyPostmanMCP.exe`，例如 `D:\Tools\EasyPostman\EasyPostmanMCP.exe` |
| Linux DEB / RPM | `/opt/easypostman/bin/EasyPostmanMCP` |

macOS 的核心配置如下，其他平台只需替换 `command`：

```json
{
  "command": "/Applications/EasyPostman.app/Contents/MacOS/EasyPostmanMCP",
  "args": []
}
```

Codex CLI 可直接执行：

```bash
codex mcp add easy-postman -- /Applications/EasyPostman.app/Contents/MacOS/EasyPostmanMCP
```

3. 保存后重启 MCP 客户端并新建对话，可用这句话验证：`请使用 EasyPostman MCP 列出所有工作区，不要执行任何接口。`

默认 `args` 为空，不需要配置工作区目录。客户端为一次 MCP 连接启动一个进程，并在多次 Tool 调用间复用它；不是每调用一个 API 就重新启动一次。Windows 便携版要保留整个解压目录，不能只复制 `EasyPostmanMCP.exe`。

如果只下载跨平台 JAR，则使用下面的备用配置，并确保 Java 17+ 可用：

```json
{
  "command": "java",
  "args": ["-jar", "/absolute/path/to/easy-postman-{version}.jar", "mcp", "serve"]
}
```

MCP 默认读取 EasyPostman 已登记的工作区；AI 可通过 `list_workspaces` 获取 `workspaceId`，再为每次调用选择工作区和环境。桌面端与 MCP 是独立进程，可以同时运行；MCP 不会切换桌面页面或修改桌面端当前选择。

如需只授权一个工作区，原生启动器可在 `args` 中传入工作区绝对路径；JAR 方式则在 `"serve"` 后追加。该目录中应包含 `collections.json`，`environments.json` 可选。

Claude Desktop、Cursor 等 JSON 配置客户端通常把上面的对象放在 `mcpServers.easy-postman` 下。Codex 也可以使用 MCP Server 设置或 `config.toml`。参考 [Codex MCP 官方文档](https://developers.openai.com/codex/mcp/)。

### AI 可以做什么

AI 可以列出工作区、集合、环境和已保存请求，并通过 `run_request` 或 `run_collection` 为本次调用选择工作区与环境后执行 HTTP API。环境变量值不会被枚举；接口响应体会返回给 MCP 客户端，因此不要把含敏感业务数据的工作区授权给不可信客户端。

MCP 只能发现并执行授权工作区里已保存的 HTTP 请求，不能临时拼装任意 URL。接口响应体会返回给 MCP 客户端，执行请求也可能产生真实业务副作用，因此生产工作区只应授权给可信客户端。

📖 [MCP 完整安装、工具、架构与安全边界](docs/MCP_SERVER_DESIGN_zh.md)

## 🚀 快速开始

桌面端的基本流程：

1. 创建本地工作区，或连接一个 Git 工作区。
2. 创建或导入集合，选择环境。
3. 输入 URL、参数和认证信息，然后发送请求。
4. 按需添加脚本、断言、功能测试或性能测试计划。

从源码运行：

```bash
git clone https://github.com/lakernote/easy-postman.git
cd easy-postman
mvn -pl easy-postman-app -am -DskipTests clean package
java -jar easy-postman-app/target/easy-postman-*.jar
```

📖 [完整构建指南](docs/BUILD_zh.md)

## 🧪 无头 CLI

同一个 JAR 可以直接运行 EasyPostman 原生工作区，不需要导出集合或环境：

```bash
java -jar easy-postman.jar collection run /srv/api-workspace -c "Basic HTTP Examples" -e "Dev Env"
java -jar easy-postman.jar functional run /srv/api-workspace --bail --out target/result.json
```

- [Collection CLI 完整指南](docs/COLLECTION_CLI_zh.md)
- [Functional CLI 完整指南](docs/FUNCTIONAL_CLI_zh.md)
- [集群压测指南](docs/PERFORMANCE_CLUSTER_LOAD_TEST_zh.md)

## 📚 文档

| 文档 | 内容 |
|---|---|
| [功能详细说明](docs/FEATURES_zh.md) | API 调试、脚本、Mock、性能测试和工作区能力 |
| [MCP Server](docs/MCP_SERVER_DESIGN_zh.md) | 安装、Tool 参数、工作区/环境选择、架构和安全边界 |
| [Collection CLI](docs/COLLECTION_CLI_zh.md) | 集合无头运行、变量、迭代数据、上传和 CI 退出码 |
| [Functional CLI](docs/FUNCTIONAL_CLI_zh.md) | 按 `functional_config.json` 执行功能测试 |
| [插件架构](docs/PLUGINS_zh.md) | 插件开发、在线与离线安装 |
| [脚本 API](docs/SCRIPT_API_REFERENCE_zh.md) | 请求前和测试脚本参考 |
| [构建指南](docs/BUILD_zh.md) | 源码构建和原生安装包 |
| [常见问题](docs/FQA.MD) | 安装和使用问题 |

## 🛠️ 开发与贡献

```bash
# 快速编译
mvn -q -pl easy-postman-app -am -DskipTests compile

# 全量构建与测试
mvn clean package
```

欢迎提交 Bug、建议、文档和代码：[创建 Issue](https://github.com/lakernote/easy-postman/issues/new/choose) · [贡献指南](.github/CONTRIBUTING.md)

## 🙏 致谢与支持

EasyPostman 使用了 [FlatLaf](https://github.com/JFormDesigner/FlatLaf)、[RSyntaxTextArea](https://github.com/bobbylight/RSyntaxTextArea)、[OkHttp](https://github.com/square/okhttp) 等优秀开源项目。

如果项目对你有帮助，欢迎 [Star](https://github.com/lakernote/easy-postman)、[参与讨论](https://github.com/lakernote/easy-postman/discussions) 或添加微信 `lakernote` 交流。

<div align="center">

**让接口调试像 Postman 一样顺手，让性能测试像 JMeter 一样完整**

Made with ❤️ by [laker](https://github.com/lakernote)

</div>
