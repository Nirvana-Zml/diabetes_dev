package com.diabetes.checkin.controller;

import com.diabetes.checkin.dto.ExerciseCheckinRequest;
import com.diabetes.checkin.dto.FoodCheckinRequest;
import com.diabetes.checkin.dto.GlucoseCheckinRequest;
import com.diabetes.checkin.dto.ImageUploadResponse;
import com.diabetes.checkin.dto.MedicationCheckinRequest;
import com.diabetes.checkin.service.*;
import com.diabetes.common.api.ApiResponse;
import com.diabetes.common.auth.CurrentUserId;
import com.diabetes.common.exception.BusinessException;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * 健康打卡模块 API（食物 / 用药 / 运动），见 docs/健康打卡模块产品设计说明书.md §8
 */
@RestController
@RequestMapping("/api/v1/checkin")
public class CheckinModuleController {

    private final CheckinImageService checkinImageService;
    private final CheckinPresetService checkinPresetService;
    private final FoodCheckinService foodCheckinService;
    private final MedicationCheckinService medicationCheckinService;
    private final ExerciseCheckinService exerciseCheckinService;
    private final GlucoseCheckinService glucoseCheckinService;

    public CheckinModuleController(CheckinImageService checkinImageService,
                                   CheckinPresetService checkinPresetService,
                                   FoodCheckinService foodCheckinService,
                                   MedicationCheckinService medicationCheckinService,
                                   ExerciseCheckinService exerciseCheckinService,
                                   GlucoseCheckinService glucoseCheckinService) {
        this.checkinImageService = checkinImageService;
        this.checkinPresetService = checkinPresetService;
        this.foodCheckinService = foodCheckinService;
        this.medicationCheckinService = medicationCheckinService;
        this.exerciseCheckinService = exerciseCheckinService;
        this.glucoseCheckinService = glucoseCheckinService;
    }

    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImageUploadResponse> uploadImage(@CurrentUserId String userId,
                                                        @RequestParam String type,
                                                        @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(checkinImageService.upload(userId, type, file));
    }

    @GetMapping("/food/categories")
    public ApiResponse<List<Map<String, Object>>> foodCategories() {
        return ApiResponse.ok(checkinPresetService.listFoodCategories());
    }

    @GetMapping("/food/presets")
    public ApiResponse<List<Map<String, Object>>> foodPresets(@RequestParam(required = false) String categoryId) {
        return ApiResponse.ok(checkinPresetService.listFoodPresets(categoryId));
    }

    @PostMapping("/food")
    public ApiResponse<Map<String, Object>> createFoodCheckin(@CurrentUserId String userId,
                                                              @Valid @RequestBody FoodCheckinRequest request) {
        return ApiResponse.ok(foodCheckinService.createCheckin(userId, request));
    }

    @GetMapping("/food/records")
    public ApiResponse<List<Map<String, Object>>> foodRecords(@CurrentUserId String userId,
                                                              @RequestParam String date) {
        return ApiResponse.ok(foodCheckinService.listRecords(userId, date));
    }

    @GetMapping("/medication/presets")
    public ApiResponse<List<Map<String, Object>>> medicationPresets() {
        return ApiResponse.ok(checkinPresetService.listMedicationPresets());
    }

    @PostMapping("/medication")
    public ApiResponse<Map<String, Object>> createMedicationCheckin(@CurrentUserId String userId,
                                                                    @Valid @RequestBody MedicationCheckinRequest request) {
        return ApiResponse.ok(medicationCheckinService.createCheckin(userId, request));
    }

    @GetMapping("/medication/records")
    public ApiResponse<List<Map<String, Object>>> medicationRecords(@CurrentUserId String userId,
                                                                     @RequestParam String date) {
        return ApiResponse.ok(medicationCheckinService.listRecords(userId, date));
    }

    @GetMapping("/exercise/presets")
    public ApiResponse<List<Map<String, Object>>> exercisePresets() {
        return ApiResponse.ok(checkinPresetService.listExercisePresets());
    }

    @PostMapping("/exercise")
    public ApiResponse<Map<String, Object>> createExerciseCheckin(@CurrentUserId String userId,
                                                                  @Valid @RequestBody ExerciseCheckinRequest request) {
        return ApiResponse.ok(exerciseCheckinService.createCheckin(userId, request));
    }

    @GetMapping("/exercise/records")
    public ApiResponse<List<Map<String, Object>>> exerciseRecords(@CurrentUserId String userId,
                                                                   @RequestParam String date) {
        return ApiResponse.ok(exerciseCheckinService.listRecords(userId, date));
    }

    @PostMapping("/glucose")
    public ApiResponse<Map<String, Object>> createGlucoseCheckin(@CurrentUserId String userId,
                                                                @Valid @RequestBody GlucoseCheckinRequest request) {
        return ApiResponse.ok(glucoseCheckinService.createCheckin(userId, request));
    }

    @GetMapping("/glucose/records")
    public ApiResponse<List<Map<String, Object>>> glucoseRecords(@CurrentUserId String userId,
                                                                 @RequestParam String date) {
        return ApiResponse.ok(glucoseCheckinService.listRecords(userId, date));
    }

    @GetMapping("/glucose/history")
    public ApiResponse<Map<String, Object>> glucoseHistory(@CurrentUserId String userId,
                                                          @RequestParam(required = false) String startDate,
                                                          @RequestParam(required = false) String endDate,
                                                          @RequestParam(defaultValue = "14") int days) {
        LocalDate end = parseQueryDate(endDate, LocalDate.now());
        LocalDate start = (startDate != null && !startDate.isBlank())
                ? parseQueryDate(startDate, end)
                : end.minusDays(Math.max(days, 1) - 1L);
        return ApiResponse.ok(glucoseCheckinService.getHistory(userId, start, end));
    }

    private LocalDate parseQueryDate(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, "日期格式错误，应为 yyyy-MM-dd");
        }
    }
}
