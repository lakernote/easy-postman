package com.laker.postman.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.laker.postman.model.Environment;
import com.laker.postman.util.SystemUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 环境变量管理服务，负责环境变量的持久化、加载和处理
 */
@Slf4j
public class EnvironmentService {
    private static final Map<String, Environment> environments = Collections.synchronizedMap(new LinkedHashMap<>());
    /**
     * -- GETTER --
     * 获取当前激活的环境
     */
    @Getter
    private static Environment activeEnvironment = null;
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{(.+?)}}");

    static {
        loadEnvironments();
    }

    /**
     * 加载所有环境变量
     */
    public static void loadEnvironments() {
        File file = new File(SystemUtil.ENV_PATH);
        if (!file.exists()) {
            log.info("环境变量文件不存在，将创建默认环境");
            createDefaultEnvironments();
            return;
        }

        try {
            environments.clear();
            JSONArray array = JSONUtil.readJSONArray(file, StandardCharsets.UTF_8);
            for (Object obj : array) {
                JSONObject envJson = (JSONObject) obj;
                Environment env = new Environment();
                env.setId(envJson.getStr("id"));
                env.setName(envJson.getStr("name"));
                env.setActive(envJson.getBool("active", false));

                JSONObject varsJson = envJson.getJSONObject("variables");
                if (varsJson != null) {
                    for (Map.Entry<String, Object> entry : varsJson.entrySet()) {
                        env.addVariable(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
                    }
                }

                environments.put(env.getId(), env);
                if (env.isActive()) {
                    activeEnvironment = env;
                }
            }

            if (activeEnvironment == null && !environments.isEmpty()) {
                // 如果没有激活的环境，激活第一个
                Environment firstEnv = environments.values().iterator().next();
                firstEnv.setActive(true);
                activeEnvironment = firstEnv;
                saveEnvironments();
            }
        } catch (Exception e) {
            log.error("加载环境变量失败", e);
            createDefaultEnvironments();
        }
    }

    /**
     * 创建默认环境
     */
    private static void createDefaultEnvironments() {
        environments.clear();

        // 创建开发环境
        Environment devEnv = new Environment("开发环境");
        devEnv.setId("dev-" + System.currentTimeMillis());
        devEnv.setActive(true);
        devEnv.addVariable("baseUrl", "https://so.gitee.com");
        devEnv.addVariable("apiKey", "dev-api-key-123");

        // 创建测试环境
        Environment testEnv = new Environment("测试环境");
        testEnv.setId("test-" + System.currentTimeMillis());
        testEnv.addVariable("baseUrl", "https://so.csdn.net/so/search");
        testEnv.addVariable("apiKey", "test-api-key-456");

        environments.put(devEnv.getId(), devEnv);
        environments.put(testEnv.getId(), testEnv);
        activeEnvironment = devEnv;

        saveEnvironments();
    }

    /**
     * 保存所有环境变量
     */
    public static void saveEnvironments() {
        try {
            File file = new File(SystemUtil.ENV_PATH);
            File parentDir = file.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }

            JSONArray array = new JSONArray();
            for (Environment env : environments.values()) {
                JSONObject envJson = new JSONObject();
                envJson.set("id", env.getId());
                envJson.set("name", env.getName());
                envJson.set("active", env.isActive());

                JSONObject varsJson = new JSONObject();
                for (Map.Entry<String, String> entry : env.getVariables().entrySet()) {
                    varsJson.set(entry.getKey(), entry.getValue());
                }
                envJson.set("variables", varsJson);

                array.add(envJson);
            }

            FileUtil.writeString(array.toStringPretty(), file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("保存环境变量失败", e);
        }
    }

    /**
     * 添加或更新环境
     */
    public static void saveEnvironment(Environment environment) {
        environments.put(environment.getId(), environment);
        saveEnvironments();
    }

    /**
     * 删除环境
     */
    public static void deleteEnvironment(String id) {
        Environment env = environments.remove(id);
        if (env != null && env.isActive() && !environments.isEmpty()) {
            // 如果删除的是当前激活的环境，激活第一个环境
            Environment firstEnv = environments.values().iterator().next();
            firstEnv.setActive(true);
            activeEnvironment = firstEnv;
        }
        saveEnvironments();
    }

    /**
     * 获取所有环境
     */
    public static List<Environment> getAllEnvironments() {
        return new ArrayList<>(environments.values());
    }

    /**
     * 设置激活的环境
     */
    public static void setActiveEnvironment(String id) {
        if (activeEnvironment != null) {
            activeEnvironment.setActive(false);
        }

        Environment env = environments.get(id);
        if (env != null) {
            env.setActive(true);
            activeEnvironment = env;
            saveEnvironments();
        }
    }

    /**
     * 替换文本中的环境变量占位符
     * 例如: {{baseUrl}}/api/users -> https://api.example.com/api/users
     */
    public static String replaceVariables(String text) {
        if (text == null || text.isEmpty() || activeEnvironment == null) {
            return text;
        }

        Matcher matcher = VAR_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String value = activeEnvironment.getVariable(varName);

            // 如果变量不存在，保留原样
            if (value == null) {
                matcher.appendReplacement(result, matcher.group(0));
            } else {
                // 替换变量为对应的值
                matcher.appendReplacement(result, value.replace("$", "\\$"));
            }
        }

        matcher.appendTail(result);
        return result.toString();
    }
}