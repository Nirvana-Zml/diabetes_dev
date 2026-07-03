package com.diabetes.checkin.service;

import com.diabetes.checkin.dto.ExerciseCheckinRequest;
import com.diabetes.checkin.dto.FoodCheckinRequest;
import com.diabetes.checkin.dto.GlucoseCheckinRequest;
import com.diabetes.checkin.dto.ImageUploadResponse;
import com.diabetes.checkin.dto.MedicationCheckinRequest;
import com.diabetes.checkin.entity.CheckinDietDetail;
import com.diabetes.checkin.entity.CheckinExerciseDetail;
import com.diabetes.checkin.entity.CheckinGlucoseDetail;
import com.diabetes.checkin.entity.CheckinMedicationDetail;
import com.diabetes.checkin.entity.CheckinRecord;
import com.diabetes.checkin.entity.ExercisePreset;
import com.diabetes.checkin.entity.FoodPreset;
import com.diabetes.checkin.entity.MedicationPreset;
import com.diabetes.checkin.mapper.CheckinDietDetailMapper;
import com.diabetes.checkin.mapper.CheckinExerciseDetailMapper;
import com.diabetes.checkin.mapper.CheckinGlucoseDetailMapper;
import com.diabetes.checkin.mapper.CheckinMedicationDetailMapper;
import com.diabetes.checkin.mapper.CheckinRecordMapper;
import com.diabetes.checkin.mapper.PresetMapper;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.storage.MinioStorageService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CheckinModuleServicesTest {

    private final CheckinRecordWriter writer = mock(CheckinRecordWriter.class);
    private final PresetMapper presetMapper = mock(PresetMapper.class);
    private final MinioStorageService minio = mock(MinioStorageService.class);

    @Test
    void foodCheckin_supportsPresetAndCustomRecords() {
        CheckinDietDetailMapper detailMapper = mock(CheckinDietDetailMapper.class);
        FoodCheckinService service = new FoodCheckinService(writer, detailMapper, presetMapper, minio);
        when(writer.createRecord(anyString(), eq(CheckinConstants.TYPE_DIET), any(), any())).thenReturn("chk_food");
        when(presetMapper.findFoodPresetById("food_1")).thenReturn(foodPreset("food_1", "cat_1", "米饭", "1.16", "0.95"));
        when(presetMapper.findFoodPresetById("food_2")).thenReturn(foodPreset("food_2", "cat_1", "米饭", "1.16", null), null);
        when(presetMapper.findCategoryNameById("cat_1")).thenReturn("主食");

        Map<String, Object> presetResult = service.createCheckin("u1", foodPresetRequest());

        assertEquals("chk_food", presetResult.get("checkinId"));
        assertEquals(110, presetResult.get("totalCalories"));

        FoodCheckinRequest custom = foodCustomRequest();
        Map<String, Object> customResult = service.createCheckin("u1", custom);
        Map<String, Object> gramsResult = service.createCheckin("u1",
                mutate(foodCustomRequest(), r -> {
                    r.setInputUnit(CheckinConstants.INPUT_UNIT_G);
                    r.setInputAmount(new BigDecimal("10"));
                    r.setMlToGRatio(null);
                }));
        Map<String, Object> defaultMlRatioResult = service.createCheckin("u1",
                mutate(foodCustomRequest(), r -> r.setMlToGRatio(null)));
        Map<String, Object> presetDefaultRatioResult = service.createCheckin("u1",
                mutate(foodPresetRequest(), r -> r.setFoodId("food_2")));

        assertEquals(160, customResult.get("totalCalories"));
        assertEquals(8, gramsResult.get("totalCalories"));
        assertEquals(80, defaultMlRatioResult.get("totalCalories"));
        assertEquals(116, presetDefaultRatioResult.get("totalCalories"));
        ArgumentCaptor<CheckinDietDetail> captor = ArgumentCaptor.forClass(CheckinDietDetail.class);
        verify(detailMapper, times(5)).insert(captor.capture());
        assertEquals("food/u1/upload_abc.jpg", captor.getAllValues().get(1).getImageObjectKey());

        CheckinDietDetail record = captor.getAllValues().get(1);
        record.setRecordTime(LocalDateTime.of(2024, 1, 1, 8, 0));
        when(detailMapper.findByUserAndDate("u1", LocalDate.of(2024, 1, 1))).thenReturn(List.of(record));
        when(minio.buildCheckinImageUrl("food/u1/upload_abc.jpg")).thenReturn("url");
        Map<String, Object> listed = service.listRecords("u1", "2024-01-01").get(0);
        assertEquals("早餐", listed.get("mealPeriodLabel"));
        assertEquals("url", listed.get("imageUrl"));
        assertNotNull(listed.get("recordTime"));
        record.setRecordTime(null);
        assertNull(service.listRecords("u1", "2024-01-01").get(0).get("recordTime"));
    }

    @Test
    void foodCheckin_parsesRecordTimeFormats() {
        CheckinDietDetailMapper detailMapper = mock(CheckinDietDetailMapper.class);
        FoodCheckinService service = new FoodCheckinService(writer, detailMapper, presetMapper, minio);
        when(writer.createRecord(anyString(), eq(CheckinConstants.TYPE_DIET), any(), any())).thenReturn("chk_food");
        when(presetMapper.findFoodPresetById("food_1")).thenReturn(foodPreset("food_1", "cat_1", "米饭", "1.16", "0.95"));
        when(presetMapper.findCategoryNameById("cat_1")).thenReturn("主食");

        service.createCheckin("u1", mutate(foodPresetRequest(), r -> r.setRecordTime("08:00")));
        service.createCheckin("u1", mutate(foodPresetRequest(), r -> r.setRecordTime("2024-01-01T09:30:00")));
        service.createCheckin("u1", mutate(foodPresetRequest(), r -> r.setRecordTime("09:30:00")));
        service.createCheckin("u1", mutate(foodPresetRequest(), r -> r.setRecordTime("bad-time")));
        service.createCheckin("u1", mutate(foodPresetRequest(), r -> r.setRecordTime(" ")));

        ArgumentCaptor<LocalDateTime> timeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(writer, times(5)).createRecord(eq("u1"), eq(CheckinConstants.TYPE_DIET),
                eq(LocalDate.of(2024, 1, 1)), timeCaptor.capture());
        assertEquals(LocalDateTime.parse("2024-01-01T08:00:00"), timeCaptor.getAllValues().get(0));
        assertEquals(LocalDateTime.parse("2024-01-01T09:30:00"), timeCaptor.getAllValues().get(1));
        assertEquals(LocalDateTime.parse("2024-01-01T09:30:00"), timeCaptor.getAllValues().get(2));
        assertNotNull(timeCaptor.getAllValues().get(3));
        assertNotNull(timeCaptor.getAllValues().get(4));
    }

    @Test
    void foodCheckin_validatesInvalidInputs() {
        CheckinDietDetailMapper detailMapper = mock(CheckinDietDetailMapper.class);
        FoodCheckinService service = new FoodCheckinService(writer, detailMapper, presetMapper, minio);
        when(presetMapper.findFoodPresetById("food_1")).thenReturn(foodPreset("food_1", "cat_1", "米饭", "1", null));
        when(presetMapper.findCategoryNameById("cat_1")).thenReturn("主食");

        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(foodPresetRequest(), r -> r.setCheckinDate("bad"))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(foodPresetRequest(), r -> r.setSourceType(9))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(foodPresetRequest(), r -> r.setFoodId(null))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(foodPresetRequest(), r -> r.setFoodId(" "))));
        when(presetMapper.findFoodPresetById("missing")).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(foodPresetRequest(), r -> r.setFoodId("missing"))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(foodCustomRequest(), r -> r.setFoodName(null))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(foodCustomRequest(), r -> r.setFoodName(" "))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(foodCustomRequest(), r -> r.setCategoryId(null))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(foodCustomRequest(), r -> r.setCategoryId(" "))));
        when(presetMapper.findCategoryNameById("bad")).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(foodCustomRequest(), r -> r.setCategoryId("bad"))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(foodCustomRequest(), r -> r.setCaloriesPerGram(null))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(foodCustomRequest(), r -> r.setCaloriesPerGram(BigDecimal.ZERO))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(foodCustomRequest(), r -> r.setImageObjectKey(null))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(foodCustomRequest(), r -> r.setImageObjectKey(""))));
        assertDoesNotThrow(() -> service.createCheckin("u1", mutate(foodPresetRequest(), r -> r.setImageObjectKey(" "))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(foodPresetRequest(), r -> r.setImageObjectKey("food/a/b.jpg"))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(foodCustomRequest(), r -> r.setImageObjectKey("food/u2/upload_abc.jpg"))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(foodCustomRequest(), r -> r.setInputUnit(9))));
    }

    @Test
    void exerciseCheckin_supportsPresetCustomAndValidation() {
        CheckinExerciseDetailMapper detailMapper = mock(CheckinExerciseDetailMapper.class);
        ExerciseCheckinService service = new ExerciseCheckinService(writer, detailMapper, presetMapper);
        when(writer.createRecord(anyString(), eq(CheckinConstants.TYPE_EXERCISE), any())).thenReturn("chk_ex");
        when(presetMapper.findExercisePresetById("ex_1")).thenReturn(exercisePreset("ex_1"));

        assertEquals(123, service.createCheckin("u1", exercisePresetRequest()).get("caloriesBurned"));
        assertEquals(100, service.createCheckin("u1", exerciseCustomRequest()).get("caloriesBurned"));
        assertThrows(NullPointerException.class, () -> service.createCheckin("u1", mutate(exerciseCustomRequest(), r -> r.setDurationMinutes(null))));

        CheckinExerciseDetail detail = new CheckinExerciseDetail();
        detail.setCheckinId("chk_ex");
        detail.setSourceType(2);
        detail.setExerciseName("慢跑");
        detail.setCaloriesPerMinute(new BigDecimal("5"));
        detail.setDurationMinutes(20);
        detail.setCaloriesBurned(100);
        detail.setRecordTime(LocalDateTime.of(2024, 1, 1, 9, 0));
        when(detailMapper.findByUserAndDate("u1", LocalDate.of(2024, 1, 1))).thenReturn(List.of(detail));
        assertEquals("慢跑", service.listRecords("u1", "2024-01-01").get(0).get("exerciseName"));
        detail.setRecordTime(null);
        assertNull(service.listRecords("u1", "2024-01-01").get(0).get("recordTime"));

        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(exercisePresetRequest(), r -> r.setCheckinDate("bad"))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(exercisePresetRequest(), r -> r.setSourceType(9))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(exercisePresetRequest(), r -> r.setExerciseId(null))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(exercisePresetRequest(), r -> r.setExerciseId(" "))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(exercisePresetRequest(), r -> r.setExerciseId("missing"))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(exerciseCustomRequest(), r -> r.setExerciseName(null))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(exerciseCustomRequest(), r -> r.setExerciseName(" "))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(exerciseCustomRequest(), r -> r.setCaloriesPerMinute(null))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(exerciseCustomRequest(), r -> r.setCaloriesPerMinute(BigDecimal.ZERO))));
    }

    @Test
    void medicationCheckin_supportsPresetCustomAndValidation() {
        CheckinMedicationDetailMapper detailMapper = mock(CheckinMedicationDetailMapper.class);
        MedicationCheckinService service = new MedicationCheckinService(writer, detailMapper, presetMapper, minio);
        when(writer.createRecord(anyString(), eq(CheckinConstants.TYPE_MEDICATION), any())).thenReturn("chk_med");
        when(presetMapper.findMedicationPresetById("drug_1")).thenReturn(medicationPreset("drug_1"));

        assertEquals("2024-01-01", service.createCheckin("u1", medicationPresetRequest()).get("checkinDate"));
        assertEquals("chk_med", service.createCheckin("u1", medicationCustomRequest()).get("checkinId"));
        assertEquals("chk_med", service.createCheckin("u1", mutate(medicationPresetRequest(), r -> r.setImageObjectKey("medical/drug_1.jpg"))).get("checkinId"));

        CheckinMedicationDetail detail = new CheckinMedicationDetail();
        detail.setCheckinId("chk_med");
        detail.setSourceType(2);
        detail.setDrugName("二甲双胍");
        detail.setDosage("1片");
        detail.setTaken(0);
        detail.setImageObjectKey("medical/u1/upload_abc.jpg");
        detail.setRecordTime(LocalDateTime.of(2024, 1, 1, 9, 0));
        when(detailMapper.findByUserAndDate("u1", LocalDate.of(2024, 1, 1))).thenReturn(List.of(detail));
        when(minio.buildCheckinImageUrl("medical/u1/upload_abc.jpg")).thenReturn("url");
        assertFalse((Boolean) service.listRecords("u1", "2024-01-01").get(0).get("taken"));
        detail.setTaken(null);
        detail.setRecordTime(null);
        Map<String, Object> medicationMap = service.listRecords("u1", "2024-01-01").get(0);
        assertFalse((Boolean) medicationMap.get("taken"));
        assertNull(medicationMap.get("recordTime"));
        detail.setTaken(1);
        assertTrue((Boolean) service.listRecords("u1", "2024-01-01").get(0).get("taken"));

        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(medicationPresetRequest(), r -> r.setCheckinDate("bad"))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(medicationPresetRequest(), r -> r.setSourceType(9))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(medicationPresetRequest(), r -> r.setDrugId(null))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(medicationPresetRequest(), r -> r.setDrugId(" "))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(medicationPresetRequest(), r -> r.setDrugId("missing"))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(medicationCustomRequest(), r -> r.setDrugName(null))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(medicationCustomRequest(), r -> r.setDrugName(" "))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(medicationCustomRequest(), r -> r.setImageObjectKey(null))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(medicationCustomRequest(), r -> r.setImageObjectKey(""))));
        assertEquals("chk_med", service.createCheckin("u1", mutate(medicationPresetRequest(), r -> r.setImageObjectKey(" "))).get("checkinId"));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(medicationPresetRequest(), r -> r.setImageObjectKey("medical/a/b.jpg"))));
        assertThrows(BusinessException.class, () -> service.createCheckin("u1", mutate(medicationCustomRequest(), r -> r.setImageObjectKey("medical/u2/upload_abc.jpg"))));
    }

    @Test
    void glucoseCheckin_createsListsAndBuildsHistory() {
        CheckinRecordMapper recordMapper = mock(CheckinRecordMapper.class);
        CheckinGlucoseDetailMapper detailMapper = mock(CheckinGlucoseDetailMapper.class);
        CheckinService checkinService = mock(CheckinService.class);
        com.diabetes.common.client.UserServiceClient userServiceClient = mock(com.diabetes.common.client.UserServiceClient.class);
        GlucoseCheckinService service = new GlucoseCheckinService(recordMapper, detailMapper, checkinService, userServiceClient, "test-key");
        when(checkinService.calculateStreakForDate("u1", LocalDate.of(2024, 1, 1))).thenReturn(3);

        Map<String, Object> created = service.createCheckin("u1", glucoseRequest(null, null, new BigDecimal("6.2")));
        Map<String, Object> explicit = service.createCheckin("u1", glucoseRequest(1, 2, new BigDecimal("5.8")));

        assertEquals(4, created.get("measureContext"));
        assertEquals("随机", created.get("measureContextLabel"));
        assertEquals(1, explicit.get("measureContext"));
        assertEquals(15, created.get("pointsEarned"));
        verify(checkinService, times(2)).invalidateUserCache("u1", LocalDate.of(2024, 1, 1));

        when(detailMapper.findByUserAndDate("u1", LocalDate.of(2024, 1, 1))).thenReturn(List.of(
                glucoseDetail("low", new BigDecimal("3.8"), 1, 1),
                glucoseDetail("normal", new BigDecimal("5.5"), 2, 2),
                glucoseDetail("elevated", new BigDecimal("6.5"), 3, 1),
                glucoseDetail("high", new BigDecimal("7.0"), 9, 1),
                glucoseDetail("unknown", null, 4, 1),
                glucoseDetail("unitNull", new BigDecimal("5.1"), 4, 1)
        ));
        glucoseDetail("unitNull", new BigDecimal("5.1"), 4, 1);
        List<Map<String, Object>> records = service.listRecords("u1", "2024-01-01");
        assertEquals(List.of("low", "normal", "elevated", "high", "unknown", "normal"),
                records.stream().map(r -> r.get("status")).toList());
        assertEquals("mg/dL", records.get(1).get("unitLabel"));
        CheckinGlucoseDetail unitNull = glucoseDetail("unitNull2", new BigDecimal("5.1"), 4, 1);
        unitNull.setUnit(null);
        when(detailMapper.findByUserAndDate("u1", LocalDate.of(2024, 1, 5))).thenReturn(List.of(unitNull));
        assertEquals("mmol/L", service.listRecords("u1", "2024-01-05").get(0).get("unitLabel"));

        when(detailMapper.findByUserAndDateRange("u1", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2)))
                .thenReturn(List.of(glucoseDetail("a", new BigDecimal("6.0"), 1, 1), glucoseDetail("b", new BigDecimal("8.0"), 1, 1),
                        glucoseDetail("c", null, 1, 1), glucoseDetail("d", new BigDecimal("7.0"), 1, 1),
                        glucoseDetail("e", new BigDecimal("5.0"), 1, 1)));
        Map<String, Object> history = service.getHistory("u1", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2));
        assertEquals(new BigDecimal("5.2"), ((Map<?, ?>) history.get("summary")).get("avg"));

        CheckinGlucoseDetail noDates = glucoseDetail("nodate", new BigDecimal("5.0"), 1, 1);
        noDates.setCheckinDate(null);
        noDates.setRecordTime(null);
        when(detailMapper.findByUserAndDate("u1", LocalDate.of(2024, 1, 4))).thenReturn(List.of(noDates));
        Map<String, Object> noDateMap = service.listRecords("u1", "2024-01-04").get(0);
        assertNull(noDateMap.get("checkinDate"));
        assertNull(noDateMap.get("recordTime"));

        when(detailMapper.findByUserAndDateRange("u1", LocalDate.of(2024, 1, 3), LocalDate.of(2024, 1, 3))).thenReturn(List.of());
        assertNull(((Map<?, ?>) service.getHistory("u1", LocalDate.of(2024, 1, 3), LocalDate.of(2024, 1, 3)).get("summary")).get("avg"));
        assertThrows(BusinessException.class, () -> service.getHistory("u1", LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 1)));
        assertThrows(BusinessException.class, () -> service.listRecords("u1", "bad"));
    }

    @Test
    void imageService_uploadsAndValidatesFiles() throws Exception {
        CheckinImageService service = new CheckinImageService(minio);
        MultipartFile file = file(false, 10, "image/jpeg");
        when(minio.uploadCheckinFoodUser(eq("u1"), anyString(), any(), eq(10L), eq("image/jpeg")))
                .thenReturn(new MinioStorageService.CheckinImageUploadResult("food/u1/upload_a.jpg", "url1"));
        when(minio.uploadCheckinMedicalUser(eq("u1"), anyString(), any(), eq(10L), eq("image/jpeg")))
                .thenReturn(new MinioStorageService.CheckinImageUploadResult("medical/u1/upload_a.jpg", "url2"));

        ImageUploadResponse food = service.upload("u1", "food", file);
        ImageUploadResponse medical = service.upload("u1", "medical", file);

        assertEquals("food/u1/upload_a.jpg", food.getObjectKey());
        assertEquals("url2", medical.getImageUrl());
        assertThrows(BusinessException.class, () -> service.upload(null, "food", file));
        assertThrows(BusinessException.class, () -> service.upload(" ", "food", file));
        assertThrows(BusinessException.class, () -> service.upload("u1", "food", null));
        assertThrows(BusinessException.class, () -> service.upload("u1", "food", file(true, 0, "image/jpeg")));
        assertThrows(BusinessException.class, () -> service.upload("u1", "food", file(false, 2 * 1024 * 1024 + 1L, "image/jpeg")));
        assertThrows(BusinessException.class, () -> service.upload("u1", "food", file(false, 1, null)));
        assertThrows(BusinessException.class, () -> service.upload("u1", "food", file(false, 1, "text/plain")));
        assertThrows(BusinessException.class, () -> service.upload("u1", "bad", file));
        assertThrows(BusinessException.class, () -> service.upload("u1", "food", brokenFile()));
    }

    @Test
    void imagePathHelperAndConstants_coverUtilityBranches() throws Exception {
        assertTrue(CheckinImagePathHelper.isValidPresetKey("food/a.jpg", "food"));
        assertFalse(CheckinImagePathHelper.isValidPresetKey(null, "food"));
        assertFalse(CheckinImagePathHelper.isValidPresetKey(" ", "food"));
        assertFalse(CheckinImagePathHelper.isValidPresetKey("medical/a.jpg", "food"));
        assertTrue(CheckinImagePathHelper.isValidPresetKey("food/.jpg", "food"));
        assertFalse(CheckinImagePathHelper.isValidPresetKey("food/a.png", "food"));
        assertFalse(CheckinImagePathHelper.isValidPresetKey("food/a/b.jpg", "food"));
        assertTrue(CheckinImagePathHelper.isValidUserUploadKey("food/u1/upload_a.jpg", "food", "u1"));
        assertFalse(CheckinImagePathHelper.isValidUserUploadKey(null, "food", "u1"));
        assertFalse(CheckinImagePathHelper.isValidUserUploadKey(" ", "food", "u1"));
        assertFalse(CheckinImagePathHelper.isValidUserUploadKey("food/u1/upload_a.jpg", "food", null));
        assertFalse(CheckinImagePathHelper.isValidUserUploadKey("food/u1/upload_a.jpg", "food", " "));
        assertFalse(CheckinImagePathHelper.isValidUserUploadKey("medical/u1/upload_a.jpg", "food", "u1"));
        assertFalse(CheckinImagePathHelper.isValidUserUploadKey("food/u1/upload_a.png", "food", "u1"));
        assertFalse(CheckinImagePathHelper.isValidUserUploadKey("food/u1/upload_.jpg", "food", "u1"));
        assertFalse(CheckinImagePathHelper.isValidUserUploadKey("food/u1/x.jpg", "food", "u1"));
        assertEquals("food/a.jpg", CheckinImagePathHelper.normalize("/food/a.jpg"));
        assertEquals("", CheckinImagePathHelper.normalize(null));
        assertEquals("未知", CheckinConstants.mealPeriodLabel(99));

        invokePrivateConstructor(CheckinImagePathHelper.class);
        invokePrivateConstructor(CheckinConstants.class);
    }

    @Test
    void recordWriter_createsRecordAndInvalidatesCache() {
        CheckinRecordMapper recordMapper = mock(CheckinRecordMapper.class);
        CheckinService checkinService = mock(CheckinService.class);
        CheckinRecordWriter service = new CheckinRecordWriter(recordMapper, checkinService);

        String id = service.createRecord("u1", 1, LocalDate.of(2024, 1, 1));
        String explicit = service.createRecord("u1", 1, LocalDate.of(2024, 1, 1),
                LocalDateTime.of(2024, 1, 1, 9, 0));
        String defaulted = service.createRecord("u1", 1, LocalDate.of(2024, 1, 1), null);

        assertTrue(id.startsWith("chk_"));
        assertTrue(explicit.startsWith("chk_"));
        assertTrue(defaulted.startsWith("chk_"));
        ArgumentCaptor<CheckinRecord> captor = ArgumentCaptor.forClass(CheckinRecord.class);
        verify(recordMapper, times(3)).insert(captor.capture());
        assertEquals(0, captor.getValue().getPointsEarned());
        verify(checkinService, times(3)).invalidateUserCache("u1", LocalDate.of(2024, 1, 1));
    }

    private static FoodCheckinRequest foodPresetRequest() {
        FoodCheckinRequest request = new FoodCheckinRequest();
        request.setCheckinDate("2024-01-01");
        request.setMealPeriod(1);
        request.setSourceType(CheckinConstants.SOURCE_PRESET);
        request.setFoodId("food_1");
        request.setInputUnit(CheckinConstants.INPUT_UNIT_ML);
        request.setInputAmount(new BigDecimal("100"));
        return request;
    }

    private static FoodCheckinRequest foodCustomRequest() {
        FoodCheckinRequest request = new FoodCheckinRequest();
        request.setCheckinDate("2024-01-01");
        request.setMealPeriod(1);
        request.setSourceType(CheckinConstants.SOURCE_CUSTOM);
        request.setFoodName(" 牛奶 ");
        request.setCategoryId("cat_1");
        request.setCaloriesPerGram(new BigDecimal("0.8"));
        request.setInputUnit(CheckinConstants.INPUT_UNIT_ML);
        request.setInputAmount(new BigDecimal("100"));
        request.setMlToGRatio(new BigDecimal("2"));
        request.setImageObjectKey("/food/u1/upload_abc.jpg");
        return request;
    }

    private static ExerciseCheckinRequest exercisePresetRequest() {
        ExerciseCheckinRequest request = new ExerciseCheckinRequest();
        request.setCheckinDate("2024-01-01");
        request.setSourceType(CheckinConstants.SOURCE_PRESET);
        request.setExerciseId("ex_1");
        request.setDurationMinutes(30);
        return request;
    }

    private static ExerciseCheckinRequest exerciseCustomRequest() {
        ExerciseCheckinRequest request = new ExerciseCheckinRequest();
        request.setCheckinDate("2024-01-01");
        request.setSourceType(CheckinConstants.SOURCE_CUSTOM);
        request.setExerciseName(" 慢跑 ");
        request.setCaloriesPerMinute(new BigDecimal("5"));
        request.setDurationMinutes(20);
        return request;
    }

    private static MedicationCheckinRequest medicationPresetRequest() {
        MedicationCheckinRequest request = new MedicationCheckinRequest();
        request.setCheckinDate("2024-01-01");
        request.setSourceType(CheckinConstants.SOURCE_PRESET);
        request.setDrugId("drug_1");
        request.setDosage(" 1片 ");
        request.setTaken(true);
        return request;
    }

    private static MedicationCheckinRequest medicationCustomRequest() {
        MedicationCheckinRequest request = new MedicationCheckinRequest();
        request.setCheckinDate("2024-01-01");
        request.setSourceType(CheckinConstants.SOURCE_CUSTOM);
        request.setDrugName(" 二甲双胍 ");
        request.setDosage(" 1片 ");
        request.setTaken(false);
        request.setImageObjectKey("/medical/u1/upload_abc.jpg");
        return request;
    }

    private static GlucoseCheckinRequest glucoseRequest(Integer context, Integer unit, BigDecimal value) {
        GlucoseCheckinRequest request = new GlucoseCheckinRequest();
        request.setCheckinDate("2024-01-01");
        request.setGlucoseValue(value);
        request.setMeasureContext(context);
        request.setUnit(unit);
        return request;
    }

    private static FoodPreset foodPreset(String id, String categoryId, String name, String calories, String ratio) {
        FoodPreset preset = new FoodPreset();
        preset.setFoodId(id);
        preset.setCategoryId(categoryId);
        preset.setFoodName(name);
        preset.setCaloriesPerGram(new BigDecimal(calories));
        preset.setMlToGRatio(ratio == null ? null : new BigDecimal(ratio));
        return preset;
    }

    private static ExercisePreset exercisePreset(String id) {
        ExercisePreset preset = new ExercisePreset();
        preset.setExerciseId(id);
        preset.setExerciseName("骑行");
        preset.setCaloriesPerMinute(new BigDecimal("4.1"));
        return preset;
    }

    private static MedicationPreset medicationPreset(String id) {
        MedicationPreset preset = new MedicationPreset();
        preset.setDrugId(id);
        preset.setDrugName("胰岛素");
        return preset;
    }

    private static CheckinGlucoseDetail glucoseDetail(String id, BigDecimal value, int context, int unit) {
        CheckinGlucoseDetail detail = new CheckinGlucoseDetail();
        detail.setCheckinId(id);
        detail.setGlucoseValue(value);
        detail.setMeasureContext(context);
        detail.setUnit(unit);
        detail.setCheckinDate(LocalDate.of(2024, 1, 1));
        detail.setRecordTime(LocalDateTime.of(2024, 1, 1, 8, 0));
        return detail;
    }

    private static MultipartFile file(boolean empty, long size, String contentType) throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(empty);
        when(file.getSize()).thenReturn(size);
        when(file.getContentType()).thenReturn(contentType);
        if (!empty && size <= 2 * 1024 * 1024 && contentType != null && contentType.startsWith("image/")) {
            when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1}));
        }
        return file;
    }

    private static MultipartFile brokenFile() throws IOException {
        MultipartFile file = file(false, 1, "image/jpeg");
        when(file.getInputStream()).thenThrow(new IOException("broken"));
        return file;
    }

    private static <T> T mutate(T value, java.util.function.Consumer<T> consumer) {
        consumer.accept(value);
        return value;
    }

    private static void invokePrivateConstructor(Class<?> type) throws Exception {
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        constructor.newInstance();
    }
}
