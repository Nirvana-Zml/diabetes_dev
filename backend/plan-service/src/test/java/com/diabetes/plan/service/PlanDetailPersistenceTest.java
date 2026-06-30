package com.diabetes.plan.service;

import com.diabetes.plan.mapper.HealthPlanDietItemMapper;
import com.diabetes.plan.mapper.HealthPlanExerciseItemMapper;
import com.diabetes.plan.mapper.HealthPlanRestItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanDetailPersistenceTest {

    @Mock
    private HealthPlanDietItemMapper dietItemMapper;
    @Mock
    private HealthPlanExerciseItemMapper exerciseItemMapper;
    @Mock
    private HealthPlanRestItemMapper restItemMapper;

    private PlanDetailPersistence persistence;

    @BeforeEach
    void setUp() {
        persistence = new PlanDetailPersistence(dietItemMapper, exerciseItemMapper, restItemMapper);
    }

    @Test
    void saveDetails_fullContent() {
        Map<String, Object> breakfast = Map.of(
                "foods", List.of(
                        Map.of("name", "鸡蛋", "amount", "1个", "calories", 70, "gi_level", "low"),
                        Map.of("name", "  ", "amount", "1", "calories", 1),
                        Map.of("name", "豆浆", "amount", "250ml", "calories", 80)),
                "time", "07:00");
        Map<String, Object> diet = Map.of(
                "meal_plan", Map.of(
                        "breakfast", breakfast,
                        "lunch", Map.of("foods", "not-list"),
                        "dinner", Map.of(),
                        "snack", Map.of("foods", List.of())));

        Map<String, Object> exercise = Map.of("items", List.of(
                Map.of("type", "快走", "duration", "30分钟", "frequency", "每日",
                        "intensity", "中等", "calories_burned", 120, "caution", "注意"),
                Map.of("type", "", "duration", "10分钟"),
                "not-a-map"));

        Map<String, Object> rest = new LinkedHashMap<>();
        rest.put("wake_up", "06:30");
        rest.put("sleep", "22:30");
        rest.put("nap", "午休20分钟");
        rest.put("glucose_monitor_times", new ArrayList<>(List.of("空腹", "  ")));
        rest.put("routine_tips", new ArrayList<>(List.of("早睡", "")));
        rest.put("blank_tip", "   ");

        Map<String, Object> content = Map.of(
                "dietPlan", diet,
                "exercisePlan", exercise,
                "restPlan", rest);

        persistence.saveDetails("plan_1", content);

        verify(dietItemMapper, atLeast(2)).insert(any());
        verify(exerciseItemMapper, times(1)).insert(any());
        verify(restItemMapper, atLeast(4)).insert(any());
    }

    @Test
    void saveDetails_invalidObjects() {
        Map<String, Object> content = Map.of(
                "dietPlan", "bad",
                "exercisePlan", 1,
                "restPlan", List.of());

        persistence.saveDetails("plan_1", content);

        verifyNoInteractions(dietItemMapper, exerciseItemMapper, restItemMapper);
    }

    @Test
    void saveDetails_edgeCases() {
        persistence.saveDetails("plan_1", Map.of("dietPlan", Map.of("meal_plan", "bad")));
        persistence.saveDetails("plan_1", Map.of("exercisePlan", Map.of("items", "bad")));
        verifyNoInteractions(dietItemMapper, exerciseItemMapper);
    }

    @Test
    void saveDetails_numericCaloriesAndInvalidCalories() {
        Map<String, Object> breakfast = Map.of(
                "foods", List.of(
                        Map.of("name", "蛋", "amount", "1", "calories", 70, "gi_level", "low"),
                        Map.of("name", "坏", "amount", "1", "calories", "bad")),
                "time", "07:00");
        Map<String, Object> diet = Map.of("meal_plan", Map.of("breakfast", breakfast));
        Map<String, Object> exercise = Map.of("items", List.of(
                Map.of("type", "跑", "duration", "30分钟", "frequency", "每日",
                        "intensity", "高", "calories_burned", 200)));
        Map<String, Object> rest = new LinkedHashMap<>();
        rest.put("nap", "午休");
        List<Object> monitors = new ArrayList<>();
        monitors.add(null);
        monitors.add("睡前");
        rest.put("glucose_monitor_times", monitors);
        List<Object> tips = new ArrayList<>();
        tips.add(null);
        tips.add("固定作息");
        rest.put("routine_tips", tips);

        persistence.saveDetails("plan_1", Map.of(
                "dietPlan", diet, "exercisePlan", exercise, "restPlan", rest));

        verify(dietItemMapper, times(2)).insert(any());
        verify(exerciseItemMapper).insert(any());
        verify(restItemMapper, atLeast(3)).insert(any());
    }

    @Test
    void saveDetails_nullAndStringCalories() {
        Map<String, Object> breakfast = Map.of(
                "foods", List.of(
                        Map.of("name", "蛋", "amount", "1", "calories", "80"),
                        new LinkedHashMap<>(Map.of("name", "茶", "amount", "1"))),
                "time", "07:00");
        Map<String, Object> diet = Map.of("meal_plan", Map.of("breakfast", breakfast));
        persistence.saveDetails("plan_1", Map.of("dietPlan", diet));
        verify(dietItemMapper, times(2)).insert(any());
    }

    @Test
    void saveDetails_skipsBlankNapOnly() {
        Map<String, Object> rest = Map.of(
                "wake_up", "  ",
                "sleep", "",
                "nap", "   ");
        persistence.saveDetails("plan_1", Map.of("restPlan", rest));
        verify(restItemMapper, times(2)).insert(any());
    }
}
