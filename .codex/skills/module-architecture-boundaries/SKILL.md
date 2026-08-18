---
name: module-architecture-boundaries
description: Use when adding or refactoring EasyPostman modules, shared code, plugin contracts, UI utilities, i18n, settings, theme/font handling, or deciding where a class belongs.
---

# Module Architecture Boundaries

Use this skill before adding shared code or moving code between modules. The goal is to keep EasyPostman's Maven modules small enough to reason about and strict enough that future changes do not turn into a catch-all common layer.

## Source of truth

Read `docs/ARCHITECTURE_MODULES_zh.md` first when the task is about module placement, architecture cleanup, shared UI, plugin contracts, i18n, theme, font, settings, startup, update, welcome/help, request core, collection core, API core, or performance core.

## Placement rules

1. Put non-UI base capabilities in `easy-postman-foundation`.
   Examples: shared DTOs, enums, constants, config paths, JSON helpers, system utilities, user-setting helpers, i18n mechanism, base message keys, and generic parsing/formatting helpers such as Cron, JSON Path, XML, file-size, file-extension, time-display, and HTTP header constants.

2. Put plugin extension contracts in `easy-postman-plugin-api`.
   Examples: `EasyPostmanPlugin`, `PluginContext`, `PluginDescriptor`, service interfaces, toolbox/script/snippet contracts.

3. Put request specification models in `easy-postman-request-core`.
   Examples: `HttpRequestItem`, `SavedResponse`, `HttpHeader`, `HttpParam`, `HttpFormData`, `HttpFormUrlencoded`, `CookieInfo`, auth/body/protocol enums, redirect metadata, and transport-auth metadata. Keep it UI-free and transport-implementation-free: no Swing, OkHttp, app service/panel code, plugin runtime, or concrete send/render implementation.

4. Put collection domain models and neutral import parsing in `easy-postman-collection-core`.
   Examples: `RequestGroup`, `CollectionNode`, `CollectionNodeType`, `CollectionParseResult`, collection auth parsing helpers, and Postman collection parsing. Keep it UI-free and host-free: no Swing/AWT, OkHttp, app service/panel/runtime code, platform, plugin runtime, IOC, or concrete send/render implementation.

5. Put HTTP transport runtime in `easy-postman-http-runtime`.
   Examples: `PreparedRequest`, `HttpResponse`, `HttpEventInfo`, runtime settings/provider, OkHttp adapters, TLS/client certificate ports, Cookie store, SSE callbacks, redirect execution, UI-neutral interaction sinks, and network observation sinks. Keep it UI-free and host-free: no Swing/AWT, app `SettingManager`, app plugin-host accessors, panel code, platform IOC, or JavaFX/Swing-specific adapters.

6. Put the self-hosted Mock Server runtime in `easy-postman-mock-core`.
   Examples: mock definition DTOs, Example route snapshots, method/path/query/header/body matching, JDK `HttpServer`, bounded call logs, volatile session state, optional shared access-key enforcement, and the neutral `MockScriptExecutor` port. Keep it UI-free and host-free: no Swing/AWT, collection/request models, app services, GraalVM, OkHttp/Netty, platform IOC, or plugin runtime. Collection mapping, workspace persistence, GraalJS adaptation, headless CLI, and Swing UI stay in `easy-postman-app`.

7. Put shared Swing design-system code in `easy-postman-ui`.
   Examples: `FontsUtil`, `IconUtil`, `NotificationCenter`, `EditorThemeUtil`, `ModernColors`, reusable toolbar buttons/search/table/dialog/form controls such as `EditButton`, `SaveButton`, `WrapToggleButton`, `EasyComboBox`, `EasyJSpinner`, `EasyPasswordField`, and the icons/resources those reusable components directly reference.
   UI singleton framework classes such as `UiSingletonFactory`, `UiSingletonPanel`, `UiSingletonMenuBar`, plus Swing refresh/save helpers such as `IRefreshable` and `DebouncedSaveSupport`, also belong here.
   Generic action/control/status icons such as save, copy, paste, search, clear, cancel, close, delete, duplicate, eye, info, warning, arrows, chevrons, wrap, start, stop, send, connect, collapse, expand, more, detail, import, and export belong here. Do not duplicate the same `icons/*.svg` resource path in `easy-postman-app`, and do not make official plugins depend on app-only icon resources.

8. Put plugin loading mechanics in `easy-postman-plugin-runtime`.
   Examples: plugin scanning, descriptor parsing, classloaders, registry, lifecycle, disabled/uninstall state.

9. Put performance domain core contracts in `easy-postman-performance-core`: editable plan data, executable `plan.json`, runtime contracts, stats/report snapshots, worker assignments, and asset references. Keep concrete GUI/headless execution adapters in `easy-postman-app` until the app execution semantics can be extracted without pulling in Swing, workspace services, or app-only state.

10. Put host platform framework capabilities in `easy-postman-platform` when they can be separated from concrete app UI.
   Current examples: the custom IOC container under `com.laker.postman.ioc`, and update discovery core under `com.laker.postman.platform.update` (version comparison, update source selection, asset resolution, changelog fetching/formatting, update result models).
   Future examples: startup orchestration, welcome/help, settings center, and theme/font application orchestration.

11. Keep concrete host UI and composition in `easy-postman-app`.
   Examples: `App`, `MainFrame`, menus, app-only panels, settings pages, update dialogs, update download/install/exit flow, welcome/help pages, and concrete startup wiring that still depends on app UI.
   Do not recreate a generic app model package for HTTP runtime exchange snapshots. Domain-specific app models should live with their owner package, such as `functional.model`, `script.model`, `stream`, `snippet`, `history`, `certificate`, `variable`, `environment`, or `service.curl`.

12. Keep HTTP request preparation adapters separated from HTTP transport runtime.
   Request preparation, validation, collection inheritance, variable resolution, scripts, and default request factories may stay in `easy-postman-app/http.request` while they still depend on app services. URL/query helpers belong in request-core. Transport execution belongs in `easy-postman-http-runtime`. Swing implementations belong in UI adapters such as `com.laker.postman.panel.http.runtime`, and app-specific runtime bootstrap belongs under `com.laker.postman.http.runtime.app`.

## Plugin Compatibility Boundary

Before removing, renaming, moving, or changing signatures/behavior for public types in modules that plugins may depend on, check whether old plugin JARs can still link against the new host. This includes `easy-postman-plugin-api`, `easy-postman-foundation`, and `easy-postman-ui`, because official and third-party plugins use them with `provided` scope.

Treat these as plugin-platform breaking changes:

- Deleting or renaming a public class, enum, method, constructor, field, package, resource path, or descriptor key that a plugin could reference.
- Changing method signatures, enum constants, constructor requirements, service contracts, extension-point semantics, or runtime loading behavior.
- Moving shared UI entry points such as notification, icon, color, font, or reusable component APIs without a binary-compatible facade.

When the change is intentionally incompatible, do all of this in the same patch:

1. Bump root `pom.xml` `plugin.platform.version` to the next incompatible platform version. Do not use `revision` for this.
2. Ensure official plugin `pom.xml` files resolve `plugin.minPlatformVersion` and `plugin.maxPlatformVersion` to the new platform version. If the plugins are rebuilt for the host release, update their plugin `<version>` and `plugin.minAppVersion` to the release they require.
3. Update plugin runtime/update tests so an old platform range, such as the previous `plugin.platform.version`, is rejected before plugin code is loaded.
4. Do not edit existing catalog entries for already-published plugin JARs to claim a new platform range. Those entries are tied to their existing `sha256`. New platform ranges belong to new rebuilt plugin artifacts generated by the plugin release workflow. If a catalog must be edited manually, update both `plugin-catalog/` and `easy-postman-plugins/plugin-manager/src/main/resources/plugin-catalog/`.

If preserving compatibility is the goal instead, keep a binary-compatible facade or overload with the old package/class/signature and add tests for old entry points. Do not bump `plugin.platform.version` for a compatible refactor.

## Update Boundaries

- Put update discovery core in `platform`: `UpdateInfo`, `UpdateCheckFrequency`, `VersionChecker`, `VersionComparator`, `UpdateSourceSelector`, release sources, asset resolvers, changelog service/formatter, and Windows registry/package-mode helpers.
- Keep concrete update UX in `app`: `AppUpdateCheckCoordinator`, `UpdateUiController`, `UpdateDownloader`, update dialogs/notifications, manual-download/open-browser commands, install prompts, and app shutdown for installation.
- `platform` update code must not import app `SettingManager`; inject a minimal provider such as `UpdateSettingsProvider` and adapt it in app with `SettingManager::getUpdateSourcePreference`.

## I18n, Fonts, Theme

- I18n mechanism and cross-module generic labels belong in `foundation`: `I18nUtil`, `CommonI18n`, `CommonMessageKeys`, and `common-messages*`.
- I18n resources follow their owner: generic short labels such as OK, Cancel, Save, Copy, Close, Search, Success, Error, Warning, and Tip in foundation common bundles; shared UI component-specific strings in `ui` (`ui-messages*`); host strings in `app` (`messages_*`); plugin strings in each plugin.
- Font helpers and typography rules belong in `ui`; startup application of font settings belongs in `platform` once decoupled from app-specific wiring.
- Theme tokens, semantic colors, icon color strategies, RSyntaxTextArea editor theme XMLs, and reusable UI resources belong in `ui`; FlatLaf installation and theme switching belong in `platform` once decoupled from app-specific wiring. FlatLaf properties tied to app LAF classes can stay in `app` until those classes move.
- Primary-color buttons must use on-primary icon color. Icons on blue/brand buttons stay white and must not switch with the light/dark theme foreground.

## SVG Icon Guidance

- `README.md` and `README_zh.md` declare SVG icons are sourced from Lucide / `lucide-icons/lucide` under the ISC license. For new or refreshed generic UI/action/sidebar icons, start from Lucide before inventing custom paths.
- Keep single-color themeable SVGs as `viewBox="0 0 24 24"`, `fill="none"`, `stroke="currentColor"`, `stroke-width="2"`, `stroke-linecap="round"`, and `stroke-linejoin="round"` to match existing sidebar icons.
- Use `IconUtil.createThemed(...)` for neutral sidebar/status/tool icons so light and dark themes can recolor them. Use fixed-color SVGs or `IconUtil.create(...)` only for brand, protocol, or status assets that intentionally carry their own colors.
- Resource ownership still applies: generic actions in `easy-postman-ui`, app/domain icons in app, plugin icons in plugin resources. Do not duplicate `icons/*.svg` names across modules.

## Code Style And Testability

- Use Lombok for ordinary boilerplate when it makes code smaller and clearer: `@Slf4j`, `@RequiredArgsConstructor`, `@Getter`/`@Setter`, model annotations, and `@UtilityClass` for stateless static helpers. Avoid Lombok only when explicit code is clearer for Swing lifecycle, validation-heavy construction, or framework compatibility.
- Do not add production hooks, injectable exits, extra state, or abstraction layers only to make a test possible if that makes the production code more complex. Prefer testing observable behavior through existing public/package APIs, focused static architecture tests, or a simpler production fix with a smaller test. Add a seam only when it also improves real design, not just test mechanics.

## Anti-patterns

- Do not use a vague `common` module as the default destination.
- Do not create a vague `easy-postman-core` module for unrelated request, collection, runtime, and UI concerns.
- Do not put Swing code in `foundation`.
- Do not put request specification models back into `easy-postman-app`.
- Do not make `easy-postman-request-core` depend on Swing, OkHttp, app service/panel code, or plugin runtime.
- Do not put collection core models or Postman collection parsing back into `easy-postman-app`.
- Do not make `easy-postman-collection-core` depend on Swing, OkHttp, app service/panel/runtime code, platform, plugin runtime, or IOC.
- Do not make `easy-postman-mock-core` depend on Swing/AWT, collection/request models, app services, GraalVM, OkHttp/Netty, platform IOC, or plugin runtime. Keep it self-hosted and dependency-light; LAN/server binding is allowed, but cloud control planes and team authorization are not.
- Do not make HTTP runtime/service classes depend directly on Swing/panel or app `SettingManager`; adapt UI through neutral sinks/dispatchers and settings through `HttpRuntimeSettingsProvider` so Swing, CLI tests, and future JavaFX hosts can provide separate implementations.
- Do not put UI view-state, importer scratch DTOs, script snippets, functional runner rows/results, stream message types, certificate settings rows, or history records back into `easy-postman-app/src/main/java/com/laker/postman/model`.
- Do not put plugin service contracts in `foundation`.
- Do not put shared reusable UI components directly in `app`.
- Do not make shared UI components depend on app-owned message bundles, icons, editor themes, or other app resources.
- Do not duplicate generic short labels in `ui-messages*` or plugin message bundles when `CommonMessageKeys` already owns them.
- Do not make plugins depend on `easy-postman-app`.
- Do not introduce new app-local color/font/button conventions before checking `easy-postman-ui`.
- Do not duplicate same-named `icons/*.svg` resources between `easy-postman-app` and `easy-postman-ui`; shared control icons belong in `ui`, app/domain icons stay with their owning app/plugin module. If a plugin references an icon, it must be plugin-owned or UI-owned.

## Verification

For module-boundary changes, run:

```bash
mvn -q -pl easy-postman-app -am -Dtest=ModuleArchitectureBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -pl easy-postman-plugin-runtime -am -Dtest=PluginRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -pl easy-postman-app -am -Dtest=PluginUpdateCheckerTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -pl easy-postman-platform -am -Dtest=UpdateSourceSelectorTest,PlatformDownloadUrlResolverTest,AppReleaseSelectorTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -DskipTests compile
```
