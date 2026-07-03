package com.diabetes.checkin;

import com.diabetes.checkin.dto.ExerciseCheckinRequest;
import com.diabetes.checkin.dto.FoodCheckinRequest;
import com.diabetes.checkin.dto.GlucoseCheckinRequest;
import com.diabetes.checkin.dto.ImageUploadResponse;
import com.diabetes.checkin.dto.MedicationCheckinRequest;
import com.diabetes.checkin.dto.ReminderRuleItemRequest;
import com.diabetes.checkin.dto.ReminderRulesSaveRequest;
import com.diabetes.checkin.dto.ReminderSnoozeRequest;
import com.diabetes.checkin.entity.CheckinReminderLog;
import com.diabetes.checkin.entity.CheckinReminderRule;
import com.diabetes.checkin.entity.CheckinDietDetail;
import com.diabetes.checkin.entity.CheckinExerciseDetail;
import com.diabetes.checkin.entity.CheckinGlucoseDetail;
import com.diabetes.checkin.entity.CheckinMedicationDetail;
import com.diabetes.checkin.entity.CheckinRecord;
import com.diabetes.checkin.entity.ExercisePreset;
import com.diabetes.checkin.entity.FoodCategory;
import com.diabetes.checkin.entity.FoodPreset;
import com.diabetes.checkin.entity.MedicationPreset;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CheckinModelTest {

    @Test
    void dtoGettersSettersWork() {
        FoodCheckinRequest food = new FoodCheckinRequest();
        food.setCheckinDate("2024-01-01");
        food.setMealPeriod(1);
        food.setSourceType(2);
        food.setFoodId("food");
        food.setFoodName("name");
        food.setCaloriesPerGram(BigDecimal.ONE);
        food.setInputUnit(1);
        food.setInputAmount(BigDecimal.TEN);
        food.setMlToGRatio(new BigDecimal("1.1"));
        food.setCategoryId("cat");
        food.setImageObjectKey("key");
        food.setRecordTime("08:00");
        assertEquals("2024-01-01", food.getCheckinDate());
        assertEquals("08:00", food.getRecordTime());
        assertEquals(1, food.getMealPeriod());
        assertEquals(2, food.getSourceType());
        assertEquals("food", food.getFoodId());
        assertEquals("name", food.getFoodName());
        assertEquals(BigDecimal.ONE, food.getCaloriesPerGram());
        assertEquals(1, food.getInputUnit());
        assertEquals(BigDecimal.TEN, food.getInputAmount());
        assertEquals(new BigDecimal("1.1"), food.getMlToGRatio());
        assertEquals("cat", food.getCategoryId());
        assertEquals("key", food.getImageObjectKey());

        ExerciseCheckinRequest exercise = new ExerciseCheckinRequest();
        exercise.setCheckinDate("2024-01-01");
        exercise.setSourceType(1);
        exercise.setExerciseId("ex");
        exercise.setExerciseName("run");
        exercise.setCaloriesPerMinute(BigDecimal.ONE);
        exercise.setDurationMinutes(20);
        assertEquals("ex", exercise.getExerciseId());
        assertEquals("run", exercise.getExerciseName());
        assertEquals(20, exercise.getDurationMinutes());

        GlucoseCheckinRequest glucose = new GlucoseCheckinRequest();
        glucose.setCheckinDate("2024-01-01");
        glucose.setGlucoseValue(new BigDecimal("6.1"));
        glucose.setMeasureContext(2);
        glucose.setUnit(1);
        assertEquals(new BigDecimal("6.1"), glucose.getGlucoseValue());
        assertEquals(2, glucose.getMeasureContext());
        assertEquals(1, glucose.getUnit());

        MedicationCheckinRequest medication = new MedicationCheckinRequest();
        medication.setCheckinDate("2024-01-01");
        medication.setSourceType(1);
        medication.setDrugId("drug");
        medication.setDrugName("name");
        medication.setDosage("1片");
        medication.setTaken(true);
        medication.setImageObjectKey("key");
        assertTrue(medication.getTaken());
        assertEquals("drug", medication.getDrugId());
        assertEquals("name", medication.getDrugName());
        assertEquals("1片", medication.getDosage());
        assertEquals("key", medication.getImageObjectKey());

        ImageUploadResponse empty = new ImageUploadResponse();
        empty.setImageId("id");
        empty.setObjectKey("key");
        empty.setImageUrl("url");
        ImageUploadResponse full = new ImageUploadResponse("id2", "key2", "url2");
        assertEquals("id", empty.getImageId());
        assertEquals("key", empty.getObjectKey());
        assertEquals("url", empty.getImageUrl());
        assertEquals("id2", full.getImageId());
        assertEquals("key2", full.getObjectKey());
        assertEquals("url2", full.getImageUrl());

        ReminderRuleItemRequest ruleItem = new ReminderRuleItemRequest();
        ruleItem.setCheckinType(1);
        ruleItem.setRemindTime("08:00");
        ruleItem.setEnabled(false);
        ruleItem.setSortOrder(2);
        assertFalse(ruleItem.getEnabled());
        assertEquals(2, ruleItem.getSortOrder());

        ReminderRulesSaveRequest rulesSave = new ReminderRulesSaveRequest();
        rulesSave.setRules(List.of(ruleItem));
        assertEquals(1, rulesSave.getRules().size());

        ReminderSnoozeRequest snooze = new ReminderSnoozeRequest();
        snooze.setMinutes(30);
        assertEquals(30, snooze.getMinutes());
    }

    @Test
    void entityGettersSettersWork() {
        LocalDate date = LocalDate.of(2024, 1, 1);
        LocalDateTime time = LocalDateTime.of(2024, 1, 1, 8, 0);

        CheckinRecord record = new CheckinRecord();
        record.setCheckinId("chk");
        record.setUserId("u1");
        record.setCheckinType(1);
        record.setCheckinDate(date);
        record.setRecordTime(time);
        record.setPointsEarned(1);
        record.setStreakDays(2);
        record.setDelFlag(0);
        assertEquals("u1", record.getUserId());
        assertEquals(1, record.getCheckinType());
        assertEquals(date, record.getCheckinDate());
        assertEquals(time, record.getRecordTime());
        assertEquals(1, record.getPointsEarned());
        assertEquals(2, record.getStreakDays());
        assertEquals(0, record.getDelFlag());

        CheckinDietDetail diet = new CheckinDietDetail();
        diet.setCheckinId("chk");
        diet.setMealPeriod(1);
        diet.setSourceType(2);
        diet.setFoodId("food");
        diet.setFoodName("name");
        diet.setCategoryName("cat");
        diet.setCaloriesPerGram(BigDecimal.ONE);
        diet.setInputUnit(1);
        diet.setInputAmount(BigDecimal.TEN);
        diet.setGrams(BigDecimal.TEN);
        diet.setTotalCalories(10);
        diet.setImageObjectKey("key");
        diet.setRecordTime(time);
        assertEquals("cat", diet.getCategoryName());
        assertEquals(BigDecimal.TEN, diet.getGrams());

        CheckinExerciseDetail exercise = new CheckinExerciseDetail();
        exercise.setCheckinId("chk");
        exercise.setSourceType(1);
        exercise.setExerciseId("ex");
        exercise.setExerciseName("run");
        exercise.setCaloriesPerMinute(BigDecimal.ONE);
        exercise.setDurationMinutes(20);
        exercise.setCaloriesBurned(20);
        exercise.setRecordTime(time);
        assertEquals(20, exercise.getCaloriesBurned());

        CheckinGlucoseDetail glucose = new CheckinGlucoseDetail();
        glucose.setCheckinId("chk");
        glucose.setGlucoseValue(new BigDecimal("6.1"));
        glucose.setMeasureContext(1);
        glucose.setUnit(1);
        glucose.setCheckinDate(date);
        glucose.setRecordTime(time);
        assertEquals(date, glucose.getCheckinDate());

        CheckinMedicationDetail medication = new CheckinMedicationDetail();
        medication.setCheckinId("chk");
        medication.setSourceType(1);
        medication.setDrugId("drug");
        medication.setDrugName("name");
        medication.setDosage("1片");
        medication.setTaken(1);
        medication.setImageObjectKey("key");
        medication.setRecordTime(time);
        assertEquals(1, medication.getTaken());

        FoodCategory category = new FoodCategory();
        category.setCategoryId("cat");
        category.setCategoryName("主食");
        assertEquals("主食", category.getCategoryName());

        FoodPreset food = new FoodPreset();
        food.setFoodId("food");
        food.setCategoryId("cat");
        food.setFoodName("米饭");
        food.setCaloriesPerGram(BigDecimal.ONE);
        food.setIsLiquid(0);
        food.setMlToGRatio(BigDecimal.ONE);
        assertEquals(0, food.getIsLiquid());

        MedicationPreset drug = new MedicationPreset();
        drug.setDrugId("drug");
        drug.setDrugName("药");
        assertEquals("药", drug.getDrugName());

        ExercisePreset preset = new ExercisePreset();
        preset.setExerciseId("ex");
        preset.setExerciseName("跑步");
        preset.setCaloriesPerMinute(BigDecimal.ONE);
        assertEquals("跑步", preset.getExerciseName());

        CheckinReminderLog reminderLog = new CheckinReminderLog();
        reminderLog.setLogId("l1");
        reminderLog.setUserId("u1");
        reminderLog.setRuleId("r1");
        reminderLog.setCheckinType(1);
        reminderLog.setRemindDate(date);
        reminderLog.setChannel("in_app");
        reminderLog.setStatus(0);
        reminderLog.setSnoozeUntil(time);
        reminderLog.setSnoozeCount(1);
        reminderLog.setCreatedAt(time);
        reminderLog.setUpdatedAt(time);
        assertEquals("l1", reminderLog.getLogId());
        assertEquals("u1", reminderLog.getUserId());
        assertEquals("r1", reminderLog.getRuleId());
        assertEquals(1, reminderLog.getCheckinType());
        assertEquals(date, reminderLog.getRemindDate());
        assertEquals("in_app", reminderLog.getChannel());
        assertEquals(1, reminderLog.getSnoozeCount());
        assertEquals(time, reminderLog.getCreatedAt());
        assertEquals(time, reminderLog.getUpdatedAt());

        CheckinReminderRule reminderRule = new CheckinReminderRule();
        reminderRule.setRuleId("r1");
        reminderRule.setUserId("u1");
        reminderRule.setCheckinType(1);
        reminderRule.setRemindTime(LocalTime.of(8, 0));
        reminderRule.setEnabled(true);
        reminderRule.setRuleSource("user");
        reminderRule.setInterventionId("int_1");
        reminderRule.setExpiresAt(time);
        reminderRule.setSortOrder(0);
        reminderRule.setCreatedAt(time);
        reminderRule.setUpdatedAt(time);
        assertEquals("user", reminderRule.getRuleSource());
        assertEquals("int_1", reminderRule.getInterventionId());
        assertEquals("u1", reminderRule.getUserId());
        assertEquals(time, reminderRule.getCreatedAt());
        assertEquals(time, reminderRule.getUpdatedAt());
    }
}
