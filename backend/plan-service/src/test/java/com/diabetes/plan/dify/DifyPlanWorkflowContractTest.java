package com.diabetes.plan.dify;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DifyPlanWorkflowContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildInputObject_withRiskData() {
        Map<String, Object> profile = Map.of(
                "user_profile", Map.of("age", 35),
                "health_profile", Map.of("height", 170),
                "checkin_data", Map.of("recent_days", 14),
                "risk_data", Map.of("riskLevel", "low"));
        Map<String, Object> input = DifyPlanWorkflowContract.buildInputObject("u_1", profile, 1800);

        assertEquals("u_1", input.get("user_id"));
        assertEquals(1800, input.get("daily_calories"));
        assertTrue(input.containsKey("risk_data"));
    }

    @Test
    void buildInputObject_withoutRiskData() {
        Map<String, Object> profile = Map.of(
                "user_profile", Map.of("age", 35),
                "health_profile", Map.of("height", 170),
                "checkin_data", Map.of("recent_days", 14),
                "risk_data", Map.of());
        Map<String, Object> input = DifyPlanWorkflowContract.buildInputObject("u_1", profile, 1800);

        assertFalse(input.containsKey("risk_data"));
    }

    @Test
    void buildInputObject_nullProfile() {
        Map<String, Object> input = DifyPlanWorkflowContract.buildInputObject("u_1", null, 1500);
        assertEquals("u_1", input.get("user_id"));
        assertNotNull(input.get("user_profile"));
    }

    @Test
    void jsonSchemasAndWorkflowSpec() {
        assertNotNull(DifyPlanWorkflowContract.inputJsonSchema());
        assertNotNull(DifyPlanWorkflowContract.llmOutputJsonSchema());
        assertNotNull(DifyPlanWorkflowContract.outputJsonSchema());

        Map<String, Object> spec = DifyPlanWorkflowContract.workflowSpec("http://dify/", "key-1", "blocking");
        assertEquals("http://dify/v1/workflows/run", spec.get("workflowUrl"));
        assertEquals("key-1", spec.get("apiKey"));
        assertEquals("flat", spec.get("inputLayout"));
        assertNotNull(spec.get("inputExample"));
        assertNotNull(spec.get("outputExample"));
    }

    @Test
    void workflowSpec_nullBaseUrl() {
        Map<String, Object> spec = DifyPlanWorkflowContract.workflowSpec(null, "", "streaming");
        assertEquals("/v1/workflows/run", spec.get("workflowUrl"));
    }

    @Test
    void constants() {
        assertEquals("plan_llm_output", DifyPlanWorkflowContract.LLM_OUTPUT_KEY);
        assertEquals("flat", DifyPlanWorkflowContract.INPUT_LAYOUT);
        assertEquals(7, DifyPlanWorkflowContract.INPUT_FIELD_NAMES.size());
        assertEquals("inputs", DifyPlanWorkflowContract.INPUT_VARIABLE_NAME);
        assertEquals("health_plan", DifyPlanWorkflowContract.OUTPUT_KEY);
    }
}
