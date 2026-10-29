package com.laker.postman.performance.model;

import com.laker.postman.performance.core.model.PerformanceProtocol;


import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.script.model.TestResult;

import java.util.List;

public class ResultNodeInfo {

    /**
     * 接口名称
     */
    public final String name;

    /**
     * 错误信息
     */
    public final String errorMsg;

    /**
     * 请求
     */
    public final PreparedRequest req;

    /**
     * 响应
     */
    public final HttpResponse resp;

    /**
     * 断言结果
     */
    public final List<TestResult> testResults;

    public final PerformanceProtocol protocol;

    /**
     * 耗时（毫秒）——在构造时就算好
     */
    public final int costMs;

    /**
     * HTTP响应状态码
     */
    public final int responseCode;

    /**
     * 执行层面是否明确失败（前置脚本崩溃 / HTTP 请求异常 / 后置脚本崩溃）。
     * 与断言失败区分：断言失败由 testResults 体现，执行失败由此字段体现。
     */
    public final boolean executionFailed;
    public final boolean cancelled;

    public ResultNodeInfo(
            String name,
            String errorMsg,
            PreparedRequest req,
            HttpResponse resp,
            List<TestResult> testResults,
            boolean executionFailed) {
        this(name, errorMsg, req, resp, testResults, executionFailed, PerformanceProtocol.HTTP);
    }

    public ResultNodeInfo(
            String name,
            String errorMsg,
            PreparedRequest req,
            HttpResponse resp,
            List<TestResult> testResults,
            boolean executionFailed,
            PerformanceProtocol protocol) {
        this(name, errorMsg, req, resp, testResults, executionFailed, false, protocol);
    }

    public ResultNodeInfo(
            String name,
            String errorMsg,
            PreparedRequest req,
            HttpResponse resp,
            List<TestResult> testResults,
            boolean executionFailed,
            boolean cancelled,
            PerformanceProtocol protocol) {
        this.name = name;
        this.errorMsg = errorMsg;
        this.req = req;
        this.resp = resp;
        this.testResults = testResults;
        this.executionFailed = executionFailed;
        this.cancelled = cancelled;
        this.protocol = protocol == null ? PerformanceProtocol.HTTP : protocol;

        // ✅ 统一在这里算 ms（不依赖 costNs）
        this.costMs = resp != null ? (int) resp.costMs : 0;

        // 提取响应码
        this.responseCode = resp != null ? resp.code : 0;
    }

    /**
     * 是否有断言失败
     */
    public boolean hasAssertionFailed() {
        if (testResults == null) return false;
        for (TestResult r : testResults) {
            if (!r.passed) return true;
        }
        return false;
    }

    /**
     * 统一的成功/失败判断，三处（趋势图、报表、结果表格）共用：
     * 1. 执行层面明确失败（前置脚本崩溃、HTTP异常、后置脚本崩溃）→ false
     * 2. 有断言结果 → 以断言为准（断言全通过才算成功）
     * 3. 无断言 → 以 HTTP 状态码为准（2xx/3xx 为成功）
     * 4. 无响应 → false
     */
    public boolean isActuallySuccessful() {
        return isActuallySuccessful(executionFailed, resp, testResults);
    }

    public static boolean isActuallySuccessful(boolean executionFailed,
                                               HttpResponse resp,
                                               List<TestResult> testResults) {
        // 1. 执行层面明确失败，直接返回 false
        if (executionFailed) {
            return false;
        }

        // 2. 如果有断言结果，以断言为准
        if (testResults != null && !testResults.isEmpty()) {
            for (TestResult result : testResults) {
                if (!result.passed) {
                    return false;
                }
            }
            return true;
        }

        // 3. 如果没有断言，以 HTTP 状态码为准
        if (resp != null && resp.code > 0) {
            return resp.code == 101 || (resp.code >= 200 && resp.code < 400);
        }

        // 4. 兜底：没有响应则返回 false
        return false;
    }
}
