package com.laker.postman.mock.app;

import com.laker.postman.ioc.Component;
import com.laker.postman.ioc.PostConstruct;
import com.laker.postman.ioc.PreDestroy;
import com.laker.postman.mock.model.MockCallLog;
import com.laker.postman.mock.model.MockRoute;
import com.laker.postman.mock.model.MockServerDefinition;
import com.laker.postman.mock.runtime.LocalMockServer;
import com.laker.postman.model.Workspace;
import com.laker.postman.service.WorkspaceService;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MockServerManager {
    private final MockServerPersistenceService persistenceService;
    private final MockCollectionRouteProvider routeProvider;
    private final MockScriptExecutorAdapter scriptExecutor;

    private final List<MockServerDefinition> definitions = new ArrayList<>();
    private final Map<String, LocalMockServer> runtimes = new LinkedHashMap<>();
    private String workspaceId;

    @PostConstruct
    public synchronized void initialize() {
        loadCurrentWorkspace();
    }

    public synchronized List<MockServerDefinition> listDefinitions() {
        ensureCurrentWorkspace();
        return definitions.stream().map(MockServerDefinition::copy).toList();
    }

    public synchronized Optional<MockServerDefinition> findDefinition(String id) {
        ensureCurrentWorkspace();
        return definitions.stream().filter(item -> Objects.equals(item.getId(), id))
                .findFirst().map(MockServerDefinition::copy);
    }

    public synchronized void saveDefinition(MockServerDefinition definition) {
        ensureCurrentWorkspace();
        validate(definition);
        boolean duplicatePort = definitions.stream()
                .anyMatch(item -> !Objects.equals(item.getId(), definition.getId())
                        && item.getPort() == definition.getPort());
        if (duplicatePort) {
            throw new IllegalArgumentException(I18nUtil.getMessage(
                    MessageKeys.MOCK_SERVER_DUPLICATE_PORT, definition.getPort()));
        }
        int index = indexOf(definition.getId());
        MockServerDefinition copy = normalizedCopy(definition);
        if (index >= 0) {
            definitions.set(index, copy);
        } else {
            definitions.add(copy);
        }
        persistenceService.save(definitions);
    }

    public synchronized void removeDefinition(String id) {
        ensureCurrentWorkspace();
        stopInternal(id, true);
        definitions.removeIf(item -> Objects.equals(item.getId(), id));
        persistenceService.save(definitions);
    }

    public synchronized void start(String id) throws IOException {
        ensureCurrentWorkspace();
        MockServerDefinition definition = requireDefinition(id);
        LocalMockServer existing = runtimes.get(id);
        if (existing != null && existing.isRunning()) {
            return;
        }
        List<MockRoute> routes = buildRoutes(definition);
        LocalMockServer runtime = new LocalMockServer(definition, routes, scriptExecutor);
        runtime.start();
        runtimes.put(id, runtime);
    }

    public synchronized void startAutoStartServers() {
        ensureCurrentWorkspace();
        for (MockServerDefinition definition : List.copyOf(definitions)) {
            if (definition.isAutoStart() && !isRunning(definition.getId())) {
                try {
                    start(definition.getId());
                } catch (Exception ex) {
                    log.warn("Failed to auto-start mock server '{}'", definition.getName(), ex);
                }
            }
        }
    }

    public synchronized void stop(String id) {
        ensureCurrentWorkspace();
        stopInternal(id, false);
    }

    public synchronized boolean isRunning(String id) {
        ensureCurrentWorkspace();
        LocalMockServer runtime = runtimes.get(id);
        return runtime != null && runtime.isRunning();
    }

    public synchronized String baseUrl(String id) {
        ensureCurrentWorkspace();
        LocalMockServer runtime = runtimes.get(id);
        MockServerDefinition definition = requireDefinition(id);
        int port = runtime == null ? definition.getPort() : runtime.port();
        return MockNetworkAddressResolver.accessUrl(definition, port);
    }

    public synchronized String deploymentCommand(String id) {
        ensureCurrentWorkspace();
        MockServerDefinition definition = requireDefinition(id);
        String workspacePath = persistenceService.configPath().getParent().toAbsolutePath().normalize().toString();
        return "java -jar easy-postman.jar mock run " + shellQuote(workspacePath)
                + " --server " + shellQuote(definition.getName())
                + " --host " + MockServerDefinition.ALL_INTERFACES_HOST
                + " --port " + definition.getPort();
    }

    public synchronized List<MockRouteEntry> routeEntries(String id) {
        ensureCurrentWorkspace();
        MockServerDefinition definition = requireDefinition(id);
        List<MockRouteEntry> entries = new ArrayList<>();
        for (MockRoute route : safeStandaloneRoutes(definition)) {
            entries.add(new MockRouteEntry(
                    "",
                    I18nUtil.getMessage(MessageKeys.MOCK_SERVER_ROUTE_SOURCE_STANDALONE),
                    route.routeId(),
                    true,
                    route.requestId(),
                    route.requestName(),
                    route.exampleId(),
                    route.exampleName(),
                    route.method(),
                    route.pathPattern(),
                    route.response().getStatusCode(),
                    route.response().getDelayMs(),
                    true,
                    !route.script().isBlank()
            ));
        }
        entries.addAll(routeProvider.listRouteEntries(definition.collectionSourceIds()));
        return List.copyOf(entries);
    }

    public synchronized Optional<MockRoute> findStandaloneRoute(String serverId, String routeId) {
        ensureCurrentWorkspace();
        return safeStandaloneRoutes(requireDefinition(serverId)).stream()
                .filter(route -> Objects.equals(route.routeId(), routeId))
                .findFirst()
                .map(MockServerManager::copyRoute);
    }

    public synchronized void addStandaloneRoute(String serverId, MockRoute route) {
        Objects.requireNonNull(route, "route");
        mutateStandaloneRoutes(serverId, routes -> routes.add(copyRoute(route)));
    }

    public synchronized void updateStandaloneRoute(String serverId, MockRoute route) {
        Objects.requireNonNull(route, "route");
        mutateStandaloneRoutes(serverId, routes -> {
            for (int index = 0; index < routes.size(); index++) {
                if (Objects.equals(routes.get(index).routeId(), route.routeId())) {
                    routes.set(index, copyRoute(route));
                    return;
                }
            }
            throw new IllegalArgumentException("Mock route not found: " + route.routeId());
        });
    }

    public synchronized boolean removeStandaloneRoute(String serverId, String routeId) {
        MockServerDefinition definition = requireDefinition(serverId);
        List<MockRoute> routes = new ArrayList<>(safeStandaloneRoutes(definition));
        boolean removed = routes.removeIf(route -> Objects.equals(route.routeId(), routeId));
        if (removed) {
            definition.setStandaloneRoutes(routes);
            persistenceService.save(definitions);
        }
        return removed;
    }

    public synchronized List<MockCallLog> logs(String id) {
        ensureCurrentWorkspace();
        LocalMockServer runtime = runtimes.get(id);
        return runtime == null ? List.of() : runtime.logs();
    }

    public synchronized void clearLogs(String id) {
        ensureCurrentWorkspace();
        LocalMockServer runtime = runtimes.get(id);
        if (runtime != null) runtime.clearLogs();
    }

    public synchronized Map<String, Map<String, Object>> state(String id) {
        ensureCurrentWorkspace();
        LocalMockServer runtime = runtimes.get(id);
        return runtime == null ? Map.of() : runtime.stateSnapshot();
    }

    public synchronized void clearState(String id) {
        ensureCurrentWorkspace();
        LocalMockServer runtime = runtimes.get(id);
        if (runtime != null) runtime.clearState();
    }

    public synchronized void updateScript(String id, String script) {
        MockServerDefinition definition = requireDefinition(id);
        definition.setScript(script == null ? "" : script);
        persistenceService.save(definitions);
        LocalMockServer runtime = runtimes.get(id);
        if (runtime != null) runtime.updateScript(definition.getScript());
    }

    public synchronized void reloadWorkspace() {
        stopAll();
        loadCurrentWorkspace();
    }

    private void ensureCurrentWorkspace() {
        String currentId = currentWorkspaceId();
        if (!Objects.equals(workspaceId, currentId)) {
            stopAll();
            loadCurrentWorkspace();
        }
    }

    private void loadCurrentWorkspace() {
        workspaceId = currentWorkspaceId();
        definitions.clear();
        persistenceService.load().stream().map(this::normalizedCopy).forEach(definitions::add);
    }

    private MockServerDefinition normalizedCopy(MockServerDefinition source) {
        MockServerDefinition copy = source.copy();
        copy.setHost(MockServerDefinition.ALL_INTERFACES_HOST);
        copy.setCollectionSources(new ArrayList<>(copy.normalizedCollectionSources()));
        if (copy.getStandaloneRoutes() == null) copy.setStandaloneRoutes(new ArrayList<>());
        if (copy.getMatchHeaderNames() == null) copy.setMatchHeaderNames(new ArrayList<>());
        if (copy.getScript() == null) copy.setScript("");
        if (copy.getAccessKey() == null) copy.setAccessKey("");
        return copy;
    }

    private void validate(MockServerDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("Mock server definition is required");
        if (definition.getName() == null || definition.getName().isBlank()) {
            throw new IllegalArgumentException("Mock server name is required");
        }
        if (definition.getPort() < 1 || definition.getPort() > 65_535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        if (definition.getFixedDelayMs() < 0 || definition.getFixedDelayMs() > 60_000) {
            throw new IllegalArgumentException("Delay must be between 0 and 60000 ms");
        }
    }

    private MockServerDefinition requireDefinition(String id) {
        return definitions.stream().filter(item -> Objects.equals(item.getId(), id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Mock server not found: " + id));
    }

    private List<MockRoute> buildRoutes(MockServerDefinition definition) {
        List<MockRoute> routes = new ArrayList<>(safeStandaloneRoutes(definition));
        routes.addAll(routeProvider.buildRoutes(definition.collectionSourceIds()));
        return List.copyOf(routes);
    }

    private List<MockRoute> safeStandaloneRoutes(MockServerDefinition definition) {
        return definition.getStandaloneRoutes() == null ? List.of() : definition.getStandaloneRoutes();
    }

    private void mutateStandaloneRoutes(String serverId,
                                        java.util.function.Consumer<List<MockRoute>> mutation) {
        MockServerDefinition definition = requireDefinition(serverId);
        List<MockRoute> routes = new ArrayList<>(safeStandaloneRoutes(definition));
        mutation.accept(routes);
        definition.setStandaloneRoutes(routes);
        persistenceService.save(definitions);
    }

    private static MockRoute copyRoute(MockRoute route) {
        return new MockRoute(
                route.routeId(), route.requestId(), route.requestName(), route.exampleId(), route.exampleName(),
                route.method(), route.pathPattern(), route.queryParameters(), route.requestHeaders(),
                route.requestBody(), route.response(), route.script()
        );
    }

    private int indexOf(String id) {
        for (int i = 0; i < definitions.size(); i++) {
            if (Objects.equals(definitions.get(i).getId(), id)) return i;
        }
        return -1;
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private void stopInternal(String id, boolean removeRuntime) {
        LocalMockServer runtime = runtimes.get(id);
        if (runtime != null) runtime.stop();
        if (removeRuntime) runtimes.remove(id);
    }

    private String currentWorkspaceId() {
        Workspace workspace = WorkspaceService.getInstance().getCurrentWorkspace();
        return workspace == null ? "default-workspace" : workspace.getId();
    }

    private void stopAll() {
        runtimes.values().forEach(LocalMockServer::stop);
        runtimes.clear();
    }

    @PreDestroy
    public synchronized void shutdown() {
        stopAll();
    }
}
