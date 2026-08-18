package com.laker.postman.architecture;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class ModuleArchitectureBoundaryTest {

    @Test
    public void collectionAndFunctionalCliStayPhysicallySeparated() throws IOException {
        Path root = repositoryRoot();
        Path collectionCli = root.resolve("easy-postman-app/src/main/java/com/laker/postman/collection/cli");
        Path functionalCli = root.resolve("easy-postman-app/src/main/java/com/laker/postman/functional/cli");
        Path workspaceCli = root.resolve("easy-postman-app/src/main/java/com/laker/postman/workspace/cli");

        assertTrue(Files.isDirectory(collectionCli), "collection run must own a dedicated CLI package");
        assertTrue(Files.isDirectory(functionalCli), "functional run must own a dedicated CLI package");
        assertTrue(Files.isRegularFile(workspaceCli.resolve("WorkspaceRunExecutor.java")),
                "shared headless execution belongs in the neutral workspace CLI package");
        assertTrue(Files.isRegularFile(workspaceCli.resolve("WorkspaceRunPlanner.java")),
                "collection and functional selection must be plugged into the shared executor");

        List<String> collectionViolations = sourcePackageViolations(collectionCli, List.of(
                "com.laker.postman.functional.cli",
                "functional_config.json"
        ));
        List<String> functionalViolations = sourcePackageViolations(functionalCli, List.of(
                "com.laker.postman.collection.cli"
        ));

        assertTrue(collectionViolations.isEmpty(),
                "collection run must not depend on functional CLI configuration: " + collectionViolations);
        assertTrue(functionalViolations.isEmpty(),
                "functional run must not depend on collection CLI commands: " + functionalViolations);
    }

    @Test
    public void mavenModulesUseFoundationAndUiNames() throws IOException {
        Path root = repositoryRoot();
        assertTrue(Files.isDirectory(root.resolve("easy-postman-foundation")));
        assertTrue(Files.isDirectory(root.resolve("easy-postman-request-core")));
        assertTrue(Files.isDirectory(root.resolve("easy-postman-http-runtime")));
        assertTrue(Files.isDirectory(root.resolve("easy-postman-collection-core")));
        assertTrue(Files.isDirectory(root.resolve("easy-postman-mock-core")));
        assertTrue(Files.isDirectory(root.resolve("easy-postman-platform")));
        assertTrue(Files.isDirectory(root.resolve("easy-postman-ui")));
        assertFalse(Files.exists(root.resolve("easy-postman-core")));
        assertFalse(Files.exists(root.resolve("easy-postman-performance-runtime-okhttp")));
        assertFalse(Files.exists(root.resolve("easy-postman-plugin-bridge")));
        assertFalse(Files.exists(root.resolve("easy-postman-plugin-ui")));

        for (Path pom : pomFiles(root)) {
            String source = Files.readString(pom);
            assertFalse(source.contains("<module>easy-postman-core</module>"), pom + " still references a vague core module");
            assertFalse(source.contains("easy-postman-performance-runtime-okhttp"), pom + " still references the retired performance runtime module");
            assertFalse(source.contains("easy-postman-plugin-bridge"), pom + " still references the old bridge module");
            assertFalse(source.contains("easy-postman-plugin-ui"), pom + " still references the old plugin UI module");
        }
    }

    @Test
    public void requestCoreOwnsRequestSpecificationModels() {
        Path root = repositoryRoot();
        Path requestModelPackage = root.resolve("easy-postman-request-core/src/main/java/com/laker/postman/request/model");
        Path appModelPackage = root.resolve("easy-postman-app/src/main/java/com/laker/postman/model");
        List<String> requestModelFiles = List.of(
                "AuthType.java",
                "CookieInfo.java",
                "HttpFormData.java",
                "HttpFormUrlencoded.java",
                "HttpHeader.java",
                "HttpParam.java",
                "HttpRequestItem.java",
                "RedirectInfo.java",
                "RequestAuthTypes.java",
                "RequestBodyTypes.java",
                "RequestItemProtocolEnum.java",
                "SavedResponse.java",
                "TransportAuth.java"
        );

        for (String requestModelFile : requestModelFiles) {
            assertTrue(Files.isRegularFile(requestModelPackage.resolve(requestModelFile)),
                    requestModelFile + " is request specification data and belongs in easy-postman-request-core");
            assertFalse(Files.exists(appModelPackage.resolve(requestModelFile)),
                    requestModelFile + " must not stay in the app model package");
        }
    }

    @Test
    public void requestCoreSourcesStayUiAndTransportFree() throws IOException {
        Path root = repositoryRoot();
        List<String> violations = sourceRootsPackageViolations(
                List.of(root.resolve("easy-postman-request-core/src/main/java")),
                forbiddenRequestCoreImports()
        );

        assertTrue(violations.isEmpty(),
                "Request core must stay UI-free and transport-implementation-free: " + violations);
    }

    @Test
    public void collectionCoreOwnsCollectionModelsAndPostmanParser() {
        Path root = repositoryRoot();
        Path collectionModelPackage = root.resolve("easy-postman-collection-core/src/main/java/com/laker/postman/collection/model");
        Path importerPackage = root.resolve("easy-postman-collection-core/src/main/java/com/laker/postman/collection/importer");
        Path postmanImporterPackage = importerPackage.resolve("postman");

        List<String> collectionModelFiles = List.of(
                "RequestGroup.java",
                "CollectionNodeType.java",
                "CollectionNode.java",
                "CollectionParseResult.java"
        );
        for (String collectionModelFile : collectionModelFiles) {
            assertTrue(Files.isRegularFile(collectionModelPackage.resolve(collectionModelFile)),
                    collectionModelFile + " is collection domain data and belongs in easy-postman-collection-core");
        }

        assertTrue(Files.isRegularFile(importerPackage.resolve("AuthParserUtil.java")));
        assertTrue(Files.isRegularFile(postmanImporterPackage.resolve("PostmanCollectionParser.java")));

        assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/java/com/laker/postman/model/RequestGroup.java")),
                "RequestGroup must not stay in the app model package");
        assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/java/com/laker/postman/service/common/NodeType.java")),
                "NodeType must be renamed to CollectionNodeType in collection-core");
        assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/java/com/laker/postman/service/common/CollectionNode.java")),
                "CollectionNode must not stay in app service/common");
        assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/java/com/laker/postman/service/common/CollectionParseResult.java")),
                "CollectionParseResult must not stay in app service/common");
        assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/java/com/laker/postman/service/common/AuthParserUtil.java")),
                "AuthParserUtil must not stay in app service/common");
        assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/java/com/laker/postman/service/postman/PostmanCollectionParser.java")),
                "PostmanCollectionParser must not stay in the app postman service package");
    }

    @Test
    public void foundationOwnsEnvironmentVariableModels() {
        Path root = repositoryRoot();
        Path foundationModelPackage = root.resolve("easy-postman-foundation/src/main/java/com/laker/postman/model");
        Path appModelPackage = root.resolve("easy-postman-app/src/main/java/com/laker/postman/model");

        for (String foundationModelFile : List.of("Environment.java", "Variable.java")) {
            assertTrue(Files.isRegularFile(foundationModelPackage.resolve(foundationModelFile)),
                    foundationModelFile + " is shared non-UI environment data and belongs in easy-postman-foundation");
            assertFalse(Files.exists(appModelPackage.resolve(foundationModelFile)),
                    foundationModelFile + " must not stay in the app model package");
        }
    }

    @Test
    public void foundationEnvironmentModelDoesNotOwnScriptRuntimeApi() {
        Path environmentModel = repositoryRoot()
                .resolve("easy-postman-foundation/src/main/java/com/laker/postman/model/Environment.java");
        List<String> violations = sourceContainsViolations(environmentModel, List.of(
                "public void unset(",
                "public void clear()",
                "public String replaceIn(",
                "public Map<String, String> toObject(",
                "replaceDynamicVariables",
                "$isoTimestamp",
                "$guid",
                "set(String key, Object value)"
        ));

        assertTrue(violations.isEmpty(),
                "Foundation Environment is shared data and must not own Postman script/runtime API behavior: "
                        + violations);
    }

    @Test
    public void collectionCoreSourcesStayHeadlessAndHostFree() throws IOException {
        Path root = repositoryRoot();
        List<String> violations = sourceRootsPackageViolations(
                List.of(root.resolve("easy-postman-collection-core/src/main/java")),
                forbiddenCollectionCoreImports()
        );

        assertTrue(violations.isEmpty(),
                "Collection core must stay UI-free and host/runtime/transport-free: " + violations);
    }

    @Test
    public void mockCoreStaysHeadlessLocalAndDependencyLight() throws IOException {
        Path root = repositoryRoot();
        Path sourceRoot = root.resolve("easy-postman-mock-core/src/main/java");
        List<String> sourceViolations = sourcePackageViolations(sourceRoot, List.of(
                "javax.swing", "java.awt", "com.laker.postman.panel", "com.laker.postman.service",
                "com.laker.postman.ioc", "com.laker.postman.collection", "com.laker.postman.request",
                "org.graalvm", "okhttp3", "io.netty", "org.nanohttpd"
        ));
        assertTrue(sourceViolations.isEmpty(),
                "Mock core must stay headless, host-free, and independent of external server/script stacks: "
                        + sourceViolations);

        String pom = Files.readString(root.resolve("easy-postman-mock-core/pom.xml"));
        List<String> dependencyViolations = List.of(
                        "<artifactId>easy-postman</artifactId>",
                        "<artifactId>easy-postman-ui</artifactId>",
                        "<artifactId>easy-postman-platform</artifactId>",
                        "<artifactId>easy-postman-request-core</artifactId>",
                        "<artifactId>easy-postman-collection-core</artifactId>",
                        "<artifactId>polyglot</artifactId>",
                        "<artifactId>okhttp", "<artifactId>netty", "<artifactId>nanohttpd"
                ).stream().filter(pom::contains).toList();
        assertTrue(dependencyViolations.isEmpty(),
                "Mock core pom must not pull host, UI, collection/request, or external server/script dependencies: "
                        + dependencyViolations);
    }

    @Test
    public void collectionCorePomDoesNotDependOnHostUiRuntimeOrTransport() throws IOException {
        Path collectionPom = repositoryRoot().resolve("easy-postman-collection-core/pom.xml");
        String source = Files.readString(collectionPom);
        List<String> forbiddenDependencies = List.of(
                "<artifactId>easy-postman</artifactId>",
                "<artifactId>easy-postman-ui</artifactId>",
                "<artifactId>easy-postman-platform</artifactId>",
                "<artifactId>easy-postman-plugin-runtime</artifactId>",
                "<groupId>com.squareup.okhttp3</groupId>",
                "<artifactId>okhttp",
                "<artifactId>flatlaf</artifactId>",
                "<artifactId>miglayout-swing</artifactId>",
                "<artifactId>rsyntaxtextarea</artifactId>",
                "<artifactId>autocomplete</artifactId>",
                "<artifactId>hutool-core</artifactId>"
        );
        List<String> violations = forbiddenDependencies.stream()
                .filter(source::contains)
                .toList();

        assertTrue(violations.isEmpty(),
                "Collection core pom must not depend on host UI/runtime or transport implementations: " + violations);
    }

    @Test
    public void collectionCoreDoesNotOwnAppSpecificImportersUntilTheyMoveAsAWhole() throws IOException {
        Path collectionCoreSource = repositoryRoot().resolve("easy-postman-collection-core/src/main/java");
        List<String> violations = sourcePackageViolations(collectionCoreSource, List.of(
                "ApiPost",
                "parseApiPost",
                "IntelliJ",
                "Swagger",
                "OpenAPI",
                "HAR"
        ));

        assertTrue(violations.isEmpty(),
                "Collection core should not contain slices of app-owned importers before those importers move as a whole: "
                        + violations);
    }

    @Test
    public void appOwnedCollectionAdaptersAndRuntimeTypesStayOutOfCollectionCore() throws IOException {
        Path root = repositoryRoot();
        for (String appOwnedFile : List.of(
                "easy-postman-app/src/main/java/com/laker/postman/service/postman/PostmanCollectionExporter.java",
                "easy-postman-app/src/main/java/com/laker/postman/service/common/TreeNodeBuilder.java",
                "easy-postman-app/src/main/java/com/laker/postman/panel/collections/tree/adapter/SwingCollectionTreePersistence.java",
                "easy-postman-app/src/main/java/com/laker/postman/service/collections/InheritanceService.java"
        )) {
            assertTrue(Files.isRegularFile(root.resolve(appOwnedFile)),
                    appOwnedFile + " is still app-owned and must not move into collection-core in this slice");
        }

        for (String runtimeOwnedFile : List.of(
                "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/model/PreparedRequest.java",
                "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/model/HttpResponse.java",
                "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/model/HttpEventInfo.java"
        )) {
            assertTrue(Files.isRegularFile(root.resolve(runtimeOwnedFile)),
                    runtimeOwnedFile + " is HTTP runtime exchange data and must not move into collection-core");
        }
        for (String retiredAppModelFile : List.of(
                "easy-postman-app/src/main/java/com/laker/postman/model/PreparedRequest.java",
                "easy-postman-app/src/main/java/com/laker/postman/model/HttpResponse.java",
                "easy-postman-app/src/main/java/com/laker/postman/model/HttpEventInfo.java"
        )) {
            assertFalse(Files.exists(root.resolve(retiredAppModelFile)),
                    retiredAppModelFile + " must stay out of the app model package");
        }

        Path collectionCoreSource = root.resolve("easy-postman-collection-core/src/main/java");
        for (String forbiddenFileName : List.of(
                "PostmanCollectionExporter.java",
                "TreeNodeBuilder.java",
                "SwingCollectionTreeDocumentMapper.java",
                "SwingCollectionTreeQueries.java",
                "SwingCollectionTreePersistence.java",
                "SwingCollectionRequestSaveCoordinator.java",
                "SwingCollectionRequestMutation.java",
                "SwingSavedResponseTreeMutation.java",
                "InheritanceService.java",
                "PreparedRequest.java",
                "HttpResponse.java",
                "HttpEventInfo.java"
        )) {
            assertFalse(javaSourceFiles(collectionCoreSource).stream()
                            .anyMatch(path -> path.getFileName().toString().equals(forbiddenFileName)),
                    forbiddenFileName + " must not be owned by easy-postman-collection-core");
        }
    }

    @Test
    public void sourcesDoNotUseWildcardImportsForSplitDomainModels() throws IOException {
        Path root = repositoryRoot();
        List<String> violations = sourceRootsLinePatternViolations(
                List.of(
                        root.resolve("easy-postman-app/src/main/java"),
                        root.resolve("easy-postman-app/src/test/java"),
                        root.resolve("easy-postman-request-core/src/main/java"),
                        root.resolve("easy-postman-collection-core/src/main/java")
                ),
                List.of(
                        "import\\s+com\\.laker\\.postman\\.request\\.model\\.\\*;",
                        "import\\s+static\\s+com\\.laker\\.postman\\.request\\.model\\.[A-Za-z0-9_]+\\.\\*;",
                        "import\\s+com\\.laker\\.postman\\.collection(\\.[A-Za-z0-9_]+)*\\.\\*;",
                        "import\\s+static\\s+com\\.laker\\.postman\\.collection(\\.[A-Za-z0-9_]+)*\\.[A-Za-z0-9_]+\\.\\*;",
                        "import\\s+com\\.laker\\.postman\\.model\\.\\*;",
                        "import\\s+static\\s+com\\.laker\\.postman\\.model\\.[A-Za-z0-9_]+\\.\\*;"
                )
        );

        assertTrue(violations.isEmpty(),
                "Request/collection/model packages span modules, so app sources must use explicit imports: " + violations);
    }

    @Test
    public void packagingRuntimeModulesIncludePerformanceWorkerHttpServer() throws IOException {
        Path root = repositoryRoot();
        List<Path> packagingFiles = List.of(
                root.resolve(".github/workflows/pr-check.yml"),
                root.resolve(".github/workflows/release.yml"),
                root.resolve("build/mac.sh"),
                root.resolve("build/linux-deb.sh"),
                root.resolve("build/linux-rpm.sh"),
                root.resolve("build/win-exe.bat")
        );

        for (Path file : packagingFiles) {
            String source = Files.readString(file);
            assertTrue(source.contains("java.net.http"), file + " must keep java.net.http for master HTTP client");
            assertTrue(source.contains("jdk.httpserver"), file + " must keep jdk.httpserver for worker mode");
        }
    }

    @Test
    public void pluginServiceContractsLiveInPluginApi() {
        Path root = repositoryRoot();
        Path servicePackage = root.resolve("easy-postman-plugin-api/src/main/java/com/laker/postman/plugin/api/service");
        assertTrue(Files.isRegularFile(servicePackage.resolve("GitPluginService.java")));
        assertTrue(Files.isRegularFile(servicePackage.resolve("ClientCertificatePluginService.java")));
        assertTrue(Files.isRegularFile(servicePackage.resolve("RequestCollectionImportService.java")));
        assertFalse(Files.exists(root.resolve("easy-postman-foundation/src/main/java/com/laker/postman/plugin/bridge")),
                "Foundation must not own plugin service contracts");
    }

    @Test
    public void platformOwnsIocFramework() {
        Path root = repositoryRoot();
        assertTrue(Files.isRegularFile(root.resolve("easy-postman-platform/src/main/java/com/laker/postman/ioc/BeanFactory.java")));
        assertTrue(Files.isRegularFile(root.resolve("easy-postman-platform/src/main/java/com/laker/postman/ioc/ApplicationContext.java")));
        assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/java/com/laker/postman/ioc")),
                "The app module must consume the IOC framework from easy-postman-platform instead of owning it");
    }

    @Test
    public void platformOwnsUpdateDiscoveryCore() {
        Path root = repositoryRoot();
        List<String> platformUpdateCoreFiles = List.of(
                "UpdateSettingsProvider.java",
                "VersionChecker.java",
                "WindowsRegistryChecker.java",
                "model/UpdateInfo.java",
                "model/UpdateCheckFrequency.java",
                "version/VersionComparator.java",
                "asset/AssetFinder.java",
                "asset/PlatformDownloadUrlResolver.java",
                "asset/WindowsVersionDetector.java",
                "source/AbstractUpdateSource.java",
                "source/AppReleaseSelector.java",
                "source/GitHubUpdateSource.java",
                "source/GiteeUpdateSource.java",
                "source/UpdateSource.java",
                "source/UpdateSourceSelector.java",
                "changelog/ChangelogFormatter.java",
                "changelog/ChangelogService.java"
        );

        for (String updateCoreFile : platformUpdateCoreFiles) {
            assertTrue(Files.isRegularFile(root.resolve("easy-postman-platform/src/main/java/com/laker/postman/platform/update/" + updateCoreFile)),
                    updateCoreFile + " belongs in easy-postman-platform");
        }

        assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/java/com/laker/postman/model/UpdateInfo.java")),
                "UpdateInfo is platform update discovery data, not app UI state");
        assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/java/com/laker/postman/model/UpdateCheckFrequency.java")),
                "UpdateCheckFrequency is platform update discovery policy, not app UI state");
        assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/java/com/laker/postman/service/update/VersionChecker.java")),
                "VersionChecker belongs in easy-postman-platform");
        assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/java/com/laker/postman/service/update/version")),
                "Version comparison belongs in easy-postman-platform");
        assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/java/com/laker/postman/service/update/source")),
                "Update sources belong in easy-postman-platform");
        assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/java/com/laker/postman/service/update/asset")),
                "Update asset resolution belongs in easy-postman-platform");
        assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/java/com/laker/postman/service/update/changelog")),
                "Changelog fetching/formatting belongs in easy-postman-platform");

        assertTrue(Files.isRegularFile(root.resolve("easy-postman-app/src/main/java/com/laker/postman/service/update/AppUpdateCheckCoordinator.java")));
        assertTrue(Files.isRegularFile(root.resolve("easy-postman-app/src/main/java/com/laker/postman/service/update/UpdateUiController.java")));
        assertTrue(Files.isRegularFile(root.resolve("easy-postman-app/src/main/java/com/laker/postman/service/update/UpdateDownloader.java")));
    }

    @Test
    public void uiModuleOwnsReusableSwingSingletonFramework() {
        Path root = repositoryRoot();
        assertTrue(Files.isRegularFile(root.resolve("easy-postman-ui/src/main/java/com/laker/postman/common/UiSingletonFactory.java")));
        assertTrue(Files.isRegularFile(root.resolve("easy-postman-ui/src/main/java/com/laker/postman/common/UiSingletonPanel.java")));
        assertTrue(Files.isRegularFile(root.resolve("easy-postman-ui/src/main/java/com/laker/postman/common/UiSingletonMenuBar.java")));
        assertTrue(Files.isRegularFile(root.resolve("easy-postman-ui/src/main/java/com/laker/postman/common/exception/GetInstanceException.java")));
        assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/java/com/laker/postman/common/UiSingletonFactory.java")),
                "Reusable Swing singleton framework belongs in easy-postman-ui");
        assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/java/com/laker/postman/common/UiSingletonPanel.java")),
                "Reusable Swing singleton framework belongs in easy-postman-ui");
        assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/java/com/laker/postman/common/UiSingletonMenuBar.java")),
                "Reusable Swing singleton framework belongs in easy-postman-ui");
    }

    @Test
    public void uiModuleOwnsReusableSwingRefreshAndSaveHelpers() {
        Path root = repositoryRoot();
        assertTrue(Files.isRegularFile(root.resolve("easy-postman-ui/src/main/java/com/laker/postman/common/DebouncedSaveSupport.java")));
        assertTrue(Files.isRegularFile(root.resolve("easy-postman-ui/src/main/java/com/laker/postman/common/IRefreshable.java")));
        assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/java/com/laker/postman/common/DebouncedSaveSupport.java")),
                "Reusable Swing save helper belongs in easy-postman-ui");
        assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/java/com/laker/postman/common/IRefreshable.java")),
                "Reusable Swing refresh contract belongs in easy-postman-ui");
    }

    @Test
    public void uiModuleOwnsReusableSwingFormControls() {
        Path root = repositoryRoot();
        List<String> controlFiles = List.of(
                "EasyComboBox.java",
                "EasyJSpinner.java",
                "EasyPasswordField.java"
        );

        for (String controlFile : controlFiles) {
            assertTrue(Files.isRegularFile(root.resolve("easy-postman-ui/src/main/java/com/laker/postman/common/component/" + controlFile)),
                    controlFile + " belongs in easy-postman-ui");
            assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/java/com/laker/postman/common/component/" + controlFile)),
                    controlFile + " must not be owned by the app module");
        }
    }

    @Test
    public void uiModuleOwnsReusableToolbarButtons() {
        Path root = repositoryRoot();
        List<String> buttonFiles = List.of(
                "DownloadButton.java",
                "EditButton.java",
                "ExportButton.java",
                "FormatButton.java",
                "HelpButton.java",
                "ImportButton.java",
                "LoadButton.java",
                "PlusButton.java",
                "SaveButton.java",
                "SearchButton.java",
                "StartButton.java",
                "StopButton.java",
                "WrapToggleButton.java"
        );

        for (String buttonFile : buttonFiles) {
            assertTrue(Files.isRegularFile(root.resolve("easy-postman-ui/src/main/java/com/laker/postman/common/component/button/" + buttonFile)),
                    buttonFile + " belongs in easy-postman-ui");
            assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/java/com/laker/postman/common/component/button/" + buttonFile)),
                    buttonFile + " must not be owned by the app module");
        }
    }

    @Test
    public void foundationOwnsReusableNonUiUtilities() {
        Path root = repositoryRoot();
        List<String> utilityFiles = List.of(
                "FileExtensionUtil.java",
                "FileMimeTypeUtil.java",
                "FileSizeDisplayUtil.java",
                "HttpHeaderConstants.java",
                "CronExpressionUtil.java",
                "JsonPathUtil.java",
                "XmlUtil.java",
                "TimeDisplayUtil.java"
        );

        for (String utilityFile : utilityFiles) {
            assertTrue(Files.isRegularFile(root.resolve("easy-postman-foundation/src/main/java/com/laker/postman/util/" + utilityFile)),
                    utilityFile + " belongs in easy-postman-foundation");
            assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/java/com/laker/postman/util/" + utilityFile)),
                    utilityFile + " must not be owned by the app module");
        }
    }

    @Test
    public void foundationDoesNotOwnUiTypes() throws IOException {
        Path foundationSource = repositoryRoot().resolve("easy-postman-foundation/src/main/java");
        List<String> uiImports = javaSourceFiles(foundationSource).stream()
                .flatMap(file -> uiImportViolations(file).stream())
                .toList();

        assertTrue(uiImports.isEmpty(),
                "Foundation must stay non-UI and must not import AWT/Swing/UI libraries: " + uiImports);
    }

    @Test
    public void appModelPackageStaysHeadless() throws IOException {
        Path modelSource = repositoryRoot().resolve("easy-postman-app/src/main/java/com/laker/postman/model");
        List<String> violations = sourcePackageViolations(modelSource, List.of(
                "javax.swing",
                "java.awt",
                "com.formdev",
                "org.fife",
                "UiSingletonFactory",
                "IconUtil",
                "com.laker.postman.panel."
        ));

        assertTrue(violations.isEmpty(),
                "App model package must stay headless and must not depend on Swing/editor/UI panel types: " + violations);
    }

    @Test
    public void appModelPackageDoesNotOwnUiViewStateTypes() {
        Path root = repositoryRoot();
        Path appModelPackage = root.resolve("easy-postman-app/src/main/java/com/laker/postman/model");

        for (String modelFile : List.of(
                "EnvironmentItem.java",
                "VariableInfo.java",
                "RequestEditSubPanelType.java",
                "VariableSegment.java",
                "CurlRequest.java",
                "TrustedCertificateEntry.java",
                "RequestHistoryItem.java",
                "Category.java",
                "Snippet.java",
                "SnippetType.java",
                "AssertionResult.java",
                "BatchExecutionHistory.java",
                "IterationResult.java",
                "RequestResult.java",
                "RunnerRowData.java",
                "TestResult.java",
                "MessageType.java"
        )) {
            assertFalse(Files.exists(appModelPackage.resolve(modelFile)),
                    modelFile + " is UI/view-state or parser-owned metadata and must not live in the app model package");
        }

        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-app/src/main/java/com/laker/postman/environment/EnvironmentItem.java")),
                "EnvironmentItem belongs to the environment UI/application owner package");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-app/src/main/java/com/laker/postman/variable/VariableInfo.java")),
                "VariableInfo belongs to the variable autocomplete owner package");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-app/src/main/java/com/laker/postman/panel/collections/editor/request/RequestEditSubPanelType.java")),
                "RequestEditSubPanelType belongs with the request editor UI package");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-app/src/main/java/com/laker/postman/variable/VariableSegment.java")),
                "VariableSegment belongs with variable parsing/autocomplete metadata");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-app/src/main/java/com/laker/postman/variable/VariableParser.java")),
                "VariableParser belongs with variable parsing/autocomplete metadata");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-app/src/main/java/com/laker/postman/service/curl/CurlRequest.java")),
                "CurlRequest is a cURL parser DTO and belongs with the cURL parser package");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-app/src/main/java/com/laker/postman/service/curl/CurlImportUtil.java")),
                "CurlImportUtil is a cURL import adapter and belongs with the cURL parser package");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-foundation/src/main/java/com/laker/postman/certificate/TrustedCertificateEntry.java")),
                "TrustedCertificateEntry is a headless trust-material DTO and belongs in foundation");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-app/src/main/java/com/laker/postman/history/RequestHistoryItem.java")),
                "RequestHistoryItem belongs with request history ownership");
        for (String snippetFile : List.of("Category.java", "Snippet.java", "SnippetType.java")) {
            assertTrue(Files.isRegularFile(root.resolve(
                            "easy-postman-app/src/main/java/com/laker/postman/snippet/" + snippetFile)),
                    snippetFile + " belongs with script snippet ownership");
        }
        for (String functionalFile : List.of(
                "AssertionResult.java",
                "BatchExecutionHistory.java",
                "IterationResult.java",
                "RequestResult.java",
                "RunnerRowData.java"
        )) {
            assertTrue(Files.isRegularFile(root.resolve(
                            "easy-postman-app/src/main/java/com/laker/postman/functional/model/" + functionalFile)),
                    functionalFile + " belongs with functional runner ownership");
        }
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-app/src/main/java/com/laker/postman/script/model/TestResult.java")),
                "TestResult belongs with script assertion result ownership");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-app/src/main/java/com/laker/postman/stream/MessageType.java")),
                "MessageType belongs with stream message ownership");

        assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/java/com/laker/postman/util/VariableParser.java")),
                "VariableParser is variable-domain parsing logic, not a generic app util");
        assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/java/com/laker/postman/util/CurlImportUtil.java")),
                "CurlImportUtil is cURL import logic, not a generic app util");
    }

    @Test
    public void appModelPackageDoesNotOwnRuntimeHttpExchangeModels() throws IOException {
        Path modelSource = repositoryRoot().resolve("easy-postman-app/src/main/java/com/laker/postman/model");
        Set<String> actualFiles = Files.isDirectory(modelSource)
                ? javaSourceFiles(modelSource).stream()
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toCollection(TreeSet::new))
                : Set.of();

        assertTrue(actualFiles.isEmpty(),
                "App model package must stay empty; runtime HTTP exchange snapshots belong in easy-postman-http-runtime: "
                        + actualFiles);
    }

    @Test
    public void modelPackageDoesNotDependOnServiceLayer() throws IOException {
        Path modelSource = repositoryRoot().resolve("easy-postman-app/src/main/java/com/laker/postman/model");
        List<String> violations = sourcePackageViolations(modelSource, List.of("com.laker.postman.service."));

        assertTrue(violations.isEmpty(),
                "App model package must not depend on app service layer types: " + violations);
    }

    @Test
    public void serviceLayerDoesNotDependOnSwingPanels() throws IOException {
        Path serviceSource = repositoryRoot().resolve("easy-postman-app/src/main/java/com/laker/postman/service");
        List<String> violations = sourcePackageViolations(serviceSource, List.of(
                "com.laker.postman.common.component.CsvDataPanel",
                "CsvDataPanel.CsvState"
        ));

        assertTrue(violations.isEmpty(),
                "App service layer must not depend on concrete Swing panels or panel-owned DTOs: " + violations);
    }

    @Test
    public void httpRequestValidationDoesNotOwnUiPromptsOrColors() {
        Path root = repositoryRoot();
        List<String> violations = new ArrayList<>();
        for (String relativePath : List.of(
                "easy-postman-app/src/main/java/com/laker/postman/http/request/HttpRequestValidator.java",
                "easy-postman-app/src/main/java/com/laker/postman/http/request/HttpRequestValidationResult.java",
                "easy-postman-request-core/src/main/java/com/laker/postman/request/util/HttpUrlUtil.java",
                "easy-postman-app/src/main/java/com/laker/postman/http/request/HttpRequestProtocol.java",
                "easy-postman-request-core/src/main/java/com/laker/postman/request/defaults/HttpRequestDefaults.java",
                "easy-postman-request-core/src/main/java/com/laker/postman/request/defaults/GeneratedRequestHeaderPolicy.java")) {
            violations.addAll(sourceContainsViolations(root.resolve(relativePath), List.of(
                    "import javax.swing",
                    "import java.awt",
                    "JOptionPane",
                    "ModernColors",
                    "methodColor"
            )));
        }

        assertTrue(violations.isEmpty(),
                "HTTP request validation/utilities must return structured results; UI prompts and colors belong in panels: "
                        + violations);
    }

    @Test
    public void okHttpResponseHandlerDoesNotOwnSwingDownloadUi() {
        Path responseHandler = repositoryRoot()
                .resolve("easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/okhttp/OkHttpResponseHandler.java");
        List<String> violations = sourceContainsViolations(responseHandler, List.of(
                "import javax.swing",
                "DownloadProgressDialog",
                "JOptionPane",
                "SwingUtilities"
        ));

        assertTrue(violations.isEmpty(),
                "OkHttp response handling must use UI-neutral sinks; Swing/JavaFX adapters belong in the UI layer: "
                        + violations);
    }

    @Test
    public void webSocketLifecycleLoggingDoesNotOwnConsolePanelUi() {
        Path listener = repositoryRoot()
                .resolve("easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/okhttp/WebSocketLifecycleLogListener.java");
        List<String> violations = sourceContainsViolations(listener, List.of(
                "ConsolePanel",
                "GraphicsEnvironment",
                "javax.swing",
                "java.awt"
        ));

        assertTrue(violations.isEmpty(),
                "WebSocket transport lifecycle logging must use UI-neutral sinks; Swing/JavaFX adapters belong in UI: "
                        + violations);
    }

    @Test
    public void sslConfigurationDoesNotOwnConsolePanelUi() {
        Path sslConfiguration = repositoryRoot()
                .resolve("easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/ssl/SSLConfigurationUtil.java");
        List<String> violations = sourceContainsViolations(sslConfiguration, List.of(
                "ConsolePanel",
                "GraphicsEnvironment",
                "javax.swing",
                "java.awt"
        ));

        assertTrue(violations.isEmpty(),
                "SSL transport configuration must use UI-neutral lifecycle logging sinks: " + violations);
    }

    @Test
    public void cookieServiceDoesNotOwnSwingDispatch() {
        Path cookieService = repositoryRoot()
                .resolve("easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/cookie/HttpCookieStore.java");
        List<String> violations = sourceContainsViolations(cookieService, List.of(
                "SwingUtilities",
                "javax.swing",
                "java.awt",
                "com.laker.postman.panel."
        ));

        assertTrue(violations.isEmpty(),
                "Cookie change notification must use UI-neutral callback dispatchers; Swing/JavaFX adapters belong in UI: "
                        + violations);
    }

    @Test
    public void httpRuntimeServicesUseSettingsProviderBoundary() throws IOException {
        Path root = repositoryRoot();
        List<String> violations = new ArrayList<>();
        for (String runtimeSource : List.of(
                "easy-postman-app/src/main/java/com/laker/postman/http/request",
                "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/transport",
                "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/okhttp",
                "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/ssl",
                "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/sse",
                "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/cookie",
                "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/redirect",
                "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/error",
                "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/config")) {
            violations.addAll(sourcePackageViolations(root.resolve(runtimeSource), List.of(
                    "import com.laker.postman.service.setting.SettingManager",
                    "SettingManager."
            )));
        }

        assertTrue(violations.isEmpty(),
                "HTTP runtime/service code must read runtime settings through HttpRuntimeSettingsProvider so Swing, JavaFX, CLI, "
                        + "and tests can provide different hosts: " + violations);
    }

    @Test
    public void httpRuntimeTransportDoesNotOwnUiCode() throws IOException {
        Path root = repositoryRoot();
        List<String> violations = new ArrayList<>();
        for (String runtimeSource : List.of(
                "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/transport",
                "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/okhttp",
                "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/ssl",
                "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/sse",
                "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/interaction",
                "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/observation",
                "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/cookie",
                "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/redirect",
                "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/error")) {
            violations.addAll(sourcePackageViolations(root.resolve(runtimeSource), List.of(
                    "import javax.swing",
                    "import java.awt",
                    "com.laker.postman.panel.",
                    "UiSingletonFactory"
            )));
        }

        assertTrue(violations.isEmpty(),
                "HTTP transport runtime must stay UI-neutral so Swing, JavaFX, CLI, and performance runners can share it: "
                        + violations);
    }

    @Test
    public void httpRuntimeUsesExplicitPackageAndClassNames() {
        Path root = repositoryRoot();
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/transport/HttpTransport.java")),
                "HTTP transport execution should be exposed through an injectable port");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/transport/DefaultHttpTransport.java")),
                "Default HTTP transport wiring belongs in easy-postman-http-runtime/http.runtime.transport");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/transport/ScopedHttpBaseClientProvider.java")),
                "Scoped HTTP base clients belong with transport runtime execution");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/okhttp/OkHttpExchangeEventListener.java")),
                "OkHttp exchange event collection belongs in http.runtime.okhttp and should not be named Console");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/okhttp/WebSocketLifecycleLogListener.java")),
                "WebSocket lifecycle logging belongs in http.runtime.okhttp");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/ssl/SSLConfigurationUtil.java")),
                "TLS/certificate configuration belongs in http.runtime.ssl");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/sse/SseResponseCallback.java")),
                "SSE response callbacks belong in http.runtime.sse and should not carry UI names");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/cookie/HttpCookieStore.java")),
                "HTTP cookie state belongs in http.runtime.cookie");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/redirect/HttpRedirectExecutor.java")),
                "Manual redirect execution belongs in http.runtime.redirect");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/error/NetworkErrorMessageResolver.java")),
                "Network transport error mapping belongs in http.runtime.error");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/model/PreparedRequest.java")),
                "Prepared request exchange snapshot belongs in easy-postman-http-runtime");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/model/HttpResponse.java")),
                "HTTP response exchange snapshot belongs in easy-postman-http-runtime");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/model/HttpEventInfo.java")),
                "HTTP timing/trace snapshot belongs in easy-postman-http-runtime");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-app/src/main/java/com/laker/postman/http/request/PreparedRequestFactory.java")),
                "Prepared request creation belongs in http.request and should use a factory name");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-app/src/main/java/com/laker/postman/http/request/PreparedRequestFinalizer.java")),
                "Prepared request send-time finalization belongs in http.request");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/config/HttpRequestRuntimeSettingsResolver.java")),
                "Request setting normalization belongs in HTTP runtime config");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/config/HttpRuntimeSettingsProvider.java")),
                "HTTP runtime settings belong in easy-postman-http-runtime/http.runtime.config");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/transport/HttpTransport.java")),
                "HTTP transport port belongs in easy-postman-http-runtime/http.runtime.transport");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/transport/DefaultHttpTransport.java")),
                "Default HTTP transport orchestration belongs in easy-postman-http-runtime/http.runtime.transport");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/transport/HttpExchangeOptions.java")),
                "HTTP exchange options keep the transport port small and UI-neutral");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/transport/RealtimeConnectionOptions.java")),
                "Realtime connection options keep SSE/WebSocket transport calls explicit");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/interaction/DownloadProgressSink.java")),
                "HTTP UI-neutral interaction ports belong in http.runtime.interaction");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/observation/NetworkLogSink.java")),
                "HTTP network observation ports belong in http.runtime.observation");
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-app/src/main/java/com/laker/postman/panel/http/runtime/SwingHttpRuntimeInteractionAdapter.java")),
                "Swing-specific HTTP runtime adapters belong in panel.http.runtime");

        for (String retiredPath : List.of(
                "easy-postman-app/src/main/java/com/laker/postman/service/http/HttpService.java",
                "easy-postman-app/src/main/java/com/laker/postman/service/http/HttpSingleRequestExecutor.java",
                "easy-postman-app/src/main/java/com/laker/postman/service/http/HttpTransportRuntime.java",
                "easy-postman-app/src/main/java/com/laker/postman/service/http/HttpRuntimeExecutor.java",
                "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/transport/HttpTransportRuntime.java",
                "easy-postman-http-runtime/src/main/java/com/laker/postman/http/runtime/transport/HttpRuntimeExecutor.java",
                "easy-postman-app/src/main/java/com/laker/postman/service/http/HttpBaseClientProvider.java",
                "easy-postman-app/src/main/java/com/laker/postman/service/http/HttpCallTracker.java",
                "easy-postman-app/src/main/java/com/laker/postman/service/http/ScopedHttpBaseClientProvider.java",
                "easy-postman-app/src/main/java/com/laker/postman/service/http/CompressionDecompressNetworkInterceptor.java",
                "easy-postman-app/src/main/java/com/laker/postman/service/http/HttpUtil.java",
                "easy-postman-app/src/main/java/com/laker/postman/service/http/HttpRequestUtil.java",
                "easy-postman-app/src/main/java/com/laker/postman/service/http/PreparedRequestBuilder.java",
                "easy-postman-app/src/main/java/com/laker/postman/service/http/RequestFinalizer.java",
                "easy-postman-app/src/main/java/com/laker/postman/service/http/RequestSettingsResolver.java",
                "easy-postman-app/src/main/java/com/laker/postman/service/http/CookieService.java",
                "easy-postman-app/src/main/java/com/laker/postman/service/http/RedirectHandler.java",
                "easy-postman-app/src/main/java/com/laker/postman/service/http/NetworkErrorMessageResolver.java",
                "easy-postman-app/src/main/java/com/laker/postman/service/http/okhttp",
                "easy-postman-app/src/main/java/com/laker/postman/service/http/ssl",
                "easy-postman-app/src/main/java/com/laker/postman/service/http/sse",
                "easy-postman-app/src/main/java/com/laker/postman/http/runtime/okhttp/EasyConsoleEventListener.java",
                "easy-postman-app/src/main/java/com/laker/postman/http/runtime/okhttp/LogWebSocketListener.java",
                "easy-postman-app/src/main/java/com/laker/postman/http/runtime/sse/SseUiCallback.java",
                "easy-postman-app/src/main/java/com/laker/postman/http/runtime/sse/SseResEventListener.java",
                "easy-postman-app/src/main/java/com/laker/postman/http/runtime/sse/SseEventListener.java",
                "easy-postman-app/src/main/java/com/laker/postman/http/runtime/transport",
                "easy-postman-app/src/main/java/com/laker/postman/http/runtime/okhttp",
                "easy-postman-app/src/main/java/com/laker/postman/http/runtime/ssl",
                "easy-postman-app/src/main/java/com/laker/postman/http/runtime/sse",
                "easy-postman-app/src/main/java/com/laker/postman/http/runtime/cookie",
                "easy-postman-app/src/main/java/com/laker/postman/http/runtime/redirect",
                "easy-postman-app/src/main/java/com/laker/postman/http/runtime/error",
                "easy-postman-app/src/main/java/com/laker/postman/http/runtime/config",
                "easy-postman-app/src/main/java/com/laker/postman/http/runtime/interaction",
                "easy-postman-app/src/main/java/com/laker/postman/http/runtime/observation",
                "easy-postman-app/src/main/java/com/laker/postman/model/PreparedRequest.java",
                "easy-postman-app/src/main/java/com/laker/postman/model/HttpResponse.java",
                "easy-postman-app/src/main/java/com/laker/postman/model/HttpEventInfo.java",
                "easy-postman-app/src/main/java/com/laker/postman/http/callback",
                "easy-postman-app/src/main/java/com/laker/postman/http/response",
                "easy-postman-app/src/main/java/com/laker/postman/http/trace",
                "easy-postman-app/src/main/java/com/laker/postman/panel/http/SwingHttpResponseInteraction.java"
        )) {
            assertFalse(Files.exists(root.resolve(retiredPath)),
                    retiredPath + " is a retired HTTP runtime name/package");
        }
    }

    @Test
    public void settingManagerDoesNotResolveSidebarUiTabs() {
        Path settingManager = repositoryRoot()
                .resolve("easy-postman-app/src/main/java/com/laker/postman/service/setting/SettingManager.java");
        List<String> violations = sourceContainsViolations(settingManager, List.of(
                "com.laker.postman.panel.",
                "panel.sidebar",
                "List<SidebarTab>",
                "Set<SidebarTab>",
                "SidebarTab.resolve"
        ));

        assertTrue(violations.isEmpty(),
                "SettingManager must expose raw sidebar settings only and must not resolve UI sidebar tabs: "
                        + violations);
    }

    @Test
    public void pluginApiDoesNotExposeEditorImplementationTypes() throws IOException {
        Path pluginApiSource = repositoryRoot().resolve("easy-postman-plugin-api/src/main/java");
        List<String> violations = sourcePackageViolations(pluginApiSource, List.of(
                "org.fife.ui.autocomplete"
        ));

        assertTrue(violations.isEmpty(),
                "Plugin API must not expose concrete editor implementation types: " + violations);
    }

    @Test
    public void performanceHeadlessPackageDirectoriesStayOutOfPanelNamespace() {
        Path root = repositoryRoot();
        List<Path> retiredHeadlessPanelPackages = List.of(
                root.resolve("easy-postman-app/src/main/java/com/laker/postman/panel/performance/execution"),
                root.resolve("easy-postman-app/src/main/java/com/laker/postman/panel/performance/runtime"),
                root.resolve("easy-postman-app/src/main/java/com/laker/postman/panel/performance/model"),
                root.resolve("easy-postman-app/src/main/java/com/laker/postman/panel/performance/plan"),
                root.resolve("easy-postman-app/src/main/java/com/laker/postman/panel/performance/report"),
                root.resolve("easy-postman-app/src/test/java/com/laker/postman/panel/performance/execution"),
                root.resolve("easy-postman-app/src/test/java/com/laker/postman/panel/performance/runtime"),
                root.resolve("easy-postman-app/src/test/java/com/laker/postman/panel/performance/model"),
                root.resolve("easy-postman-app/src/test/java/com/laker/postman/panel/performance/plan"),
                root.resolve("easy-postman-app/src/test/java/com/laker/postman/panel/performance/report")
        );
        List<Path> existingRetiredPackages = retiredHeadlessPanelPackages.stream()
                .filter(Files::exists)
                .toList();

        assertTrue(existingRetiredPackages.isEmpty(),
                "Headless performance execution/runtime/model/plan/report packages must move out of panel namespace: "
                        + existingRetiredPackages);
    }

    @Test
    public void performanceSourcesDoNotReferenceRetiredPanelHeadlessPackages() throws IOException {
        Path root = repositoryRoot();
        List<String> retiredPackageNames = retiredPanelPerformanceHeadlessPackageNames();
        List<String> violations = Stream.of(
                        root.resolve("easy-postman-app/src/main/java"),
                        root.resolve("easy-postman-app/src/test/java")
                )
                .flatMap(sourceRoot -> {
                    try {
                        return sourcePackageViolations(sourceRoot, retiredPackageNames).stream();
                    } catch (IOException e) {
                        throw new IllegalStateException("Failed to scan " + sourceRoot, e);
                    }
                })
                .toList();

        assertTrue(violations.isEmpty(),
                "Main/test sources must not reference retired panel.performance execution/runtime/model/plan/report packages: "
                        + violations);
    }

    @Test
    public void performanceHeadlessSourcesStayUiFree() throws IOException {
        Path root = repositoryRoot();
        List<Path> performanceHeadlessRoots = List.of(
                root.resolve("easy-postman-app/src/main/java/com/laker/postman/performance"),
                root.resolve("easy-postman-app/src/test/java/com/laker/postman/performance"),
                root.resolve("easy-postman-performance-core/src/main/java/com/laker/postman/performance"),
                root.resolve("easy-postman-performance-core/src/test/java/com/laker/postman/performance")
        );

        List<String> uiImportViolations = sourceRootsPackageViolations(
                performanceHeadlessRoots,
                forbiddenSwingAndPanelImports()
        );
        assertTrue(uiImportViolations.isEmpty(),
                "Headless performance app/core main/test sources must not import Swing, panel, or shared UI packages: "
                        + uiImportViolations);
    }

    @Test
    public void performanceRuntimeBehaviorTestsStayInHeadlessRuntimePackage() {
        Path root = repositoryRoot();
        Path panelPerformanceTests = root.resolve("easy-postman-app/src/test/java/com/laker/postman/panel/performance");
        Path headlessRuntimeTests = root.resolve("easy-postman-app/src/test/java/com/laker/postman/performance/runtime");

        for (String testFile : List.of(
                "PerformanceExecutionEngineTest.java",
                "PerformancePlanExecutorTest.java"
        )) {
            assertTrue(Files.isRegularFile(headlessRuntimeTests.resolve(testFile)),
                    testFile + " exercises headless runtime behavior and belongs in com.laker.postman.performance.runtime");
            assertFalse(Files.exists(panelPerformanceTests.resolve(testFile)),
                    testFile + " must not stay in the panel.performance UI test package");
        }
    }

    @Test
    public void performanceSwingTreePlanAdapterStaysInUiTreePackage() {
        Path root = repositoryRoot();
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-app/src/main/java/com/laker/postman/panel/performance/tree/PerformanceSwingTreePlanAdapter.java")),
                "PerformanceSwingTreePlanAdapter is a Swing tree adapter and belongs in panel.performance.tree");
        assertFalse(Files.exists(root.resolve(
                        "easy-postman-app/src/main/java/com/laker/postman/performance/plan/PerformanceSwingTreePlanAdapter.java")),
                "PerformanceSwingTreePlanAdapter must not live in the headless performance.plan package");
        assertFalse(Files.exists(root.resolve(
                        "easy-postman-app/src/main/java/com/laker/postman/panel/performance/plan/PerformanceSwingTreePlanAdapter.java")),
                "PerformanceSwingTreePlanAdapter must not live in the retired panel.performance.plan package");
    }

    @Test
    public void performanceRequestNodeStateMutationsStayCentralized() throws IOException {
        Path root = repositoryRoot();
        Path sourceRoot = root.resolve("easy-postman-app/src/main/java");
        Set<String> allowedFiles = Set.of(
                "com/laker/postman/performance/model/PerformanceTreeNode.java",
                "com/laker/postman/panel/performance/PerformanceTreeSnapshot.java",
                "com/laker/postman/panel/performance/tree/PerformanceRequestNodeStateSynchronizer.java",
                "com/laker/postman/panel/performance/tree/PerformanceSwingTreePlanAdapter.java"
        );

        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        String relative = sourceRoot.relativize(path).toString();
                        return relative.startsWith("com/laker/postman/panel/performance/")
                                || relative.equals("com/laker/postman/performance/model/PerformanceTreeNode.java");
                    })
                    .filter(path -> !allowedFiles.contains(sourceRoot.relativize(path).toString()))
                    .forEach(path -> {
                        try {
                            String source = Files.readString(path);
                            if (source.matches("(?s).*\\.(?:httpRequestItem|requestSnapshot|requestExecutionScope)\\s*=(?!=).*")) {
                                violations.add(sourceRoot.relativize(path).toString());
                            }
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to scan " + path, e);
                        }
                    });
        }

        assertTrue(violations.isEmpty(),
                "Performance request node item/snapshot/scope mutations must go through PerformanceRequestNodeStateSynchronizer: "
                        + violations);
    }

    @Test
    public void performanceThreadGroupPlannerDoesNotHavePanelWrapper() {
        Path root = repositoryRoot();
        assertTrue(Files.isRegularFile(root.resolve(
                        "easy-postman-performance-core/src/main/java/com/laker/postman/performance/core/threadgroup/PerformanceCoreThreadGroupPlanner.java")),
                "Thread group planning is already implemented by performance-core");
        assertFalse(Files.exists(root.resolve(
                        "easy-postman-app/src/main/java/com/laker/postman/panel/performance/threadgroup/PerformanceThreadGroupPlanner.java")),
                "Do not keep a panel package wrapper around PerformanceCoreThreadGroupPlanner");
        assertFalse(Files.exists(root.resolve(
                        "easy-postman-app/src/test/java/com/laker/postman/panel/performance/threadgroup/PerformanceThreadGroupPlannerTest.java")),
                "Wrapper tests belong with the core planner test after the wrapper is removed");
    }

    @Test
    public void performanceResultCollectorsAndSwingPanelsStayInOwnedPackages() {
        Path root = repositoryRoot();
        Path headlessResultPackage = root.resolve("easy-postman-app/src/main/java/com/laker/postman/performance/result");
        for (String file : List.of(
                "PerformanceResultCollector.java",
                "PerformanceResultDisplayMapper.java",
                "PerformanceWorkerResultDetailDisplayMapper.java"
        )) {
            assertTrue(Files.isRegularFile(headlessResultPackage.resolve(file)),
                    file + " is non-UI result logic and belongs in com.laker.postman.performance.result");
            assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/java/com/laker/postman/panel/performance/result/" + file)),
                    file + " must not remain in panel.performance.result");
        }

        Path swingResultPackage = root.resolve("easy-postman-app/src/main/java/com/laker/postman/panel/performance/result");
        for (String file : List.of(
                "PerformanceReportPanel.java",
                "PerformanceResultTablePanel.java",
                "PerformanceResultTableVisualizer.java",
                "PerformanceTrendPanel.java",
                "PerformanceTrendTheme.java",
                "PerformanceTrendView.java",
                "LazyPerformanceTrendPanel.java"
        )) {
            assertTrue(Files.isRegularFile(swingResultPackage.resolve(file)),
                    file + " is Swing result UI and belongs in panel.performance.result");
            assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/java/com/laker/postman/performance/result/" + file)),
                    file + " must not move into the headless performance.result package");
        }
    }

    @Test
    public void appPluginRuntimeAccessorsUseHostPackageName() {
        Path root = repositoryRoot();
        assertTrue(Files.isRegularFile(root.resolve("easy-postman-app/src/main/java/com/laker/postman/plugin/host/PluginAccess.java")));
        assertTrue(Files.isRegularFile(root.resolve("easy-postman-app/src/main/java/com/laker/postman/plugin/host/GitServiceAccess.java")));
        assertTrue(Files.isRegularFile(root.resolve("easy-postman-app/src/main/java/com/laker/postman/plugin/host/ClientCertificatePluginAccess.java")));
        assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/java/com/laker/postman/plugin/bridge")),
                "The retired app-side plugin.bridge package must stay removed");
    }

    @Test
    public void startupSpecificWindowsStayInStartupPackage() {
        Path root = repositoryRoot();
        assertTrue(Files.isRegularFile(root.resolve(
                "easy-postman-app/src/main/java/com/laker/postman/startup/SplashWindow.java")));
        assertTrue(Files.isRegularFile(root.resolve(
                "easy-postman-app/src/main/java/com/laker/postman/startup/SplashWindowInitializationException.java")));
        Path retiredCommonWindowPackage = root.resolve("easy-postman-app/src/main/java/com/laker/postman/common/window");
        if (Files.exists(retiredCommonWindowPackage)) {
            try (Stream<Path> files = Files.walk(retiredCommonWindowPackage)) {
                assertTrue(files.noneMatch(Files::isRegularFile),
                        "Startup-specific windows must not live under the app common.window package");
            } catch (IOException e) {
                throw new IllegalStateException("Failed to inspect " + retiredCommonWindowPackage, e);
            }
        }
    }

    @Test
    public void staticRuntimeHelpersUseLombokUtilityClass() throws IOException {
        Path root = repositoryRoot();
        for (Path helper : List.of(
                root.resolve("easy-postman-platform/src/main/java/com/laker/postman/ioc/BeanFactory.java"),
                root.resolve("easy-postman-plugin-runtime/src/main/java/com/laker/postman/plugin/runtime/PluginLoader.java")
        )) {
            String source = Files.readString(helper);
            assertTrue(source.contains("@UtilityClass"), helper + " should use Lombok @UtilityClass for static helpers");
            assertFalse(source.contains("private " + fileNameWithoutExtension(helper) + "()"),
                    helper + " should not keep a hand-written private utility constructor");
        }
    }

    @Test
    public void appExitFlowMustNotCreateLazySingletonPanels() throws IOException {
        Path root = repositoryRoot();
        String exitCoordinator = Files.readString(root.resolve(
                "easy-postman-app/src/main/java/com/laker/postman/panel/lifecycle/AppExitCoordinator.java"));
        String mainFrame = Files.readString(root.resolve(
                "easy-postman-app/src/main/java/com/laker/postman/frame/MainFrame.java"));

        assertFalse(exitCoordinator.contains("getInstance(FunctionalPanel.class)"),
                "Exit flow must not create FunctionalPanel just to save on shutdown; use getExistingInstance");
        assertFalse(exitCoordinator.contains("getInstance(PerformancePanel.class)"),
                "Exit flow must not create PerformancePanel just to save on shutdown; use getExistingInstance");
        assertFalse(mainFrame.contains("getInstance(PerformancePanel.class)"),
                "Window cleanup must not create PerformancePanel just to dispose timers; use getExistingInstance");
    }

    @Test
    public void workspaceSwitchFlowMustNotCreateLazySingletonPanels() throws IOException {
        Path root = repositoryRoot();
        String topMenuBar = Files.readString(root.resolve(
                "easy-postman-app/src/main/java/com/laker/postman/panel/topmenu/TopMenuBar.java"));
        String workspacePanel = Files.readString(root.resolve(
                "easy-postman-app/src/main/java/com/laker/postman/panel/workspace/WorkspacePanel.java"));

        assertFalse(topMenuBar.contains("getInstance(FunctionalPanel.class)"),
                "Workspace switching from top menu must refresh FunctionalPanel only if it already exists");
        assertFalse(topMenuBar.contains("getInstance(PerformancePanel.class)"),
                "Workspace switching from top menu must refresh PerformancePanel only if it already exists");
        assertFalse(workspacePanel.contains("getInstance(FunctionalPanel.class)"),
                "Workspace switching from workspace panel must refresh FunctionalPanel only if it already exists");
        assertFalse(workspacePanel.contains("getInstance(PerformancePanel.class)"),
                "Workspace switching from workspace panel must refresh PerformancePanel only if it already exists");
    }

    @Test
    public void architectureDocsAndSkillsUseCurrentModuleNames() throws IOException {
        Path root = repositoryRoot();
        List<Path> files = List.of(
                root.resolve("AGENTS.md"),
                root.resolve("docs/ARCHITECTURE_MODULES_zh.md"),
                root.resolve("docs/PLUGINS_zh.md"),
                root.resolve("docs/PLUGIN_RUNTIME_ARCHITECTURE_zh.md"),
                root.resolve(".codex/skills/module-architecture-boundaries/SKILL.md"),
                root.resolve(".codex/skills/fontsutil-font-usage/SKILL.md"),
                root.resolve(".codex/skills/swing-flatlaf-miglayout-principles/SKILL.md")
        );

        for (Path file : files) {
            String source = Files.readString(file);
            assertFalse(source.contains("easy-postman-plugin-bridge"), file + " still documents the old bridge module");
            assertFalse(source.contains("easy-postman-plugin-ui"), file + " still documents the old plugin UI module");
            assertFalse(source.contains("api / bridge / ui"), file + " still documents the old bridge wording");
            assertFalse(source.contains("easy-postman-performance-runtime-okhttp"),
                    file + " still documents the retired performance runtime module");
        }
    }

    @Test
    public void appAndUiIconResourcesHaveSingleOwner() throws IOException {
        Path root = repositoryRoot();
        Set<String> appIcons = resourceFileNames(root.resolve("easy-postman-app/src/main/resources/icons"));
        Set<String> uiIcons = resourceFileNames(root.resolve("easy-postman-ui/src/main/resources/icons"));
        appIcons.retainAll(uiIcons);

        assertTrue(appIcons.isEmpty(),
                "Icon resources must have a single owner. Shared control icons belong in easy-postman-ui, "
                        + "and app/domain icons belong in easy-postman-app: " + appIcons);
    }

    @Test
    public void uiModuleOwnsReusableUiResources() {
        Path root = repositoryRoot();
        assertTrue(Files.isRegularFile(root.resolve("easy-postman-foundation/src/main/resources/common-messages.properties")));
        assertTrue(Files.isRegularFile(root.resolve("easy-postman-foundation/src/main/resources/common-messages_zh.properties")));
        assertTrue(Files.isRegularFile(root.resolve("easy-postman-ui/src/main/resources/ui-messages.properties")));
        assertTrue(Files.isRegularFile(root.resolve("easy-postman-ui/src/main/resources/ui-messages_zh.properties")));
        assertTrue(Files.isRegularFile(root.resolve("easy-postman-ui/src/main/resources/themes/easypostman-light.xml")));
        assertTrue(Files.isRegularFile(root.resolve("easy-postman-ui/src/main/resources/themes/easypostman-dark.xml")));
        assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/resources/themes/easypostman-light.xml")));
        assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/resources/themes/easypostman-dark.xml")));
    }

    @Test
    public void sharedControlIconsStayInUiResources() throws IOException {
        Path root = repositoryRoot();
        Set<String> uiIcons = resourceFileNames(root.resolve("easy-postman-ui/src/main/resources/icons"));
        Set<String> appIcons = resourceFileNames(root.resolve("easy-postman-app/src/main/resources/icons"));
        List<String> sharedIcons = List.of(
                "arrow-down.svg",
                "arrow-up.svg",
                "cancel.svg",
                "check.svg",
                "chevron-down.svg",
                "chevron-right.svg",
                "clear.svg",
                "close.svg",
                "collapse.svg",
                "connect.svg",
                "copy.svg",
                "delete.svg",
                "detail.svg",
                "download.svg",
                "duplicate.svg",
                "edit.svg",
                "expand.svg",
                "export.svg",
                "eye-close.svg",
                "eye-open.svg",
                "file.svg",
                "format.svg",
                "import.svg",
                "info.svg",
                "load.svg",
                "matchCase.svg",
                "matchCaseHovered.svg",
                "matchCaseSelected.svg",
                "more.svg",
                "paste.svg",
                "plus.svg",
                "refresh.svg",
                "replace-all.svg",
                "replace.svg",
                "save.svg",
                "search.svg",
                "send.svg",
                "start.svg",
                "stop.svg",
                "text-file.svg",
                "warning.svg",
                "words.svg",
                "wordsHovered.svg",
                "wordsSelected.svg",
                "wrap.svg",
                "ws-close.svg"
        );

        for (String icon : sharedIcons) {
            assertTrue(uiIcons.contains(icon), icon + " is a reusable control/status icon and belongs in easy-postman-ui");
            assertFalse(appIcons.contains(icon), icon + " must not be duplicated or owned by easy-postman-app");
        }
    }

    @Test
    public void genericCommonMessageKeysStayOutOfModuleOwnedBundles() throws IOException {
        Path root = repositoryRoot();
        Set<String> commonKeys = resourceKeys(root.resolve("easy-postman-foundation/src/main/resources/common-messages.properties"));
        List<Path> moduleBundles;
        try (Stream<Path> files = Files.walk(root)) {
            moduleBundles = files
                    .filter(path -> path.getFileName().toString().endsWith(".properties"))
                    .filter(path -> path.getFileName().toString().contains("messages"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .filter(path -> !path.toString().contains("/easy-postman-foundation/src/main/resources/common-messages"))
                    .toList();
        }

        List<String> violations = moduleBundles.stream()
                .flatMap(file -> resourceKeys(file).stream()
                        .filter(commonKeys::contains)
                        .map(key -> file + " duplicates common key " + key))
                .toList();

        assertTrue(violations.isEmpty(),
                "Generic short labels belong only in foundation common-messages bundles: " + violations);
    }

    @Test
    public void uiModuleDoesNotDependOnAppMessageBundleKeys() throws IOException {
        Path root = repositoryRoot();
        List<String> uiSourceViolations = javaSourceFiles(root.resolve("easy-postman-ui/src/main/java")).stream()
                .flatMap(file -> uiMessageKeyViolations(file).stream())
                .toList();
        assertTrue(uiSourceViolations.isEmpty(),
                "Shared UI code must use UiI18n/UiMessageKeys or CommonI18n/CommonMessageKeys instead of app MessageKeys: "
                        + uiSourceViolations);

        List<String> appBundleViolations = Stream.of(
                        root.resolve("easy-postman-app/src/main/resources/messages_en.properties"),
                        root.resolve("easy-postman-app/src/main/resources/messages_zh.properties")
                )
                .flatMap(file -> resourceBundleOwnerViolations(file, "table.", "notification.").stream())
                .toList();
        assertTrue(appBundleViolations.isEmpty(),
                "Shared UI table/notification strings belong in easy-postman-ui ui-messages bundles: "
                        + appBundleViolations);
    }

    @Test
    public void officialPluginMessagesStayWithPluginModules() throws IOException {
        Path root = repositoryRoot();
        assertTrue(Files.isRegularFile(root.resolve("easy-postman-plugins/plugin-decompiler/src/main/resources/decompiler-messages.properties")));
        assertTrue(Files.isRegularFile(root.resolve("easy-postman-plugins/plugin-decompiler/src/main/resources/decompiler-messages_zh.properties")));
        assertTrue(Files.isRegularFile(root.resolve("easy-postman-plugins/plugin-client-cert/src/main/resources/client-cert-messages.properties")));
        assertTrue(Files.isRegularFile(root.resolve("easy-postman-plugins/plugin-client-cert/src/main/resources/client-cert-messages_zh.properties")));

        List<String> appBundleViolations = Stream.of(
                        root.resolve("easy-postman-app/src/main/resources/messages_en.properties"),
                        root.resolve("easy-postman-app/src/main/resources/messages_zh.properties")
                )
                .flatMap(file -> resourceBundleOwnerViolations(file, "toolbox.decompiler").stream())
                .toList();
        assertTrue(appBundleViolations.isEmpty(),
                "Decompiler plugin strings belong in plugin-decompiler message bundles: " + appBundleViolations);

        List<String> pluginSourceViolations = Stream.of(
                        root.resolve("easy-postman-plugins/plugin-decompiler/src/main/java"),
                        root.resolve("easy-postman-plugins/plugin-client-cert/src/main/java")
                )
                .flatMap(sourceRoot -> {
                    try {
                        return javaSourceFiles(sourceRoot).stream();
                    } catch (IOException e) {
                        throw new IllegalStateException("Failed to read " + sourceRoot, e);
                    }
                })
                .flatMap(file -> pluginAppMessageKeyViolations(file).stream())
                .toList();
        assertTrue(pluginSourceViolations.isEmpty(),
                "Plugins must use plugin-local message keys/bundles instead of app MessageKeys: "
                        + pluginSourceViolations);
    }

    @Test
    public void pluginEntryIconsStayWithPlugins() {
        Path root = repositoryRoot();
        assertTrue(Files.isRegularFile(root.resolve("easy-postman-plugins/plugin-kafka/src/main/resources/icons/kafka.svg")));
        assertTrue(Files.isRegularFile(root.resolve("easy-postman-plugins/plugin-decompiler/src/main/resources/icons/decompile.svg")));
        assertTrue(Files.isRegularFile(root.resolve("easy-postman-plugins/plugin-redis/src/main/resources/icons/redis.svg")));
        assertTrue(Files.isRegularFile(root.resolve("easy-postman-plugins/plugin-capture/src/main/resources/icons/capture.svg")));
        assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/resources/icons/kafka.svg")));
        assertFalse(Files.exists(root.resolve("easy-postman-app/src/main/resources/icons/decompile.svg")));
    }

    @Test
    public void pluginIconReferencesUsePluginOrSharedUiResources() throws IOException {
        Path root = repositoryRoot();
        Set<String> uiIcons = resourceFileNames(root.resolve("easy-postman-ui/src/main/resources/icons"));
        Set<String> pluginIcons = pluginIconFileNames(root.resolve("easy-postman-plugins"));
        Set<String> appIcons = resourceFileNames(root.resolve("easy-postman-app/src/main/resources/icons"));

        List<String> violations = javaSourceFiles(root.resolve("easy-postman-plugins")).stream()
                .flatMap(file -> iconReferences(file).stream()
                        .filter(icon -> !uiIcons.contains(icon) && !pluginIcons.contains(icon))
                        .map(icon -> file + " references app-only or missing icon " + icon
                                + (appIcons.contains(icon) ? " from easy-postman-app" : "")))
                .toList();

        assertTrue(violations.isEmpty(),
                "Official plugins must package plugin-specific icons or use shared icons from easy-postman-ui: "
                        + violations);
    }

    @Test
    public void resourcesDoNotContainMacMetadataFiles() throws IOException {
        Path root = repositoryRoot();
        List<Path> metadataFiles;
        try (Stream<Path> files = Files.walk(root)) {
            metadataFiles = files
                    .filter(path -> path.getFileName().toString().equals(".DS_Store"))
                    .filter(path -> !path.toString().contains("/.git/"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .toList();
        }

        assertTrue(metadataFiles.isEmpty(), "Resource tree must not contain macOS metadata files: " + metadataFiles);
    }

    private static List<Path> javaSourceFiles(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .toList();
        }
    }

    private static String fileNameWithoutExtension(Path file) {
        String fileName = file.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex < 0 ? fileName : fileName.substring(0, dotIndex);
    }

    private static List<String> sourceContainsViolations(Path file, List<String> forbiddenPatterns) {
        try {
            String source = Files.readString(file);
            return forbiddenPatterns.stream()
                    .filter(source::contains)
                    .map(pattern -> file + " contains " + pattern)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + file, e);
        }
    }

    private static List<String> sourcePackageViolations(Path sourceRoot, List<String> forbiddenPatterns) throws IOException {
        if (!Files.isDirectory(sourceRoot)) {
            return List.of();
        }
        return javaSourceFiles(sourceRoot).stream()
                .flatMap(file -> sourceContainsViolations(file, forbiddenPatterns).stream())
                .toList();
    }

    private static List<String> sourceRootsPackageViolations(List<Path> sourceRoots,
                                                             List<String> forbiddenPatterns) throws IOException {
        return sourceRoots.stream()
                .flatMap(sourceRoot -> {
                    try {
                        return sourcePackageViolations(sourceRoot, forbiddenPatterns).stream();
                    } catch (IOException e) {
                        throw new IllegalStateException("Failed to scan " + sourceRoot, e);
                    }
                })
                .toList();
    }

    private static List<String> sourceRootsLinePatternViolations(List<Path> sourceRoots,
                                                                 List<String> forbiddenRegexes) throws IOException {
        return sourceRoots.stream()
                .flatMap(sourceRoot -> {
                    try {
                        return javaSourceFiles(sourceRoot).stream()
                                .flatMap(file -> sourceLinePatternViolations(file, forbiddenRegexes).stream());
                    } catch (IOException e) {
                        throw new IllegalStateException("Failed to scan " + sourceRoot, e);
                    }
                })
                .toList();
    }

    private static List<String> sourceLinePatternViolations(Path file, List<String> forbiddenRegexes) {
        try {
            List<java.util.regex.Pattern> forbiddenPatterns = forbiddenRegexes.stream()
                    .map(java.util.regex.Pattern::compile)
                    .toList();
            return Files.readString(file).lines()
                    .map(String::trim)
                    .filter(line -> forbiddenPatterns.stream().anyMatch(pattern -> pattern.matcher(line).matches()))
                    .map(line -> file + " contains " + line)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + file, e);
        }
    }

    private static List<String> retiredPanelPerformanceHeadlessPackageNames() {
        String retiredPackagePrefix = "com.laker.postman.panel.performance.";
        return List.of("execution.", "runtime.", "model.", "plan.", "report.").stream()
                .map(retiredPackagePrefix::concat)
                .toList();
    }

    private static List<String> forbiddenSwingAndPanelImports() {
        return List.of(
                "import javax.swing.",
                "import java.awt.",
                "import com.formdev.",
                "import net.miginfocom.",
                "import com.laker.postman.panel.",
                "import com.laker.postman.common.component.",
                "import com.laker.postman.common.util.FontsUtil",
                "import com.laker.postman.common.util.IconUtil",
                "import com.laker.postman.common.component.notification.NotificationCenter",
                "import com.laker.postman.common.util.EditorThemeUtil",
                "import com.laker.postman.common.constants.ModernColors"
        );
    }

    private static List<String> forbiddenRequestCoreImports() {
        return List.of(
                "import javax.swing.",
                "import java.awt.",
                "import okhttp",
                "import org.fife",
                "import com.formdev.",
                "import net.miginfocom.",
                "import com.laker.postman.panel.",
                "import com.laker.postman.service.",
                "import com.laker.postman.common.component.",
                "import com.laker.postman.common.UiSingleton",
                "import com.laker.postman.plugin.runtime.",
                "import com.laker.postman.ioc.",
                "import com.laker.postman.platform.",
                "import com.laker.postman.util.I18nUtil",
                "import com.laker.postman.util.MessageKeys",
                "import com.laker.postman.http.runtime.model.PreparedRequest",
                "import com.laker.postman.http.runtime.model.HttpResponse",
                "import com.laker.postman.http.runtime.model.HttpEventInfo"
        );
    }

    private static List<String> forbiddenCollectionCoreImports() {
        return List.of(
                "import javax.swing.",
                "import java.awt.",
                "import okhttp",
                "import org.fife",
                "import com.formdev.",
                "import net.miginfocom.",
                "import com.laker.postman.panel.",
                "import com.laker.postman.service.",
                "import com.laker.postman.common.component.",
                "import com.laker.postman.common.UiSingleton",
                "import com.laker.postman.common.util.FontsUtil",
                "import com.laker.postman.common.util.IconUtil",
                "import com.laker.postman.common.component.notification.NotificationCenter",
                "import com.laker.postman.common.util.EditorThemeUtil",
                "import com.laker.postman.common.constants.ModernColors",
                "import com.laker.postman.plugin.runtime.",
                "import com.laker.postman.ioc.",
                "import com.laker.postman.platform.",
                "import com.laker.postman.http.runtime.model.PreparedRequest",
                "import com.laker.postman.http.runtime.model.HttpResponse",
                "import com.laker.postman.http.runtime.model.HttpEventInfo"
        );
    }

    private static List<String> uiImportViolations(Path file) {
        try {
            String source = Files.readString(file);
            return List.of("java.awt", "javax.swing", "com.formdev", "net.miginfocom", "org.fife").stream()
                    .filter(source::contains)
                    .map(pattern -> file + " contains " + pattern)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + file, e);
        }
    }

    private static List<String> uiMessageKeyViolations(Path file) {
        try {
            String source = Files.readString(file)
                    .replace("UiMessageKeys.", "")
                    .replace("CommonMessageKeys.", "");
            return List.of(
                            "import com.laker.postman.util.MessageKeys",
                            "MessageKeys.",
                            "I18nUtil.getMessage(MessageKeys"
                    ).stream()
                    .filter(source::contains)
                    .map(pattern -> file + " contains " + pattern)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + file, e);
        }
    }

    private static List<String> pluginAppMessageKeyViolations(Path file) {
        try {
            String source = Files.readString(file);
            return List.of(
                            "import com.laker.postman.util.MessageKeys",
                            "I18nUtil.getMessage(MessageKeys"
                    ).stream()
                    .filter(source::contains)
                    .map(pattern -> file + " contains " + pattern)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + file, e);
        }
    }

    private static List<String> resourceBundleOwnerViolations(Path file, String... forbiddenPrefixes) {
        try {
            return Files.readString(file).lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .filter(line -> Stream.of(forbiddenPrefixes).anyMatch(line::startsWith))
                    .map(line -> file + " contains " + line)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + file, e);
        }
    }

    private static Set<String> resourceFileNames(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return Set.of();
        }
        try (Stream<Path> files = Files.list(root)) {
            return files
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }

    private static Set<String> resourceKeys(Path file) {
        try {
            if (!Files.isRegularFile(file)) {
                return Set.of();
            }
            return Files.readString(file).lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .filter(line -> line.contains("="))
                    .map(line -> line.substring(0, line.indexOf('=')).trim())
                    .collect(Collectors.toCollection(TreeSet::new));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + file, e);
        }
    }

    private static Set<String> pluginIconFileNames(Path pluginsRoot) throws IOException {
        if (!Files.isDirectory(pluginsRoot)) {
            return Set.of();
        }
        try (Stream<Path> files = Files.walk(pluginsRoot)) {
            return files
                    .filter(path -> path.toString().contains("/src/main/resources/icons/"))
                    .filter(path -> path.getFileName().toString().endsWith(".svg"))
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }

    private static List<String> iconReferences(Path file) {
        try {
            String source = Files.readString(file);
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("icons/([A-Za-z0-9_.-]+\\.svg)")
                    .matcher(source);
            List<String> icons = new java.util.ArrayList<>();
            while (matcher.find()) {
                icons.add(matcher.group(1));
            }
            return icons;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + file, e);
        }
    }

    private static List<Path> pomFiles(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files
                    .filter(path -> path.getFileName().toString().equals("pom.xml"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .toList();
        }
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("easy-postman-app"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate repository root");
    }
}
