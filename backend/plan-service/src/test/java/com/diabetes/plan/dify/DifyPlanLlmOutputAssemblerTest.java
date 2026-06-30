package com.diabetes.plan.dify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DifyPlanLlmOutputAssemblerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void isShallowLlmOutput() throws Exception {
        JsonNode shallow = objectMapper.readTree("""
                {"breakfast_foods":[{"name":"燕麦","amount":"40g","calories":150,"gi_level":"low"}],
                 "exercise_items":[{"type":"快走","duration":"30分钟"}],
                 "rest_glucose_monitor_times":["空腹"]}
                """);
        assertTrue(DifyPlanLlmOutputAssembler.isShallowLlmOutput(shallow));

        JsonNode deep = objectMapper.readTree("{\"diet_plan\":{}}");
        assertFalse(DifyPlanLlmOutputAssembler.isShallowLlmOutput(deep));
        assertFalse(DifyPlanLlmOutputAssembler.isShallowLlmOutput(null));
        assertFalse(DifyPlanLlmOutputAssembler.isShallowLlmOutput(objectMapper.missingNode()));
    }

    @Test
    void assembleToPlanContent_full() throws Exception {
        JsonNode node = objectMapper.readTree("""
                {
                  "summary": "测试方案",
                  "diet_principles": ["少油少盐"],
                  "foods_to_avoid": ["含糖饮料"],
                  "foods_to_recommend": ["蔬菜"],
                  "breakfast_time": "07:30",
                  "breakfast_foods": [{"name":"鸡蛋","amount":"1个","calories":70,"gi_level":"低"}],
                  "breakfast_total_calories": 70,
                  "lunch_time": "12:00",
                  "lunch_foods": [{"name":"","amount":"1","calories":1,"gi_level":"x"}],
                  "lunch_total_calories": 0,
                  "dinner_time": "18:00",
                  "dinner_foods": [],
                  "dinner_total_calories": 0,
                  "snack_time": "15:00",
                  "snack_foods": [{"name":"坚果","amount":"10g","calories":50,"gi_level":"high"}],
                  "snack_total_calories": 50,
                  "exercise_weekly_goal": "每周150分钟",
                  "exercise_items": [
                    {"type":"快走","duration":"30分钟","frequency":"每日","intensity":"中","calories_burned":120,"caution":"餐后"},
                    {"type":"","duration":"10分钟"}
                  ],
                  "rest_wake_up": "06:30",
                  "rest_sleep": "22:30",
                  "rest_nap": "午休",
                  "rest_glucose_monitor_times": ["空腹", null],
                  "rest_routine_tips": ["早睡"],
                  "medication_note": "遵医嘱"
                }
                """);

        Map<String, Object> content = DifyPlanLlmOutputAssembler.assembleToPlanContent(node);

        assertEquals("测试方案", content.get("summary"));
        assertTrue(content.containsKey("dietPlan"));
        assertTrue(content.containsKey("exercisePlan"));
        assertTrue(content.containsKey("restPlan"));
        assertEquals("遵医嘱", content.get("medicationNote"));
        assertTrue(DifyPlanLlmOutputAssembler.hasRequiredSections(content));
    }

    @Test
    void hasRequiredSections_falseCases() {
        assertFalse(DifyPlanLlmOutputAssembler.hasRequiredSections(null));
        assertFalse(DifyPlanLlmOutputAssembler.hasRequiredSections(Map.of()));
        assertFalse(DifyPlanLlmOutputAssembler.hasRequiredSections(Map.of("dietPlan", Map.of())));
    }

    @Test
    void assembleToPlanContent_defaults() throws Exception {
        JsonNode node = objectMapper.readTree("""
                {"breakfast_foods":[{"name":"面包","amount":"1片","calories":80,"gi_level":"unknown"}],
                 "lunch_foods":[{"name":"饭","amount":"1碗","calories":200,"gi_level":""}],
                 "dinner_foods":[{"name":"菜","amount":"1份","calories":50,"gi_level":"中"}],
                 "snack_foods":[{"name":"果","amount":"1个","calories":60,"gi_level":"高GI"}],
                 "exercise_items":[{"type":"瑜伽","duration":"20分钟","frequency":"每周3次","intensity":"轻","calories_burned":80,"caution":""}]}
                """);
        Map<String, Object> content = DifyPlanLlmOutputAssembler.assembleToPlanContent(node);
        assertNotNull(content.get("summary"));
        assertTrue(DifyPlanLlmOutputAssembler.hasRequiredSections(content));
    }

    @Test
    void assembleToPlanContent_nonObjectExerciseAndFoodItems() throws Exception {
        JsonNode node = objectMapper.readTree("""
                {"breakfast_foods":[null,{"name":"蛋","amount":"1","calories":70,"gi_level":"low"}],
                 "lunch_foods":[{"name":"饭","amount":"1","calories":100,"gi_level":"low"}],
                 "dinner_foods":[{"name":"菜","amount":"1","calories":50,"gi_level":"low"}],
                 "snack_foods":[{"name":"果","amount":"1","calories":60,"gi_level":"low"}],
                 "exercise_items":[null,{"type":"走","duration":"10分钟","frequency":"每日","intensity":"中","calories_burned":1,"caution":""}]}
                """);
        assertTrue(DifyPlanLlmOutputAssembler.hasRequiredSections(
                DifyPlanLlmOutputAssembler.assembleToPlanContent(node)));
    }

    @Test
    void assembleToPlanContent_noExerciseItems() throws Exception {
        JsonNode node = objectMapper.readTree("""
                {"breakfast_foods":[{"name":"蛋","amount":"1","calories":70,"gi_level":"low"}],
                 "lunch_foods":[{"name":"饭","amount":"1","calories":100,"gi_level":"low"}],
                 "dinner_foods":[{"name":"菜","amount":"1","calories":50,"gi_level":"low"}],
                 "snack_foods":[{"name":"果","amount":"1","calories":60,"gi_level":"low"}]}
                """);
        Map<String, Object> content = DifyPlanLlmOutputAssembler.assembleToPlanContent(node);
        assertTrue(((List<?>) ((Map<?, ?>) content.get("exercisePlan")).get("items")).isEmpty());
    }

    @Test
    void assembleToPlanContent_nonArrayFoods() throws Exception {
        JsonNode node = objectMapper.readTree("""
                {"breakfast_foods":"not-an-array",
                 "lunch_foods":[{"name":"饭","amount":"1","calories":100,"gi_level":"low"}],
                 "dinner_foods":[{"name":"菜","amount":"1","calories":50,"gi_level":"low"}],
                 "snack_foods":[{"name":"果","amount":"1","calories":60,"gi_level":"low"}],
                 "exercise_items":[{"type":"走","duration":"10分钟","frequency":"每日","intensity":"中","calories_burned":1,"caution":""}]}
                """);
        Map<String, Object> content = DifyPlanLlmOutputAssembler.assembleToPlanContent(node);
        assertTrue(DifyPlanLlmOutputAssembler.hasRequiredSections(content));
    }
}
