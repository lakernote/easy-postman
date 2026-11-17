package com.laker.postman.service.env;

import cn.hutool.json.JSONUtil;
import com.laker.postman.model.Environment;
import lombok.experimental.UtilityClass;

import java.util.List;

/**
 * 新工作区的示例环境。独立于 EnvironmentService，避免生成示例数据时触发其静态加载（会在文件写入前读取到空数据）。
 */
@UtilityClass
public class DefaultEnvironments {

    public static String json() {
        long now = System.currentTimeMillis();
        Environment devEnv = new Environment("Dev Env");
        devEnv.setId("dev-" + now);
        devEnv.setActive(true);
        devEnv.addVariable("baseUrl", "https://so.gitee.com");
        devEnv.addVariable("apiKey", "dev-api-key-123");

        Environment testEnv = new Environment("Test Env");
        testEnv.setId("test-" + now);
        testEnv.addVariable("baseUrl", "https://so.csdn.net/so/search");
        testEnv.addVariable("apiKey", "test-api-key-456");
        return JSONUtil.toJsonPrettyStr(List.of(devEnv, testEnv));
    }
}
