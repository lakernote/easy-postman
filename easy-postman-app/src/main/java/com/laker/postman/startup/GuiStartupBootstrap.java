package com.laker.postman.startup;

import com.laker.postman.ioc.BeanFactory;
import com.laker.postman.panel.collections.tree.StartupCollectionPrefetch;
import com.laker.postman.util.SystemUtil;
import com.laker.postman.plugin.api.service.RequestCollectionImportService;
import com.laker.postman.plugin.host.AppSwingRequestCollectionImportService;
import com.laker.postman.plugin.runtime.PluginRuntime;
import com.laker.postman.service.EnvironmentService;
import com.laker.postman.service.WorkspaceService;
import lombok.experimental.UtilityClass;

/**
 * GUI 启动分支的宿主运行时初始化。
 */
@UtilityClass
class GuiStartupBootstrap {

    void initBeanFactory() {
        BeanFactory.register(HostComponents.classes());
    }

    void initWorkspaceState() {
        WorkspaceService workspaceService = WorkspaceService.getInstance();
        // 与插件加载、主窗口创建并行预读，主内容挂载时集合树通常可直接显示
        StartupCollectionPrefetch.start(SystemUtil.getCollectionPathForWorkspace(workspaceService.getCurrentWorkspace()));
        EnvironmentService.getAllEnvironments();
    }

    void initPluginRuntime() {
        // 插件加载期间可能查询宿主桥接服务，因此必须先注册宿主能力再初始化插件运行时。
        PluginRuntime.getRegistry().registerService(
                RequestCollectionImportService.class,
                new AppSwingRequestCollectionImportService()
        );
        PluginRuntime.initialize();
    }
}
