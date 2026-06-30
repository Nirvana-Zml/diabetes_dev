package com.diabetes.home.dify;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DifyQaChatContractTest {

    @Test
    void buildsInputsSchemasAndWorkflowSpec() throws Exception {
        assertEquals("", DifyQaChatContract.buildInputs(null).get(DifyQaChatContract.KNOWLEDGE_CONTEXT_INPUT));
        assertEquals("ctx", DifyQaChatContract.buildInputs("ctx").get(DifyQaChatContract.KNOWLEDGE_CONTEXT_INPUT));
        assertEquals("", DifyQaChatContract.buildWorkflowInputs(null, null).get("query"));
        assertEquals("ctx", DifyQaChatContract.buildWorkflowInputs("q", "ctx").get(DifyQaChatContract.KNOWLEDGE_CONTEXT_INPUT));

        assertTrue(((Map<?, ?>) DifyQaChatContract.inputJsonSchema().get("properties")).containsKey("query"));
        assertTrue(((Map<?, ?>) DifyQaChatContract.outputJsonSchema().get("properties")).containsKey(DifyQaChatContract.OUTPUT_TEXT));
        Map<String, Object> spec = DifyQaChatContract.workflowSpec("http://dify/", "key");
        assertEquals("http://dify/v1/workflows/run", spec.get("workflowUrl"));
        assertEquals("key", spec.get("apiKey"));
        assertEquals("streaming", spec.get("responseMode"));
        assertEquals(DifyQaChatContract.PHASE1_DOC_TYPE, spec.get("docType"));
        assertEquals("/v1/workflows/run", DifyQaChatContract.workflowSpec(null, null).get("workflowUrl"));

        Constructor<DifyQaChatContract> constructor = DifyQaChatContract.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        constructor.newInstance();
    }
}
