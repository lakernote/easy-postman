## Project Overview

EasyPostman is a Java 17 + Swing desktop API testing app. Entry point: `com.laker.postman.App`. Build tool: Maven multi-module.

---

## Module Structure

```
easy-postman-parent (root pom.xml, revision = host version)
├── easy-postman-foundation      # Lowest non-UI base layer: shared models, constants, paths, JSON, system/settings/i18n utilities
├── easy-postman-request-core    # Headless request spec models: HttpRequestItem, SavedResponse, headers/body/auth/protocol DTOs
├── easy-postman-http-runtime    # Headless HTTP transport runtime: PreparedRequest, HttpResponse, OkHttp/SSL/SSE/Cookie/redirect runtime ports
├── easy-postman-collection-core # Headless collection domain models and Postman collection parser
├── easy-postman-mock-core       # Self-hosted JDK HTTP mock runtime: route matching, access key, session state, logs, script port
├── easy-postman-plugin-api      # Stable plugin SPI and service contracts: EasyPostmanPlugin, PluginContext, PluginDescriptor, GitPluginService, ClientCertificatePluginService
├── easy-postman-platform        # Host platform framework: custom IOC + update discovery core; startup/welcome/help/settings orchestration later
├── easy-postman-ui              # Common Swing UI base components, FontsUtil, IconUtil, NotificationCenter, EditorThemeUtil, ModernColors, ModernButtonFactory
├── easy-postman-performance-core           # Headless performance domain core: editable plan contracts, run plan.json, runtime contracts, stats, report snapshots
├── easy-postman-plugin-runtime  # Plugin scan/load/lifecycle: PluginRuntime, PluginScanner, PluginLoader, PluginRegistry
├── easy-postman-plugins/        # Official plugins (each builds an independent JAR)
│   ├── plugin-manager           # Host-side plugin management exception: catalog parsing, online/offline install facade
│   ├── plugin-client-cert
│   ├── plugin-capture
│   ├── plugin-redis
│   ├── plugin-kafka
│   └── plugin-decompiler
└── easy-postman-app             # Host application; consumes plugin-registered capabilities
```

When choosing a module, use this rule set:

- Put shared non-UI foundation logic in `easy-postman-foundation`: DTOs, enums, constants, paths, JSON, system utilities, user-setting helpers, i18n mechanism, and generic parsing/formatting helpers such as Cron, JSON Path, XML, file-size, file-extension, time-display, and HTTP header constants.
- Put request specification models in `easy-postman-request-core`: `HttpRequestItem`, `SavedResponse`, headers, params, form body rows, cookies, request auth/body/protocol enums, redirect and transport-auth metadata. This module must not depend on Swing, OkHttp, app service/panel code, plugin runtime, or concrete send/render implementations.
- Put HTTP transport runtime in `easy-postman-http-runtime`: runtime exchange snapshots (`PreparedRequest`, `HttpResponse`, `HttpEventInfo`), runtime settings/provider, OkHttp adapters, TLS/client certificate ports, Cookie store, SSE callbacks, redirect execution, UI-neutral interaction sinks, and network observation sinks. This module must not depend on Swing/AWT, panel code, app `SettingManager`, app plugin-host accessors, platform IOC, or JavaFX/Swing-specific adapters.
- Put collection domain models and neutral import parsing in `easy-postman-collection-core`: `RequestGroup`, `CollectionNode`, `CollectionNodeType`, `CollectionParseResult`, collection auth parsing helpers, and Postman collection parsing. This module may depend on foundation and request-core, but must not depend on Swing/AWT, OkHttp, app panel/service/runtime, platform, plugin-runtime, IOC, or concrete send/render implementations.
- Put the self-hosted Mock Server runtime in `easy-postman-mock-core`: persisted definition DTOs, Example route snapshots, matching, JDK `HttpServer`, optional access-key enforcement, bounded request logs, volatile session state, and the neutral script executor port. It may depend on foundation but must not depend on Swing/AWT, collection/request models, app services, GraalVM, OkHttp/Netty, platform IOC, or plugin runtime. Collection-to-route mapping, workspace persistence, GraalJS adaptation, headless CLI, and Swing management UI stay in `easy-postman-app`.
- Put plugin-facing extension contracts in `easy-postman-plugin-api`: plugin SPI, service interfaces, toolbox/script/snippet contracts.
- Put host platform framework in `easy-postman-platform`: custom IOC, update discovery core, then startup, welcome/help, settings center, and theme/font application orchestration when dependencies are ready.
- Put shared Swing design-system code in `easy-postman-ui`: reusable components, UI singleton base/factory, toolbar buttons (`EditButton`, `SaveButton`, `WrapToggleButton`), form controls (`EasyComboBox`, `EasyJSpinner`, `EasyPasswordField`), fonts, icons, notification UI, editor theme helpers, semantic colors, and UI resources directly used by those components.
- Keep icon resource ownership unique: generic action/control/status icons (`save`, `copy`, `paste`, `search`, `clear`, `cancel`, `close`, `delete`, `duplicate`, `eye`, `info`, `warning`, arrows, chevrons, wrap, start/stop, send, connect, collapse/expand, more/detail, import/export) belong in `easy-postman-ui`; app/domain icons belong in their owning app or plugin module. Official plugins may use icons from their own resources or `easy-postman-ui`, not app-only resources. Do not duplicate the same `icons/*.svg` path across app and ui.
- Keep plugin loading, classloaders, descriptor parsing, registry, and lifecycle in `easy-postman-plugin-runtime`.
- Keep host-specific composition, app panels, menus, concrete startup wiring, and app-only services in `easy-postman-app` until each dependency is ready to migrate into `easy-postman-platform`.
- Keep HTTP request preparation that still depends on collection inheritance, variables, scripts, and app services in `easy-postman-app/http.request`; keep generic URL/query helpers in request-core; keep transport execution in `easy-postman-http-runtime`; keep Swing implementations in UI adapters such as `com.laker.postman.panel.http.runtime` or app bootstrap adapters under `com.laker.postman.http.runtime.app`. A future JavaFX host should provide JavaFX adapters without changing the HTTP runtime.
- Inside `easy-postman-app`, do not recreate a generic `com.laker.postman.model` package for HTTP runtime exchange data. Domain-specific app models belong with their owner package, for example `functional.model`, `script.model`, `stream`, `snippet`, `history`, `certificate`, `variable`, `environment`, and `service.curl`.
- Do not put SPI code, shared UI components, or shared foundation utilities directly into `easy-postman-app`.

Reference module rules: `docs/ARCHITECTURE_MODULES_zh.md`.

---

## Build Commands

```bash
# Full build (all modules + all plugins), skip tests
mvn clean package -DskipTests

# Build only the host app (fastest iteration)
mvn -pl easy-postman-app -am -DskipTests clean package

# Build host app + one plugin
mvn -pl easy-postman-app,easy-postman-plugins/plugin-redis -am clean package -DskipTests

# Quick compile check (no jar, fast)
mvn -q -pl easy-postman-app -am -DskipTests compile

# Run tests for a specific class in headless mode
mvn -q -pl easy-postman-app -am -Dtest=<TestClass> -Dsurefire.failIfNoSpecifiedTests=false -Djava.awt.headless=true test
```

Output: `easy-postman-app/target/easy-postman-${revision}.jar`

Native installers are produced by `build/mac.sh`, `build/win-exe.bat`, `build/linux-deb.sh`, `build/linux-rpm.sh` — these call `jpackage` and reference a fixed filename `easy-postman.jar` (not the versioned one).

---

## Startup Sequence

```
App.main()
  -> AppLauncher.launch(args)
       -> configureBaseRuntimeEnvironment()
       -> registerShutdownHook()
       -> AppCommandRouter.route(args)
            -> performance run/worker/master: set java.awt.headless=true
            -> run/worker: HeadlessStartupBootstrap.initRuntime()
                 -> BeanFactory.init("com.laker.postman")
                 -> PluginRuntime.initialize()
            -> master: load plan + dispatch workers over HTTP/JSON
            -> return CLI exit code without entering Swing EDT
       -> configurePlatformWindowDecorations()  // GUI branch, Linux: FlatLaf window decorations
       -> SwingUtilities.invokeLater()
            -> SimpleThemeManager.initTheme()   // reads easy_postman_settings.properties
            -> FontManager.applyFontSettings()
            -> SplashWindow or direct SwingWorker
                 -> StartupCoordinator.prepareMainFrame()
                      -> BeanFactory.init("com.laker.postman")   // scans @Component beans
                      -> PluginRuntime.initialize()               // scan, load, lifecycle
                      -> MainFrame (EDT)
```

---

## Custom IOC Container

The project uses its **own lightweight IOC container** (`com.laker.postman.ioc`) from `easy-postman-platform`, not Spring. Do not import Spring annotations.

| Annotation | Purpose |
|---|---|
| `@Component` | Marks a class as a managed bean (auto-scanned from `com.laker.postman`) |
| `@Autowired` | Field/constructor/method injection |
| `@PostConstruct` | Called after all fields are injected |
| `@PreDestroy` | Called on `BeanFactory.destroy()` |

Retrieve beans outside of injection: `BeanFactory.getBean(MyService.class)`.

Three-level circular dependency cache is implemented in `ApplicationContext` — if you see a circular dependency crash, check bean design rather than patching the cache.

---

## Lombok Usage

When adding or refactoring Java code, use Lombok by default for boilerplate reduction where it keeps the code clearer:
- Use `@Slf4j` instead of manually declaring logger fields.
- Use `@RequiredArgsConstructor` for required dependency constructors, especially support/service classes with `final` fields.
- Use `@Getter`, `@Setter`, `@Data`, `@Value`, and `@Builder` for simple models/value objects when appropriate.
- Use `@UtilityClass` for stateless utility classes instead of private constructors plus static methods.

Avoid Lombok only when explicit code is clearer for Swing lifecycle, side-effectful constructors, validation-heavy construction, or framework compatibility.

---

## Swing Panel Conventions

All UI panels that are logically singletons must:
1. Extend `UiSingletonPanel`
2. Be obtained via `UiSingletonFactory.getInstance(MyPanel.class)` — **never `new MyPanel()`**
3. Implement `initUI()` for component creation and `registerListeners()` for event wiring
4. Let `UiSingletonFactory` call `initializeSingletonUi()` after construction (this calls `initUI()` then `registerListeners()`)

`UiSingletonMenuBar` follows the same pattern for menu bars.

Business services and repositories must be obtained through the IOC container (`BeanFactory.getBean(...)` or constructor injection), not through `UiSingletonFactory`.

Classes that coordinate Swing tabs, dialogs, or panels but are not singleton components should live under `com.laker.postman.panel...` and use UI-oriented names (for example `RequestEditorTabs`, `OpenedRequestTabsSaver`) rather than `*Service`.

Collection services must not fetch panels directly. Use service-side models/stores such as `OpenedRequestsStore`, `CollectionTreeNodeTypes`, and the registered root-node access in `CollectionTreeRootRegistry`; the UI layer is responsible for registering the active tree root.

---

## Key Constant Files (in `easy-postman-foundation`)

- `AppConstants` — `APP_NAME`, `BASE_PACKAGE`
- `ConfigPathConstants` — all data file paths (`EASY_POSTMAN_SETTINGS`, `COLLECTIONS`, `ENVIRONMENTS`, `DEFAULT_WORKSPACE_DIR`, etc.)
- `JsonUtil` — Jackson-based JSON serialization/deserialization (supports JSON5/comments); use this instead of raw Jackson calls
- `AppRuntimeLayout` — resolves portable mode (`isPortableMode(Class<?>)`) and key directory paths (`applicationRootDirectory`, `codeSourceDirectory`); portable mode is triggered by a `.portable` marker file or the `easyPostman.portable` system property

Data root: `SystemUtil.getEasyPostmanPath()` — returns `<user.home>/EasyPostman/` in normal mode, or `<app-dir>/data/` in portable mode.

---

## Workspace & Git Sync

The app supports multiple named workspaces. Each workspace is an isolated directory (collections, environments, settings). A workspace can optionally be backed by a Git repository.

- Models: `Workspace`, `WorkspaceType` in `easy-postman-foundation`
- Workspace UI: `WorkspacePanel` and its components in `com.laker.postman.panel.workspace`
- Git operations are declared in `com.laker.postman.plugin.api.service.GitPluginService` and implemented by `GitWorkspacePluginService` in `com.laker.postman.plugin.git`
- Host-side accessor: `GitServiceAccess` (in `com.laker.postman.plugin.host`, app module) provides the built-in Git service behind the `GitPluginService` contract.
- `WorkspaceStorageUtil` (app util) handles workspace list persistence

---

## Script Execution

Pre/post scripts run through `ScriptExecutionPipeline` (`com.laker.postman.service.js`), which merges collection/folder/request-level scripts, pools Rhino contexts (`JsContextPool`), and injects polyfills.

Built-in JS libraries bundled at `easy-postman-app/src/main/resources/js-libs/`:
- `crypto-js.min.js`, `lodash.min.js`, `moment.min.js`

Plugin scripts are injected via `registerScriptApi` (alias → object factory); they become accessible as `pm.<alias>` inside scripts.

---

## Internationalisation

User-visible app strings must use `I18nUtil.getMessage(MessageKeys.SOME_KEY)`. Cross-module generic labels such as OK, Cancel, Save, Copy, Close, Search, Success, Error, Warning, and Tip live in `easy-postman-foundation` as `CommonI18n.get(CommonMessageKeys.SOME_KEY)` backed by `common-messages*`; `I18nUtil` also falls back to that bundle for existing app keys. Shared UI component-specific strings use `UiI18n.get(UiMessageKeys.SOME_KEY)` from `easy-postman-ui`. Resource bundles should follow ownership: foundation common text belongs with foundation, shared UI component text belongs with `easy-postman-ui`, app text belongs with `easy-postman-app`, and plugin text belongs with the plugin module. Do not duplicate exact common keys in app/ui/plugin bundles. Never hard-code UI strings directly.

---

## Theme & Settings

- Theme is currently applied by `SimpleThemeManager` in the app (light/dark via FlatLaf, animated transitions). Startup/application of theme and font settings belongs in `easy-postman-platform` once those dependencies are extracted cleanly.
- Application settings are persisted to `easy_postman_settings.properties` via `SettingManager`; lightweight user preferences such as language, theme, and window state are persisted to `user_settings.json` via `UserPreferencesStore` (foundation module).
- Font size setting key: `ui_font_size` in that properties file.
- Custom FlatLaf token overrides: `easy-postman-app/src/main/resources/com/laker/postman/common/themes/EasyLightLaf.properties` and `EasyDarkLaf.properties`
- RSyntaxTextArea editor theme XMLs: `easy-postman-ui/src/main/resources/themes/easypostman-light.xml` and `easypostman-dark.xml`
- Shared semantic colors for both themes: `ModernColors` in `easy-postman-ui` (`com.laker.postman.common.constants.ModernColors`)
- Font helpers, icon helpers, notification UI, editor theme helpers, and reusable Swing components belong in `easy-postman-ui`.

---

## Update Architecture

Update discovery core lives in `easy-postman-platform` under `com.laker.postman.platform.update`: `UpdateInfo`, `UpdateCheckFrequency`, `VersionChecker`, release sources, version comparison, asset resolution, changelog fetching/formatting, and Windows package/registry helpers.

Concrete update UX stays in `easy-postman-app`: `AppUpdateCheckCoordinator`, `UpdateUiController`, `UpdateDownloader`, update dialogs/notifications, browser-opening actions, installer launch, and app shutdown.

Platform update code must not import app `SettingManager`; use `UpdateSettingsProvider` and adapt it from app with `SettingManager::getUpdateSourcePreference`.

---

## Plugin System (summary)

- Each plugin is a standalone JAR with a descriptor at `META-INF/easy-postman/*.properties` (generated from the plugin's `pom.xml`).
- Plugin entry class implements `EasyPostmanPlugin`; `onLoad(PluginContext)` registers all capabilities.
- Extension points: `registerScriptApi`, `registerService`, `registerToolboxContribution`, `registerScriptCompletionContributor`, `registerSnippet`.
- Host consumes registered capabilities from `PluginRegistry`.
- **Plugin service interfaces** (`GitPluginService`, `ClientCertificatePluginService`, `RequestCollectionImportService`) live in `easy-postman-plugin-api` under `com.laker.postman.plugin.api.service`. Plugins register implementations via `context.registerService(GitPluginService.class, impl)`. Host code retrieves them through `PluginAccess.getService(Type.class)` or typed app-side accessors such as `GitServiceAccess` and `ClientCertificatePluginAccess` in `com.laker.postman.plugin.host`.
- Version model: `revision` = host release version; `plugin.platform.version` = plugin compatibility boundary for SPI, runtime wiring, and public APIs in platform modules that plugins may depend on. Only bump `plugin.platform.version` when those boundaries change incompatibly.
- Catalog source of truth: `pom.xml → descriptor → release asset → catalog`. Do not hand-edit `plugin-catalog/` or the bundled fallback in `plugin-manager/src/main/resources/plugin-catalog/` independently — update both together.
- Reference runtime architecture: `docs/PLUGIN_RUNTIME_ARCHITECTURE_zh.md`.

---

## CI / GitHub Actions

| Workflow | Trigger | Purpose |
|---|---|---|
| `pr-check.yml` | PR to main/master/develop | Maven build + tests + PR validation |
| `release.yml` | Push tag | Multi-platform native installer build |
| `plugin-release.yml` | Plugin tag | Build plugin JARs, validate consistency, publish, update catalog |
| `codeql-analysis.yml` | Schedule/push | Security analysis |
| `auto-label.yml` | Issue/PR opened or edited | Auto-apply labels based on title/body keywords |
| `sync-labels.yml` | Push to main (`.github/labels.yml` changed) or manual | Sync label definitions to the repository |
| `welcome.yml` | Issue/PR opened | Post a welcome comment for first-time contributors |

---

## Skills

### Available skills

- swing-flatlaf-miglayout-principles: Use when modifying EasyPostman Swing forms, tool-window panels, or FlatLaf/MigLayout layouts, especially when refactors introduce clipped focus rings, dense spacing, border conflicts, rounded-card corner leaks, or inconsistent form structure. (file: .codex/skills/swing-flatlaf-miglayout-principles/SKILL.md)
- swing-intellij-dialog-style: Use when modifying EasyPostman Swing dialogs, popups, wizards, or modal flows to better match IntelliJ IDEA or FlatLaf settings-style visual hierarchy, especially when read-only panels, editable inputs, steps, scroll panes, footers, or action choices are visually unclear. (file: .codex/skills/swing-intellij-dialog-style/SKILL.md)
- fontsutil-font-usage: Use when modifying EasyPostman Swing UI fonts, especially when dialogs, labels, tables, tabs, or renderers look too large or too small, or when a change must follow the user's configured UI font size. Prefer FontsUtil.getDefaultFontWithOffset(...). (file: .codex/skills/fontsutil-font-usage/SKILL.md)
- swing-ui-test-headless-guard: Use when adding or updating EasyPostman Swing/TestNG UI tests that may run in headless CI. Reuse `AbstractSwingUiTest` instead of duplicating headless or no-display skip logic. (file: .codex/skills/swing-ui-test-headless-guard/SKILL.md)
- module-architecture-boundaries: Use when adding/refactoring modules, shared code, plugin contracts, UI utilities, i18n, settings, theme/font handling, or deciding where a class belongs. (file: .codex/skills/module-architecture-boundaries/SKILL.md)

### How to use skills

- If the request names a skill or clearly matches the description above, read the skill and follow it.
- Keep the skill body concise and use it only for repo-specific knowledge that is hard to infer from the code alone.
