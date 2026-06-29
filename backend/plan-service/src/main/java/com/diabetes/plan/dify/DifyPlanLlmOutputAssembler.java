package com.diabetes.plan.dify;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 Dify LLM 浅层 Structured Output 组装为 plan-service 内部方案结构。
 *
 * <p>避免在工作流内嵌套过深的 {@code health_plan}（Dify 深度限制 5）。</p>
 */
public final class DifyPlanLlmOutputAssembler {

    private DifyPlanLlmOutputAssembler() {
    }

    public static boolean isShallowLlmOutput(JsonNode node) {
        if (node == null || node.isMissingNode() || !node.isObject()) {
            return false;
        }
        if (node.has("diet_plan") || node.has("dietPlan")) {
            return false;
        }
        return node.has("breakfast_foods")
                || node.has("lunch_foods")
                || node.has("exercise_items")
                || node.has("rest_glucose_monitor_times");
    }

    public static Map<String, Object> assembleToPlanContent(JsonNode shallow) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", textOrDefault(shallow, "summary", "基于用户画像生成的健康管理方案"));
        result.put("dietPlan", buildDietPlan(shallow));
        result.put("exercisePlan", buildExercisePlan(shallow));
        result.put("restPlan", buildRestPlan(shallow));
        result.put("medicationNote", textOrDefault(shallow, "medication_note",
                "请遵医嘱按时用药，定期监测血糖，出现不适及时就医。"));
        return result;
    }

    public static boolean hasRequiredSections(Map<String, Object> content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        return isNonEmptyMap(content.get("dietPlan"))
                && isNonEmptyMap(content.get("exercisePlan"))
                && isNonEmptyMap(content.get("restPlan"));
    }

    private static Map<String, Object> buildDietPlan(JsonNode node) {
        Map<String, Object> diet = new LinkedHashMap<>();
        Map<String, Object> mealPlan = new LinkedHashMap<>();
        mealPlan.put("breakfast", buildMeal(node, "breakfast"));
        mealPlan.put("lunch", buildMeal(node, "lunch"));
        mealPlan.put("dinner", buildMeal(node, "dinner"));
        mealPlan.put("snack", buildMeal(node, "snack"));
        diet.put("meal_plan", mealPlan);
        diet.put("diet_principles", stringList(node.get("diet_principles")));
        diet.put("foods_to_avoid", stringList(node.get("foods_to_avoid")));
        diet.put("foods_to_recommend", stringList(node.get("foods_to_recommend")));
        return diet;
    }

    private static Map<String, Object> buildMeal(JsonNode node, String prefix) {
        Map<String, Object> meal = new LinkedHashMap<>();
        meal.put("time", textOrEmpty(node, prefix + "_time"));
        meal.put("foods", foodList(node.get(prefix + "_foods")));
        meal.put("total_calories", intOrZero(node, prefix + "_total_calories"));
        return meal;
    }

    private static List<Map<String, Object>> foodList(JsonNode foodsNode) {
        List<Map<String, Object>> foods = new ArrayList<>();
        if (foodsNode == null || !foodsNode.isArray()) {
            return foods;
        }
        for (JsonNode item : foodsNode) {
            if (item == null || !item.isObject()) {
                continue;
            }
            String name = textOrEmpty(item, "name");
            if (name.isBlank()) {
                continue;
            }
            Map<String, Object> food = new LinkedHashMap<>();
            food.put("name", name);
            food.put("amount", textOrEmpty(item, "amount"));
            food.put("calories", intOrZero(item, "calories"));
            food.put("gi_level", normalizeGiLevel(textOrEmpty(item, "gi_level")));
            foods.add(food);
        }
        return foods;
    }

    private static Map<String, Object> buildExercisePlan(JsonNode node) {
        Map<String, Object> exercise = new LinkedHashMap<>();
        exercise.put("weekly_goal", textOrEmpty(node, "exercise_weekly_goal"));
        List<Map<String, Object>> items = new ArrayList<>();
        JsonNode array = node.get("exercise_items");
        if (array != null && array.isArray()) {
            for (JsonNode item : array) {
                if (item == null || !item.isObject()) {
                    continue;
                }
                String type = textOrEmpty(item, "type");
                if (type.isBlank()) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("type", type);
                row.put("duration", textOrEmpty(item, "duration"));
                row.put("frequency", textOrEmpty(item, "frequency"));
                row.put("intensity", textOrEmpty(item, "intensity"));
                row.put("calories_burned", intOrZero(item, "calories_burned"));
                row.put("caution", textOrEmpty(item, "caution"));
                items.add(row);
            }
        }
        exercise.put("items", items);
        return exercise;
    }

    private static Map<String, Object> buildRestPlan(JsonNode node) {
        Map<String, Object> rest = new LinkedHashMap<>();
        rest.put("wake_up", textOrEmpty(node, "rest_wake_up"));
        rest.put("sleep", textOrEmpty(node, "rest_sleep"));
        rest.put("nap", textOrEmpty(node, "rest_nap"));
        rest.put("glucose_monitor_times", stringList(node.get("rest_glucose_monitor_times")));
        rest.put("routine_tips", stringList(node.get("rest_routine_tips")));
        return rest;
    }

    private static String normalizeGiLevel(String raw) {
        if (raw == null || raw.isBlank()) {
            return "medium";
        }
        String value = raw.trim().toLowerCase();
        if ("low".equals(value) || "medium".equals(value) || "high".equals(value)) {
            return value;
        }
        if (raw.contains("低")) {
            return "low";
        }
        if (raw.contains("高")) {
            return "high";
        }
        return "medium";
    }

    private static List<String> stringList(JsonNode arrayNode) {
        List<String> list = new ArrayList<>();
        if (arrayNode == null || !arrayNode.isArray()) {
            return list;
        }
        for (JsonNode item : arrayNode) {
            if (item != null && !item.isNull()) {
                list.add(item.asText(""));
            }
        }
        return list;
    }

    private static String textOrDefault(JsonNode node, String field, String defaultValue) {
        String value = textOrEmpty(node, field);
        return value.isBlank() ? defaultValue : value;
    }

    private static String textOrEmpty(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || !node.has(field) || node.get(field).isNull()) {
            return "";
        }
        return node.get(field).asText("").trim();
    }

    private static int intOrZero(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || !node.has(field) || node.get(field).isNull()) {
            return 0;
        }
        return node.get(field).asInt(0);
    }

    private static boolean isNonEmptyMap(Object value) {
        return value instanceof Map<?, ?> map && !map.isEmpty();
    }
}
