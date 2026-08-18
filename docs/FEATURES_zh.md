# 🎯 功能详细说明

## 🏢 工作区管理

### 本地工作区
- ✅ 适合个人项目
- ✅ 数据完全本地存储，隐私安全
- ✅ 无需网络连接
- ✅ 完全掌控您的数据

### Git 工作区
- ✅ 支持版本控制
- ✅ 支持团队协作
- ✅ 从远程仓库克隆（GitHub/Gitee/GitLab）
- ✅ 本地初始化 Git 仓库
- ✅ 项目级别数据隔离
- ✅ 快速工作区切换
- ✅ 多种认证方式：
  - 用户名/密码
  - Personal Access Token
  - SSH Key

### Git 操作
- **提交（Commit）**：保存本地变更到版本控制
- **推送（Push）**：将本地提交推送到远程仓库
- **拉取（Pull）**：从远程仓库获取最新变更
- **冲突检测**：智能冲突处理和解决方案

### 团队协作流程
1. **团队领导者**：
   - 创建 Git 工作区（从远程克隆或本地初始化）
   - 配置接口集合和环境变量
   - 提交并推送到远程仓库
2. **团队成员**：
   - 创建 Git 工作区（从远程克隆）
   - 获取最新的接口数据和环境配置
   - 本地修改后提交并推送更新
3. **日常协作**：
   - 开始工作前：先执行 **Pull** 拉取最新变更
   - 完成修改后：执行 **Commit** 提交本地变更
   - 分享更新：执行 **Push** 推送到远程仓库

---

## 🔌 接口调试

### 协议支持
- ✅ HTTP/1.1 和 HTTP/2
- ✅ 完整的 REST API 方法：GET、POST、PUT、DELETE、PATCH、HEAD、OPTIONS
- ✅ SSE（Server-Sent Events）
- ✅ WebSocket

### 请求体格式
- Form Data
- x-www-form-urlencoded
- JSON
- XML
- Binary

### 功能特性
- ✅ 文件上传下载（支持拖拽）
- ✅ Cookie 自动管理和手动编辑
- ✅ 请求头、查询参数可视化编辑
- ✅ 响应数据格式化显示（JSON、XML、HTML）
- ✅ 响应时间、状态码、大小统计

### 自托管 Mock Server
- ✅ 从左侧一级菜单“Mock Server”为当前工作区创建服务
- ✅ 集合不是必选项；一个服务可关联 0～多个集合，并列出其中全部 HTTP 请求（包括尚未配置响应的请求）
- ✅ 无需先调用真实接口或创建集合，可直接新增独立路由并编辑状态码、响应头、Body 和延迟
- ✅ 集合右键可创建 Mock Server，请求右键可直接添加 Mock 响应
- ✅ 集合请求的响应仍保存为集合 Example；独立路由直接保存在 `mock_servers.json`
- ✅ 匹配 method/path/query，可选匹配指定 header 和 JSON/文本 body
- ✅ 支持 `x-mock-response-id`、`x-mock-response-name`、`x-mock-response-code` 选择 Example
- ✅ 支持 `x-mock-match-request-body`、`x-mock-match-request-headers` 按单次请求启用精确匹配
- ✅ 支持路由级 **Code Mock** 和可选全局脚本，通过 `pm.request`、`pm.response`、`pm.state` 动态响应
- ✅ Code Mock 编辑器支持 `pm.*` 自动补全、启用状态、API 速查，以及 8 个精选常用示例，可预览、插入或替换
- ✅ 支持 CORS、固定/路由延迟、`x-mock-response-delay` 临时延迟、有界/可关闭的调用日志和 `x-mock-session-id` 会话状态
- ✅ 使用零额外服务端依赖的 JDK `HttpServer`，按 CPU 扩展工作线程并按 HTTP Method 预索引路由，适合并发联调和轻量压测
- ✅ 默认监听所有网卡并显示可分享的局域网 URL；可通过 `x-api-key` 设置共享访问密钥
- ✅ `mock_servers.json` 与 `collections.json` 一同保存在当前工作区，Git 工作区会一起版本管理
- ✅ 支持 `mock run` 无界面运行，可将工作区复制到服务器或 CI 自行托管
- ✅ 管理页可直接复制对应工作区和服务的自托管启动命令
- ✅ 不包含 EasyPostman 云端托管、团队权限、外部 npm 包或 AI 生成

服务器启动示例：

```bash
EASY_POSTMAN_MOCK_API_KEY=change-me \
java -jar easy-postman.jar mock run /path/to/workspace \
  --server "Payments Mock" --host 0.0.0.0 --port 3001
```

公网使用建议放在 Nginx、Caddy 或云负载均衡后面终止 HTTPS，并限制防火墙入站端口。

脚本示例：

```javascript
const input = JSON.parse(pm.request.body || "{}");
if (Number(input.amount) === 0.5) {
  pm.response.setStatusCode(402);
  pm.response.setBody(JSON.stringify({ status: "deny" }));
} else if (Number(input.amount) === 1.1) {
  pm.response.setStatusCode(200);
  pm.response.setBody(JSON.stringify({ status: "partial_approval" }));
}
```

`pm.request` 提供 `method`、`path`、`body`、`header(name)`、`query(name)` 和 `pathVariable(name)`；`pm.response` 可设置状态码、header、body 和 `delayMs`。状态仅存于内存，清空状态、刷新服务运行时或退出应用后不保留。

---

## 🌍 环境管理

- ✅ 多环境快速切换（开发/测试/生产）
- ✅ 全局变量和环境变量支持
- ✅ 变量嵌套引用：`{{baseUrl}}/api/{{version}}`
- ✅ 动态变量：
  - `{{$timestamp}}` - 当前时间戳
  - `{{$randomInt}}` - 随机整数
  - `{{$uuid}}` - UUID 生成器
- ✅ 环境变量导入导出

---

## 📝 脚本支持

### Pre-request Script（请求前脚本）
- 在发送请求前执行 JavaScript
- 动态设置变量
- 准备测试数据
- 修改请求参数

### Tests Script（测试脚本）
- 在收到响应后执行 JavaScript
- 解析响应数据
- 从响应中设置变量
- 实现请求链路
- 断言支持

### 内置功能
- ✅ 代码片段库
- ✅ JavaScript 运行时环境
- ✅ 断言测试
- ✅ 请求链路

---

## ⚡ 性能测试

### 线程组模式
1. **固定线程数**：稳定负载测试
   - 恒定线程数
   - 适合基准性能测试

2. **递增式**：逐步增加负载
   - 逐步增加线程
   - 测试系统在递增负载下的行为

3. **阶梯式**：分阶段负载测试
   - 负载分阶段增加
   - 识别不同级别的性能瓶颈

4. **尖刺式**：突发流量测试
   - 突然的流量激增
   - 测试系统弹性

### 监控与报告
- ✅ 实时性能监控
- ✅ 详细测试报告：
  - 响应时间分布
  - TPS（每秒事务数）
  - 错误率分析
  - 成功/失败统计
- ✅ 结果树分析
- ✅ 性能趋势图表
- ✅ 导出测试结果

---

## 📊 数据分析

### 请求历史
- ✅ 所有请求的时间线视图
- ✅ 快速重放之前的请求
- ✅ 按状态码、方法、URL 过滤
- ✅ 自动保存和持久化存储

### 网络事件日志
- ✅ 详细的网络事件监控
- ✅ 请求/响应头
- ✅ 时间分解
- ✅ 错误诊断

### 响应统计
- ✅ 响应时间分析
- ✅ 数据大小跟踪
- ✅ 错误请求自动分类
- ✅ 导出统计数据

---

## 🔄 数据管理

### 导入支持
- ✅ **Postman Collection v2.1**：支持导入导出
- ✅ **cURL 命令**：转换为请求
- 🚧 **HAR 文件**：开发中
- 🚧 **OpenAPI/Swagger**：开发中

### 导出支持
- ✅ Postman Collection 格式
- ✅ cURL 命令
- ✅ 环境变量
- ✅ 测试结果

---

## 🎨 用户界面

### 主题
- ✅ 亮色模式
- ✅ 暗色模式
- ✅ 根据系统偏好自动切换

### 编辑器功能
- ✅ 语法高亮：
  - JSON
  - XML
  - JavaScript
  - HTML
- ✅ 自动格式化
- ✅ 代码折叠
- ✅ 搜索和替换

### 国际化
- ✅ 简体中文
- ✅ English
- 🚧 更多语言即将推出

---

## ☕ 附加工具

### Java 反编译器
- ✅ 内置反编译器用于分析 Java 类
- ✅ 从 JAR 文件查看源代码
- ✅ 适用于调试和逆向工程

### 客户端证书
- ✅ 支持 mTLS（双向 TLS）
- ✅ 导入客户端证书
- ✅ 安全的 API 测试

---

## 🔒 隐私与安全

- ✅ **100% 本地存储**：无云同步，数据完全私密
- ✅ **无遥测**：无跟踪，无分析
- ✅ **离线优先**：完全离线工作
- ✅ **开源**：代码透明可审查
- ✅ **Git 加密**：支持加密的 Git 仓库
