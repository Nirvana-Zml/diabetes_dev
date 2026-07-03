package com.diabetes.user.dify;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DifyHealthTrendWorkflowContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void buildInputs_withData() throws Exception {
        List<Map<String, Object>> history = List.of(Map.of("date", "2026-07-01", "bmi", 24.5));
        Map<String, Object> baseline = Map.of("age", 45, "gender", "male");

        Map<String, Object> inputs = DifyHealthTrendWorkflowContract.buildInputs(mapper, history, baseline);

        assertEquals(DifyHealthTrendWorkflowContract.DEFAULT_QUERY, inputs.get("query"));
        String historyJson = (String) inputs.get("health_history");
        assertTrue(historyJson.contains("\"date\":\"2026-07-01\""));
        assertTrue(historyJson.contains("\"bmi\":24.5"));
        assertTrue(((String) inputs.get("user_baseline")).contains("\"age\":45"));
    }

    @Test
    void buildInputs_nullHistoryAndBaseline() throws Exception {
        Map<String, Object> inputs = DifyHealthTrendWorkflowContract.buildInputs(mapper, null, null);

        assertEquals(DifyHealthTrendWorkflowContract.DEFAULT_QUERY, inputs.get("query"));
        assertEquals("[]", inputs.get("health_history"));
        assertEquals("{}", inputs.get("user_baseline"));
    }

    @Test
    void inputJsonSchema() {
        Map<String, Object> schema = DifyHealthTrendWorkflowContract.inputJsonSchema();

        assertNotNull(schema);
        assertTrue(schema.containsKey("type"));
        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        assertTrue(properties.containsKey("query"));
        assertTrue(properties.containsKey("health_history"));
        assertTrue(properties.containsKey("user_baseline"));
    }

    @Test
    void outputJsonSchema() {
        Map<String, Object> schema = DifyHealthTrendWorkflowContract.outputJsonSchema();

        assertNotNull(schema);
        assertTrue(schema.containsKey("type"));
        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        assertTrue(properties.containsKey("summary"));
        assertTrue(properties.containsKey("risk_level"));
        assertTrue(properties.containsKey("bmi_trend"));
        assertTrue(properties.containsKey("glucose_trend"));
        assertTrue(properties.containsKey("bp_trend"));
        assertTrue(properties.containsKey("anomalies"));
    }

    @Test
    void workflowSpec() {
        Map<String, Object> spec = DifyHealthTrendWorkflowContract.workflowSpec(
                "http://localhost:5001/", "api-key-xxx", "blocking");

        assertEquals("http://localhost:5001/v1/workflows/run", spec.get("workflowUrl"));
        assertEquals("api-key-xxx", spec.get("apiKey"));
        assertEquals("blocking", spec.get("responseMode"));
        assertEquals(DifyHealthTrendWorkflowContract.INPUT_LAYOUT, spec.get("inputLayout"));
        assertEquals(DifyHealthTrendWorkflowContract.INPUT_FIELD_NAMES, spec.get("inputFieldNames"));
        assertEquals(DifyHealthTrendWorkflowContract.OUTPUT_KEY, spec.get("outputKey"));
        assertNotNull(spec.get("inputJsonSchema"));
        assertNotNull(spec.get("outputJsonSchema"));
    }

    @Test
    void workflowSpec_nullBaseUrl() {
        Map<String, Object> spec = DifyHealthTrendWorkflowContract.workflowSpec(
                null, "api-key-xxx", "blocking");

        assertEquals("/v1/workflows/run", spec.get("workflowUrl"));
    }
}
