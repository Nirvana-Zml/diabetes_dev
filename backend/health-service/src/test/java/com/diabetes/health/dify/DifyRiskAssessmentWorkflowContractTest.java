package com.diabetes.health.dify;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DifyRiskAssessmentWorkflowContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void buildInputObject() {
        Map<String, Object> profile = Map.of("age", 35, "gender", "male");
        Map<String, Object> questionnaire = Map.of("height", 170, "weight", 70);
        Map<String, Object> medicalCalc = Map.of("bmi", 24.2, "baseRiskScore", 45);
        List<Map<String, Object>> factors = List.of(Map.of("name", "家族史"));

        Map<String, Object> result = DifyRiskAssessmentWorkflowContract.buildInputObject(
                "user1", profile, questionnaire, medicalCalc, factors, mapper);

        assertNotNull(result);
        assertEquals("user1", result.get("user_id"));
        assertNotNull(result.get("user_profile"));
        assertNotNull(result.get("questionnaire"));
        assertNotNull(result.get("medical_calc_results"));
        assertNotNull(result.get("risk_factors"));
    }

    @Test
    void buildInputObject_nullRiskFactors() {
        Map<String, Object> profile = Map.of("age", 35);
        Map<String, Object> questionnaire = Map.of("height", 170);
        Map<String, Object> medicalCalc = Map.of("bmi", 24.2);

        Map<String, Object> result = DifyRiskAssessmentWorkflowContract.buildInputObject(
                "user1", profile, questionnaire, medicalCalc, null, mapper);

        assertNotNull(result);
        assertEquals("[]", result.get("risk_factors"));
    }

    @Test
    void ensureStringEncodedInputs() {
        Map<String, Object> payload = Map.of(
                "user_profile", Map.of("age", 35),
                "questionnaire", Map.of("height", 170),
                "medical_calc_results", Map.of("bmi", 24.2),
                "risk_factors", List.of());

        Map<String, Object> result = DifyRiskAssessmentWorkflowContract.ensureStringEncodedInputs(payload, mapper);

        assertNotNull(result);
        assertTrue(result.get("user_profile") instanceof String);
        assertTrue(result.get("questionnaire") instanceof String);
        assertTrue(result.get("medical_calc_results") instanceof String);
        assertTrue(result.get("risk_factors") instanceof String);
    }

    @Test
    void ensureStringEncodedInputs_null() {
        Map<String, Object> result = DifyRiskAssessmentWorkflowContract.ensureStringEncodedInputs(null, mapper);
        assertEquals(Map.of(), result);
    }

    @Test
    void ensureStringEncodedInputs_empty() {
        Map<String, Object> result = DifyRiskAssessmentWorkflowContract.ensureStringEncodedInputs(Map.of(), mapper);
        assertEquals(Map.of(), result);
    }

    @Test
    void ensureStringEncodedInputs_alreadyString() {
        Map<String, Object> payload = Map.of(
                "user_profile", "{\"age\":35}",
                "questionnaire", "",
                "risk_factors", "[]");

        Map<String, Object> result = DifyRiskAssessmentWorkflowContract.ensureStringEncodedInputs(payload, mapper);

        assertEquals("{\"age\":35}", result.get("user_profile"));
        assertEquals("{}", result.get("questionnaire"));
        assertEquals("[]", result.get("risk_factors"));
    }

    @Test
    void inputJsonSchema() {
        Map<String, Object> schema = DifyRiskAssessmentWorkflowContract.inputJsonSchema();

        assertNotNull(schema);
        assertTrue(schema.containsKey("type"));
        assertTrue(schema.containsKey("properties"));
        assertTrue(((Map<?, ?>) schema.get("properties")).containsKey("user_id"));
        assertTrue(((Map<?, ?>) schema.get("properties")).containsKey("user_profile"));
        assertTrue(((Map<?, ?>) schema.get("properties")).containsKey("questionnaire"));
    }

    @Test
    void outputJsonSchema() {
        Map<String, Object> schema = DifyRiskAssessmentWorkflowContract.outputJsonSchema();

        assertNotNull(schema);
        assertTrue(schema.containsKey("type"));
        assertTrue(schema.containsKey("properties"));
        assertTrue(((Map<?, ?>) schema.get("properties")).containsKey("risk_score"));
        assertTrue(((Map<?, ?>) schema.get("properties")).containsKey("risk_level"));
        assertTrue(((Map<?, ?>) schema.get("properties")).containsKey("risk_factors"));
        assertTrue(((Map<?, ?>) schema.get("properties")).containsKey("suggestions"));
    }

    @Test
    void workflowSpec() {
        Map<String, Object> spec = DifyRiskAssessmentWorkflowContract.workflowSpec(
                "http://localhost:5001/", "api-key-xxx", "json", "blocking", mapper);

        assertNotNull(spec);
        assertEquals("http://localhost:5001/v1/workflows/run", spec.get("workflowUrl"));
        assertEquals("api-key-xxx", spec.get("apiKey"));
        assertEquals("blocking", spec.get("responseMode"));
        assertEquals("json", spec.get("inputFormat"));
        assertNotNull(spec.get("inputJsonSchema"));
        assertNotNull(spec.get("outputJsonSchema"));
        assertNotNull(spec.get("inputExample"));
    }

    @Test
    void workflowSpec_nullBaseUrl() {
        Map<String, Object> spec = DifyRiskAssessmentWorkflowContract.workflowSpec(
                null, "api-key-xxx", "json", "blocking", mapper);

        assertNotNull(spec);
        assertEquals("/v1/workflows/run", spec.get("workflowUrl"));
    }

    @Test
    void stringifyInputField_blankString() {
        Map<String, Object> payload = Map.of(
                "user_profile", "",
                "risk_factors", "   ");

        Map<String, Object> result = DifyRiskAssessmentWorkflowContract.ensureStringEncodedInputs(payload, mapper);

        assertEquals("{}", result.get("user_profile"));
        assertEquals("[]", result.get("risk_factors"));
    }

    @Test
    void stringifyInputField_nullValue() {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("user_profile", null);
        payload.put("risk_factors", null);

        Map<String, Object> result = DifyRiskAssessmentWorkflowContract.ensureStringEncodedInputs(payload, mapper);

        assertEquals("{}", result.get("user_profile"));
        assertEquals("[]", result.get("risk_factors"));
    }

    @Test
    void toSchemaCompliantProfile_withAge() {
        Map<String, Object> profile = Map.of("age", "35", "gender", 1);

        Map<String, Object> result = DifyRiskAssessmentWorkflowContract.buildInputObject(
                "user1", profile, Map.of(), Map.of(), List.of(), mapper);

        assertNotNull(result);
        assertTrue(result.get("user_profile") instanceof String);
        String profileJson = (String) result.get("user_profile");
        assertTrue(profileJson.contains("\"age\":35"));
        assertTrue(profileJson.contains("\"gender\":\"1\""));
    }

    @Test
    void toSchemaCompliantMedicalCalc_nullAndEmpty() {
        Map<String, Object> result1 = DifyRiskAssessmentWorkflowContract.buildInputObject(
                "user1", Map.of(), Map.of(), null, List.of(), mapper);
        assertNotNull(result1);

        Map<String, Object> result2 = DifyRiskAssessmentWorkflowContract.buildInputObject(
                "user1", Map.of(), Map.of(), Map.of(), List.of(), mapper);
        assertNotNull(result2);
    }

    @Test
    void toSchemaCompliantMedicalCalc_withBmi() {
        Map<String, Object> medicalCalc = Map.of("bmi", "24.5", "bmiLevel", "overweight");

        Map<String, Object> result = DifyRiskAssessmentWorkflowContract.buildInputObject(
                "user1", Map.of(), Map.of(), medicalCalc, List.of(), mapper);

        assertNotNull(result);
        String medicalCalcJson = (String) result.get("medical_calc_results");
        assertTrue(medicalCalcJson.contains("\"bmi\":24.5"));
        assertTrue(medicalCalcJson.contains("\"bmiLevel\":\"overweight\""));
    }

    @Test
    void toSchemaCompliantMedicalCalc_allFields() {
        Map<String, Object> medicalCalc = Map.of(
                "bmi", "25.0",
                "bmiLevel", "normal",
                "glucoseLevel", "prediabetes",
                "baseRiskScore", "45");

        Map<String, Object> result = DifyRiskAssessmentWorkflowContract.buildInputObject(
                "user1", Map.of(), Map.of(), medicalCalc, List.of(), mapper);

        assertNotNull(result);
        String medicalCalcJson = (String) result.get("medical_calc_results");
        assertTrue(medicalCalcJson.contains("\"bmi\":25.0"));
        assertTrue(medicalCalcJson.contains("\"bmiLevel\":\"normal\""));
        assertTrue(medicalCalcJson.contains("\"glucoseLevel\":\"prediabetes\""));
        assertTrue(medicalCalcJson.contains("\"baseRiskScore\":45"));
    }

    @Test
    void toSchemaCompliantMedicalCalc_null() {
        Map<String, Object> result = DifyRiskAssessmentWorkflowContract.buildInputObject(
                "user1", Map.of(), Map.of(), null, List.of(), mapper);

        assertNotNull(result);
        String medicalCalcJson = (String) result.get("medical_calc_results");
        assertEquals("{}", medicalCalcJson);
    }

    @Test
    void toSchemaCompliantMedicalCalc_onlyBmi() {
        Map<String, Object> medicalCalc = Map.of("bmi", "24.5");

        Map<String, Object> result = DifyRiskAssessmentWorkflowContract.buildInputObject(
                "user1", Map.of(), Map.of(), medicalCalc, List.of(), mapper);

        assertNotNull(result);
        String medicalCalcJson = (String) result.get("medical_calc_results");
        assertTrue(medicalCalcJson.contains("\"bmi\":24.5"));
    }

    @Test
    void toSchemaCompliantMedicalCalc_withoutBmi() {
        Map<String, Object> medicalCalc = Map.of("bmiLevel", "normal", "baseRiskScore", "30");

        Map<String, Object> result = DifyRiskAssessmentWorkflowContract.buildInputObject(
                "user1", Map.of(), Map.of(), medicalCalc, List.of(), mapper);

        assertNotNull(result);
        String medicalCalcJson = (String) result.get("medical_calc_results");
        assertFalse(medicalCalcJson.contains("\"bmi\""));
        assertTrue(medicalCalcJson.contains("\"bmiLevel\":\"normal\""));
    }
}