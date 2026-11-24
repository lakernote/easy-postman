package com.laker.postman.startup;

import com.laker.postman.mock.app.MockCollectionRouteProvider;
import com.laker.postman.mock.app.MockScriptExecutorAdapter;
import com.laker.postman.mock.app.MockServerManager;
import com.laker.postman.mock.app.MockServerPersistenceService;
import com.laker.postman.panel.lifecycle.AppExitCoordinator;
import com.laker.postman.service.FunctionalPersistenceService;
import com.laker.postman.service.HistoryPersistenceService;
import com.laker.postman.service.PerformancePersistenceService;
import com.laker.postman.service.UpdateService;
import com.laker.postman.service.sync.WebDavSyncScheduler;
import com.laker.postman.service.update.AppUpdateCheckCoordinator;
import com.laker.postman.service.update.plugin.PluginUpdateCheckCoordinator;
import com.laker.postman.service.update.plugin.PluginUpdateChecker;
import lombok.experimental.UtilityClass;

/**
 * 宿主 IOC 组件清单。启动时直接注册，避免扫描整个类路径。
 * 新增 {@code @Component} 类必须登记到这里，HostComponentsTest 会校验清单与实际注解一致。
 */
@UtilityClass
class HostComponents {

    Class<?>[] classes() {
        return new Class<?>[]{
                AppExitCoordinator.class,
                AppUpdateCheckCoordinator.class,
                FunctionalPersistenceService.class,
                HistoryPersistenceService.class,
                MockCollectionRouteProvider.class,
                MockScriptExecutorAdapter.class,
                MockServerManager.class,
                MockServerPersistenceService.class,
                PerformancePersistenceService.class,
                PluginUpdateCheckCoordinator.class,
                PluginUpdateChecker.class,
                UpdateService.class,
                WebDavSyncScheduler.class
        };
    }
}
