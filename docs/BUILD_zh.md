# 🚀 构建指南

这份文档只讲两件事：

- 从源码构建 EasyPostman
- 生成各平台发行包

插件架构、本地插件开发、在线/离线安装说明，统一见：

- `docs/PLUGINS_zh.md`

## 前置要求

### 必需

- **Java 17** 或更高版本
- **Maven 3.6+**
- **Git**

### 推荐

- **[JetBrains Runtime (JBR)](https://github.com/JetBrains/JetBrainsRuntime)** - 为 Swing 应用优化的 JDK

> 💡 **为什么选择 JetBrains Runtime？**
> 
> JetBrains Runtime 是专门为 Swing/AWT 应用优化的 JDK 发行版，提供：
> - ✨ 更好的 Swing/AWT 渲染性能
> - 🎨 改进的字体渲染和 HiDPI 支持
> - 🐛 修复了标准 JDK 中的 Swing 相关 bug
> - 🚀 针对桌面应用优化的 GC 和性能调优
> 
> **下载地址**: [JetBrains Runtime Releases](https://github.com/JetBrains/JetBrainsRuntime/releases)

---

## 🔧 从源码构建

### 1. 克隆仓库

```bash
# GitHub
git clone https://github.com/lakernote/easy-postman.git
cd easy-postman

# 或 Gitee（国内镜像）
git clone https://gitee.com/lakernote/easy-postman.git
cd easy-postman
```

### 2. 构建项目

```bash
# 清理并打包
mvn clean package

# 跳过测试（更快）
mvn clean package -DskipTests
```

这将生成：
- `easy-postman-app/target/easy-postman-{版本号}.jar` - 主程序可执行 JAR
- `easy-postman-mcp/target/easy-postman-mcp-{版本号}.jar` - MCP 协议模块；发布时会合并进主程序 JAR，不需要单独分发
- `easy-postman-plugins/plugin-*/target/easy-postman-{版本号}-plugin-*.jar` - 插件 JAR

### 3. 运行应用

```bash
# 直接运行
java -jar easy-postman-app/target/easy-postman-*.jar

# 或使用自定义 JVM 选项
java -Xms256m -Xmx2g -jar easy-postman-app/target/easy-postman-*.jar

# 验证主 JAR 内置的 MCP Server
java -jar easy-postman-app/target/easy-postman-*.jar mcp serve --help
```

### 插件本地构建

如果要本地构建单个插件，直接用 Maven 即可：

```bash
mvn -pl easy-postman-app,easy-postman-plugins/plugin-redis -am clean package -DskipTests
```

如果要一次性构建全部插件：

```bash
mvn clean package -DskipTests
```

更多插件安装、离线包、catalog 和独立发版说明，见 `docs/PLUGINS_zh.md`。

---

## 📦 生成原生安装包

### macOS

#### 要求
- macOS 10.13+（High Sierra 或更高版本）
- Xcode 命令行工具：`xcode-select --install`
- 支持 jpackage 的 JDK 17+

#### 构建 DMG

```bash
# 赋予脚本执行权限
chmod +x build/mac.sh

# 运行构建脚本
./build/mac.sh
```

**输出**: `dist/EasyPostman-{版本号}-macos-{架构}.dmg`
- Apple Silicon (M1/M2/M3/M4): `macos-arm64.dmg`
- Intel: `macos-x86_64.dmg`
- 安装后的 `.app` 同时包含 `Contents/MacOS/EasyPostman` 和 `Contents/MacOS/EasyPostmanMCP`，二者共享主 JAR 与内置 JRE

#### 手动构建

```bash
# 准备 jpackage 输入；安装包内部统一使用固定 JAR 名
mkdir -p target/dist-input
cp easy-postman-app/target/easy-postman-{版本号}.jar target/dist-input/easy-postman.jar

# Apple Silicon
jpackage \
  --input target/dist-input \
  --name EasyPostman \
  --main-jar easy-postman.jar \
  --main-class com.laker.postman.App \
  --add-launcher EasyPostmanMCP=build/easy-postman-mcp-launcher.properties \
  --type dmg \
  --icon assets/mac/EasyPostman.icns \
  --app-version {版本号} \
  --vendor "laker" \
  --mac-package-name "EasyPostman"

# Intel
jpackage \
  --input target/dist-input \
  --name EasyPostman \
  --main-jar easy-postman.jar \
  --main-class com.laker.postman.App \
  --add-launcher EasyPostmanMCP=build/easy-postman-mcp-launcher.properties \
  --type dmg \
  --icon assets/mac/EasyPostman.icns \
  --app-version {版本号} \
  --vendor "laker" \
  --mac-package-name "EasyPostman" \
  --java-options "--add-opens java.base/java.lang=ALL-UNNAMED"
```

---

### Windows

#### 要求
- Windows 10/11
- 支持 jpackage 的 JDK 17+
- [WiX Toolset 3.11+](https://wixtoolset.org/)（用于 MSI）
- [Inno Setup 6+](https://jrsoftware.org/isinfo.php)（用于 EXE 安装程序）

#### 构建 EXE 安装程序

```batch
# 运行构建脚本
build\win-exe.bat
```

**输出**:
- `dist/EasyPostman-{版本号}-windows-x64.exe` - 安装程序
- `dist/EasyPostman-{版本号}-windows-x64-portable.zip` - 便携版
- 安装目录和便携版 ZIP 都包含 `EasyPostman.exe`、`EasyPostmanMCP.exe`、`app/` 与 `runtime/`；便携版完整解压后即可使用 MCP，不需要 Java

#### 手动构建

```batch
rem 准备 jpackage 输入
if not exist target\dist-input mkdir target\dist-input
copy easy-postman-app\target\easy-postman-{版本号}.jar target\dist-input\easy-postman.jar

rem 创建包含两个启动器和内置 JRE 的应用目录
jpackage ^
  --type app-image ^
  --input target\dist-input ^
  --name EasyPostman ^
  --main-jar easy-postman.jar ^
  --main-class com.laker.postman.App ^
  --add-launcher EasyPostmanMCP=build\easy-postman-mcp-launcher.properties ^
  --dest target ^
  --icon assets\win\EasyPostman.ico ^
  --app-version {版本号} ^
  --vendor "laker"

rem 创建便携版 ZIP；正式 EXE 安装器请直接运行 build\win-exe.bat
echo This is a portable version>target\EasyPostman\.portable
mkdir target\portable
xcopy target\EasyPostman target\portable\EasyPostman\ /E /I
cd target\portable
powershell Compress-Archive -Path EasyPostman -DestinationPath ..\EasyPostman-{版本号}-windows-x64-portable.zip
cd ..\..
```

---

### Linux (Ubuntu/Debian)

#### 要求
- Ubuntu 18.04+ 或 Debian 10+
- 支持 jpackage 的 JDK 17+
- dpkg-dev: `sudo apt install dpkg-dev`

#### 构建 DEB 包

```bash
# 赋予脚本执行权限
chmod +x build/linux-deb.sh

# 运行构建脚本
./build/linux-deb.sh
```

**输出**: `dist/` 目录下生成与当前打包机器架构对应的 DEB 文件，例如 `dist/*.deb`

安装后桌面启动器和 MCP 启动器分别位于 `/opt/easypostman/bin/EasyPostman` 与 `/opt/easypostman/bin/EasyPostmanMCP`，共享内置 JRE。

#### 手动构建

```bash
mkdir -p target/dist-input
cp easy-postman-app/target/easy-postman-{版本号}.jar target/dist-input/easy-postman.jar

jpackage \
  --input target/dist-input \
  --name easypostman \
  --main-jar easy-postman.jar \
  --main-class com.laker.postman.App \
  --add-launcher EasyPostmanMCP=build/easy-postman-mcp-launcher.properties \
  --type deb \
  --icon assets/linux/EasyPostman.png \
  --app-version {版本号} \
  --vendor "laker" \
  --linux-package-name "easypostman" \
  --linux-menu-group "Development" \
  --linux-shortcut
```

#### 安装 DEB 包

```bash
sudo dpkg -i dist/<generated-package>.deb

# 如果缺少依赖
sudo apt-get install -f
```

---

### Linux (基于 RPM)

#### 要求
- Fedora/RHEL/CentOS
- 支持 jpackage 的 JDK 17+
- rpm-build: `sudo dnf install rpm-build`

#### 构建 RPM 包

```bash
# 赋予脚本执行权限
chmod +x build/linux-rpm.sh

# 运行构建脚本
./build/linux-rpm.sh
```

**输出**: `dist/` 目录下生成与当前打包机器架构对应的 RPM 文件，例如 `dist/*.rpm`

---

## 🔍 高级构建选项

### 自定义 JVM 选项

编辑启动脚本或使用 jpackage `--java-options`：

```bash
jpackage \
  --java-options "-Xms256m" \
  --java-options "-Xmx2g" \
  --java-options "-Dfile.encoding=UTF-8" \
  # ... 其他选项
```

### 使用 jlink 创建自定义运行时

创建仅包含所需模块的最小 JRE：

```bash
# 列出所需模块
jdeps --print-module-deps target/easy-postman-{版本号}.jar

# 创建自定义运行时
jlink \
  --add-modules java.base,java.desktop,java.logging,java.naming,java.net.http,jdk.httpserver,java.sql,java.xml \
  --output target/custom-runtime \
  --strip-debug \
  --compress 2 \
  --no-header-files \
  --no-man-pages

# 使用自定义运行时打包
jpackage \
  --runtime-image target/custom-runtime \
  # ... 其他选项
```

---

## 🐛 故障排除

### macOS: "应用已损坏，无法打开"

```bash
# 移除隔离属性
sudo xattr -rd com.apple.quarantine /Applications/EasyPostman.app
```

### Windows: "Windows 已保护你的电脑"

点击"更多信息" → "仍要运行"。这是未签名应用的 SmartScreen 警告。

### Linux: 缺少依赖

```bash
# Ubuntu/Debian
sudo apt-get install -f

# Fedora/RHEL
sudo dnf install <缺少的包>
```

### 构建失败，提示 "jpackage: command not found"

确保使用的是 JDK 17+（不是 JRE）且 jpackage 在 PATH 中：

```bash
# 检查 Java 版本
java -version

# 检查 jpackage
jpackage --version

# 如果未找到，使用完整路径
$JAVA_HOME/bin/jpackage --version
```

---

## 📚 其他资源

- [jpackage 文档](https://docs.oracle.com/en/java/javase/17/jpackage/)
- [Maven 文档](https://maven.apache.org/guides/)
- [JetBrains Runtime](https://github.com/JetBrains/JetBrainsRuntime)

---

## 💬 需要帮助？

- 💬 GitHub 讨论区: [https://github.com/lakernote/easy-postman/discussions](https://github.com/lakernote/easy-postman/discussions)
- 🐛 报告问题: [https://github.com/lakernote/easy-postman/issues](https://github.com/lakernote/easy-postman/issues)
- 👥 微信群: 添加 **lakernote** 加入
