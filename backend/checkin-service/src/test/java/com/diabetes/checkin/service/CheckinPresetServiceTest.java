package com.diabetes.checkin.service;

import com.diabetes.checkin.entity.ExercisePreset;
import com.diabetes.checkin.entity.FoodCategory;
import com.diabetes.checkin.entity.FoodPreset;
import com.diabetes.checkin.entity.MedicationPreset;
import com.diabetes.checkin.entity.UserFoodPreset;
import com.diabetes.checkin.entity.UserMedicationPreset;
import com.diabetes.checkin.mapper.PresetMapper;
import com.diabetes.checkin.mapper.UserCustomPresetMapper;
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
        UserCustomPresetMapper userMapper = mock(UserCustomPresetMapper.class);
        MinioStorageService minio = mock(MinioStorageService.class);
        CheckinPresetService service = new CheckinPresetService(mapper, userMapper, minio);

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

        UserFoodPreset userFood = new UserFoodPreset();
        userFood.setFoodId("uf_1");
        userFood.setCategoryId("cat_1");
        userFood.setFoodName("我的面包");
        userFood.setCaloriesPerGram(new BigDecimal("2.5"));
        userFood.setIsLiquid(0);
        userFood.setMlToGRatio(new BigDecimal("1.0"));
        userFood.setImageObjectKey("food/u1/upload.jpg");

        UserMedicationPreset userDrug = new UserMedicationPreset();
        userDrug.setDrugId("ud_1");
        userDrug.setDrugName("我的药");
        userDrug.setDefaultDosage("1片");
        userDrug.setImageObjectKey("medical/u1/upload.jpg");

        when(mapper.findAllFoodCategories()).thenReturn(List.of(category));
        when(mapper.findFoodPresets("cat_1")).thenReturn(List.of(liquidFood, solidFood, nonLiquidFood));
        when(userMapper.findUserFoodPresets("u1")).thenReturn(List.of(userFood));
        when(mapper.findAllMedicationPresets()).thenReturn(List.of(drug));
        when(userMapper.findUserMedicationPresets("u1")).thenReturn(List.of(userDrug));
        when(mapper.findAllExercisePresets()).thenReturn(List.of(exercise));
        when(minio.buildCheckinImageUrl(anyString())).thenAnswer(invocation -> "url:" + invocation.getArgument(0));

        List<Map<String, Object>> categories = service.listFoodCategories("u1");
        assertEquals("主食", categories.get(0).get("categoryName"));
        assertEquals(UserCustomPresetService.CUSTOM_CATEGORY_NAME, categories.get(1).get("categoryName"));

        List<Map<String, Object>> foods = service.listFoodPresets("u1", "cat_1");
        assertTrue((Boolean) foods.get(0).get("isLiquid"));
        assertFalse((Boolean) foods.get(1).get("isLiquid"));
        assertFalse((Boolean) foods.get(2).get("isLiquid"));
        assertEquals("url:food/food_1.jpg", foods.get(0).get("imageUrl"));

        List<Map<String, Object>> customFoods = service.listFoodPresets("u1", UserCustomPresetService.CUSTOM_CATEGORY_ID);
        assertEquals(1, customFoods.size());
        assertTrue((Boolean) customFoods.get(0).get("isUserCustom"));
        assertEquals("cat_1", customFoods.get(0).get("originalCategoryId"));

        List<Map<String, Object>> meds = service.listMedicationPresets("u1");
        assertEquals(2, meds.size());
        assertFalse((Boolean) meds.get(0).get("isUserCustom"));
        assertTrue((Boolean) meds.get(1).get("isUserCustom"));
        assertEquals("1片", meds.get(1).get("defaultDosage"));

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
