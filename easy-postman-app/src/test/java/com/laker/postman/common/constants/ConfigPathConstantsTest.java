package com.laker.postman.common.constants;

import com.laker.postman.model.Workspace;
import org.testng.annotations.Test;

import java.io.File;

import static org.testng.Assert.assertEquals;

public class ConfigPathConstantsTest {

    @Test(description = "性能测试配置路径应位于指定工作区目录内")
    public void shouldResolvePerformanceConfigPathForWorkspace() {
        Workspace workspace = new Workspace();
        workspace.setPath("/tmp/easy-postman-workspace" + File.separator);

        assertEquals(
                ConfigPathConstants.getPerformanceConfigPath(workspace),
                "/tmp/easy-postman-workspace" + File.separator + "performance_config.json"
        );
    }

    @Test(description = "工作区为空时性能测试配置路径应回退到旧应用级路径")
    public void shouldFallbackToDefaultWorkspacePerformanceConfigPathWhenWorkspaceMissing() {
        assertEquals(
                ConfigPathConstants.getPerformanceConfigPath(null),
                ConfigPathConstants.DEFAULT_WORKSPACE_DIR + "performance_config.json"
        );

        Workspace workspace = new Workspace();
        workspace.setPath(" ");

        assertEquals(
                ConfigPathConstants.getPerformanceConfigPath(workspace),
                ConfigPathConstants.DEFAULT_WORKSPACE_DIR + "performance_config.json"
        );
    }

    @Test(description = "功能测试配置路径应位于指定工作区目录内")
    public void shouldResolveFunctionalConfigPathForWorkspace() {
        Workspace workspace = new Workspace();
        workspace.setPath("/tmp/easy-postman-workspace" + File.separator);

        assertEquals(
                ConfigPathConstants.getFunctionalConfigPath(workspace),
                "/tmp/easy-postman-workspace" + File.separator + "functional_config.json"
        );
    }

    @Test(description = "工作区为空时功能测试配置路径应回退到旧应用级路径")
    public void shouldFallbackToDefaultWorkspaceFunctionalConfigPathWhenWorkspaceMissing() {
        assertEquals(
                ConfigPathConstants.getFunctionalConfigPath(null),
                ConfigPathConstants.DEFAULT_WORKSPACE_DIR + "functional_config.json"
        );

        Workspace workspace = new Workspace();
        workspace.setPath(" ");

        assertEquals(
                ConfigPathConstants.getFunctionalConfigPath(workspace),
                ConfigPathConstants.DEFAULT_WORKSPACE_DIR + "functional_config.json"
        );
    }

    @Test(description = "Mock Server 配置路径应位于指定工作区目录内")
    public void shouldResolveMockServersPathForWorkspace() {
        Workspace workspace = new Workspace();
        workspace.setPath("/tmp/easy-postman-workspace" + File.separator);

        assertEquals(
                ConfigPathConstants.getMockServersPath(workspace),
                "/tmp/easy-postman-workspace" + File.separator + "mock_servers.json"
        );
        assertEquals(ConfigPathConstants.getMockServersPath(null), ConfigPathConstants.MOCK_SERVERS);
    }
}
