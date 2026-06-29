package com.diabetes.checkin.mapper;

import com.diabetes.checkin.entity.*;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface PresetMapper {

    List<FoodCategory> findAllFoodCategories();

    List<FoodPreset> findFoodPresets(@Param("categoryId") String categoryId);

    FoodPreset findFoodPresetById(@Param("foodId") String foodId);

    String findCategoryNameById(@Param("categoryId") String categoryId);

    List<MedicationPreset> findAllMedicationPresets();

    MedicationPreset findMedicationPresetById(@Param("drugId") String drugId);

    List<ExercisePreset> findAllExercisePresets();

    ExercisePreset findExercisePresetById(@Param("exerciseId") String exerciseId);
}
