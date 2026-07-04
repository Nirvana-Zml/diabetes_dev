package com.diabetes.checkin.dify;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DifyFoodRecognitionWorkflowContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void buildsInputSchemaOutputSchemaAndWorkflowSpec() throws Exception {
        List<Map<String, Object>> categories = List.of(
                Map.of("category_id", "cat_grain", "category_name", "主食"));
        List<Map<String, Object>> presets = List.of(
                Map.of("food_id", "food_rice_001", "category_id", "cat_grain",
                        "food_name", "糙米饭", "calories_per_gram", 1.16, "is_liquid", 0, "ml_to_g_ratio", 1.0));

        Map<String, Object> input = DifyFoodRecognitionWorkflowContract.buildInputObject(
                "u1", "http://minio/checkin/food/u1/upload.jpg", 2, categories, presets, mapper);

        assertEquals("u1", input.get("user_id"));
        assertTrue(input.get("food_categories").toString().contains("cat_grain"));
        assertTrue(input.get("food_presets").toString().contains("food_rice_001"));
        assertEquals("2", input.get("meal_period"));

        Map<String, Object> spec = DifyFoodRecognitionWorkflowContract.workflowSpec(
                "http://dify/", "key", "blocking");
        assertEquals("http://dify/v1/workflows/run", spec.get("workflowUrl"));
        assertEquals(DifyFoodRecognitionWorkflowContract.OUTPUT_KEY, spec.get("outputKey"));
        assertTrue(DifyFoodRecognitionWorkflowContract.inputJsonSchema().containsKey("properties"));
        assertTrue(DifyFoodRecognitionWorkflowContract.outputJsonSchema().containsKey("properties"));
    }
}
