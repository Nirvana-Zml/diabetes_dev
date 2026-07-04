package com.diabetes.checkin.service;

import com.diabetes.checkin.entity.*;
import com.diabetes.checkin.mapper.PresetMapper;
import com.diabetes.checkin.mapper.UserCustomPresetMapper;
import com.diabetes.common.storage.MinioStorageService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CheckinPresetService {

    private final PresetMapper presetMapper;
    private final UserCustomPresetMapper userCustomPresetMapper;
    private final MinioStorageService minioStorageService;

    public CheckinPresetService(PresetMapper presetMapper,
                                UserCustomPresetMapper userCustomPresetMapper,
                                MinioStorageService minioStorageService) {
        this.presetMapper = presetMapper;
        this.userCustomPresetMapper = userCustomPresetMapper;
        this.minioStorageService = minioStorageService;
    }

    public List<Map<String, Object>> listFoodCategories(String userId) {
        List<Map<String, Object>> categories = new ArrayList<>(
                presetMapper.findAllFoodCategories().stream()
                        .map(this::toCategoryMap)
                        .toList());
        categories.add(customCategoryMap());
        return categories;
    }

    public List<Map<String, Object>> listFoodPresets(String userId, String categoryId) {
        if (UserCustomPresetService.CUSTOM_CATEGORY_ID.equals(categoryId)) {
            return userCustomPresetMapper.findUserFoodPresets(userId).stream()
                    .map(this::toUserFoodPresetMap)
                    .toList();
        }
        return presetMapper.findFoodPresets(categoryId).stream()
                .map(this::toFoodPresetMap)
                .toList();
    }

    public List<Map<String, Object>> listMedicationPresets(String userId) {
        List<Map<String, Object>> presets = new ArrayList<>(
                presetMapper.findAllMedicationPresets().stream()
                        .map(this::toMedicationPresetMap)
                        .toList());
        userCustomPresetMapper.findUserMedicationPresets(userId).stream()
                .map(this::toUserMedicationPresetMap)
                .forEach(presets::add);
        return presets;
    }

    public List<Map<String, Object>> listExercisePresets() {
        return presetMapper.findAllExercisePresets().stream()
                .map(this::toExercisePresetMap)
                .toList();
    }

    private Map<String, Object> customCategoryMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("categoryId", UserCustomPresetService.CUSTOM_CATEGORY_ID);
        map.put("categoryName", UserCustomPresetService.CUSTOM_CATEGORY_NAME);
        return map;
    }

    private Map<String, Object> toCategoryMap(FoodCategory category) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("categoryId", category.getCategoryId());
        map.put("categoryName", category.getCategoryName());
        return map;
    }

    private Map<String, Object> toFoodPresetMap(FoodPreset preset) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("foodId", preset.getFoodId());
        map.put("categoryId", preset.getCategoryId());
        map.put("foodName", preset.getFoodName());
        map.put("caloriesPerGram", preset.getCaloriesPerGram());
        map.put("isLiquid", preset.getIsLiquid() != null && preset.getIsLiquid() == 1);
        map.put("mlToGRatio", preset.getMlToGRatio());
        map.put("imageObjectKey", "food/" + preset.getFoodId() + ".jpg");
        map.put("imageUrl", minioStorageService.buildCheckinImageUrl("food/" + preset.getFoodId() + ".jpg"));
        map.put("isUserCustom", false);
        return map;
    }

    private Map<String, Object> toUserFoodPresetMap(UserFoodPreset preset) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("foodId", preset.getFoodId());
        map.put("categoryId", UserCustomPresetService.CUSTOM_CATEGORY_ID);
        map.put("originalCategoryId", preset.getCategoryId());
        map.put("foodName", preset.getFoodName());
        map.put("caloriesPerGram", preset.getCaloriesPerGram());
        map.put("isLiquid", preset.getIsLiquid() != null && preset.getIsLiquid() == 1);
        map.put("mlToGRatio", preset.getMlToGRatio());
        map.put("imageObjectKey", preset.getImageObjectKey());
        map.put("imageUrl", minioStorageService.buildCheckinImageUrl(preset.getImageObjectKey()));
        map.put("isUserCustom", true);
        return map;
    }

    private Map<String, Object> toMedicationPresetMap(MedicationPreset preset) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("drugId", preset.getDrugId());
        map.put("drugName", preset.getDrugName());
        map.put("imageObjectKey", "medical/" + preset.getDrugId() + ".jpg");
        map.put("imageUrl", minioStorageService.buildCheckinImageUrl("medical/" + preset.getDrugId() + ".jpg"));
        map.put("isUserCustom", false);
        return map;
    }

    private Map<String, Object> toUserMedicationPresetMap(UserMedicationPreset preset) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("drugId", preset.getDrugId());
        map.put("drugName", preset.getDrugName());
        map.put("defaultDosage", preset.getDefaultDosage());
        map.put("imageObjectKey", preset.getImageObjectKey());
        map.put("imageUrl", minioStorageService.buildCheckinImageUrl(preset.getImageObjectKey()));
        map.put("isUserCustom", true);
        return map;
    }

    private Map<String, Object> toExercisePresetMap(ExercisePreset preset) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("exerciseId", preset.getExerciseId());
        map.put("exerciseName", preset.getExerciseName());
        map.put("caloriesPerMinute", preset.getCaloriesPerMinute());
        return map;
    }
}
