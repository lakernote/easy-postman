package com.laker.postman.performance.core.worker;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PerformanceWorkerProtocol {
    // 远程 worker 控制协议版本；master 提交前用它拦截旧 worker，避免新旧 JVM 混跑产生错误报表。
    public static final String CURRENT_VERSION = "2026.06.02";
}
