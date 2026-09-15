#!/bin/bash

# 检查 java 是否可用
if ! command -v java &> /dev/null; then
    echo "❌ 未检测到 java，请先安装 JDK 17+ 并配置环境变量。"
    exit 1
fi
# 检查 mvn 是否可用
if ! command -v mvn &> /dev/null; then
    echo "❌ 未检测到 mvn，请先安装 Maven 并配置环境变量。"
    exit 1
fi
# 检查 jlink 是否可用
if ! command -v jlink &> /dev/null; then
    echo "❌ 未检测到 jlink，请确认 JDK 17+ 已正确安装。"
    exit 1
fi

# 获取项目根目录路径（包含 pom.xml）
PROJECT_ROOT=$(cd "$(dirname "$0")/.."; pwd)

# 切换到项目根目录并读取版本号
cd "$PROJECT_ROOT" || exit 1
# 直接从 pom.xml 解析 revision
VERSION=$(grep -m 1 '<revision>' pom.xml | sed 's/.*<revision>\(.*\)<\/revision>.*/\1/')
echo "🔧 开始打包 EasyPostman 版本: $VERSION"
APP_NAME="EasyPostman"
JAR_NAME_WITH_VERSION="easy-postman-$VERSION.jar"
JAR_NAME="easy-postman.jar"  # 固定名称，不带版本号
MAIN_CLASS="com.laker.postman.App"
ICON_DIR="assets/mac/EasyPostman.icns"
OUTPUT_DIR="dist"
APP_TARGET_DIR="easy-postman-app/target"

# 检查 icns 图标文件是否存在
if [ ! -f "$ICON_DIR" ]; then
    echo "❌ 图标文件 $ICON_DIR 不存在，请检查 assets/mac/EasyPostman.icns 是否存在。"
    exit 1
fi

# 检查 JDK 版本是否 >= 17
JAVA_VERSION=$(java -version 2>&1 | grep version | awk '{print substr($3, 2, 3)}' | tr -d '"')
if (( $(echo "$JAVA_VERSION < 17" | bc -l) )); then
    echo "❌ 需要 JDK 17 或更高版本。当前版本: $JAVA_VERSION"
    exit 1
fi

# Step 1: 清理 & 构建项目
echo "🚀 开始构建项目..."
mvn clean package -DskipTests=false
if [ $? -ne 0 ]; then
    echo "❌ Maven 构建失败，请检查错误日志"
    exit 1
fi
# 检查 jar 包是否生成成功
if [ ! -f "${APP_TARGET_DIR}/$JAR_NAME_WITH_VERSION" ]; then
    echo "❌ 构建未生成 jar 包: ${APP_TARGET_DIR}/$JAR_NAME_WITH_VERSION"
    exit 1
fi

# 重命名 JAR 为固定名称（方便 jpackage 配置和后续更新）
echo "📝 重命名 JAR: $JAR_NAME_WITH_VERSION -> $JAR_NAME"
cp "${APP_TARGET_DIR}/$JAR_NAME_WITH_VERSION" "${APP_TARGET_DIR}/$JAR_NAME"

# Step 2: 创建最小运行时 jlink
echo "⚙️ 使用 jlink 创建最小化运行时..."
rm -rf target/runtime
jlink \
    --add-modules java.base,java.desktop,java.logging,jdk.unsupported,java.naming,java.net.http,jdk.httpserver,java.prefs,java.sql,java.security.sasl,java.security.jgss,jdk.crypto.ec,java.management,java.management.rmi,jdk.crypto.cryptoki \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --compress=2 \
    --output target/runtime

if [ $? -ne 0 ]; then
    echo "❌ jlink 创建失败"
    exit 1
fi

# Step 3: 准备输出目录
mkdir -p ${OUTPUT_DIR}

# Step 3.5: 创建仅包含主 jar 的临时目录
DIST_INPUT_DIR="target/dist-input"
rm -rf ${DIST_INPUT_DIR}
mkdir -p ${DIST_INPUT_DIR}
cp "${APP_TARGET_DIR}/${JAR_NAME}" ${DIST_INPUT_DIR}/
echo "✅ 使用固定 JAR 名称: ${JAR_NAME}"

# Step 4: 使用 jpackage 打包 DMG
# --input 只需要包含最终运行所需的 jar 包和依赖。
echo "📦 使用 jpackage 打包 macOS 应用..."
jpackage \
    --input ${DIST_INPUT_DIR} \
    --main-jar ${JAR_NAME} \
    --main-class ${MAIN_CLASS} \
    --add-launcher EasyPostmanMCP=build/easy-postman-mcp-launcher.properties \
    --runtime-image target/runtime \
    --type dmg \
    --name "${APP_NAME}" \
    --app-version "${VERSION}" \
    --dest "${OUTPUT_DIR}" \
    --icon "${ICON_DIR}" \
    --vendor "Laker" \
    --copyright "© 2025 Laker" \
    --java-options "-Xms256m" \
    --java-options "-Xmx1g" \
    --java-options "-XX:MaxMetaspaceSize=256m" \
    --java-options "-XX:MetaspaceSize=128m" \
    --java-options "-XX:MaxDirectMemorySize=256m" \
    --java-options "-XX:+UseG1GC" \
    --java-options "-XX:MaxGCPauseMillis=200" \
    --java-options "-XX:InitiatingHeapOccupancyPercent=45" \
    --java-options "-XX:+UseStringDeduplication" \
    --java-options "-XX:+HeapDumpOnOutOfMemoryError" \
    --java-options "-XX:HeapDumpPath=./dumps" \
    --java-options "-Dfile.encoding=UTF-8" \
    --java-options "-Dsun.java2d.metal=false" \
    --java-options "-Dapple.awt.application.appearance=system" \
    --java-options "-Dswing.aatext=true" \
    --java-options "-Dhttp.keepAlive=true" \
    --java-options "--add-opens java.desktop/java.awt=ALL-UNNAMED" \
    --java-options "--add-opens java.desktop/sun.lwawt=ALL-UNNAMED" \
    --java-options "--add-opens java.desktop/sun.lwawt.macosx=ALL-UNNAMED" \
    --java-options "--add-exports java.desktop/com.apple.eawt=ALL-UNNAMED"

# 显示文件大小统计
JAR_SIZE=$(du -h "${APP_TARGET_DIR}/${JAR_NAME}" | awk '{print $1}')
RUNTIME_SIZE=$(du -sh target/runtime | awk '{print $1}')
DMG_SIZE=$(du -h "${OUTPUT_DIR}/${APP_NAME}-${VERSION}.dmg" | awk '{print $1}')

# 完成提示
echo "🎉 打包完成！输出路径：$(pwd)/${OUTPUT_DIR}"
echo ""
echo "📊 大小对比："
echo "   - Fat JAR: ${JAR_SIZE}"
echo "   - Runtime: ${RUNTIME_SIZE}"
echo "   - DMG: ${DMG_SIZE}"
