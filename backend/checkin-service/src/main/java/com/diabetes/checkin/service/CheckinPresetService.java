package com.diabetes.checkin.service;

import com.diabetes.checkin.entity.FoodCategory;
import com.diabetes.checkin.entity.FoodPreset;
import com.diabetes.checkin.entity.MedicationPreset;
import com.diabetes.checkin.entity.ExercisePreset;
import com.diabetes.checkin.mapper.PresetMapper;
import com.diabetes.common.storage.MinioStorageService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CheckinPresetService {

    private final PresetMapper presetMapper;
    private final MinioStorageService minioStorageService;

    public CheckinPresetService(PresetMapper presetMapper, MinioStorageService minioStorageService) {
        this.presetMapper = presetMapper;
        this.minioStorageService = minioStorageService;
    }

    public List<Map<String, Object>> listFoodCategories() {
        return presetMapper.findAllFoodCategories().stream()
                .map(this::toCategoryMap)
                .toList();
    }

    public List<Map<String, Object>> listFoodPresets(String categoryId) {
        return presetMapper.findFoodPresets(categoryId).stream()
                .map(this::toFoodPresetMap)
                .toList();
    }

    public List<Map<String, Object>> listMedicationPresets() {
        return presetMapper.findAllMedicationPresets().stream()
                .map(this::toMedicationPresetMap)
                .toList();
    }

    public List<Map<String, Object>> listExercisePresets() {
        return presetMapper.findAllExercisePresets().stream()
                .map(this::toExercisePresetMap)
                .toList();
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
        return map;
    }

    private Map<String, Object> toMedicationPresetMap(MedicationPreset preset) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("drugId", preset.getDrugId());
        map.put("drugName", preset.getDrugName());
        map.put("imageObjectKey", "medical/" + preset.getDrugId() + ".jpg");
        map.put("imageUrl", minioStorageService.buildCheckinImageUrl("medical/" + preset.getDrugId() + ".jpg"));
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
