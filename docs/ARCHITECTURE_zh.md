# 🏗️ 系统架构

## 架构概览

```
EasyPostman
├── 🎨 用户界面层 (UI Layer)
│   ├── Workspace 工作区管理
│   ├── Collections 接口集合管理
│   ├── Environments 环境变量配置
│   ├── History 请求历史记录
│   ├── Performance 性能测试模块
│   └── NetworkLog 网络请求监控
├── 🔧 业务逻辑层 (Business Layer)
│   ├── HTTP 请求处理引擎
│   ├── 工作区切换与隔离引擎
│   ├── Git 版本控制引擎
│   ├── 环境变量解析器
│   ├── 脚本执行引擎
│   ├── 数据导入导出模块
│   └── 性能测试执行器
├── 💾 数据访问层 (Data Layer)
│   ├── 工作区存储管理
│   ├── 本地文件存储
│   ├── Git 仓库管理
│   ├── 配置管理
│   └── 历史记录管理
└── 🌐 网络通信层 (Network Layer)
    ├── HTTP/HTTPS 客户端
    ├── WebSocket 客户端
    ├── SSE 客户端
    └── Git 远程仓库通信
```

---

## 🛠️ 技术选型说明

### 核心技术栈

- **Java 17**: 使用最新 LTS 版本，享受现代 Java 特性
  - Records、Sealed Classes、Pattern Matching
  - 增强的 NullPointerException 消息
  - Text Blocks 更好的字符串处理
  
- **JavaSwing**: 原生桌面 GUI 框架
  - 跨平台兼容性（Windows、macOS、Linux）
  - 原生外观和感觉
  - 无浏览器开销
  
- **jlink & jpackage**: 官方打包工具
  - 创建自定义运行时镜像
  - 生成原生安装包（DMG、EXE、DEB）
  - 减少分发大小

### UI 组件库

- **[FlatLaf](https://github.com/JFormDesigner/FlatLaf)**: 现代化 UI 主题
  - 支持暗色和亮色模式
  - 支持 HiDPI/Retina 显示
  - 原生 macOS 样式
  - 可自定义配色方案
  
- **[RSyntaxTextArea](https://github.com/bobbylight/RSyntaxTextArea)**: 语法高亮文本编辑器
  - 支持 JSON、XML、JavaScript、HTML 等
  - 代码折叠
  - 自动完成
  - 搜索和替换
  
- **SwingX**: 扩展 Swing 组件库
  - 增强的表格和树
  - 日期选择器
  - 搜索面板
  
- **MigLayout**: 强大的布局管理器
  - 灵活直观
  - 支持响应式设计
  - 跨平台一致性

### 网络与通信

- **[OkHttp](https://github.com/square/okhttp)**: 高性能 HTTP 客户端
  - HTTP/2 支持
  - 连接池
  - 透明 GZIP 压缩
  - 响应缓存
  - 拦截器支持
  
- **WebSocket 支持**: 实时通信
  - 全双工通信
  - 消息分帧
  - Ping/Pong 心跳
  
- **SSE (Server-Sent Events)**: 服务器推送支持
  - 事件流解析
  - 自动重连
  - 事件 ID 跟踪

### 脚本引擎

- **GraalVM JavaScript**：用于前置/后置脚本的嵌入式脚本运行时
  - ECMAScript 5.1 支持
  - Java 互操作性
  
- **GraalVM JavaScript** (Java 17+): 现代 JavaScript 引擎
  - ECMAScript 2021+ 支持
  - 更好的性能
  - 多语言支持

### 数据与存储

- **JSON 处理**:
  - Gson: JSON 序列化/反序列化
  - Jackson: 备选 JSON 处理器
  
- **XML 处理**:
  - 内置 Java XML API
  - XPath 支持
  
- **文件存储**:
  - 接口集合的本地 JSON 文件
  - 环境变量文件
  - Git 仓库集成

### 版本控制

- **JGit**: 纯 Java Git 实现
  - Clone、commit、push、pull 操作
  - 分支管理
  - 冲突检测
  - SSH 和 HTTPS 认证

### 日志记录

- **SLF4J + Logback**: 日志框架
  - 灵活配置
  - 多种输出方式（控制台、文件、滚动）
  - 性能优化
  - 异步日志支持

---

## 📦 构建与打包

### 构建工具

- **Maven**: 项目管理和构建自动化
  - 依赖管理
  - 插件生态系统
  - 多模块支持

### 打包策略

1. **跨平台 JAR**:
   ```
   mvn clean package
   ```
   - 生成 `easy-postman-{版本号}.jar`
   - 需要 Java 17+ 运行时
   - 最小的分发大小

2. **原生安装包**:
   - **macOS DMG**: 使用 `jpackage` 自定义图标和背景
   - **Windows EXE**: Inno Setup 安装程序带快捷方式
   - **Windows 便携版 ZIP**: 解压即用，无需安装
   - **Linux DEB**: Debian 软件包带桌面集成

3. **自定义运行时**:
   - 使用 `jlink` 创建最小化 JRE
   - 仅包含所需模块
   - 减少约 50% 的分发大小

### 推荐的开发 JDK

> 💡 **推荐使用 JetBrains Runtime (JBR)** 获得最佳 Swing 性能：
> 
> - 所有平台上更好的字体渲染
> - 改进的 HiDPI 支持
> - Swing 特定的 bug 修复
> - 为桌面应用优化的垃圾回收
> 
> **下载地址**: [JetBrains Runtime Releases](https://github.com/JetBrains/JetBrainsRuntime/releases)

---

## 🔧 开发工作流

### 项目结构

```
easy-postman/
├── src/main/java/          # Java 源代码
│   └── com/laker/postman/
│       ├── ui/             # UI 组件
│       ├── service/        # 业务逻辑
│       ├── model/          # 数据模型
│       ├── network/        # 网络层
│       └── utils/          # 工具类
├── src/main/resources/     # 资源文件
│   ├── icons/              # 应用图标
│   ├── themes/             # FlatLaf 主题
│   ├── js-libs/            # JavaScript 库
│   └── messages*.properties # 国际化文件
├── build/                  # 构建脚本
│   ├── mac.sh             # macOS 打包
│   ├── win-exe.bat        # Windows 安装程序
│   └── linux-deb.sh       # Linux DEB 打包
└── docs/                   # 文档
```

### 测试

- **单元测试**: JUnit 5
- **集成测试**: TestNG
- **UI 测试**: 不同主题和分辨率的手动测试

---

## 🚀 性能优化

### UI 渲染
- 大型集合的延迟加载
- 历史记录的虚拟滚动
- 防抖搜索和过滤
- 复杂组件的离屏渲染

### 网络
- 使用 OkHttp 的连接池
- 请求取消支持
- 流式响应处理
- Gzip 压缩

### 内存管理
- 缓存使用弱引用
- 限制历史记录保留
- 响应大小限制
- 垃圾回收调优

### 启动时间
- 组件的延迟初始化
- 并行资源加载
- 优化的依赖图
- GraalVM AOT 编译（未来）

---

## 🔒 安全考虑

- **本地存储**: 所有数据本地存储，无云同步
- **无遥测**: 无跟踪或分析
- **证书验证**: 正确的 SSL/TLS 验证
- **凭证存储**: Git 凭证的安全存储
- **脚本沙箱**: JavaScript 在沙箱环境中执行
- **输入验证**: 防止注入攻击

---

## 🌐 跨平台兼容性

### Windows
- 原生外观和感觉
- 文件关联
- 注册表集成
- 自动更新支持

### macOS
- 原生菜单栏集成
- Touch Bar 支持（未来）
- 沙箱兼容
- 公证以确保安全

### Linux
- 桌面文件集成
- 系统托盘支持
- 多种桌面环境（GNOME、KDE、XFCE）
- AppImage 支持（未来）

---

## 📈 未来增强

- 🚧 插件系统以实现可扩展性
- 🚧 GraphQL 支持
- 🚧 gRPC 协议支持
- ✅ 自托管 Mock Server（集合 Example、动态脚本、局域网/CLI、访问密钥、会话状态、调用日志）
- 🚧 API 文档生成
- 🚧 云工作区同步（可选）
- 🚧 移动端配套应用
