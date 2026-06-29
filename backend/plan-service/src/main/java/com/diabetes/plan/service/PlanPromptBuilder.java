package com.diabetes.plan.service;

import com.diabetes.plan.dify.DifyPlanWorkflowContract;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 组装传给 Dify 方案生成工作流的 JSON Object。
 *
 * @see DifyPlanWorkflowContract 完整入参/出参契约与示例
 */
@Component
public class PlanPromptBuilder {

    public Map<String, Object> buildDifyPayload(String userId, Map<String, Object> profile, int dailyCalories) {
        return DifyPlanWorkflowContract.buildInputObject(userId, profile, dailyCalories);
    }
}
