package com.laker.postman.service.js.api;

import com.laker.postman.model.Environment;
import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.script.model.TestResult;
import com.laker.postman.plugin.host.PluginAccess;
import com.laker.postman.service.EnvironmentService;
import com.laker.postman.service.GlobalVariablesService;
import com.laker.postman.service.js.ScriptSendRequestExecutor;
import com.laker.postman.service.variable.IterationInfoService;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Value;

import java.util.*;

/**
 * Postman 脚本 API 上下文 (pm 对象)
 * <p>
 * 该类模拟 Postman 中的 <code>pm</code> 对象，为 PreRequest/PostRequest 脚本提供完整的 API 支持。
 * 在脚本执行时，该对象会被注入到 JavaScript 引擎中，供脚本代码访问。
 * </p>
 *
 * <h3>主要功能模块：</h3>
 * <ul>
 *   <li><b>环境变量管理</b>: pm.environment.set/get - 操作当前激活的环境变量</li>
 *   <li><b>全局变量管理</b>: pm.globals.set/get - 操作应用级全局变量</li>
 *   <li><b>执行变量管理</b>: pm.variables.set/get - 操作当前运行上下文内的变量</li>
 *   <li><b>迭代数据管理</b>: pm.iterationData.get - 访问当前数据驱动行</li>
 *   <li><b>Cookie 操作</b>: pm.cookies.get/set/delete - 管理请求的 Cookie</li>
 *   <li><b>测试断言</b>: pm.test() - 执行测试断言并记录结果</li>
 *   <li><b>请求访问</b>: pm.request - 访问和修改当前请求信息</li>
 *   <li><b>响应访问</b>: pm.response - 访问响应数据（仅后置脚本可用）</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // PreRequest Script
 * pm.environment.set("timestamp", Date.now());
 * pm.variables.set("requestId", pm.uuid());
 * pm.request.headers.add({key: "X-Request-ID", value: pm.variables.get("requestId")});
 *
 * // PostRequest Script
 * pm.test("Status code is 200", function() {
 *     pm.expect(pm.response.code).to.equal(200);
 * });
 * pm.environment.set("token", pm.response.json().access_token);
 * }</pre>
 *
 * @author laker
 * @see <a href="https://learning.postman.com/docs/writing-scripts/script-references/postman-sandbox-api-reference/">Postman Scripting API Reference</a>
 */
@Slf4j
public class PostmanApiContext {

    /**
     * 测试结果列表 - 存储所有 pm.test() 调用的结果
     */
    public List<TestResult> testResults = new ArrayList<>();

    /**
     * 环境变量对象 - 对应 pm.environment
     */
    public ScriptScopedVariablesApi environment;

    /**
     * 历史兼容别名 - 对应 pm.env。
     * Postman 官方 API 使用 pm.environment，新脚本和内置片段仍应优先使用 pm.environment。
     */
    public ScriptScopedVariablesApi env;

    /**
     * 全局变量对象 - 对应 pm.globals
     */
    public ScriptScopedVariablesApi globals;

    /**
     * 响应对象 - 对应 pm.response，仅在后置脚本中可用
     */
    public ResponseAssertion response;

    /**
     * 执行变量管理器 - 对应 pm.variables，用于存储当前运行上下文内的变量
     */
    public ScriptVariablesApi variables;

    /**
     * 迭代数据管理器 - 对应 pm.iterationData，用于存储当前 CSV/JSON 行数据
     */
    public IterationDataApi iterationData = new IterationDataApi();

    /**
     * 运行元信息 - 对应 Postman 的 pm.info。
     */
    public PostmanInfoApi info;

    /**
     * 请求对象包装器 - 对应 pm.request，提供对请求的 JavaScript 访问接口
     */
    public ScriptRequestAccessor request;

    /**
     * Cookie 管理器 - 对应 pm.cookies，提供 Cookie 操作接口
     */
    public CookieApi cookies;

    /**
     * 测试 API - 对应 pm.test。
     */
    public TestApi test;

    private final Map<String, Object> pluginApis = new LinkedHashMap<>();
    private final ScriptSendRequestExecutor sendRequestExecutor;

    /**
     * Elasticsearch API - 对应 pm.elasticsearch
     */
    public ScriptElasticsearchApi elasticsearch;

    /**
     * InfluxDB API - 对应 pm.influxdb
     */
    public ScriptInfluxDbApi influxdb;

    /**
     * 构造 Postman API 上下文
     *
     * @param environment 当前激活的环境对象
     */
    public PostmanApiContext(Environment environment) {
        this(
                environment,
                GlobalVariablesService.getInstance().getGlobalVariables(),
                createEnvironmentPersistAction(environment)
        );
    }

    /**
     * 构造 headless / run-scoped 脚本上下文。
     * 这里直接使用 plan 内携带的环境和全局变量，避免 worker 执行时回落到 GUI 工作区服务。
     */
    public static PostmanApiContext scoped(Environment environment, Environment globals) {
        return new PostmanApiContext(environment, globals, null, false);
    }

    /**
     * Creates a run-scoped context whose pm.variables lookup follows Postman runner
     * precedence and therefore includes the current iteration-data row.
     */
    public static PostmanApiContext scopedWithIterationData(Environment environment, Environment globals) {
        return new PostmanApiContext(environment, globals, null, true);
    }

    private PostmanApiContext(Environment environment,
                              Environment globals,
                              Runnable environmentPersistAction) {
        this(environment, globals, environmentPersistAction, false);
    }

    private PostmanApiContext(Environment environment,
                              Environment globals,
                              Runnable environmentPersistAction,
                              boolean includeIterationDataInVariables) {
        this(
                environment,
                globals,
                environmentPersistAction,
                new ScriptSendRequestExecutor(),
                includeIterationDataInVariables
        );
    }

    private PostmanApiContext(Environment environment,
                              Environment globals,
                              Runnable environmentPersistAction,
                              ScriptSendRequestExecutor sendRequestExecutor,
                              boolean includeIterationDataInVariables) {
        this.sendRequestExecutor = sendRequestExecutor == null
                ? new ScriptSendRequestExecutor()
                : sendRequestExecutor;
        this.variables = includeIterationDataInVariables
                ? ScriptVariablesApi.withIterationData()
                : new ScriptVariablesApi();
        this.environment = new ScriptScopedVariablesApi(
                environment,
                environmentPersistAction
        );
        this.env = this.environment;
        this.globals = new ScriptScopedVariablesApi(
                globals,
                null
        );
        this.cookies = new CookieApi(); // 初始化 cookies
        this.test = new TestApi(this); // 初始化 test API
        this.info = new PostmanInfoApi(IterationInfoService.getInstance().getCurrentInfo());
        this.elasticsearch = new ScriptElasticsearchApi();
        this.influxdb = new ScriptInfluxDbApi();
        // 核心 pm 能力先由宿主内建，再把插件注册表里的扩展 API 动态挂进来。
        // 这样脚本层看到的是一个统一的 pm 对象，而不是“宿主 API + 插件 API”两套入口。
        PluginAccess.createScriptApis().forEach(this::registerPluginApi);
    }

    private static Runnable createEnvironmentPersistAction(Environment environment) {
        if (!EnvironmentService.isManagedEnvironment(environment)) {
            return null;
        }
        return () -> EnvironmentService.saveEnvironment(environment);
    }

    private void registerPluginApi(String alias, Object api) {
        if (alias == null || api == null) {
            return;
        }
        pluginApis.put(alias, api);
    }

    public Object plugin(String alias) {
        // 统一的插件 API 访问入口，脚本里可以通过 pm.plugin("kafka") 这种方式按需取能力。
        return pluginApis.get(alias);
    }

    public boolean hasPlugin(String alias) {
        return alias != null && pluginApis.containsKey(alias);
    }

    /**
     * 设置请求对象
     * 在脚本执行前由框架调用，注入当前请求信息
     *
     * @param preparedRequest 准备好的请求对象
     */
    public void setRequest(PreparedRequest preparedRequest) {
        this.request = new ScriptRequestAccessor(preparedRequest);
        if (this.info != null) {
            this.info.setRequest(preparedRequest);
        }
    }

    /**
     * 设置响应对象
     * 在后置脚本执行前由框架调用，注入响应信息
     *
     * @param httpResponse HTTP 响应对象
     */
    public void setResponse(HttpResponse httpResponse) {
        this.response = new ResponseAssertion(httpResponse);

        // 自动将响应中的 Cookie 填充到 pm.cookies，使得 pm.cookies.get() 能够工作
        populateResponseCookies(httpResponse);
    }

    /**
     * 从响应头中提取所有 Cookie 并填充到 pm.cookies
     *
     * @param httpResponse HTTP 响应对象
     */
    private void populateResponseCookies(HttpResponse httpResponse) {
        if (cookies == null) {
            return;
        }
        for (Cookie cookie : PostmanResponseCookieSupport.extract(httpResponse)) {
            cookies.set(cookie);
            log.debug("Populated response cookie to pm.cookies: {} = {}", cookie.name, cookie.value);
        }
    }

    /**
     * 生成 UUID
     * 对应脚本中的: pm.uuid()
     *
     * @return UUID 字符串
     */
    public String uuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * 获取当前时间戳（毫秒）
     * 对应脚本中的: pm.getTimestamp()
     *
     * @return 当前时间戳（毫秒）
     */
    public long getTimestamp() {
        return System.currentTimeMillis();
    }

    /**
     * 创建断言期望对象
     * 对应脚本中的: pm.expect(actual)
     *
     * <p>示例：
     * <pre>{@code
     * pm.expect(pm.response.code).to.equal(200);
     * pm.expect(pm.response.json().name).to.eql("John");
     * pm.expect(pm.response.text()).to.include("success");
     * pm.expect(pm.response.responseTime).to.be.below(1000);
     * }</pre>
     *
     * @param actual 要断言的实际值
     * @return Expectation 对象，支持链式断言
     */
    public Expectation expect(Object actual) {
        return new Expectation(actual);
    }

    public void setEnvironmentVariable(String key, Object value) {
        environment.set(key, value);
    }

    public String getEnvironmentVariable(String key) {
        return environment.get(key);
    }

    public void clearEnvironmentVariable(String key) {
        environment.unset(key);
    }

    public void clearEnvironmentVariables() {
        environment.clear();
    }

    public void setGlobalVariable(String key, Object value) {
        globals.set(key, value);
    }

    public String getGlobalVariable(String key) {
        return globals.get(key);
    }

    public void clearGlobalVariable(String key) {
        globals.unset(key);
    }

    public void clearGlobalVariables() {
        globals.clear();
    }

    /**
     * 从响应头中获取指定名称的 Cookie
     * 对应脚本中的: pm.getResponseCookie(name)
     *
     * <p>该方法从 HTTP 响应的 Set-Cookie 头中解析并返回指定名称的 Cookie。
     * 常用于在后置脚本中提取服务器设置的 Cookie 值，例如 session ID、token 等。</p>
     *
     * <p>示例：
     * <pre>{@code
     * // 获取响应中的 JSESSIONID cookie
     * var jsessionId = pm.getResponseCookie('JSESSIONID');
     * if (jsessionId) {
     *     pm.environment.set('session_id', jsessionId.value);
     *     console.log('JSESSIONID:', jsessionId.value);
     * }
     * }</pre>
     *
     * @param name Cookie 名称
     * @return Cookie 对象，包含 name、value、domain 等属性；如果不存在则返回 null
     */
    public Cookie getResponseCookie(String name) {
        if (name == null || response == null) {
            return null;
        }
        return PostmanResponseCookieSupport.find(response.getHttpResponse(), name);
    }

    /**
     * 发送 HTTP 请求
     * 对应脚本中的: pm.sendRequest(requestOptions, callback)
     *
     * @param requestOptions 请求配置，可以是 URL 字符串或包含请求详情的对象
     * @param callback       回调函数，接收 (error, response) 两个参数
     */
    public void sendRequest(Object requestOptions, Value callback) {
        if (requestOptions == null) {
            log.warn("pm.sendRequest: requestOptions is null");
            return;
        }

        try {
            sendRequestExecutor.sendRequest(requestOptions, callback);
        } catch (Exception e) {
            log.error("pm.sendRequest failed: {}", e.getMessage(), e);

            if (callback != null && callback.canExecute()) {
                Map<String, Object> error = new HashMap<>();
                error.put("message", e.getMessage());
                error.put("name", e.getClass().getSimpleName());

                try {
                    callback.execute(Value.asValue(error), Value.asValue(null));
                } catch (Exception callbackEx) {
                    log.error("Error executing callback: {}", callbackEx.getMessage());
                }
            }
        }
    }

}
