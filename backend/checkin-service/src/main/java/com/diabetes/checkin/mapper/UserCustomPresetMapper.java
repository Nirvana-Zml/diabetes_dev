package com.diabetes.checkin.mapper;

import com.diabetes.checkin.entity.UserFoodPreset;
import com.diabetes.checkin.entity.UserMedicationPreset;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserCustomPresetMapper {

    List<UserFoodPreset> findUserFoodPresets(@Param("userId") String userId);

    UserFoodPreset findUserFoodByName(@Param("userId") String userId, @Param("foodName") String foodName);

    void insertUserFood(UserFoodPreset preset);

    void updateUserFood(UserFoodPreset preset);

    List<UserMedicationPreset> findUserMedicationPresets(@Param("userId") String userId);

    UserMedicationPreset findUserMedicationByName(@Param("userId") String userId, @Param("drugName") String drugName);

    void insertUserMedication(UserMedicationPreset preset);

    void updateUserMedication(UserMedicationPreset preset);
}
