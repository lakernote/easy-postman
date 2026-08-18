# 🎯 Features in Detail

## 🏢 Workspace Management

### Local Workspace
- ✅ Perfect for personal projects
- ✅ Data stored locally for privacy
- ✅ No network required
- ✅ Full control over your data

### Git Workspace
- ✅ Version control support
- ✅ Team collaboration enabled
- ✅ Clone from remote repositories (GitHub/Gitee/GitLab)
- ✅ Initialize local Git repository
- ✅ Project-level data isolation
- ✅ Quick workspace switching
- ✅ Multiple authentication methods:
  - Username/Password
  - Personal Access Token
  - SSH Key

### Git Operations
- **Commit**: Save local changes to version control
- **Push**: Push local commits to remote repository
- **Pull**: Fetch latest changes from remote
- **Conflict Detection**: Smart conflict handling and resolution

### Team Collaboration Workflow
1. **Team Leader**:
   - Create Git workspace (clone or local init)
   - Configure API collections and environments
   - Commit and push to remote
2. **Team Members**:
   - Create Git workspace (clone from remote)
   - Get latest API data and environments
   - Commit and push updates after local changes
3. **Daily Collaboration**:
   - Before work: **Pull** to get latest changes
   - After changes: **Commit** local changes
   - Share updates: **Push** to remote

---

## 🔌 API Debugging

### Protocol Support
- ✅ HTTP/1.1 and HTTP/2
- ✅ Full REST API methods: GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS
- ✅ SSE (Server-Sent Events)
- ✅ WebSocket

### Request Body Formats
- Form Data
- x-www-form-urlencoded
- JSON
- XML
- Binary

### Features
- ✅ File upload/download (drag & drop supported)
- ✅ Cookie auto-management and manual editing
- ✅ Visual editing for headers and query params
- ✅ Formatted response display (JSON, XML, HTML)
- ✅ Response time, status code, size statistics

### Self-hosted Mock Server
- ✅ Create workspace-scoped servers from the top-level **Mock Server** sidebar menu
- ✅ Collections are optional: link zero, one, or multiple collections and show all HTTP requests, including requests without a configured response
- ✅ Add standalone routes and edit status, headers, body, and delay without calling a real API or creating a collection first
- ✅ Create a server from a collection menu or add a Mock response from a request menu
- ✅ Keep collection-backed responses as Examples; persist standalone routes directly in `mock_servers.json`
- ✅ Match method/path/query, with optional named headers and JSON/text request bodies
- ✅ Select Examples with `x-mock-response-id`, `x-mock-response-name`, or `x-mock-response-code`
- ✅ Enable exact matching per call with `x-mock-match-request-body` or `x-mock-match-request-headers`
- ✅ Route-level **Code Mock** plus an optional global script through `pm.request`, `pm.response`, and `pm.state`
- ✅ `pm.*` completion, enabled state, API quick reference, and 8 curated built-in examples with preview and insert/replace actions
- ✅ CORS, server/route delay, per-call `x-mock-response-delay`, bounded/optional call logs, and `x-mock-session-id` state isolation
- ✅ Zero-dependency JDK `HttpServer` runtime with CPU-scaled workers and method-indexed routes for concurrent development and lightweight load tests
- ✅ Listen on all network interfaces by default and display a shareable LAN URL, with an optional shared `x-api-key`
- ✅ Store `mock_servers.json` beside `collections.json` in the current workspace so Git workspaces version them together
- ✅ Run headlessly with `mock run` after copying the workspace to a server or CI runner
- ✅ Copy the workspace-specific self-host command directly from the management page
- ✅ No EasyPostman cloud hosting, team permissions, external npm packages, or AI generation

Server example:

```bash
EASY_POSTMAN_MOCK_API_KEY=change-me \
java -jar easy-postman.jar mock run /path/to/workspace \
  --server "Payments Mock" --host 0.0.0.0 --port 3001
```

For internet-facing use, terminate HTTPS behind Nginx, Caddy, or a cloud load balancer and restrict the inbound firewall rule.

Script example:

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

`pm.request` exposes `method`, `path`, `body`, `header(name)`, `query(name)`, and `pathVariable(name)`. `pm.response` lets scripts set the status code, headers, body, and `delayMs`. State is memory-only and is discarded when cleared, when the runtime is refreshed, or when the app exits.

---

## 🌍 Environment Management

- ✅ Quick environment switching (dev/test/prod)
- ✅ Global and environment variables
- ✅ Nested variable reference: `{{baseUrl}}/api/{{version}}`
- ✅ Dynamic variables:
  - `{{$timestamp}}` - Current timestamp
  - `{{$randomInt}}` - Random integer
  - `{{$uuid}}` - UUID generator
- ✅ Import/export environments

---

## 📝 Script Support

### Pre-request Script
- Run JavaScript before sending request
- Set variables dynamically
- Prepare test data
- Modify request parameters

### Tests Script
- Run JavaScript after receiving response
- Parse response data
- Set variables from response
- Chain requests together
- Assertion support

### Built-in Features
- ✅ Code snippets library
- ✅ JavaScript runtime environment
- ✅ Assertion testing
- ✅ Request chaining

---

## ⚡ Performance Testing

### Thread Group Modes
1. **Fixed**: Stable load testing
   - Constant number of threads
   - Suitable for baseline performance testing

2. **Ramp-up**: Gradually increasing load
   - Incrementally add threads
   - Test system behavior under increasing load

3. **Stair-step**: Staged load testing
   - Load increases in steps
   - Identify performance bottlenecks at different levels

4. **Spike**: Burst load testing
   - Sudden traffic surge
   - Test system resilience

### Monitoring & Reports
- ✅ Real-time performance monitoring
- ✅ Detailed test reports:
  - Response time distribution
  - TPS (Transactions Per Second)
  - Error rate analysis
  - Success/failure statistics
- ✅ Result tree analysis
- ✅ Performance trend charts
- ✅ Export test results

---

## 📊 Data Analysis

### Request History
- ✅ Timeline view of all requests
- ✅ Quick replay previous requests
- ✅ Filter by status code, method, URL
- ✅ Auto-save and persistent storage

### Network Event Logs
- ✅ Detailed network event monitoring
- ✅ Request/response headers
- ✅ Timing breakdown
- ✅ Error diagnosis

### Response Statistics
- ✅ Response time analysis
- ✅ Data size tracking
- ✅ Auto-categorized error requests
- ✅ Export statistics data

---

## 🔄 Data Migration

### Import Support
- ✅ **Postman Collection v2.1**: Supported import/export
- ✅ **cURL commands**: Convert to requests
- 🚧 **HAR files**: In development
- 🚧 **OpenAPI/Swagger**: In development

### Export Support
- ✅ Postman Collection format
- ✅ cURL commands
- ✅ Environment variables
- ✅ Test results

---

## 🎨 User Interface

### Themes
- ✅ Light mode
- ✅ Dark mode
- ✅ Auto-switch based on system preference

### Editor Features
- ✅ Syntax highlighting for:
  - JSON
  - XML
  - JavaScript
  - HTML
- ✅ Auto-formatting
- ✅ Code folding
- ✅ Search and replace

### Internationalization
- ✅ Simplified Chinese (简体中文)
- ✅ English
- 🚧 More languages coming soon

---

## ☕ Additional Tools

### Java Decompiler
- ✅ Built-in decompiler for analyzing Java classes
- ✅ View source code from JAR files
- ✅ Useful for debugging and reverse engineering

### Client Certificates
- ✅ Support for mTLS (mutual TLS)
- ✅ Import client certificates
- ✅ Secure API testing

---

## 🔒 Privacy & Security

- ✅ **100% Local Storage**: No cloud sync, your data stays private
- ✅ **No Telemetry**: No tracking, no analytics
- ✅ **Offline First**: Works completely offline
- ✅ **Open Source**: Code is transparent and auditable
- ✅ **Git Encryption**: Support for encrypted Git repositories
