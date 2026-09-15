<div align="center">

<img src="docs/icon.png" alt="EasyPostman Logo" width="100" />

# EasyPostman

**An open-source Postman-style API client + JMeter-style load testing desktop app**<br>
*Local-first · Git workspaces · Headless CLI · Built-in MCP Server*

[![GitHub license](https://img.shields.io/github/license/lakernote/easy-postman?style=flat-square)](https://github.com/lakernote/easy-postman/blob/main/LICENSE)
[![GitHub release](https://img.shields.io/github/v/release/lakernote/easy-postman?style=flat-square&color=brightgreen)](https://github.com/lakernote/easy-postman/releases)
[![GitHub downloads](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Flakernote%2Feasy-postman%2Fbadges%2Fgithub-downloads.json&style=flat-square&cacheSeconds=3600)](https://github.com/lakernote/easy-postman/releases)
[![GitHub stars](https://img.shields.io/github/stars/lakernote/easy-postman?style=flat-square&color=yellow)](https://github.com/lakernote/easy-postman/stargazers)
[![Java](https://img.shields.io/badge/Java-17+-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20macOS%20%7C%20Linux-0078D4?style=flat-square)](https://github.com/lakernote/easy-postman/releases)

[简体中文](README_zh.md) · [📦 Download](#-download-and-install) · [🤖 Install MCP](#-mcp-server) · [📖 Docs](#-documentation) · [💬 Discuss](https://github.com/lakernote/easy-postman/discussions) · WeChat: `lakernote`

</div>

---

## What is EasyPostman?

EasyPostman combines **Postman-style API debugging** and **JMeter-style performance testing** in one Java 17 desktop application. Collections, environments, and test data stay local by default, while Git workspaces support team collaboration. The same workspace can also run in CI through the headless CLI or be exposed to AI clients such as Codex, Claude Desktop, and Cursor through MCP.

| Core capability | What it provides |
|---|---|
| 🔌 API debugging | HTTP/HTTPS, SSE, WebSocket, auth, cookies, multiple body types, response inspection, and network logs |
| 🧩 Collections and scripts | Postman v2.1 / cURL import, environments, pre/post scripts, assertions, and request chaining |
| ⚡ Performance testing | Thread groups, timers, extractors, live metrics, reports, and distributed master/worker runs |
| 🏢 Local and Git workspaces | Isolated collections, environments, and settings with user-controlled storage |
| 🤖 MCP and headless CLI | Let AI run saved APIs; run collections, functional tests, and load plans in CI |

📖 [See the full feature list](docs/FEATURES.md)

## 🖼️ Preview

<table>
  <tr>
    <th width="50%">Postman-style API debugging</th>
    <th width="50%">JMeter-style performance testing</th>
  </tr>
  <tr>
    <td><a href="docs/collections.png"><img src="docs/collections.png" alt="Collections and response viewer" width="100%"></a></td>
    <td><a href="docs/performance-trend.png"><img src="docs/performance-trend.png" alt="Performance trend dashboard" width="100%"></a></td>
  </tr>
  <tr>
    <th width="50%">Scripts, assertions, and snippets</th>
    <th width="50%">Git workspace collaboration</th>
  </tr>
  <tr>
    <td><a href="docs/script-snippets.png"><img src="docs/script-snippets.png" alt="Script snippets and editor support" width="100%"></a></td>
    <td><a href="docs/workspaces-gitcommit.png"><img src="docs/workspaces-gitcommit.png" alt="Git workspace management" width="100%"></a></td>
  </tr>
</table>

📸 [View all screenshots](docs/SCREENSHOTS.md)

## 📦 Download and install

Download the latest release from **[GitHub Releases](https://github.com/lakernote/easy-postman/releases)**. A **[Gitee mirror](https://gitee.com/lakernote/easy-postman/releases)** is also available. Native installers and the Windows portable package include a runtime, so neither the desktop app nor MCP needs a separate Java installation. Only the cross-platform JAR requires Java 17+.

### Windows WinGet

```powershell
winget install --id Laker.EasyPostman --exact
```

Upgrade later with:

```powershell
winget upgrade --id Laker.EasyPostman --exact
```

### Choose a package

| Platform | File |
|---|---|
| macOS Apple Silicon | `EasyPostman-{version}-macos-arm64.dmg` |
| macOS Intel | `EasyPostman-{version}-macos-x86_64.dmg` |
| Windows installer / portable | `EasyPostman-{version}-windows-x64.exe` / `-portable.zip` |
| Debian / Ubuntu x64 | `EasyPostman-{version}-linux-amd64.deb` |
| Debian / Ubuntu ARM64 | `EasyPostman-{version}-linux-arm64.deb`; use `-compat.deb` only for older incompatible `dpkg` versions |
| RHEL / Rocky / CentOS / Fedora | `EasyPostman-{version}-1.x86_64.rpm` or `-1.aarch64.rpm` |
| Cross-platform JAR | `easy-postman-{version}.jar`, requires Java 17+ |

Windows SmartScreen or macOS Gatekeeper may show a warning on first launch. Choose “Run anyway,” or right-click the macOS app and choose “Open.” The project does not currently use a commercial code-signing certificate, and all source code is available for review.

## 🤖 MCP Server

The MCP Server is included in every EasyPostman release package, so there is **no separate MCP download or server to start manually**. The recommended native installers and Windows portable package bundle both `EasyPostmanMCP` and a JRE, so users do not need to install Java.

### Recommended setup

1. Install a native package from [Releases](https://github.com/lakernote/easy-postman/releases), or extract the Windows portable ZIP.
2. Add a local STDIO MCP Server in Codex, Claude Desktop, or Cursor and use the platform launcher as `command`:

| Platform | `command` |
|---|---|
| macOS | `/Applications/EasyPostman.app/Contents/MacOS/EasyPostmanMCP` |
| Windows installer | `C:\Program Files\EasyPostman\EasyPostmanMCP.exe` |
| Windows portable | `EasyPostmanMCP.exe` in the extracted directory, for example `D:\Tools\EasyPostman\EasyPostmanMCP.exe` |
| Linux DEB / RPM | `/opt/easypostman/bin/EasyPostmanMCP` |

The core macOS configuration is shown below; on another platform, replace only `command`:

```json
{
  "command": "/Applications/EasyPostman.app/Contents/MacOS/EasyPostmanMCP",
  "args": []
}
```

With Codex CLI:

```bash
codex mcp add easy-postman -- /Applications/EasyPostman.app/Contents/MacOS/EasyPostmanMCP
```

3. Save the configuration, restart the MCP client, and open a new conversation. A safe first test is: `Use EasyPostman MCP to list all workspaces. Do not run any request.`

Keep `args` empty for the default setup; no workspace path is required. The client starts one process per MCP connection and reuses it across tool calls—it does not restart EasyPostman for every API call. Keep the entire Windows portable directory together; `EasyPostmanMCP.exe` cannot be copied out by itself.

If you download only the cross-platform JAR, use this fallback and make sure Java 17+ is available:

```json
{
  "command": "java",
  "args": ["-jar", "/absolute/path/to/easy-postman-{version}.jar", "mcp", "serve"]
}
```

By default, MCP loads workspaces registered in EasyPostman. AI can call `list_workspaces`, then select a workspace and environment per tool call. The desktop app and MCP run as separate processes and can be used at the same time; MCP does not switch the desktop UI or change its current selection.

To authorize only one workspace, pass its absolute path in the native launcher's `args`; with the JAR fallback, append it after `"serve"`. The directory must contain `collections.json`; `environments.json` is optional.

JSON-based clients such as Claude Desktop and Cursor normally place the object above under `mcpServers.easy-postman`. Codex can also use MCP Server settings or `config.toml`. See the [official Codex MCP documentation](https://developers.openai.com/codex/mcp/).

### What AI can do

AI can list workspaces, collections, environments, and saved requests, then use `run_request` or `run_collection` to select a workspace and environment for that call and execute HTTP APIs. Environment values are not exposed by listing tools. API response bodies are returned to the MCP client, so do not authorize workspaces containing sensitive business data to an untrusted client.

MCP can discover and execute only saved HTTP requests in authorized workspaces; it cannot construct an arbitrary URL on demand. API response bodies are returned to the MCP client, and requests can cause real business side effects, so only trusted MCP clients should receive access to production workspaces.

📖 [Full MCP setup, tools, architecture, and security guide (Chinese)](docs/MCP_SERVER_DESIGN_zh.md)

## 🚀 Quick start

The basic desktop workflow:

1. Create a local workspace or connect a Git workspace.
2. Create or import a collection and select an environment.
3. Enter the URL, parameters, and authentication, then send the request.
4. Add scripts, assertions, functional tests, or a load-test plan as needed.

Build and run from source:

```bash
git clone https://github.com/lakernote/easy-postman.git
cd easy-postman
mvn -pl easy-postman-app -am -DskipTests clean package
java -jar easy-postman-app/target/easy-postman-*.jar
```

📖 [Full build guide (Chinese)](docs/BUILD_zh.md)

## 🧪 Headless CLI

The same JAR can run a native EasyPostman workspace without exporting collections or environments:

```bash
java -jar easy-postman.jar collection run /srv/api-workspace -c "Basic HTTP Examples" -e "Dev Env"
java -jar easy-postman.jar functional run /srv/api-workspace --bail --out target/result.json
```

- [Collection CLI guide (Chinese)](docs/COLLECTION_CLI_zh.md)
- [Functional CLI guide (Chinese)](docs/FUNCTIONAL_CLI_zh.md)
- [Distributed load testing guide (Chinese)](docs/PERFORMANCE_CLUSTER_LOAD_TEST_zh.md)

## 📚 Documentation

| Guide | Contents |
|---|---|
| [Feature reference](docs/FEATURES.md) | API debugging, scripts, Mock Server, performance testing, and workspaces |
| [MCP Server](docs/MCP_SERVER_DESIGN_zh.md) | Setup, tool inputs, workspace/environment routing, architecture, and security |
| [Collection CLI](docs/COLLECTION_CLI_zh.md) | Headless collection runs, variables, data, uploads, and CI exit codes |
| [Functional CLI](docs/FUNCTIONAL_CLI_zh.md) | Run functional tests from `functional_config.json` |
| [Plugin architecture](docs/PLUGINS_zh.md) | Plugin development and online/offline installation |
| [Script API](docs/SCRIPT_API_REFERENCE_zh.md) | Pre-request and test script reference |
| [Build guide](docs/BUILD_zh.md) | Source builds and native installers |
| [FAQ](docs/FQA.MD) | Installation and usage questions |

## 🛠️ Development and contributing

```bash
# Fast compile
mvn -q -pl easy-postman-app -am -DskipTests compile

# Full build and tests
mvn clean package
```

Bug reports, ideas, documentation, and code contributions are welcome: [create an issue](https://github.com/lakernote/easy-postman/issues/new/choose) · [contribution guide](.github/CONTRIBUTING.md)

## 🙏 Credits and support

EasyPostman builds on excellent open-source projects including [FlatLaf](https://github.com/JFormDesigner/FlatLaf), [RSyntaxTextArea](https://github.com/bobbylight/RSyntaxTextArea), and [OkHttp](https://github.com/square/okhttp).

If EasyPostman helps you, consider giving it a [Star](https://github.com/lakernote/easy-postman), joining the [discussions](https://github.com/lakernote/easy-postman/discussions), or contacting `lakernote` on WeChat.

<div align="center">

**Postman-style API debugging, JMeter-style performance testing**

Made with ❤️ by [laker](https://github.com/lakernote)

</div>
