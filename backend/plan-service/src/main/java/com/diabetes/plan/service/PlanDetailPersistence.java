package com.diabetes.plan.service;

import com.diabetes.common.util.IdGenerator;
import com.diabetes.plan.entity.HealthPlanDietItem;
import com.diabetes.plan.entity.HealthPlanExerciseItem;
import com.diabetes.plan.entity.HealthPlanRestItem;
import com.diabetes.plan.mapper.HealthPlanDietItemMapper;
import com.diabetes.plan.mapper.HealthPlanExerciseItemMapper;
import com.diabetes.plan.mapper.HealthPlanRestItemMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
class PlanDetailPersistence {

    private final HealthPlanDietItemMapper dietItemMapper;
    private final HealthPlanExerciseItemMapper exerciseItemMapper;
    private final HealthPlanRestItemMapper restItemMapper;

    PlanDetailPersistence(HealthPlanDietItemMapper dietItemMapper,
                          HealthPlanExerciseItemMapper exerciseItemMapper,
                          HealthPlanRestItemMapper restItemMapper) {
        this.dietItemMapper = dietItemMapper;
        this.exerciseItemMapper = exerciseItemMapper;
        this.restItemMapper = restItemMapper;
    }

    void saveDetails(String planId, Map<String, Object> content) {
        saveDietDetails(planId, content.get("dietPlan"));
        saveExerciseDetails(planId, content.get("exercisePlan"));
        saveRestDetails(planId, content.get("restPlan"));
    }

    @SuppressWarnings("unchecked")
    private void saveDietDetails(String planId, Object dietPlanObj) {
        if (!(dietPlanObj instanceof Map<?, ?> dietPlan)) {
            return;
        }
        Object mealPlanObj = dietPlan.get("meal_plan");
        if (!(mealPlanObj instanceof Map<?, ?> mealPlan)) {
            return;
        }
        for (Map.Entry<String, Integer> entry : Map.of(
                "breakfast", PlanConstants.MEAL_BREAKFAST,
                "lunch", PlanConstants.MEAL_LUNCH,
                "dinner", PlanConstants.MEAL_DINNER,
                "snack", PlanConstants.MEAL_SNACK
        ).entrySet()) {
            Object mealObj = mealPlan.get(entry.getKey());
            if (!(mealObj instanceof Map<?, ?> meal)) {
                continue;
            }
            Object foodsObj = meal.get("foods");
            if (!(foodsObj instanceof List<?> foods)) {
                continue;
            }
            int sortOrder = 0;
            for (Object foodObj : foods) {
                if (!(foodObj instanceof Map<?, ?> food)) {
                    continue;
                }
                String name = stringValue(food.get("name"));
                if (name.isBlank()) {
                    continue;
                }
                HealthPlanDietItem item = new HealthPlanDietItem();
                item.setItemId(IdGenerator.nextId("pdi_"));
                item.setPlanId(planId);
                item.setMealPeriod(entry.getValue());
                item.setFoodName(name);
                item.setPortion(stringValue(food.get("amount")));
                item.setCalories(intValue(food.get("calories")));
                item.setNote(giLevelNote(food.get("gi_level")));
                item.setSortOrder(sortOrder++);
                dietItemMapper.insert(item);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void saveExerciseDetails(String planId, Object exercisePlanObj) {
        if (!(exercisePlanObj instanceof Map<?, ?> exercisePlan)) {
            return;
        }
        Object itemsObj = exercisePlan.get("items");
        if (!(itemsObj instanceof List<?> items)) {
            return;
        }
        int sortOrder = 0;
        for (Object itemObj : items) {
            if (!(itemObj instanceof Map<?, ?> row)) {
                continue;
            }
            String type = stringValue(row.get("type"));
            if (type.isBlank()) {
                continue;
            }
            HealthPlanExerciseItem item = new HealthPlanExerciseItem();
            item.setItemId(IdGenerator.nextId("pei_"));
            item.setPlanId(planId);
            item.setExerciseType(type);
            item.setDurationMinutes(PlanConstants.parseDurationMinutes(stringValue(row.get("duration"))));
            item.setFrequency(stringValue(row.get("frequency")));
            item.setIntensity(PlanConstants.parseIntensity(stringValue(row.get("intensity"))));
            item.setCaloriesBurned(intValue(row.get("calories_burned")));
            item.setCaution(blankToNull(stringValue(row.get("caution"))));
            item.setSortOrder(sortOrder++);
            exerciseItemMapper.insert(item);
        }
    }

    @SuppressWarnings("unchecked")
    private void saveRestDetails(String planId, Object restPlanObj) {
        if (!(restPlanObj instanceof Map<?, ?> restPlan)) {
            return;
        }
        int sortOrder = 0;
        sortOrder = insertRestItem(planId, PlanConstants.SCHEDULE_WAKE_UP,
                stringValue(restPlan.get("wake_up")), "建议起床时间", sortOrder);
        sortOrder = insertRestItem(planId, PlanConstants.SCHEDULE_SLEEP,
                stringValue(restPlan.get("sleep")), "建议就寝时间", sortOrder);
        sortOrder = insertRestItem(planId, PlanConstants.SCHEDULE_NAP,
                null, stringValue(restPlan.get("nap")), sortOrder);

        Object monitorTimesObj = restPlan.get("glucose_monitor_times");
        if (monitorTimesObj instanceof List<?> monitorTimes) {
            for (Object timeObj : monitorTimes) {
                if (timeObj == null) {
                    continue;
                }
                String timePoint = timeObj.toString().trim();
                if (timePoint.isBlank()) {
                    continue;
                }
                sortOrder = insertRestItem(planId, PlanConstants.SCHEDULE_GLUCOSE_MONITOR,
                        timePoint, "血糖监测时间点", sortOrder);
            }
        }

        Object tipsObj = restPlan.get("routine_tips");
        if (tipsObj instanceof List<?> tips) {
            for (Object tipObj : tips) {
                if (tipObj == null) {
                    continue;
                }
                String tip = tipObj.toString().trim();
                if (tip.isBlank()) {
                    continue;
                }
                sortOrder = insertRestItem(planId, PlanConstants.SCHEDULE_ROUTINE_TIP,
                        null, tip, sortOrder);
            }
        }
    }

    private int insertRestItem(String planId, int scheduleType, String timePoint, String suggestion, int sortOrder) {
        if (suggestion == null || suggestion.isBlank()) {
            return sortOrder;
        }
        HealthPlanRestItem item = new HealthPlanRestItem();
        item.setItemId(IdGenerator.nextId("pri_"));
        item.setPlanId(planId);
        item.setScheduleType(scheduleType);
        item.setTimePoint(blankToNull(timePoint));
        item.setSuggestion(suggestion);
        item.setSortOrder(sortOrder);
        restItemMapper.insert(item);
        return sortOrder + 1;
    }

    private static String giLevelNote(Object giLevel) {
        String value = stringValue(giLevel);
        return value.isBlank() ? null : "gi_level:" + value;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Integer intValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
