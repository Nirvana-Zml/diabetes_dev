package com.diabetes.checkin.service;

import com.diabetes.checkin.dify.DifyFoodRecognitionWorkflowContract;
import com.diabetes.checkin.dto.FoodRecognitionRequest;
import com.diabetes.checkin.entity.FoodCategory;
import com.diabetes.checkin.entity.FoodPreset;
import com.diabetes.checkin.mapper.PresetMapper;
import com.diabetes.common.dify.DifyClient;
import com.diabetes.common.dify.DifyJsonSchema;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.storage.MinioStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FoodRecognitionService {

    private static final Logger log = LoggerFactory.getLogger(FoodRecognitionService.class);
    private static final int MAX_PRESETS = 200;

    private final PresetMapper presetMapper;
    private final DifyClient difyClient;
    private final ObjectMapper objectMapper;
    private final MinioStorageService minioStorageService;
    private final String difyApiKey;
    private final String difyBaseUrl;
    private final String difyResponseMode;
    private final String difyImagePublicBaseUrl;

    public FoodRecognitionService(PresetMapper presetMapper,
                                  DifyClient difyClient,
                                  ObjectMapper objectMapper,
                                  MinioStorageService minioStorageService,
                                  @Value("${dify.base-url:http://localhost}") String difyBaseUrl,
                                  @Value("${dify.workflows.food-recognition.api-key:}") String difyApiKey,
                                  @Value("${dify.workflows.food-recognition.response-mode:blocking}") String difyResponseMode,
                                  @Value("${dify.workflows.food-recognition.image-public-base-url:}") String difyImagePublicBaseUrl) {
        this.presetMapper = presetMapper;
        this.difyClient = difyClient;
        this.objectMapper = objectMapper;
        this.minioStorageService = minioStorageService;
        this.difyBaseUrl = difyBaseUrl;
        this.difyApiKey = difyApiKey;
        this.difyResponseMode = difyResponseMode == null ? "blocking" : difyResponseMode.trim();
        this.difyImagePublicBaseUrl = difyImagePublicBaseUrl == null ? "" : difyImagePublicBaseUrl.trim();
    }

    public Map<String, Object> getDifyWorkflowSpec() {
        return DifyFoodRecognitionWorkflowContract.workflowSpec(
                difyBaseUrl, difyApiKey, difyResponseMode);
    }

    public Map<String, Object> recognize(String userId, FoodRecognitionRequest request) {
        if (difyApiKey == null || difyApiKey.isBlank()) {
            throw new BusinessException(503, "食物识别工作流未配置，请联系管理员");
        }
        String objectKey = CheckinImagePathHelper.normalize(request.getImageObjectKey());
        validateImageObjectKey(userId, objectKey);

        String imageUrl = minioStorageService.buildCheckinImageUrl(objectKey, difyImagePublicBaseUrl);
        List<Map<String, Object>> categories = buildCategoryContext();
        List<Map<String, Object>> presets = buildPresetContext();

        Map<String, Object> payload = DifyFoodRecognitionWorkflowContract.buildInputObject(
                userId, imageUrl, request.getMealPeriod(), categories, presets, objectMapper);
        Map<String, Object> inputs = DifyJsonSchema.flatWorkflowInputs(payload);

        log.info("发起 Dify 食物识别工作流 userId={} imageUrl={}", userId, imageUrl);
        JsonNode response = difyClient.runWorkflowBlocking(difyApiKey, userId, inputs, difyResponseMode);
        assertWorkflowSucceeded(response);
        return parseRecognitionResult(response);
    }

    private List<Map<String, Object>> buildCategoryContext() {
        return presetMapper.findAllFoodCategories().stream()
                .map(this::toCategoryContext)
                .toList();
    }

    private Map<String, Object> toCategoryContext(FoodCategory category) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("category_id", category.getCategoryId());
        map.put("category_name", category.getCategoryName());
        return map;
    }

    private List<Map<String, Object>> buildPresetContext() {
        return presetMapper.findAllFoodPresets(MAX_PRESETS).stream()
                .map(this::toPresetContext)
                .toList();
    }

    private Map<String, Object> toPresetContext(FoodPreset preset) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("food_id", preset.getFoodId());
        map.put("category_id", preset.getCategoryId());
        map.put("food_name", preset.getFoodName());
        map.put("calories_per_gram", preset.getCaloriesPerGram());
        map.put("is_liquid", preset.getIsLiquid() != null && preset.getIsLiquid() == 1 ? 1 : 0);
        map.put("ml_to_g_ratio", preset.getMlToGRatio() != null ? preset.getMlToGRatio() : BigDecimal.ONE);
        return map;
    }

    private void validateImageObjectKey(String userId, String key) {
        if (!CheckinImagePathHelper.isValidUserUploadKey(key, "food", userId)) {
            throw new BusinessException(400, "imageObjectKey 格式无效，用户上传应为 food/{userId}/upload_*.jpg");
        }
    }

    private void assertWorkflowSucceeded(JsonNode response) {
        String status = response.path("data").path("status").asText(null);
        if (status == null || status.isBlank()) {
            status = response.path("status").asText(null);
        }
        if (status != null && !status.isBlank() && !"succeeded".equalsIgnoreCase(status)) {
            String error = response.path("data").path("error").asText(
                    response.path("error").asText("工作流执行失败"));
            throw new BusinessException(500, "Dify 食物识别失败: " + error);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseRecognitionResult(JsonNode response) {
        JsonNode node = extractRecognitionNode(response);
        if (node.isMissingNode()) {
            throw new BusinessException(500, "Dify 食物识别响应缺少 food_recognition 输出");
        }
        Map<String, Object> raw = objectMapper.convertValue(node, Map.class);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("foodName", stringValue(firstPresent(raw, "food_name", "foodName")));
        result.put("categoryId", stringValue(firstPresent(raw, "category_id", "categoryId")));
        result.put("categoryName", stringValue(firstPresent(raw, "category_name", "categoryName")));
        result.put("caloriesPerGram", numberValue(firstPresent(raw, "calories_per_gram", "caloriesPerGram")));
        result.put("isLiquid", boolValue(firstPresent(raw, "is_liquid", "isLiquid")));
        result.put("mlToGRatio", numberValue(firstPresent(raw, "ml_to_g_ratio", "mlToGRatio"), 1.0));
        result.put("suggestedInputUnit", intValue(firstPresent(raw, "suggested_input_unit", "suggestedInputUnit"), 1));
        result.put("suggestedInputAmount", numberValue(firstPresent(raw, "suggested_input_amount", "suggestedInputAmount")));
        result.put("suggestedGrams", numberValue(firstPresent(raw, "suggested_grams", "suggestedGrams")));
        result.put("suggestedTotalCalories", intValue(firstPresent(raw, "suggested_total_calories", "suggestedTotalCalories"), 0));
        result.put("matchedFoodId", stringValue(firstPresent(raw, "matched_food_id", "matchedFoodId")));
        result.put("sourceType", intValue(firstPresent(raw, "source_type", "sourceType"), 2));
        result.put("confidence", stringValue(firstPresent(raw, "confidence", "confidence")));
        result.put("giLevel", stringValue(firstPresent(raw, "gi_level", "giLevel")));
        result.put("nutritionTip", stringValue(firstPresent(raw, "nutrition_tip", "nutritionTip")));
        result.put("recognitionSummary", stringValue(firstPresent(raw, "recognition_summary", "recognitionSummary")));
        result.put("items", normalizeItems(raw.get("items")));
        result.put("hasError", boolValue(firstPresent(raw, "has_error", "hasError")));
        result.put("errorMessage", stringValue(firstPresent(raw, "error_message", "errorMessage")));
        return result;
    }

    private JsonNode extractRecognitionNode(JsonNode difyResponse) {
        for (JsonNode candidate : List.of(
                difyResponse.path(DifyFoodRecognitionWorkflowContract.OUTPUT_KEY),
                difyResponse.path("data").path("outputs").path(DifyFoodRecognitionWorkflowContract.OUTPUT_KEY),
                difyResponse.path("outputs").path(DifyFoodRecognitionWorkflowContract.OUTPUT_KEY))) {
            JsonNode parsed = unwrapJsonNode(candidate);
            parsed = firstRecognitionObject(parsed);
            if (!parsed.isMissingNode() && parsed.isObject()) {
                return parsed;
            }
        }

        JsonNode text = difyResponse.path("data").path("outputs").path("text");
        if (text.isMissingNode()) {
            text = difyResponse.path("outputs").path("text");
        }
        if (text.isTextual()) {
            try {
                JsonNode parsed = objectMapper.readTree(text.asText());
                JsonNode fromText = parsed.path(DifyFoodRecognitionWorkflowContract.OUTPUT_KEY);
                if (!fromText.isMissingNode()) {
                    JsonNode recognition = firstRecognitionObject(unwrapJsonNode(fromText));
                    if (!recognition.isMissingNode()) {
                        return recognition;
                    }
                }
                if (parsed.has("food_name") || parsed.has("has_error")) {
                    return parsed;
                }
            } catch (Exception e) {
                log.debug("无法从 Dify text 输出解析 food_recognition: {}", e.getMessage());
            }
        }
        return objectMapper.missingNode();
    }

    private JsonNode unwrapJsonNode(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return objectMapper.missingNode();
        }
        if (node.isTextual()) {
            try {
                return unwrapJsonNode(objectMapper.readTree(node.asText()));
            } catch (Exception e) {
                log.debug("无法解析 food_recognition JSON 字符串: {}", e.getMessage());
                return objectMapper.missingNode();
            }
        }
        return node;
    }

    /** Dify 结束节点可能返回 object 或单元素 array，统一取首个识别对象。 */
    private JsonNode firstRecognitionObject(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return objectMapper.missingNode();
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                JsonNode candidate = firstRecognitionObject(item);
                if (!candidate.isMissingNode() && candidate.isObject()) {
                    return candidate;
                }
            }
            return objectMapper.missingNode();
        }
        return node;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeItems(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("foodName", stringValue(map.get("food_name") != null ? map.get("food_name") : map.get("foodName")));
            normalized.put("categoryName", stringValue(map.get("category_name") != null ? map.get("category_name") : map.get("categoryName")));
            normalized.put("caloriesPerGram", numberValue(map.get("calories_per_gram") != null ? map.get("calories_per_gram") : map.get("caloriesPerGram")));
            normalized.put("estimatedGrams", numberValue(map.get("estimated_grams") != null ? map.get("estimated_grams") : map.get("estimatedGrams")));
            items.add(normalized);
        }
        return items;
    }

    private Object firstPresent(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key) && map.get(key) != null) {
                return map.get(key);
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean boolValue(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value == null) {
            return false;
        }
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    private double numberValue(Object value) {
        return numberValue(value, 0.0);
    }

    private double numberValue(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return (int) Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
