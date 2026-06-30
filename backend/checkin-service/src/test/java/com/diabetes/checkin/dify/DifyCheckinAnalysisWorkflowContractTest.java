package com.diabetes.checkin.dify;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DifyCheckinAnalysisWorkflowContractTest {

    @Test
    void buildsInputSchemaOutputSchemaAndWorkflowSpec() throws Exception {
        Map<String, Object> input = DifyCheckinAnalysisWorkflowContract.buildInputObject(
                "u1",
                Map.of("totalCheckins", "2", "completionRate", "0.5", "totalPoints", "10",
                        "streakDays", "3", "calendarData", Map.of("2024-01-01", Map.of("diet", true))),
                Map.of("dietTrend", List.of(Map.of("date", "2024-01-01", "count", "1"), "bad"),
                        "exerciseTrend", List.of(), "medicationTrend", List.of(), "glucoseTrend", List.of()),
                Map.of("height", "170", "weight", "60", "bmi", "20.8", "fasting_glucose", "6.1"),
                "2024-01-01",
                "2024-01-02");

        assertEquals("u1", input.get("user_id"));
        assertTrue(input.get("checkin_stats").toString().contains("calendarData"));
        assertTrue(input.get("trend_data").toString().contains("dietTrend"));
        assertTrue(input.get("user_profile").toString().contains("fastingGlucose"));

        Map<String, Object> emptyInput = DifyCheckinAnalysisWorkflowContract.buildInputObject(
                null, null, null, null, null, null);
        assertEquals(0, ((Map<?, ?>) emptyInput.get("checkin_stats")).get("totalCheckins"));
        Map<String, Object> nonMapCalendar = DifyCheckinAnalysisWorkflowContract.buildInputObject(
                "u1", Map.of("calendarData", "bad"), Map.of(), Map.of(), "2024-01-01", "2024-01-02");
        assertEquals(Map.of(), ((Map<?, ?>) nonMapCalendar.get("checkin_stats")).get("calendarData"));

        Map<String, Object> spec = DifyCheckinAnalysisWorkflowContract.workflowSpec(
                "http://dify/", "key", "blocking");
        Map<String, Object> nullBaseSpec = DifyCheckinAnalysisWorkflowContract.workflowSpec(
                null, null, null);
        assertEquals("http://dify/v1/workflows/run", spec.get("workflowUrl"));
        assertEquals("/v1/workflows/run", nullBaseSpec.get("workflowUrl"));
        assertEquals(DifyCheckinAnalysisWorkflowContract.OUTPUT_KEY, spec.get("outputKey"));
        assertTrue(DifyCheckinAnalysisWorkflowContract.inputJsonSchema().containsKey("properties"));
        assertTrue(DifyCheckinAnalysisWorkflowContract.outputJsonSchema().containsKey("properties"));

        Constructor<DifyCheckinAnalysisWorkflowContract> constructor =
                DifyCheckinAnalysisWorkflowContract.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        constructor.newInstance();

        Method putNumberIfPresent = DifyCheckinAnalysisWorkflowContract.class.getDeclaredMethod(
                "putNumberIfPresent", Map.class, Map.class, String[].class);
        putNumberIfPresent.setAccessible(true);
        Map<String, Object> target = new java.util.LinkedHashMap<>();
        putNumberIfPresent.invoke(null, target, Map.of("height", 170), null);
        putNumberIfPresent.invoke(null, target, Map.of("height", 170), new String[]{});
        putNumberIfPresent.invoke(null, target, Map.of("height", 170), new String[]{"missing"});
        Map<String, Object> nullSource = new java.util.LinkedHashMap<>();
        nullSource.put("height", null);
        putNumberIfPresent.invoke(null, target, nullSource, new String[]{"height"});
        putNumberIfPresent.invoke(null, target, Map.of("height", 170), new String[]{"height"});
        assertEquals(170.0, target.get("height"));
    }
}
