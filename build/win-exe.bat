@echo off
SETLOCAL ENABLEEXTENSIONS

:: ====================================================================
:: EasyPostman Windows EXE 安装包构建脚本
:: 使用 jpackage + Inno Setup 创建 EXE 安装程序
:: ====================================================================

echo.
echo ========================================
echo   EasyPostman EXE Installer Builder
echo ========================================
echo.

:: Step 1: 定位项目根目录
cd /d "%~dp0%\.."
set PROJECT_ROOT=%cd%

echo [1/9] 检查项目环境...
if not exist "pom.xml" (
    echo ERROR: pom.xml not found in current directory!
    echo Expected location: %cd%\pom.xml
    pause
    exit /b 1
)

:: Step 2: 从 pom.xml 获取版本号
echo [2/9] 提取版本号...
FOR /F "tokens=* USEBACKQ" %%F IN (`
    powershell -Command "[regex]::Match((Get-Content '%cd%\pom.xml' -Raw), '<revision>(.*?)</revision>').Groups[1].Value"
`) DO SET VERSION=%%F

if not defined VERSION (
    echo ERROR: Failed to get version from pom.xml
    pause
    exit /b 1
)

echo         Version: %VERSION%

:: 设置变量
set APP_NAME=EasyPostman
set JAR_NAME_WITH_VERSION=easy-postman-%VERSION%.jar
set JAR_NAME=easy-postman.jar
set MAIN_CLASS=com.laker.postman.App
set ICON_FILE=assets\win\EasyPostman.ico
set ISS_SCRIPT=build\easy-postman.iss
set OUTPUT_DIR=dist
set ARCH=x64
set APP_TARGET_DIR=easy-postman-app\target

:: Step 3: 检查 Java 版本
echo [3/9] 检查 Java 版本...
for /f tokens^=2^ delims^=^" %%a in ('java -version 2^>^&1 ^| findstr version') do set "JVER=%%a"
for /f tokens^=1^ delims^=.^" %%b in ("%JVER%") do set "MAJOR_JVER=%%b"

if %MAJOR_JVER% lss 17 (
    echo ERROR: JDK 17 or higher is required. Current version: %JVER%
    pause
    exit /b 1
)
echo         Java Version: %JVER% [OK]

:: Step 4: 检查 Inno Setup 是否安装
echo [4/9] 检查 Inno Setup...
where iscc >nul 2>nul
if errorlevel 1 (
    echo ERROR: Inno Setup not found!
    echo Please install Inno Setup from: https://jrsoftware.org/isdl.php
    echo Or install via Chocolatey: choco install innosetup
    pause
    exit /b 1
)
for /f "delims=" %%i in ('where iscc') do set ISCC_PATH=%%i
echo         Inno Setup: %ISCC_PATH% [OK]

:: Step 5: 构建项目
echo [5/9] 使用 Maven 构建项目...
call mvn clean package -DskipTests
if errorlevel 1 (
    echo ERROR: Maven build failed
    pause
    exit /b 1
)

:: 检查 JAR 文件是否生成
if not exist "%APP_TARGET_DIR%\%JAR_NAME_WITH_VERSION%" (
    echo ERROR: JAR file not found: %APP_TARGET_DIR%\%JAR_NAME_WITH_VERSION%
    pause
    exit /b 1
)
echo         JAR: %APP_TARGET_DIR%\%JAR_NAME_WITH_VERSION% [OK]

:: Step 6: 创建精简 JRE (jlink)
echo [6/9] 使用 jlink 创建精简 JRE...
if exist target\runtime rd /s /q target\runtime
jlink ^
    --add-modules java.base,java.desktop,java.logging,jdk.unsupported,java.naming,java.net.http,jdk.httpserver,java.prefs,java.sql,java.security.sasl,java.security.jgss,jdk.crypto.ec,jdk.crypto.mscapi,java.management,java.management.rmi,jdk.crypto.cryptoki ^
    --strip-debug ^
    --no-header-files ^
    --no-man-pages ^
    --compress=2 ^
    --output target\runtime
if errorlevel 1 (
    echo ERROR: jlink failed
    pause
    exit /b 1
)
echo         Runtime: target\runtime [OK]

:: Step 7: 准备打包输入
echo [7/9] 准备打包输入...
set DIST_INPUT_DIR=target\dist-input
if exist %DIST_INPUT_DIR% rd /s /q %DIST_INPUT_DIR%
mkdir %DIST_INPUT_DIR%

:: 重命名 JAR 为固定名称
copy "%APP_TARGET_DIR%\%JAR_NAME_WITH_VERSION%" "%DIST_INPUT_DIR%\%JAR_NAME%" >nul
if errorlevel 1 (
    echo ERROR: Failed to copy JAR file
    pause
    exit /b 1
)
echo         Input: %DIST_INPUT_DIR%\%JAR_NAME% [OK]

:: Step 8: 使用 jpackage 创建应用镜像
echo [8/9] 使用 jpackage 创建应用镜像...
if exist target\%APP_NAME% rd /s /q target\%APP_NAME%

jpackage ^
    --type app-image ^
    --input %DIST_INPUT_DIR% ^
    --main-jar %JAR_NAME% ^
    --main-class %MAIN_CLASS% ^
    --add-launcher EasyPostmanMCP=build\easy-postman-mcp-launcher.properties ^
    --runtime-image target\runtime ^
    --dest target ^
    --icon %ICON_FILE% ^
    --name %APP_NAME% ^
    --app-version %VERSION% ^
    --vendor "Laker" ^
    --copyright "© 2025 Laker" ^
    --java-options "-Xms256m" ^
    --java-options "-Xmx1g" ^
    --java-options "-XX:MaxMetaspaceSize=256m" ^
    --java-options "-XX:MetaspaceSize=128m" ^
    --java-options "-XX:MaxDirectMemorySize=256m" ^
    --java-options "-XX:+UseG1GC" ^
    --java-options "-XX:MaxGCPauseMillis=200" ^
    --java-options "-XX:InitiatingHeapOccupancyPercent=45" ^
    --java-options "-XX:+UseStringDeduplication" ^
    --java-options "-XX:+HeapDumpOnOutOfMemoryError" ^
    --java-options "-XX:HeapDumpPath=./dumps" ^
    --java-options "-Dfile.encoding=UTF-8" ^
    --java-options "-Dswing.aatext=true" ^
    --java-options "-Dhttp.keepAlive=true" ^
    --java-options "-Djavax.accessibility.assistive_technologies="

if errorlevel 1 (
    echo ERROR: jpackage failed
    pause
    exit /b 1
)

if not exist "target\%APP_NAME%\%APP_NAME%.exe" (
    echo ERROR: App image not created properly
    pause
    exit /b 1
)
if not exist "target\%APP_NAME%\EasyPostmanMCP.exe" (
    echo ERROR: MCP launcher was not created
    pause
    exit /b 1
)
echo         App Image: target\%APP_NAME% [OK]

:: Step 9: 使用 Inno Setup 创建 EXE 安装程序
echo [9/9] 使用 Inno Setup 创建 EXE 安装程序...
if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"

if not exist "%ISS_SCRIPT%" (
    echo ERROR: Inno Setup script not found: %ISS_SCRIPT%
    pause
    exit /b 1
)

:: 注意：路径相对于 ISS 脚本位置（build 目录），所以需要 ..\
iscc ^
    /DMyAppVersion=%VERSION% ^
    /DMyAppSourceDir=..\target\%APP_NAME% ^
    /DMyOutputDir=..\%OUTPUT_DIR% ^
    /DMyArch=%ARCH% ^
    %ISS_SCRIPT%

if errorlevel 1 (
    echo ERROR: Inno Setup compilation failed
    pause
    exit /b 1
)

:: 验证输出文件
set EXE_FILE=%OUTPUT_DIR%\%APP_NAME%-%VERSION%-windows-%ARCH%.exe
if not exist "%EXE_FILE%" (
    echo ERROR: EXE file not found: %EXE_FILE%
    echo Files in %OUTPUT_DIR%:
    dir /b "%OUTPUT_DIR%"
    pause
    exit /b 1
)

:: 显示文件大小
for %%F in ("%EXE_FILE%") do set SIZE=%%~zF
set /a SIZE_MB=%SIZE% / 1048576

:: 额外生成便携版 ZIP，并写入 .portable 标识
echo [Portable] 创建便携版 ZIP...
set PORTABLE_ROOT=target\portable
set PORTABLE_APP_DIR=%PORTABLE_ROOT%\%APP_NAME%
set PORTABLE_ZIP=%OUTPUT_DIR%\%APP_NAME%-%VERSION%-windows-%ARCH%-portable.zip

if exist "%PORTABLE_ROOT%" rd /s /q "%PORTABLE_ROOT%"
mkdir "%PORTABLE_APP_DIR%"
xcopy "target\%APP_NAME%" "%PORTABLE_APP_DIR%\" /E /I /Y >nul
if errorlevel 1 (
    echo ERROR: Failed to copy portable app image
    pause
    exit /b 1
)

echo This is a portable version>"%PORTABLE_APP_DIR%\.portable"
powershell -Command "if (Test-Path '%PORTABLE_ZIP%') { Remove-Item '%PORTABLE_ZIP%' -Force }; Compress-Archive -Path '%PORTABLE_APP_DIR%' -DestinationPath '%PORTABLE_ZIP%' -Force"
if errorlevel 1 (
    echo ERROR: Failed to create portable ZIP
    pause
    exit /b 1
)

echo.
echo ========================================
echo   构建成功！
echo ========================================
echo   文件: %EXE_FILE%
echo   大小: %SIZE_MB% MB
echo   路径: %cd%\%OUTPUT_DIR%
echo   便携版: %PORTABLE_ZIP%
echo ========================================
echo.
echo 使用方法:
echo   - 交互式安装: %APP_NAME%-%VERSION%-windows-%ARCH%.exe
echo   - 静默安装:   %APP_NAME%-%VERSION%-windows-%ARCH%.exe /VERYSILENT
echo   - 静默+启动:  %APP_NAME%-%VERSION%-windows-%ARCH%.exe /VERYSILENT /AUTOSTART
echo   - 便携版 ZIP: %APP_NAME%-%VERSION%-windows-%ARCH%-portable.zip
echo.

ENDLOCAL
pause
