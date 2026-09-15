# Build Scripts

`build/` 目录只负责发行打包，不放插件开发和验证脚本。

当前脚本：

- `mac.sh`: macOS DMG
- `win-exe.bat`: Windows EXE / portable
- `linux-deb.sh`: Debian / Ubuntu DEB（产物架构跟随当前打包机器，例如 `amd64` / `arm64`）
- `linux-rpm.sh`: RPM（产物架构跟随当前打包机器）

统一约定：

- Maven 先产出带版本号的 jar：
  `easy-postman-app/target/easy-postman-${version}.jar`
- 打包脚本再复制成固定名：
  `easy-postman.jar`
- `jpackage` 和安装包内部始终引用固定文件名
- `jpackage` 通过 `easy-postman-mcp-launcher.properties` 额外生成 `EasyPostmanMCP`；它与桌面启动器共享主 JAR 和内置 JRE，Windows 版本使用 console launcher

Windows `app-image` 和便携版 ZIP 的核心结构是：

```text
EasyPostman/
├── EasyPostman.exe
├── EasyPostmanMCP.exe
├── app/
├── runtime/
└── .portable        # 仅便携版存在
```

便携版是完整 `app-image` 加 `.portable` 标记，不需要另装 Java。`EasyPostmanMCP.exe` 依赖同目录中的 `app/` 和 `runtime/`，不能单独发布或拷贝。

打包后可先执行 `EasyPostmanMCP.exe --help` 验证 MCP 启动器和内置 JRE，再打开 `EasyPostman.exe` 验证桌面端。

这样做的好处：

- 打包脚本不需要跟着版本号改
- 安装包内部 classpath 固定
- 自动更新和替换主 jar 更简单

如果你要做的是：

- 发行包构建：看 `build/`
- 插件本地开发/安装验证：看 `scripts/plugin-dev.sh`
- 插件架构与使用说明：看 `docs/PLUGINS_zh.md`
