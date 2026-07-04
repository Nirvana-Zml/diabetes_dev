package com.diabetes.checkin.dify;

import com.diabetes.common.dify.DifyJsonSchema;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 食物图片识别 Dify 工作流 JSON 契约，见 docs/食物识别工作流数据契约.md
 */
public final class DifyFoodRecognitionWorkflowContract {

    public static final String INPUT_LAYOUT = "flat";
    public static final List<String> INPUT_FIELD_NAMES = List.of(
            "query", "user_id", "image_url", "meal_period", "food_categories", "food_presets"
    );
    public static final String OUTPUT_KEY = "food_recognition";
    public static final String DEFAULT_QUERY =
            "请识别图片中的食物，返回名称、分类、营养参数及建议食用量，便于糖尿病用户饮食打卡。";

    private DifyFoodRecognitionWorkflowContract() {
    }

    public static Map<String, Object> buildInputObject(String userId,
                                                       String imageUrl,
                                                       Integer mealPeriod,
                                                       List<Map<String, Object>> foodCategories,
                                                       List<Map<String, Object>> foodPresets,
                                                       ObjectMapper mapper) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", DEFAULT_QUERY);
        payload.put("user_id", DifyJsonSchema.asString(userId));
        payload.put("image_url", DifyJsonSchema.asString(imageUrl));
        if (mealPeriod != null) {
            // Dify 开始节点 meal_period 为 text-input，须传字符串
            payload.put("meal_period", String.valueOf(mealPeriod));
        }
        payload.put("food_categories", DifyJsonSchema.asJsonArrayString(foodCategories, mapper));
        payload.put("food_presets", DifyJsonSchema.asJsonArrayString(foodPresets, mapper));
        return payload;
    }

    public static Map<String, Object> inputJsonSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", DifyJsonSchema.stringType());
        properties.put("user_id", DifyJsonSchema.stringType());
        properties.put("image_url", DifyJsonSchema.stringType());
        properties.put("meal_period", DifyJsonSchema.stringType());
        properties.put("food_categories", DifyJsonSchema.stringType());
        properties.put("food_presets", DifyJsonSchema.stringType());
        return DifyJsonSchema.rootObject(properties);
    }

    public static Map<String, Object> outputJsonSchema() {
        Map<String, Object> itemProps = new LinkedHashMap<>();
        itemProps.put("food_name", DifyJsonSchema.stringType());
        itemProps.put("category_name", DifyJsonSchema.stringType());
        itemProps.put("calories_per_gram", DifyJsonSchema.numberType());
        itemProps.put("estimated_grams", DifyJsonSchema.numberType());

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("food_name", DifyJsonSchema.stringType());
        properties.put("category_id", DifyJsonSchema.stringType());
        properties.put("category_name", DifyJsonSchema.stringType());
        properties.put("calories_per_gram", DifyJsonSchema.numberType());
        properties.put("is_liquid", DifyJsonSchema.booleanType());
        properties.put("ml_to_g_ratio", DifyJsonSchema.numberType());
        properties.put("suggested_input_unit", DifyJsonSchema.integerType());
        properties.put("suggested_input_amount", DifyJsonSchema.numberType());
        properties.put("suggested_grams", DifyJsonSchema.numberType());
        properties.put("suggested_total_calories", DifyJsonSchema.integerType());
        properties.put("matched_food_id", DifyJsonSchema.stringType());
        properties.put("source_type", DifyJsonSchema.integerType());
        properties.put("confidence", DifyJsonSchema.stringType());
        properties.put("gi_level", DifyJsonSchema.stringType());
        properties.put("nutrition_tip", DifyJsonSchema.stringType());
        properties.put("recognition_summary", DifyJsonSchema.stringType());
        properties.put("items", DifyJsonSchema.array(DifyJsonSchema.object(itemProps)));
        properties.put("has_error", DifyJsonSchema.booleanType());
        properties.put("error_message", DifyJsonSchema.stringType());
        return DifyJsonSchema.rootObject(properties);
    }

    public static Map<String, Object> workflowSpec(String baseUrl, String apiKey, String responseMode) {
        String normalizedBase = baseUrl == null ? "" : baseUrl.replaceAll("/$", "");
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("workflowUrl", normalizedBase + "/v1/workflows/run");
        spec.put("apiKey", apiKey);
        spec.put("responseMode", responseMode);
        spec.put("inputLayout", INPUT_LAYOUT);
        spec.put("inputFieldNames", INPUT_FIELD_NAMES);
        spec.put("inputJsonSchema", inputJsonSchema());
        spec.put("outputJsonSchema", outputJsonSchema());
        spec.put("outputKey", OUTPUT_KEY);
        return spec;
    }
}
