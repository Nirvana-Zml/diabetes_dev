package com.diabetes.article.dify;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DifyArticleDraftWorkflowContractTest {

    @Test
    void buildInputObject_trimsFields() {
        Map<String, Object> result = DifyArticleDraftWorkflowContract.buildInputObject("  糖尿病饮食  ", "  关键词  ");

        assertEquals("糖尿病饮食", result.get("topic"));
        assertEquals("关键词", result.get("keywords"));
    }

    @Test
    void buildInputObject_nullFieldsBecomeEmpty() {
        Map<String, Object> result = DifyArticleDraftWorkflowContract.buildInputObject(null, null);

        assertEquals("", result.get("topic"));
        assertEquals("", result.get("keywords"));
    }

    @Test
    void inputJsonSchema_containsRequiredFields() {
        Map<String, Object> schema = DifyArticleDraftWorkflowContract.inputJsonSchema();

        assertNotNull(schema);
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.containsKey("topic"));
        assertTrue(properties.containsKey("keywords"));
    }

    @Test
    void workflowConfig_usesOverrideUrlWhenProvided() {
        Map<String, Object> config = DifyArticleDraftWorkflowContract.workflowConfig(
                "http://dify.local/", "key-123", "http://custom/run");

        assertEquals("http://custom/run", config.get("workflowUrl"));
        assertEquals("key-123", config.get("apiKey"));
        assertEquals("streaming", config.get("responseMode"));
        assertEquals("flat", config.get("inputFormat"));
        assertEquals(DifyArticleDraftWorkflowContract.OUTPUT_KEY, config.get("outputKey"));
        assertEquals(List.of("topic", "keywords"), config.get("inputVariables"));
        assertNotNull(config.get("inputExample"));
        assertNotNull(config.get("outputJsonSchema"));
    }

    @Test
    void articleDraftJsonSchema_andOutputSchema() {
        Map<String, Object> draft = DifyArticleDraftWorkflowContract.articleDraftJsonSchema();
        assertEquals("object", draft.get("type"));
        Map<String, Object> output = DifyArticleDraftWorkflowContract.outputJsonSchema();
        assertEquals("object", output.get("type"));
    }

    @Test
    void workflowConfig_buildsDefaultWorkflowUrl() {
        Map<String, Object> config = DifyArticleDraftWorkflowContract.workflowConfig(
                "http://dify.local/", null, "");

        assertEquals("http://dify.local/v1/workflows/run", config.get("workflowUrl"));
        assertEquals("", config.get("apiKey"));
    }
}
