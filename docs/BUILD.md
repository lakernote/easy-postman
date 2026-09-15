# 🚀 Build Guide

## Prerequisites

### Required

- **Java 17** or higher
- **Maven 3.6+**
- **Git**

### Recommended

- **[JetBrains Runtime (JBR)](https://github.com/JetBrains/JetBrainsRuntime)** - Optimized JDK for Swing applications

> 💡 **Why JetBrains Runtime?**
> 
> JetBrains Runtime is a JDK distribution optimized for Swing/AWT applications, providing:
> - ✨ Better Swing/AWT rendering performance
> - 🎨 Improved font rendering and HiDPI support
> - 🐛 Fixes for Swing-related bugs in standard JDK
> - 🚀 Optimized GC and performance tuning for desktop applications
> 
> **Download**: [JetBrains Runtime Releases](https://github.com/JetBrains/JetBrainsRuntime/releases)

---

## 🔧 Build from Source

### 1. Clone the Repository

```bash
# GitHub
git clone https://github.com/lakernote/easy-postman.git
cd easy-postman

# Or Gitee (China mirror)
git clone https://gitee.com/lakernote/easy-postman.git
cd easy-postman
```

### 2. Build the Project

```bash
# Clean and package
mvn clean package

# Skip tests (faster)
mvn clean package -DskipTests
```

This will generate:
- `easy-postman-app/target/easy-postman-{version}.jar` - Main executable JAR
- `easy-postman-mcp/target/easy-postman-mcp-{version}.jar` - MCP protocol module; it is merged into the main release JAR and is not distributed separately

### 3. Run the Application

```bash
# Run directly
java -jar easy-postman-app/target/easy-postman-*.jar

# Or with custom JVM options
java -Xms256m -Xmx2g -jar easy-postman-app/target/easy-postman-*.jar

# Verify the MCP Server included in the main JAR
java -jar easy-postman-app/target/easy-postman-*.jar mcp serve --help
```

---

## 📦 Generate Native Installers

### macOS

#### Requirements
- macOS 10.13+ (High Sierra or later)
- Xcode Command Line Tools: `xcode-select --install`
- JDK 17+ with jpackage support

#### Build DMG

```bash
# Make script executable
chmod +x build/mac.sh

# Run build script
./build/mac.sh
```

**Output**: `dist/EasyPostman-{version}-macos-{arch}.dmg`
- For Apple Silicon (M1/M2/M3/M4): `macos-arm64.dmg`
- For Intel: `macos-x86_64.dmg`
- The installed app contains both `Contents/MacOS/EasyPostman` and `Contents/MacOS/EasyPostmanMCP`, sharing the main JAR and bundled JRE

#### Manual Build

```bash
# Prepare jpackage input with the fixed JAR name used inside packages
mkdir -p target/dist-input
cp easy-postman-app/target/easy-postman-{version}.jar target/dist-input/easy-postman.jar

# For Apple Silicon
jpackage \
  --input target/dist-input \
  --name EasyPostman \
  --main-jar easy-postman.jar \
  --main-class com.laker.postman.App \
  --add-launcher EasyPostmanMCP=build/easy-postman-mcp-launcher.properties \
  --type dmg \
  --icon assets/mac/EasyPostman.icns \
  --app-version {version} \
  --vendor "laker" \
  --mac-package-name "EasyPostman"

# For Intel
jpackage \
  --input target/dist-input \
  --name EasyPostman \
  --main-jar easy-postman.jar \
  --main-class com.laker.postman.App \
  --add-launcher EasyPostmanMCP=build/easy-postman-mcp-launcher.properties \
  --type dmg \
  --icon assets/mac/EasyPostman.icns \
  --app-version {version} \
  --vendor "laker" \
  --mac-package-name "EasyPostman" \
  --java-options "--add-opens java.base/java.lang=ALL-UNNAMED"
```

---

### Windows

#### Requirements
- Windows 10/11
- JDK 17+ with jpackage support
- [WiX Toolset 3.11+](https://wixtoolset.org/) (for MSI)
- [Inno Setup 6+](https://jrsoftware.org/isinfo.php) (for EXE installer)

#### Build EXE Installer

```batch
# Run build script
build\win-exe.bat
```

**Output**:
- `dist/EasyPostman-{version}-windows-x64.exe` - Installer
- `dist/EasyPostman-{version}-windows-x64-portable.zip` - Portable version
- Both contain `EasyPostman.exe`, `EasyPostmanMCP.exe`, `app/`, and `runtime/`; after extracting the portable ZIP, MCP works without a separate Java installation

#### Manual Build

```batch
rem Prepare jpackage input
if not exist target\dist-input mkdir target\dist-input
copy easy-postman-app\target\easy-postman-{version}.jar target\dist-input\easy-postman.jar

rem Create an app image with both launchers and the bundled JRE
jpackage ^
  --type app-image ^
  --input target\dist-input ^
  --name EasyPostman ^
  --main-jar easy-postman.jar ^
  --main-class com.laker.postman.App ^
  --add-launcher EasyPostmanMCP=build\easy-postman-mcp-launcher.properties ^
  --dest target ^
  --icon assets\win\EasyPostman.ico ^
  --app-version {version} ^
  --vendor "laker"

rem Create a portable ZIP; run build\win-exe.bat for the production EXE installer
echo This is a portable version>target\EasyPostman\.portable
mkdir target\portable
xcopy target\EasyPostman target\portable\EasyPostman\ /E /I
cd target\portable
powershell Compress-Archive -Path EasyPostman -DestinationPath ..\EasyPostman-{version}-windows-x64-portable.zip
cd ..\..
```

---

### Linux (Ubuntu/Debian)

#### Requirements
- Ubuntu 18.04+ or Debian 10+
- JDK 17+ with jpackage support
- dpkg-dev: `sudo apt install dpkg-dev`

#### Build DEB Package

```bash
# Make script executable
chmod +x build/linux-deb.sh

# Run build script
./build/linux-deb.sh
```

**Output**: a DEB file under `dist/` for the current build machine architecture, for example `dist/*.deb`

After installation, `/opt/easypostman/bin/EasyPostman` and `/opt/easypostman/bin/EasyPostmanMCP` share the bundled JRE.

#### Manual Build

```bash
mkdir -p target/dist-input
cp easy-postman-app/target/easy-postman-{version}.jar target/dist-input/easy-postman.jar

jpackage \
  --input target/dist-input \
  --name easypostman \
  --main-jar easy-postman.jar \
  --main-class com.laker.postman.App \
  --add-launcher EasyPostmanMCP=build/easy-postman-mcp-launcher.properties \
  --type deb \
  --icon assets/linux/EasyPostman.png \
  --app-version {version} \
  --vendor "laker" \
  --linux-package-name "easypostman" \
  --linux-menu-group "Development" \
  --linux-shortcut
```

#### Install DEB Package

```bash
sudo dpkg -i dist/<generated-package>.deb

# If dependencies missing
sudo apt-get install -f
```

---

### Linux (RPM-based)

#### Requirements
- Fedora/RHEL/CentOS
- JDK 17+ with jpackage support
- rpm-build: `sudo dnf install rpm-build`

#### Build RPM Package

```bash
# Make script executable
chmod +x build/linux-rpm.sh

# Run build script
./build/linux-rpm.sh
```

**Output**: an RPM file under `dist/` for the current build machine architecture, for example `dist/*.rpm`

---

## 🔍 Advanced Build Options

### Custom JVM Options

Edit the launcher scripts or use jpackage `--java-options`:

```bash
jpackage \
  --java-options "-Xms256m" \
  --java-options "-Xmx2g" \
  --java-options "-Dfile.encoding=UTF-8" \
  # ... other options
```

### Custom Runtime with jlink

Create a minimal JRE with only required modules:

```bash
# List required modules
jdeps --print-module-deps target/easy-postman-{version}.jar

# Create custom runtime
jlink \
  --add-modules java.base,java.desktop,java.logging,java.naming,java.net.http,jdk.httpserver,java.sql,java.xml \
  --output target/custom-runtime \
  --strip-debug \
  --compress 2 \
  --no-header-files \
  --no-man-pages

# Package with custom runtime
jpackage \
  --runtime-image target/custom-runtime \
  # ... other options
```

---

## 🐛 Troubleshooting

### macOS: "App is damaged and can't be opened"

```bash
# Remove quarantine attribute
sudo xattr -rd com.apple.quarantine /Applications/EasyPostman.app
```

### Windows: "Windows protected your PC"

Click "More info" → "Run anyway". This is a SmartScreen warning for unsigned applications.

### Linux: Missing dependencies

```bash
# Ubuntu/Debian
sudo apt-get install -f

# Fedora/RHEL
sudo dnf install <missing-package>
```

### Build fails with "jpackage: command not found"

Ensure you're using JDK 17+ (not JRE) and jpackage is in PATH:

```bash
# Check Java version
java -version

# Check jpackage
jpackage --version

# If not found, use full path
$JAVA_HOME/bin/jpackage --version
```

---

## 📚 Additional Resources

- [jpackage Documentation](https://docs.oracle.com/en/java/javase/17/jpackage/)
- [Maven Documentation](https://maven.apache.org/guides/)
- [JetBrains Runtime](https://github.com/JetBrains/JetBrainsRuntime)

---

## 💬 Need Help?

- 💬 GitHub Discussions: [https://github.com/lakernote/easy-postman/discussions](https://github.com/lakernote/easy-postman/discussions)
- 🐛 Report Issues: [https://github.com/lakernote/easy-postman/issues](https://github.com/lakernote/easy-postman/issues)
- 👥 WeChat Group: Add **lakernote** to join
