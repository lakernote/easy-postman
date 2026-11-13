package com.laker.postman.performance.plan;

import com.laker.postman.model.Environment;
import com.laker.postman.model.Variable;
import com.laker.postman.performance.core.plan.PerformanceCorePlanDocument;
import com.laker.postman.performance.core.run.PerformanceRunEnvironment;
import com.laker.postman.performance.core.run.PerformanceRunPlan;
import com.laker.postman.performance.core.run.PerformanceRunPlanAssetScanner;
import com.laker.postman.performance.core.run.PerformanceRunSettings;
import com.laker.postman.performance.core.run.PerformanceRunVariable;
import com.laker.postman.performance.core.run.PerformanceRunVariableSet;
import com.laker.postman.service.setting.SettingManager;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class PerformanceRunPlanFactory {

    public PerformanceRunPlan create(PerformancePlanConfiguration configuration,
                                     Environment environment,
                                     Environment globals,
                                     String generatedBy) {
        PerformancePlanConfiguration safeConfiguration = configuration == null
                ? PerformancePlanConfiguration.builder().build()
                : configuration;
        PerformanceCorePlanDocument coreDocument = PerformanceCorePlanAdapter.toCoreDocument(
                safeConfiguration.getPlanDocument()
        );
        return PerformanceRunPlan.builder()
                .generatedBy(generatedBy)
                .environment(toRunEnvironment(environment))
                .globals(new PerformanceRunVariableSet(toRunVariables(globals == null ? null : globals.getVariableList())))
                .settings(PerformanceRunSettings.builder()
                        .efficientMode(safeConfiguration.isEfficientMode())
                        .httpMaxIdleConnections(SettingManager.getPerformanceMaxIdleConnections())
                        .httpKeepAliveSeconds(SettingManager.getPerformanceKeepAliveSeconds())
                        .httpMaxRequests(SettingManager.getPerformanceMaxRequests())
                        .httpMaxRequestsPerHost(SettingManager.getPerformanceMaxRequestsPerHost())
                        .responseBodyPreviewLimitKb(SettingManager.getPerformanceResponseBodyPreviewLimitKb())
                        .build())
                .testPlan(coreDocument)
                .assets(PerformanceRunPlanAssetScanner.scan(coreDocument))
                .build();
    }

    private PerformanceRunEnvironment toRunEnvironment(Environment environment) {
        if (environment == null) {
            return PerformanceRunEnvironment.empty();
        }
        return new PerformanceRunEnvironment(
                environment.getId(),
                environment.getName(),
                toRunVariables(environment.getVariableList())
        );
    }

    private List<PerformanceRunVariable> toRunVariables(List<Variable> variables) {
        List<PerformanceRunVariable> result = new ArrayList<>();
        if (variables == null) {
            return result;
        }
        for (Variable variable : variables) {
            if (variable == null) {
                continue;
            }
            result.add(new PerformanceRunVariable(
                    variable.isEnabled(),
                    variable.getKey(),
                    variable.getValue()
            ));
        }
        return result;
    }
}
