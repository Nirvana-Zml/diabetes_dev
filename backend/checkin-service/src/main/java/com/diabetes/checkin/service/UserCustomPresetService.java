package com.diabetes.checkin.service;

import com.diabetes.checkin.dto.FoodCheckinRequest;
import com.diabetes.checkin.dto.MedicationCheckinRequest;
import com.diabetes.checkin.entity.UserFoodPreset;
import com.diabetes.checkin.entity.UserMedicationPreset;
import com.diabetes.checkin.mapper.UserCustomPresetMapper;
import com.diabetes.common.util.IdGenerator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class UserCustomPresetService {

    static final String CUSTOM_CATEGORY_ID = "cat_custom";
    static final String CUSTOM_CATEGORY_NAME = "自定义";

    private final UserCustomPresetMapper userCustomPresetMapper;

    public UserCustomPresetService(UserCustomPresetMapper userCustomPresetMapper) {
        this.userCustomPresetMapper = userCustomPresetMapper;
    }

    public void saveFoodTemplate(String userId, FoodCheckinRequest request) {
        UserFoodPreset preset = new UserFoodPreset();
        preset.setUserId(userId);
        preset.setCategoryId(request.getCategoryId());
        preset.setFoodName(request.getFoodName().trim());
        preset.setCaloriesPerGram(request.getCaloriesPerGram());
        preset.setIsLiquid(resolveIsLiquid(request) ? 1 : 0);
        preset.setMlToGRatio(resolveMlToGRatio(request));
        preset.setImageObjectKey(CheckinImagePathHelper.normalize(request.getImageObjectKey()));

        UserFoodPreset existing = userCustomPresetMapper.findUserFoodByName(userId, preset.getFoodName());
        if (existing != null) {
            preset.setFoodId(existing.getFoodId());
            userCustomPresetMapper.updateUserFood(preset);
        } else {
            preset.setFoodId(IdGenerator.nextId("uf_"));
            userCustomPresetMapper.insertUserFood(preset);
        }
    }

    public void saveMedicationTemplate(String userId, MedicationCheckinRequest request) {
        UserMedicationPreset preset = new UserMedicationPreset();
        preset.setUserId(userId);
        preset.setDrugName(request.getDrugName().trim());
        preset.setDefaultDosage(request.getDosage().trim());
        preset.setImageObjectKey(CheckinImagePathHelper.normalize(request.getImageObjectKey()));

        UserMedicationPreset existing = userCustomPresetMapper.findUserMedicationByName(userId, preset.getDrugName());
        if (existing != null) {
            preset.setDrugId(existing.getDrugId());
            userCustomPresetMapper.updateUserMedication(preset);
        } else {
            preset.setDrugId(IdGenerator.nextId("ud_"));
            userCustomPresetMapper.insertUserMedication(preset);
        }
    }

    private static boolean resolveIsLiquid(FoodCheckinRequest request) {
        if (Boolean.TRUE.equals(request.getIsLiquid())) {
            return true;
        }
        return request.getInputUnit() != null && request.getInputUnit() == CheckinConstants.INPUT_UNIT_ML;
    }

    private static BigDecimal resolveMlToGRatio(FoodCheckinRequest request) {
        if (request.getMlToGRatio() != null) {
            return request.getMlToGRatio();
        }
        return BigDecimal.ONE;
    }
}
