package com.diabetes.checkin.service;

import com.diabetes.checkin.entity.ExercisePreset;
import com.diabetes.checkin.entity.FoodCategory;
import com.diabetes.checkin.entity.FoodPreset;
import com.diabetes.checkin.entity.MedicationPreset;
import com.diabetes.checkin.mapper.PresetMapper;
import com.diabetes.common.storage.MinioStorageService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckinPresetServiceTest {

    @Test
    void listsPresetDataWithImageUrls() {
        PresetMapper mapper = mock(PresetMapper.class);
        MinioStorageService minio = mock(MinioStorageService.class);
        CheckinPresetService service = new CheckinPresetService(mapper, minio);

        FoodCategory category = new FoodCategory();
        category.setCategoryId("cat_1");
        category.setCategoryName("主食");
        FoodPreset liquidFood = food("food_1", 1);
        FoodPreset solidFood = food("food_2", null);
        FoodPreset nonLiquidFood = food("food_3", 0);
        MedicationPreset drug = new MedicationPreset();
        drug.setDrugId("drug_1");
        drug.setDrugName("胰岛素");
        ExercisePreset exercise = new ExercisePreset();
        exercise.setExerciseId("ex_1");
        exercise.setExerciseName("骑行");
        exercise.setCaloriesPerMinute(new BigDecimal("4.1"));

        when(mapper.findAllFoodCategories()).thenReturn(List.of(category));
        when(mapper.findFoodPresets("cat_1")).thenReturn(List.of(liquidFood, solidFood, nonLiquidFood));
        when(mapper.findAllMedicationPresets()).thenReturn(List.of(drug));
        when(mapper.findAllExercisePresets()).thenReturn(List.of(exercise));
        when(minio.buildCheckinImageUrl(anyString())).thenAnswer(invocation -> "url:" + invocation.getArgument(0));

        assertEquals("主食", service.listFoodCategories().get(0).get("categoryName"));
        List<Map<String, Object>> foods = service.listFoodPresets("cat_1");
        assertTrue((Boolean) foods.get(0).get("isLiquid"));
        assertFalse((Boolean) foods.get(1).get("isLiquid"));
        assertFalse((Boolean) foods.get(2).get("isLiquid"));
        assertEquals("url:food/food_1.jpg", foods.get(0).get("imageUrl"));
        assertEquals("url:medical/drug_1.jpg", service.listMedicationPresets().get(0).get("imageUrl"));
        assertEquals(new BigDecimal("4.1"), service.listExercisePresets().get(0).get("caloriesPerMinute"));
    }

    private static FoodPreset food(String id, Integer liquid) {
        FoodPreset preset = new FoodPreset();
        preset.setFoodId(id);
        preset.setCategoryId("cat_1");
        preset.setFoodName("食物");
        preset.setCaloriesPerGram(new BigDecimal("1.2"));
        preset.setIsLiquid(liquid);
        preset.setMlToGRatio(new BigDecimal("1.0"));
        return preset;
    }
}
