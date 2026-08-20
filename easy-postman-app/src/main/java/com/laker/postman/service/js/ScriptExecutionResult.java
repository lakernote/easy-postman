package com.laker.postman.service.js;

import com.laker.postman.script.model.TestResult;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 脚本执行结果
 * 封装脚本执行的结果信息，包括成功状态、测试结果、错误信息等
 */
@Getter
@Builder
public class ScriptExecutionResult {
    /**
     * 是否执行成功
     */
    private final boolean success;

    /**
     * 测试结果列表（仅后置脚本）
     */
    @Builder.Default
    private final List<TestResult> testResults = new ArrayList<>();

    /**
     * 错误信息（如果执行失败）
     */
    private final String errorMessage;

    /**
     * 异常对象（如果执行失败）
     */
    private final Exception exception;

    /**
     * 前置类脚本是否显式修改了请求体。
     */
    private final boolean requestBodyMutated;

    /**
     * 创建成功的结果
     */
    public static ScriptExecutionResult success() {
        return ScriptExecutionResult.builder()
                .success(true)
                .build();
    }

    /**
     * 创建成功的结果（带测试结果）
     */
    public static ScriptExecutionResult success(List<TestResult> testResults) {
        return ScriptExecutionResult.builder()
                .success(true)
                .testResults(testResults != null ? new ArrayList<>(testResults) : new ArrayList<>())
                .build();
    }

    /**
     * 创建失败的结果
     */
    public static ScriptExecutionResult failure(String errorMessage, Exception exception) {
        return ScriptExecutionResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .exception(exception)
                .build();
    }

    /**
     * 创建失败的结果（带测试结果）
     */
    public static ScriptExecutionResult failure(String errorMessage, Exception exception, List<TestResult> testResults) {
        return ScriptExecutionResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .exception(exception)
                .testResults(testResults != null ? new ArrayList<>(testResults) : new ArrayList<>())
                .build();
    }

    /**
     * 是否有测试结果
     */
    public boolean hasTestResults() {
        return !testResults.isEmpty();
    }

    /**
     * 所有测试是否通过
     * 注意：如果没有测试，返回true（没有失败的测试）
     */
    public boolean allTestsPassed() {
        // 如果没有测试，视为通过
        if (!hasTestResults()) {
            return true;
        }
        // 如果有测试，检查是否全部通过
        return testResults.stream().allMatch(test -> test.passed);
    }
}
