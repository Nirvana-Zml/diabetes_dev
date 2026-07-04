package com.diabetes.checkin.service;

import com.diabetes.checkin.dto.FoodRecognitionRequest;
import com.diabetes.checkin.entity.FoodCategory;
import com.diabetes.checkin.entity.FoodPreset;
import com.diabetes.checkin.mapper.PresetMapper;
import com.diabetes.common.dify.DifyClient;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.storage.MinioStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FoodRecognitionServiceTest {

    private final PresetMapper presetMapper = mock(PresetMapper.class);
    private final DifyClient difyClient = mock(DifyClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MinioStorageService minio = mock(MinioStorageService.class);

    @Test
    void recognize_parsesWorkflowOutput() throws Exception {
        FoodRecognitionService service = new FoodRecognitionService(
                presetMapper, difyClient, objectMapper, minio,
                "http://dify", "app-key", "blocking", "https://ngrok.example.com");

        FoodCategory category = new FoodCategory();
        category.setCategoryId("cat_grain");
        category.setCategoryName("主食");
        FoodPreset preset = new FoodPreset();
        preset.setFoodId("food_rice_001");
        preset.setCategoryId("cat_grain");
        preset.setFoodName("糙米饭");
        preset.setCaloriesPerGram(new BigDecimal("1.16"));
        preset.setIsLiquid(0);
        preset.setMlToGRatio(BigDecimal.ONE);

        when(presetMapper.findAllFoodCategories()).thenReturn(List.of(category));
        when(presetMapper.findAllFoodPresets(200)).thenReturn(List.of(preset));
        when(minio.buildCheckinImageUrl("food/u1/upload_abc.jpg", "https://ngrok.example.com"))
                .thenReturn("https://ngrok.example.com/checkin/food/u1/upload_abc.jpg");

        ObjectNode recognition = objectMapper.createObjectNode();
        recognition.put("food_name", "糙米饭");
        recognition.put("category_id", "cat_grain");
        recognition.put("calories_per_gram", 1.16);
        recognition.put("is_liquid", false);
        recognition.put("suggested_input_amount", 150);
        recognition.put("matched_food_id", "food_rice_001");
        recognition.put("source_type", 1);
        recognition.put("has_error", false);

        ObjectNode outputs = objectMapper.createObjectNode();
        outputs.set("food_recognition", recognition);
        ObjectNode data = objectMapper.createObjectNode();
        data.put("status", "succeeded");
        data.set("outputs", outputs);
        ObjectNode response = objectMapper.createObjectNode();
        response.set("data", data);

        when(difyClient.runWorkflowBlocking(eq("app-key"), eq("u1"), anyMap(), eq("blocking")))
                .thenReturn(response);

        FoodRecognitionRequest request = new FoodRecognitionRequest();
        request.setImageObjectKey("food/u1/upload_abc.jpg");
        request.setMealPeriod(2);

        Map<String, Object> result = service.recognize("u1", request);
        assertEquals("糙米饭", result.get("foodName"));
        assertEquals("food_rice_001", result.get("matchedFoodId"));
        assertEquals(1, result.get("sourceType"));
        assertFalse((Boolean) result.get("hasError"));
    }

    @Test
    void recognize_parsesArrayWorkflowOutput() throws Exception {
        FoodRecognitionService service = new FoodRecognitionService(
                presetMapper, difyClient, objectMapper, minio,
                "http://dify", "app-key", "blocking", "https://ngrok.example.com");

        when(presetMapper.findAllFoodCategories()).thenReturn(List.of());
        when(presetMapper.findAllFoodPresets(200)).thenReturn(List.of());
        when(minio.buildCheckinImageUrl("food/u1/upload_abc.jpg", "https://ngrok.example.com"))
                .thenReturn("https://ngrok.example.com/checkin/food/u1/upload_abc.jpg");

        ObjectNode recognition = objectMapper.createObjectNode();
        recognition.put("food_name", "香蕉");
        recognition.put("category_id", "cat_fruit");
        recognition.put("calories_per_gram", 0.89);
        recognition.put("is_liquid", false);
        recognition.put("suggested_input_amount", 150);
        recognition.put("source_type", 2);
        recognition.put("has_error", false);

        ObjectNode outputs = objectMapper.createObjectNode();
        outputs.set("food_recognition", objectMapper.createArrayNode().add(recognition));
        ObjectNode data = objectMapper.createObjectNode();
        data.put("status", "succeeded");
        data.set("outputs", outputs);
        ObjectNode response = objectMapper.createObjectNode();
        response.set("data", data);

        when(difyClient.runWorkflowBlocking(eq("app-key"), eq("u1"), anyMap(), eq("blocking")))
                .thenReturn(response);

        FoodRecognitionRequest request = new FoodRecognitionRequest();
        request.setImageObjectKey("food/u1/upload_abc.jpg");
        request.setMealPeriod(2);

        Map<String, Object> result = service.recognize("u1", request);
        assertEquals("香蕉", result.get("foodName"));
        assertEquals("cat_fruit", result.get("categoryId"));
        assertEquals(150.0, result.get("suggestedInputAmount"));
        assertEquals(2, result.get("sourceType"));
    }

    @Test
    void recognize_requiresApiKey() {
        FoodRecognitionService service = new FoodRecognitionService(
                presetMapper, difyClient, objectMapper, minio,
                "http://dify", " ", "blocking", "");
        FoodRecognitionRequest request = new FoodRecognitionRequest();
        request.setImageObjectKey("food/u1/upload_abc.jpg");
        assertThrows(BusinessException.class, () -> service.recognize("u1", request));
    }
}
