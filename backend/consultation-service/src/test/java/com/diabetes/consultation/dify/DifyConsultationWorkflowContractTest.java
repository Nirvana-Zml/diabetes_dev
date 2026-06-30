package com.diabetes.consultation.dify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DifyConsultationWorkflowContract 工作流契约测试")
class DifyConsultationWorkflowContractTest {

    @Test
    @DisplayName("buildInputObject - 构建输入对象")
    void buildInputObject() {
        Map<String, Object> result = DifyConsultationWorkflowContract.buildInputObject(
                "最近血糖偏高",
                "sess_001",
                "你是内分泌科医生...",
                "{\"age\":52}",
                "[用户] 你好...",
                "【片段1】糖尿病指南..."
        );

        assertEquals(6, result.size());
        assertEquals("最近血糖偏高", result.get("query"));
        assertEquals("sess_001", result.get("conversation_id"));
        assertEquals("你是内分泌科医生...", result.get("doctor_role"));
        assertEquals("{\"age\":52}", result.get("patient_profile"));
        assertEquals("[用户] 你好...", result.get("conversation_history"));
        assertEquals("【片段1】糖尿病指南...", result.get("knowledge_context"));
    }

    @Test
    @DisplayName("buildInputObject - 空参数")
    void buildInputObject_empty() {
        Map<String, Object> result = DifyConsultationWorkflowContract.buildInputObject(
                null, null, null, null, null, null
        );

        assertEquals(6, result.size());
    }

    @Test
    @DisplayName("inputJsonSchema - 返回输入JSON Schema")
    void inputJsonSchema() {
        Map<String, Object> result = DifyConsultationWorkflowContract.inputJsonSchema();

        assertNotNull(result.get("type"));
        assertNotNull(result.get("properties"));
        assertNotNull(result.get("required"));
        List<?> required = (List<?>) result.get("required");
        assertTrue(required.contains("query"));
        assertTrue(required.contains("conversation_id"));
        assertTrue(required.contains("doctor_role"));
        assertTrue(required.contains("patient_profile"));
    }

    @Test
    @DisplayName("outputJsonSchema - 返回输出JSON Schema")
    void outputJsonSchema() {
        Map<String, Object> result = DifyConsultationWorkflowContract.outputJsonSchema();

        assertNotNull(result.get("type"));
        assertNotNull(result.get("properties"));
        Map<?, ?> properties = (Map<?, ?>) result.get("properties");
        assertNotNull(properties.get("content"));
        assertNotNull(properties.get("suggestion"));
    }

    @Test
    @DisplayName("workflowSpec - 返回工作流规范")
    void workflowSpec() {
        Map<String, Object> result = DifyConsultationWorkflowContract.workflowSpec(
                "http://localhost:5000/",
                "test-api-key",
                "blocking"
        );

        assertEquals("http://localhost:5000/v1/workflows/run", result.get("workflowUrl"));
        assertEquals("test-api-key", result.get("apiKey"));
        assertEquals("blocking", result.get("responseMode"));
        assertEquals("flat", result.get("inputLayout"));
        assertNotNull(result.get("inputFieldNames"));
        assertNotNull(result.get("inputJsonSchema"));
        assertNotNull(result.get("outputJsonSchema"));
        assertNotNull(result.get("inputExample"));
        assertEquals("doctor_reply", result.get("outputKey"));
    }

    @Test
    @DisplayName("workflowSpec - baseUrl末尾无斜杠")
    void workflowSpec_noTrailingSlash() {
        Map<String, Object> result = DifyConsultationWorkflowContract.workflowSpec(
                "http://localhost:5000",
                "api-key",
                "blocking"
        );

        assertEquals("http://localhost:5000/v1/workflows/run", result.get("workflowUrl"));
    }

    @Test
    @DisplayName("workflowSpec - baseUrl为空")
    void workflowSpec_emptyBaseUrl() {
        Map<String, Object> result = DifyConsultationWorkflowContract.workflowSpec(
                null,
                "api-key",
                "blocking"
        );

        assertEquals("/v1/workflows/run", result.get("workflowUrl"));
    }

    @Test
    @DisplayName("INPUT_FIELD_NAMES - 验证输入字段列表")
    void inputFieldNames() {
        List<String> fields = DifyConsultationWorkflowContract.INPUT_FIELD_NAMES;

        assertEquals(6, fields.size());
        assertTrue(fields.contains("query"));
        assertTrue(fields.contains("conversation_id"));
        assertTrue(fields.contains("doctor_role"));
        assertTrue(fields.contains("patient_profile"));
        assertTrue(fields.contains("conversation_history"));
        assertTrue(fields.contains("knowledge_context"));
    }

    @Test
    @DisplayName("OUTPUT_KEY - 验证输出键名")
    void outputKey() {
        assertEquals("doctor_reply", DifyConsultationWorkflowContract.OUTPUT_KEY);
    }

    @Test
    @DisplayName("INPUT_LAYOUT - 验证输入布局")
    void inputLayout() {
        assertEquals("flat", DifyConsultationWorkflowContract.INPUT_LAYOUT);
    }
}