package com.diabetes.checkin.service;

import com.diabetes.checkin.dto.FoodCheckinRequest;
import com.diabetes.checkin.entity.CheckinDietDetail;
import com.diabetes.checkin.entity.FoodPreset;
import com.diabetes.checkin.mapper.CheckinDietDetailMapper;
import com.diabetes.checkin.mapper.PresetMapper;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.storage.MinioStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FoodCheckinService {

    private final CheckinRecordWriter recordWriter;
    private final CheckinDietDetailMapper dietDetailMapper;
    private final PresetMapper presetMapper;
    private final MinioStorageService minioStorageService;

    public FoodCheckinService(CheckinRecordWriter recordWriter,
                              CheckinDietDetailMapper dietDetailMapper,
                              PresetMapper presetMapper,
                              MinioStorageService minioStorageService) {
        this.recordWriter = recordWriter;
        this.dietDetailMapper = dietDetailMapper;
        this.presetMapper = presetMapper;
        this.minioStorageService = minioStorageService;
    }

    @Transactional
    public Map<String, Object> createCheckin(String userId, FoodCheckinRequest request) {
        LocalDate date = parseDate(request.getCheckinDate());

        CheckinDietDetail detail = new CheckinDietDetail();
        detail.setMealPeriod(request.getMealPeriod());
        detail.setSourceType(request.getSourceType());
        detail.setInputUnit(request.getInputUnit());
        detail.setInputAmount(request.getInputAmount());

        if (request.getSourceType() == CheckinConstants.SOURCE_PRESET) {
            fillFromPreset(request, detail);
        } else if (request.getSourceType() == CheckinConstants.SOURCE_CUSTOM) {
            fillFromCustom(request, detail);
        } else {
            throw new BusinessException(400, "sourceType 无效");
        }

        if (detail.getImageObjectKey() == null || detail.getImageObjectKey().isBlank()) {
            throw new BusinessException(400, "imageObjectKey 必填");
        }
        validateImageObjectKey(userId, detail.getImageObjectKey(), request.getSourceType(), "food");
        detail.setImageObjectKey(CheckinImagePathHelper.normalize(detail.getImageObjectKey()));

        calculateNutrition(detail, request.getInputUnit(), request.getInputAmount(),
                resolveMlToGRatio(request, detail));

        String checkinId = recordWriter.createRecord(userId, CheckinConstants.TYPE_DIET, date);
        detail.setCheckinId(checkinId);
        dietDetailMapper.insert(detail);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checkinId", checkinId);
        result.put("checkinDate", date.toString());
        result.put("totalCalories", detail.getTotalCalories());
        return result;
    }

    public List<Map<String, Object>> listRecords(String userId, String dateStr) {
        LocalDate date = parseDate(dateStr);
        return dietDetailMapper.findByUserAndDate(userId, date).stream()
                .map(this::toRecordMap)
                .toList();
    }

    private void fillFromPreset(FoodCheckinRequest request, CheckinDietDetail detail) {
        if (request.getFoodId() == null || request.getFoodId().isBlank()) {
            throw new BusinessException(400, "选用预设食物时 foodId 必填");
        }
        FoodPreset preset = presetMapper.findFoodPresetById(request.getFoodId());
        if (preset == null) {
            throw new BusinessException(404, "预设食物不存在");
        }
        detail.setFoodId(preset.getFoodId());
        detail.setFoodName(preset.getFoodName());
        detail.setCaloriesPerGram(preset.getCaloriesPerGram());
        detail.setCategoryName(presetMapper.findCategoryNameById(preset.getCategoryId()));
        if (request.getImageObjectKey() != null && !request.getImageObjectKey().isBlank()) {
            detail.setImageObjectKey(request.getImageObjectKey());
        } else {
            detail.setImageObjectKey("food/" + preset.getFoodId() + ".jpg");
        }
    }

    private void fillFromCustom(FoodCheckinRequest request, CheckinDietDetail detail) {
        if (request.getFoodName() == null || request.getFoodName().isBlank()) {
            throw new BusinessException(400, "自定义食物时 foodName 必填");
        }
        if (request.getCategoryId() == null || request.getCategoryId().isBlank()) {
            throw new BusinessException(400, "自定义食物时 categoryId 必填");
        }
        String categoryName = presetMapper.findCategoryNameById(request.getCategoryId());
        if (categoryName == null) {
            throw new BusinessException(404, "食物分类不存在");
        }
        if (request.getCaloriesPerGram() == null || request.getCaloriesPerGram().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "自定义食物时 caloriesPerGram 必填且大于 0");
        }
        detail.setFoodName(request.getFoodName().trim());
        detail.setCategoryName(categoryName);
        detail.setCaloriesPerGram(request.getCaloriesPerGram());
        detail.setImageObjectKey(request.getImageObjectKey());
    }

    private BigDecimal resolveMlToGRatio(FoodCheckinRequest request, CheckinDietDetail detail) {
        if (request.getInputUnit() != CheckinConstants.INPUT_UNIT_ML) {
            return BigDecimal.ONE;
        }
        if (request.getSourceType() == CheckinConstants.SOURCE_PRESET) {
            FoodPreset preset = presetMapper.findFoodPresetById(detail.getFoodId());
            return preset != null && preset.getMlToGRatio() != null ? preset.getMlToGRatio() : BigDecimal.ONE;
        }
        return request.getMlToGRatio() != null ? request.getMlToGRatio() : BigDecimal.ONE;
    }

    private void calculateNutrition(CheckinDietDetail detail, Integer inputUnit, BigDecimal inputAmount,
                                    BigDecimal mlToGRatio) {
        BigDecimal grams;
        if (inputUnit == CheckinConstants.INPUT_UNIT_G) {
            grams = inputAmount;
        } else if (inputUnit == CheckinConstants.INPUT_UNIT_ML) {
            grams = inputAmount.multiply(mlToGRatio).setScale(2, RoundingMode.HALF_UP);
        } else {
            throw new BusinessException(400, "inputUnit 无效");
        }
        detail.setGrams(grams);
        int totalCalories = detail.getCaloriesPerGram()
                .multiply(grams)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
        detail.setTotalCalories(totalCalories);
    }

    private Map<String, Object> toRecordMap(CheckinDietDetail detail) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("checkinId", detail.getCheckinId());
        map.put("mealPeriod", detail.getMealPeriod());
        map.put("mealPeriodLabel", CheckinConstants.mealPeriodLabel(detail.getMealPeriod()));
        map.put("sourceType", detail.getSourceType());
        map.put("foodId", detail.getFoodId());
        map.put("foodName", detail.getFoodName());
        map.put("categoryName", detail.getCategoryName());
        map.put("caloriesPerGram", detail.getCaloriesPerGram());
        map.put("inputUnit", detail.getInputUnit());
        map.put("inputAmount", detail.getInputAmount());
        map.put("grams", detail.getGrams());
        map.put("totalCalories", detail.getTotalCalories());
        map.put("imageObjectKey", detail.getImageObjectKey());
        map.put("imageUrl", minioStorageService.buildCheckinImageUrl(detail.getImageObjectKey()));
        map.put("recordTime", detail.getRecordTime() != null ? detail.getRecordTime().toString() : null);
        return map;
    }

    private void validateImageObjectKey(String userId, String key, int sourceType, String folder) {
        String normalized = CheckinImagePathHelper.normalize(key);
        if (sourceType == CheckinConstants.SOURCE_PRESET) {
            if (!CheckinImagePathHelper.isValidPresetKey(normalized, folder)) {
                throw new BusinessException(400, "imageObjectKey 格式无效，预设应为 " + folder + "/{ID}.jpg");
            }
        } else if (!CheckinImagePathHelper.isValidUserUploadKey(normalized, folder, userId)) {
            throw new BusinessException(400, "imageObjectKey 格式无效，用户上传应为 " + folder + "/{userId}/upload_*.jpg");
        }
    }

    private LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, "日期格式错误，应为 yyyy-MM-dd");
        }
    }
}
