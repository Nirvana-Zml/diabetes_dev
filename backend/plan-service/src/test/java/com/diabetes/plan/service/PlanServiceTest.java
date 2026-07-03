package com.diabetes.plan.service;

import com.diabetes.common.client.UserServiceClient;
import com.diabetes.common.dify.DifyClient;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.plan.dify.DifyPlanLlmOutputAssembler;
import com.diabetes.plan.entity.HealthPlan;
import com.diabetes.plan.mapper.HealthPlanMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock
    private HealthPlanMapper healthPlanMapper;
    @Mock
    private PlanPersistenceService planPersistenceService;
    @Mock
    private UserProfileService userProfileService;
    @Mock
    private CalorieCalculator calorieCalculator;
    @Mock
    private PlanPromptBuilder planPromptBuilder;
    @Mock
    private DifyClient difyClient;
    @Mock
    private UserServiceClient userServiceClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PlanService planServiceNoDify;
    private PlanService planServiceWithDify;

    @BeforeEach
    void setUp() {
        planServiceNoDify = new PlanService(
                healthPlanMapper, planPersistenceService, userProfileService,
                calorieCalculator, planPromptBuilder, difyClient, userServiceClient, objectMapper,
                "http://dify", "", "blocking", "");
        planServiceWithDify = new PlanService(
                healthPlanMapper, planPersistenceService, userProfileService,
                calorieCalculator, planPromptBuilder, difyClient, userServiceClient, objectMapper,
                "http://dify/", "api-key", null, "internal-key");
    }

    @Test
    void getDifyWorkflowSpec() {
        Map<String, Object> spec = planServiceWithDify.getDifyWorkflowSpec();
        assertEquals("api-key", spec.get("apiKey"));
        assertEquals("blocking", spec.get("responseMode"));
    }

    @Test
    void getLatest_and_getDetail() throws Exception {
        HealthPlan plan = samplePlan();
        plan.setAiRawResponse(objectMapper.writeValueAsString(Map.of(
                "dietPlan", Map.of("k", "v"),
                "exercisePlan", Map.of("e", 1),
                "restPlan", Map.of("r", 1),
                "summary", "AI摘要")));
        when(healthPlanMapper.findLatestByUserId("u_1")).thenReturn(plan);
        when(healthPlanMapper.findById("plan_1")).thenReturn(plan);

        Map<String, Object> latest = planServiceNoDify.getLatest("u_1");
        assertEquals("plan_1", latest.get("planId"));
        assertEquals("AI摘要", latest.get("summary"));

        assertNull(planServiceNoDify.getLatest("u_none"));
        assertNotNull(planServiceNoDify.getDetail("plan_1"));
        assertNull(planServiceNoDify.getDetail("missing"));
    }

    @Test
    void getDetail_invalidAiRaw() {
        HealthPlan plan = samplePlan();
        plan.setAiRawResponse("{invalid");
        when(healthPlanMapper.findById("plan_1")).thenReturn(plan);

        Map<String, Object> detail = planServiceNoDify.getDetail("plan_1");
        assertEquals(Map.of(), detail.get("dietPlan"));
    }

    @Test
    void getHistory() {
        HealthPlan plan = samplePlan();
        when(healthPlanMapper.findHistory("u_1", 0, 10)).thenReturn(List.of(plan));
        when(healthPlanMapper.countByUserId("u_1")).thenReturn(1);

        Map<String, Object> history = planServiceNoDify.getHistory("u_1", 1, 10);
        assertEquals(1, history.get("total"));
        assertEquals(1, ((List<?>) history.get("plans")).size());
    }

    @Test
    void toggleFavorite() {
        HealthPlan plan = samplePlan();
        plan.setIsFavorite(0);
        when(healthPlanMapper.findById("plan_1")).thenReturn(plan);

        Map<String, Object> favorited = planServiceNoDify.toggleFavorite("u_1", "plan_1");
        assertEquals(true, favorited.get("favorited"));
        verify(healthPlanMapper).updateFavorite("plan_1", 1);

        plan.setIsFavorite(1);
        Map<String, Object> unfavorited = planServiceNoDify.toggleFavorite("u_1", "plan_1");
        assertEquals(false, unfavorited.get("favorited"));
    }

    @Test
    void toggleFavorite_nullFavorite() {
        HealthPlan plan = samplePlan();
        plan.setIsFavorite(null);
        when(healthPlanMapper.findById("plan_1")).thenReturn(plan);

        Map<String, Object> result = planServiceNoDify.toggleFavorite("u_1", "plan_1");
        assertEquals(true, result.get("favorited"));
        verify(healthPlanMapper).updateFavorite("plan_1", 1);
    }

    @Test
    void toggleFavorite_notFound() {
        when(healthPlanMapper.findById("x")).thenReturn(null);
        assertThrows(BusinessException.class, () -> planServiceNoDify.toggleFavorite("u_1", "x"));
    }

    @Test
    void toggleFavorite_forbidden() {
        HealthPlan plan = samplePlan();
        plan.setUserId("u_2");
        when(healthPlanMapper.findById("plan_1")).thenReturn(plan);
        assertThrows(BusinessException.class, () -> planServiceNoDify.toggleFavorite("u_1", "plan_1"));
    }

    @Test
    void generatePlanContent_noDifyKey() throws Exception {
        Map<String, Object> content = invokeGeneratePlanContent(planServiceNoDify, "u_1", Map.of(), 1800);
        assertTrue(content.containsKey("dietPlan"));
        assertTrue(content.containsKey("exercisePlan"));
        assertTrue(content.containsKey("restPlan"));
    }

    @Test
    void generatePlanContent_difyShallowOutput() throws Exception {
        when(planPromptBuilder.buildDifyPayload(anyString(), any(), anyInt())).thenReturn(Map.of("query", "q"));
        JsonNode response = objectMapper.readTree("""
                {"data":{"status":"succeeded","outputs":{
                  "plan_llm_output":{
                    "summary":"AI",
                    "breakfast_foods":[{"name":"蛋","amount":"1","calories":70,"gi_level":"low"}],
                    "lunch_foods":[{"name":"饭","amount":"1碗","calories":200,"gi_level":"medium"}],
                    "dinner_foods":[{"name":"菜","amount":"1份","calories":50,"gi_level":"low"}],
                    "snack_foods":[{"name":"果","amount":"1","calories":60,"gi_level":"low"}],
                    "exercise_items":[{"type":"快走","duration":"30分钟","frequency":"每日","intensity":"中","calories_burned":100,"caution":""}],
                    "rest_wake_up":"06:30","rest_sleep":"22:30","rest_nap":"午休",
                    "rest_glucose_monitor_times":["空腹"],"rest_routine_tips":["早睡"]
                  }
                }}}
                """);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(response);

        Map<String, Object> content = invokeGeneratePlanContent(planServiceWithDify, "u_1", Map.of(), 1800);
        assertEquals("AI", content.get("summary"));
        assertTrue(content.containsKey("aiRaw"));
    }

    @Test
    void generatePlanContent_difyHealthPlanSnakeCase() throws Exception {
        when(planPromptBuilder.buildDifyPayload(anyString(), any(), anyInt())).thenReturn(Map.of());
        JsonNode response = objectMapper.readTree("""
                {"status":"succeeded","health_plan":{
                  "diet_plan":{"meal_plan":{"breakfast":{"foods":[{"name":"蛋","amount":"1","calories":70}]}}},
                  "exercise_plan":{"items":[{"type":"快走","duration":"30分钟"}]},
                  "rest_plan":{"wake_up":"06:30"},
                  "medication_note":"用药",
                  "summary":"概要"
                }}
                """);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(response);

        Map<String, Object> content = invokeGeneratePlanContent(planServiceWithDify, "u_1", Map.of(), 1800);
        assertEquals("用药", content.get("medicationNote"));
        assertEquals("概要", content.get("summary"));
    }

    @Test
    void generatePlanContent_difyCamelCaseOutputs() throws Exception {
        when(planPromptBuilder.buildDifyPayload(anyString(), any(), anyInt())).thenReturn(Map.of());
        JsonNode response = objectMapper.readTree("""
                {"data":{"status":"succeeded","outputs":{
                  "dietPlan":{"meal_plan":{"breakfast":{"foods":[{"name":"饭","amount":"1","calories":100}]}}},
                  "exercisePlan":{"items":[{"type":"走","duration":"20分钟"}]},
                  "restPlan":{"sleep":"22:00"},
                  "medicationNote":"note"
                }}}
                """);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(response);

        Map<String, Object> content = invokeGeneratePlanContent(planServiceWithDify, "u_1", Map.of(), 1800);
        assertEquals("note", content.get("medicationNote"));
    }

    @Test
    void generatePlanContent_difyTextOutput() throws Exception {
        when(planPromptBuilder.buildDifyPayload(anyString(), any(), anyInt())).thenReturn(Map.of());
        String inner = objectMapper.writeValueAsString(Map.of(
                "diet_plan", Map.of("meal_plan", Map.of("breakfast", Map.of("foods", List.of(Map.of("name", "蛋", "amount", "1", "calories", 1))))),
                "exercise_plan", Map.of("items", List.of(Map.of("type", "走", "duration", "10分钟"))),
                "rest_plan", Map.of("wake_up", "06:00")));
        JsonNode response = objectMapper.readTree(
                "{\"data\":{\"status\":\"succeeded\",\"outputs\":{\"text\":" + objectMapper.writeValueAsString(inner) + "}}}");
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(response);

        Map<String, Object> content = invokeGeneratePlanContent(planServiceWithDify, "u_1", Map.of(), 1800);
        assertNotNull(content.get("dietPlan"));
    }

    @Test
    void generatePlanContent_difyFailedStatus() throws Exception {
        when(planPromptBuilder.buildDifyPayload(anyString(), any(), anyInt())).thenReturn(Map.of());
        JsonNode response = objectMapper.readTree("{\"data\":{\"status\":\"failed\",\"error\":\"boom\"}}");
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(response);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> invokeGeneratePlanContent(planServiceWithDify, "u_1", Map.of(), 1800));
        assertEquals(502, ex.getCode());
    }

    @Test
    void generatePlanContent_difyInvalidResponse() throws Exception {
        when(planPromptBuilder.buildDifyPayload(anyString(), any(), anyInt())).thenReturn(Map.of());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(objectMapper.readTree("{\"data\":{\"status\":\"succeeded\",\"outputs\":{}}}"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> invokeGeneratePlanContent(planServiceWithDify, "u_1", Map.of(), 1800));
        assertEquals(502, ex.getCode());
    }

    @Test
    void generatePlanContent_difyThrows() throws Exception {
        when(planPromptBuilder.buildDifyPayload(anyString(), any(), anyInt())).thenReturn(Map.of());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("network"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> invokeGeneratePlanContent(planServiceWithDify, "u_1", Map.of(), 1800));
        assertTrue(ex.getMessage().contains("Dify 方案生成失败"));
    }

    @Test
    void extractHealthPlan_deprecated() throws Exception {
        Method method = PlanService.class.getDeclaredMethod("extractHealthPlan", JsonNode.class);
        method.setAccessible(true);
        JsonNode node = objectMapper.readTree("{\"health_plan\":{\"diet_plan\":{}}}");
        assertFalse(((JsonNode) method.invoke(planServiceNoDify, node)).isMissingNode());
    }

    @Test
    void generatePlanContent_shallowLlmAtRoot() throws Exception {
        when(planPromptBuilder.buildDifyPayload(anyString(), any(), anyInt())).thenReturn(Map.of());
        JsonNode response = objectMapper.readTree("""
                {"data":{"status":"succeeded"},
                 "plan_llm_output":{
                   "breakfast_foods":[{"name":"蛋","amount":"1","calories":70,"gi_level":"low"}],
                   "lunch_foods":[{"name":"饭","amount":"1","calories":100,"gi_level":"low"}],
                   "dinner_foods":[{"name":"菜","amount":"1","calories":50,"gi_level":"low"}],
                   "snack_foods":[{"name":"果","amount":"1","calories":60,"gi_level":"low"}],
                   "exercise_items":[{"type":"走","duration":"10分钟","frequency":"每日","intensity":"中","calories_burned":1,"caution":""}]
                 }}
                """);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(response);
        assertNotNull(invokeGeneratePlanContent(planServiceWithDify, "u_1", Map.of(), 1800));
    }

    @Test
    void generatePlanContent_outputsDietPlanDirectly() throws Exception {
        when(planPromptBuilder.buildDifyPayload(anyString(), any(), anyInt())).thenReturn(Map.of());
        JsonNode response = objectMapper.readTree("""
                {"outputs":{
                  "diet_plan":{"meal_plan":{"breakfast":{"foods":[{"name":"蛋","amount":"1","calories":1}]}}},
                  "exercise_plan":{"items":[{"type":"走","duration":"10分钟"}]},
                  "rest_plan":{"wake_up":"06:00"}
                }}
                """);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(response);
        assertNotNull(invokeGeneratePlanContent(planServiceWithDify, "u_1", Map.of(), 1800));
    }

    @Test
    void generatePlanContent_textWithLlmOutputKey() throws Exception {
        when(planPromptBuilder.buildDifyPayload(anyString(), any(), anyInt())).thenReturn(Map.of());
        String inner = objectMapper.writeValueAsString(Map.of(
                "plan_llm_output", objectMapper.readTree("""
                        {"breakfast_foods":[{"name":"蛋","amount":"1","calories":70,"gi_level":"low"}],
                         "lunch_foods":[{"name":"饭","amount":"1","calories":100,"gi_level":"low"}],
                         "dinner_foods":[{"name":"菜","amount":"1","calories":50,"gi_level":"low"}],
                         "snack_foods":[{"name":"果","amount":"1","calories":60,"gi_level":"low"}],
                         "exercise_items":[{"type":"走","duration":"10分钟","frequency":"每日","intensity":"中","calories_burned":1,"caution":""}]}
                        """)));
        JsonNode response = objectMapper.readTree(
                "{\"data\":{\"status\":\"succeeded\",\"outputs\":{\"text\":" + objectMapper.writeValueAsString(inner) + "}}}");
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(response);
        assertNotNull(invokeGeneratePlanContent(planServiceWithDify, "u_1", Map.of(), 1800));
    }

    @Test
    void generatePlanContent_invalidTextOutput() throws Exception {
        when(planPromptBuilder.buildDifyPayload(anyString(), any(), anyInt())).thenReturn(Map.of());
        JsonNode response = objectMapper.readTree(
                "{\"data\":{\"status\":\"succeeded\",\"outputs\":{\"text\":\"not-json\"}}}");
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(response);
        assertThrows(BusinessException.class,
                () -> invokeGeneratePlanContent(planServiceWithDify, "u_1", Map.of(), 1800));
    }

    @Test
    void generatePlanContent_longErrorPreview() throws Exception {
        when(planPromptBuilder.buildDifyPayload(anyString(), any(), anyInt())).thenReturn(Map.of());
        String longField = "x".repeat(900);
        JsonNode response = objectMapper.readTree(
                "{\"data\":{\"status\":\"succeeded\",\"outputs\":{\"" + longField + "\":\"y\"}}}");
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(response);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> invokeGeneratePlanContent(planServiceWithDify, "u_1", Map.of(), 1800));
        assertTrue(ex.getMessage().contains("..."));
    }

    @Test
    void extractLlmOutput_andDeepHealthPlan_paths() throws Exception {
        Method extractLlm = PlanService.class.getDeclaredMethod("extractLlmOutput", JsonNode.class);
        extractLlm.setAccessible(true);
        Method extractDeep = PlanService.class.getDeclaredMethod("extractDeepHealthPlan", JsonNode.class);
        extractDeep.setAccessible(true);

        JsonNode outputsShallow = objectMapper.readTree(
                "{\"data\":{\"outputs\":{\"breakfast_foods\":[{\"name\":\"蛋\",\"amount\":\"1\",\"calories\":1,\"gi_level\":\"low\"}],"
                        + "\"lunch_foods\":[{\"name\":\"饭\",\"amount\":\"1\",\"calories\":1,\"gi_level\":\"low\"}],"
                        + "\"dinner_foods\":[{\"name\":\"菜\",\"amount\":\"1\",\"calories\":1,\"gi_level\":\"low\"}],"
                        + "\"snack_foods\":[{\"name\":\"果\",\"amount\":\"1\",\"calories\":1,\"gi_level\":\"low\"}],"
                        + "\"exercise_items\":[{\"type\":\"走\",\"duration\":\"10分钟\",\"frequency\":\"每日\",\"intensity\":\"中\",\"calories_burned\":1,\"caution\":\"\"}]}}}");
        assertFalse(((JsonNode) extractLlm.invoke(planServiceWithDify, outputsShallow)).isMissingNode());

        JsonNode healthAtOutputs = objectMapper.readTree(
                "{\"data\":{\"outputs\":{\"health_plan\":{\"diet_plan\":{\"meal_plan\":{}}}}}}");
        assertFalse(((JsonNode) extractDeep.invoke(planServiceWithDify, healthAtOutputs)).isMissingNode());
    }

    @Test
    void generatePlanContent_outputsRootLlmOutput() throws Exception {
        when(planPromptBuilder.buildDifyPayload(anyString(), any(), anyInt())).thenReturn(Map.of());
        JsonNode response = objectMapper.readTree("""
                {"outputs":{"plan_llm_output":{
                  "breakfast_foods":[{"name":"蛋","amount":"1","calories":70,"gi_level":"low"}],
                  "lunch_foods":[{"name":"饭","amount":"1","calories":100,"gi_level":"low"}],
                  "dinner_foods":[{"name":"菜","amount":"1","calories":50,"gi_level":"low"}],
                  "snack_foods":[{"name":"果","amount":"1","calories":60,"gi_level":"low"}],
                  "exercise_items":[{"type":"走","duration":"10分钟","frequency":"每日","intensity":"中","calories_burned":1,"caution":""}]
                }}}
                """);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(response);
        assertNotNull(invokeGeneratePlanContent(planServiceWithDify, "u_1", Map.of(), 1800));
    }

    @Test
    void generatePlanContent_outputsRootHealthPlan() throws Exception {
        when(planPromptBuilder.buildDifyPayload(anyString(), any(), anyInt())).thenReturn(Map.of());
        JsonNode response = objectMapper.readTree("""
                {"outputs":{"health_plan":{
                  "diet_plan":{"meal_plan":{"breakfast":{"foods":[{"name":"蛋","amount":"1","calories":1}]}}},
                  "exercise_plan":{"items":[{"type":"走","duration":"10分钟"}]},
                  "rest_plan":{"wake_up":"06:00"}
                }}}
                """);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(response);
        assertNotNull(invokeGeneratePlanContent(planServiceWithDify, "u_1", Map.of(), 1800));
    }

    @Test
    void generatePlanContent_textWithHealthPlanKey() throws Exception {
        when(planPromptBuilder.buildDifyPayload(anyString(), any(), anyInt())).thenReturn(Map.of());
        String inner = objectMapper.writeValueAsString(Map.of(
                "health_plan", Map.of(
                        "diet_plan", Map.of("meal_plan", Map.of("breakfast", Map.of("foods", List.of(Map.of("name", "蛋", "amount", "1", "calories", 1))))),
                        "exercise_plan", Map.of("items", List.of(Map.of("type", "走", "duration", "10分钟"))),
                        "rest_plan", Map.of("wake_up", "06:00"))));
        JsonNode response = objectMapper.readTree(
                "{\"outputs\":{\"text\":" + objectMapper.writeValueAsString(inner) + "}}");
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(response);
        assertNotNull(invokeGeneratePlanContent(planServiceWithDify, "u_1", Map.of(), 1800));
    }

    @Test
    void generatePlanContent_textWithShallowRoot() throws Exception {
        when(planPromptBuilder.buildDifyPayload(anyString(), any(), anyInt())).thenReturn(Map.of());
        String inner = """
                {"breakfast_foods":[{"name":"蛋","amount":"1","calories":70,"gi_level":"low"}],
                 "lunch_foods":[{"name":"饭","amount":"1","calories":100,"gi_level":"low"}],
                 "dinner_foods":[{"name":"菜","amount":"1","calories":50,"gi_level":"low"}],
                 "snack_foods":[{"name":"果","amount":"1","calories":60,"gi_level":"low"}],
                 "exercise_items":[{"type":"走","duration":"10分钟","frequency":"每日","intensity":"中","calories_burned":1,"caution":""}]}
                """;
        JsonNode response = objectMapper.readTree(
                "{\"outputs\":{\"text\":" + objectMapper.writeValueAsString(inner) + "}}");
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(response);
        assertNotNull(invokeGeneratePlanContent(planServiceWithDify, "u_1", Map.of(), 1800));
    }

    @Test
    void generatePlanContent_nullDifyApiKey() throws Exception {
        PlanService nullKeyService = new PlanService(
                healthPlanMapper, planPersistenceService, userProfileService,
                calorieCalculator, planPromptBuilder, difyClient, userServiceClient, objectMapper,
                "http://dify", null, "blocking", "");
        Map<String, Object> content = invokeGeneratePlanContent(nullKeyService, "u_1", Map.of(), 1800);
        assertTrue(content.containsKey("dietPlan"));
    }

    @Test
    void generatePlanContent_healthPlanCamelCaseNested() throws Exception {
        when(planPromptBuilder.buildDifyPayload(anyString(), any(), anyInt())).thenReturn(Map.of());
        JsonNode response = objectMapper.readTree("""
                {"data":{"status":"succeeded","outputs":{"health_plan":{
                  "dietPlan":{"meal_plan":{"breakfast":{"foods":[{"name":"蛋","amount":"1","calories":1}]}}},
                  "exercisePlan":{"items":[{"type":"走","duration":"10分钟"}]},
                  "restPlan":{"sleep":"22:00"},
                  "medicationNote":"camel"
                }}}}
                """);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(response);

        Map<String, Object> content = invokeGeneratePlanContent(planServiceWithDify, "u_1", Map.of(), 1800);
        assertEquals("camel", content.get("medicationNote"));
    }

    @Test
    void parseDifyPlanResponse_llmOutputNotShallow() throws Exception {
        Method method = PlanService.class.getDeclaredMethod(
                "parseDifyPlanResponse", JsonNode.class, Map.class);
        method.setAccessible(true);
        JsonNode response = objectMapper.readTree("""
                {"data":{"outputs":{"plan_llm_output":{
                  "diet_plan":{"meal_plan":{"breakfast":{"foods":[{"name":"蛋","amount":"1","calories":1}]}}},
                  "exercise_plan":{"items":[{"type":"走","duration":"10分钟"}]},
                  "rest_plan":{"wake_up":"06:00"}
                }}}}
                """);
        Map<String, Object> fallback = Map.of(
                "dietPlan", Map.of(), "exercisePlan", Map.of(), "restPlan", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) method.invoke(
                planServiceWithDify, response, fallback);
        assertNull(parsed);
    }

    @Test
    void assertWorkflowSucceeded_blankDataStatusUsesRoot() throws Exception {
        Method method = PlanService.class.getDeclaredMethod("assertWorkflowSucceeded", JsonNode.class);
        method.setAccessible(true);
        JsonNode response = objectMapper.readTree("{\"data\":{\"status\":\"\"},\"status\":\"succeeded\"}");
        assertDoesNotThrow(() -> method.invoke(planServiceWithDify, response));
    }

    @Test
    void assertWorkflowSucceeded_noStatus() throws Exception {
        Method method = PlanService.class.getDeclaredMethod("assertWorkflowSucceeded", JsonNode.class);
        method.setAccessible(true);
        JsonNode response = objectMapper.readTree("{\"data\":{\"outputs\":{}}}");
        assertDoesNotThrow(() -> method.invoke(planServiceWithDify, response));
    }

    @Test
    void toggleFavorite_nullPlanUserId() {
        HealthPlan plan = samplePlan();
        plan.setUserId(null);
        plan.setIsFavorite(0);
        when(healthPlanMapper.findById("plan_1")).thenReturn(plan);

        Map<String, Object> result = planServiceNoDify.toggleFavorite("u_1", "plan_1");
        assertEquals(true, result.get("favorited"));
        verify(healthPlanMapper).updateFavorite("plan_1", 1);
    }

    @Test
    void getDetail_noAiRawResponse() {
        HealthPlan plan = samplePlan();
        plan.setAiRawResponse(null);
        when(healthPlanMapper.findById("plan_1")).thenReturn(plan);

        Map<String, Object> detail = planServiceNoDify.getDetail("plan_1");
        assertEquals("plan_1", detail.get("planId"));
        assertFalse(detail.containsKey("dietPlan"));
    }

    @Test
    void getDetail_aiRawWithoutSummary() throws Exception {
        HealthPlan plan = samplePlan();
        plan.setSummary("DB摘要");
        plan.setAiRawResponse(objectMapper.writeValueAsString(Map.of(
                "dietPlan", Map.of("k", "v"),
                "exercisePlan", Map.of("e", 1),
                "restPlan", Map.of("r", 1))));
        when(healthPlanMapper.findById("plan_1")).thenReturn(plan);

        Map<String, Object> detail = planServiceNoDify.getDetail("plan_1");
        assertEquals("DB摘要", detail.get("summary"));
    }

    @Test
    void extractDeepHealthPlan_outputsDietPlanCamelCase() throws Exception {
        Method method = PlanService.class.getDeclaredMethod("extractDeepHealthPlan", JsonNode.class);
        method.setAccessible(true);
        JsonNode response = objectMapper.readTree("""
                {"outputs":{"dietPlan":{"meal_plan":{}},"exercisePlan":{"items":[]},"restPlan":{"sleep":"22:00"}}}
                """);
        JsonNode plan = (JsonNode) method.invoke(planServiceWithDify, response);
        assertFalse(plan.isMissingNode());
        assertTrue(plan.has("dietPlan"));
    }

    @Test
    void extractDeepHealthPlan_textWithDietPlanCamelCase() throws Exception {
        Method method = PlanService.class.getDeclaredMethod("extractDeepHealthPlan", JsonNode.class);
        method.setAccessible(true);
        String inner = objectMapper.writeValueAsString(Map.of(
                "dietPlan", Map.of("meal_plan", Map.of("breakfast", Map.of("foods", List.of()))),
                "exercisePlan", Map.of("items", List.of(Map.of("type", "走", "duration", "10分钟"))),
                "restPlan", Map.of("wake_up", "06:00")));
        JsonNode response = objectMapper.readTree(
                "{\"outputs\":{\"text\":" + objectMapper.writeValueAsString(inner) + "}}");
        JsonNode plan = (JsonNode) method.invoke(planServiceWithDify, response);
        assertFalse(plan.isMissingNode());
        assertTrue(plan.has("dietPlan"));
    }

    @Test
    void generatePlanContent_difyFailedStatusAtRoot() throws Exception {
        when(planPromptBuilder.buildDifyPayload(anyString(), any(), anyInt())).thenReturn(Map.of());
        JsonNode response = objectMapper.readTree("{\"status\":\"failed\",\"error\":\"root boom\"}");
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(response);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> invokeGeneratePlanContent(planServiceWithDify, "u_1", Map.of(), 1800));
        assertTrue(ex.getMessage().contains("root boom"));
    }

    @Test
    void parseDifyPlanResponse_mixedCaseSectionKeys() throws Exception {
        Method method = PlanService.class.getDeclaredMethod(
                "parseDifyPlanResponse", JsonNode.class, Map.class);
        method.setAccessible(true);
        Map<String, Object> fallback = Map.of(
                "dietPlan", Map.of(), "exercisePlan", Map.of(), "restPlan", Map.of());

        JsonNode dietCamel = objectMapper.readTree("""
                {"health_plan":{
                  "dietPlan":{"meal_plan":{"breakfast":{"foods":[{"name":"蛋","amount":"1","calories":1}]}}},
                  "exercise_plan":{"items":[{"type":"走","duration":"10分钟"}]},
                  "rest_plan":{"wake_up":"06:00"}
                }}
                """);
        @SuppressWarnings("unchecked")
        Map<String, Object> parsedDiet = (Map<String, Object>) method.invoke(
                planServiceWithDify, dietCamel, fallback);
        assertNotNull(parsedDiet.get("dietPlan"));

        JsonNode exerciseCamel = objectMapper.readTree("""
                {"health_plan":{
                  "diet_plan":{"meal_plan":{"breakfast":{"foods":[{"name":"蛋","amount":"1","calories":1}]}}},
                  "exercisePlan":{"items":[{"type":"走","duration":"10分钟"}]},
                  "rest_plan":{"wake_up":"06:00"}
                }}
                """);
        @SuppressWarnings("unchecked")
        Map<String, Object> parsedExercise = (Map<String, Object>) method.invoke(
                planServiceWithDify, exerciseCamel, fallback);
        assertNotNull(parsedExercise.get("exercisePlan"));

        JsonNode restCamel = objectMapper.readTree("""
                {"health_plan":{
                  "diet_plan":{"meal_plan":{"breakfast":{"foods":[{"name":"蛋","amount":"1","calories":1}]}}},
                  "exercise_plan":{"items":[{"type":"走","duration":"10分钟"}]},
                  "restPlan":{"wake_up":"06:00"}
                }}
                """);
        @SuppressWarnings("unchecked")
        Map<String, Object> parsedRest = (Map<String, Object>) method.invoke(
                planServiceWithDify, restCamel, fallback);
        assertNotNull(parsedRest.get("restPlan"));
    }

    @Test
    void extractDeepHealthPlan_outputsDietPlanSnakeCase() throws Exception {
        Method method = PlanService.class.getDeclaredMethod("extractDeepHealthPlan", JsonNode.class);
        method.setAccessible(true);
        JsonNode response = objectMapper.readTree("""
                {"outputs":{"diet_plan":{"meal_plan":{}},"exercise_plan":{"items":[]},"rest_plan":{"sleep":"22:00"}}}
                """);
        JsonNode plan = (JsonNode) method.invoke(planServiceWithDify, response);
        assertFalse(plan.isMissingNode());
        assertTrue(plan.has("diet_plan"));
    }

    @Test
    void assertWorkflowSucceeded_explicitSucceededStatus() throws Exception {
        Method method = PlanService.class.getDeclaredMethod("assertWorkflowSucceeded", JsonNode.class);
        method.setAccessible(true);
        JsonNode response = objectMapper.readTree("{\"data\":{\"status\":\"succeeded\"}}");
        assertDoesNotThrow(() -> method.invoke(planServiceWithDify, response));
    }

    @Test
    void parseDifyPlanResponse_sectionKeyElseIfFalseBranches() throws Exception {
        Method method = PlanService.class.getDeclaredMethod(
                "parseDifyPlanResponse", JsonNode.class, Map.class);
        method.setAccessible(true);
        Map<String, Object> fallback = Map.of(
                "dietPlan", Map.of("fallback", true),
                "exercisePlan", Map.of("fallback", true),
                "restPlan", Map.of("fallback", true));

        JsonNode noDietKeys = objectMapper.readTree("""
                {"health_plan":{
                  "exercise_plan":{"items":[{"type":"走","duration":"10分钟"}]},
                  "rest_plan":{"wake_up":"06:00"}
                }}
                """);
        @SuppressWarnings("unchecked")
        Map<String, Object> parsedNoDiet = (Map<String, Object>) method.invoke(
                planServiceWithDify, noDietKeys, fallback);
        assertEquals(Map.of("fallback", true), parsedNoDiet.get("dietPlan"));

        JsonNode noExerciseKeys = objectMapper.readTree("""
                {"health_plan":{
                  "diet_plan":{"meal_plan":{"breakfast":{"foods":[{"name":"蛋","amount":"1","calories":1}]}}},
                  "rest_plan":{"wake_up":"06:00"}
                }}
                """);
        @SuppressWarnings("unchecked")
        Map<String, Object> parsedNoExercise = (Map<String, Object>) method.invoke(
                planServiceWithDify, noExerciseKeys, fallback);
        assertEquals(Map.of("fallback", true), parsedNoExercise.get("exercisePlan"));

        JsonNode noRestKeys = objectMapper.readTree("""
                {"health_plan":{
                  "diet_plan":{"meal_plan":{"breakfast":{"foods":[{"name":"蛋","amount":"1","calories":1}]}}},
                  "exercise_plan":{"items":[{"type":"走","duration":"10分钟"}]}
                }}
                """);
        @SuppressWarnings("unchecked")
        Map<String, Object> parsedNoRest = (Map<String, Object>) method.invoke(
                planServiceWithDify, noRestKeys, fallback);
        assertEquals(Map.of("fallback", true), parsedNoRest.get("restPlan"));
    }

    @Test
    void extractDeepHealthPlan_outputsWithoutDietKeys() throws Exception {
        Method method = PlanService.class.getDeclaredMethod("extractDeepHealthPlan", JsonNode.class);
        method.setAccessible(true);
        JsonNode response = objectMapper.readTree("""
                {"outputs":{"exercise_plan":{"items":[]},"rest_plan":{"sleep":"22:00"}}}
                """);
        assertTrue(((JsonNode) method.invoke(planServiceWithDify, response)).isMissingNode());
    }

    @Test
    void parseDifyPlanResponse_shallowWithoutRequiredSections() throws Exception {
        Method method = PlanService.class.getDeclaredMethod(
                "parseDifyPlanResponse", JsonNode.class, Map.class);
        method.setAccessible(true);
        JsonNode response = objectMapper.readTree("""
                {"data":{"outputs":{"plan_llm_output":{
                  "breakfast_foods":[{"name":"蛋","amount":"1","calories":70,"gi_level":"low"}],
                  "lunch_foods":[{"name":"饭","amount":"1","calories":100,"gi_level":"low"}],
                  "dinner_foods":[{"name":"菜","amount":"1","calories":50,"gi_level":"low"}],
                  "snack_foods":[{"name":"果","amount":"1","calories":60,"gi_level":"low"}],
                  "exercise_items":[{"type":"走","duration":"10分钟","frequency":"每日","intensity":"中","calories_burned":1,"caution":""}]
                }}}}
                """);
        Map<String, Object> fallback = Map.of(
                "dietPlan", Map.of(), "exercisePlan", Map.of(), "restPlan", Map.of());
        try (MockedStatic<DifyPlanLlmOutputAssembler> mocked = mockStatic(DifyPlanLlmOutputAssembler.class)) {
            mocked.when(() -> DifyPlanLlmOutputAssembler.isShallowLlmOutput(any())).thenCallRealMethod();
            mocked.when(() -> DifyPlanLlmOutputAssembler.assembleToPlanContent(any())).thenCallRealMethod();
            mocked.when(() -> DifyPlanLlmOutputAssembler.hasRequiredSections(any())).thenReturn(false);
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = (Map<String, Object>) method.invoke(
                    planServiceWithDify, response, fallback);
            assertNull(parsed);
        }
    }

    @Test
    void assertWorkflowSucceeded_blankRootStatus() throws Exception {
        Method method = PlanService.class.getDeclaredMethod("assertWorkflowSucceeded", JsonNode.class);
        method.setAccessible(true);
        JsonNode blankRoot = objectMapper.readTree("{\"status\":\"\"}");
        assertDoesNotThrow(() -> method.invoke(planServiceWithDify, blankRoot));

        JsonNode succeededMixedCase = objectMapper.readTree("{\"status\":\"SuCcEeded\"}");
        assertDoesNotThrow(() -> method.invoke(planServiceWithDify, succeededMixedCase));
    }

    @Test
    void extractDeepHealthPlan_noOutputs() throws Exception {
        Method method = PlanService.class.getDeclaredMethod("extractDeepHealthPlan", JsonNode.class);
        method.setAccessible(true);
        assertTrue(((JsonNode) method.invoke(planServiceWithDify, objectMapper.readTree("{}"))).isMissingNode());
    }

    @Test
    void parseDifyPlanResponse_camelCaseHealthPlan() throws Exception {
        Method method = PlanService.class.getDeclaredMethod(
                "parseDifyPlanResponse", JsonNode.class, Map.class);
        method.setAccessible(true);
        JsonNode response = objectMapper.readTree("""
                {"health_plan":{
                  "dietPlan":{"meal_plan":{"breakfast":{"foods":[{"name":"蛋","amount":"1","calories":1}]}}},
                  "exercisePlan":{"items":[{"type":"走","duration":"10分钟"}]},
                  "restPlan":{"wake_up":"06:00"},
                  "medicationNote":"note",
                  "summary":"sum"
                }}
                """);
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) method.invoke(
                planServiceWithDify, response, Map.of("dietPlan", Map.of(), "exercisePlan", Map.of(), "restPlan", Map.of()));
        assertEquals("note", parsed.get("medicationNote"));
        assertEquals("sum", parsed.get("summary"));
    }

    @Test
    void generatePlanContent_nonBusinessLastError() throws Exception {
        when(planPromptBuilder.buildDifyPayload(anyString(), any(), anyInt())).thenReturn(Map.of());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenThrow(new IllegalStateException("boom"))
                .thenThrow(new IllegalStateException("boom"))
                .thenThrow(new IllegalStateException("boom"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> invokeGeneratePlanContent(planServiceWithDify, "u_1", Map.of(), 1800));
        assertTrue(ex.getMessage().contains("boom"));
    }

    @Test
    void generatePlanStream_sseOnErrorCallback() throws Exception {
        when(calorieCalculator.calculateDailyCalories(any())).thenReturn(1800);
        when(planPersistenceService.savePlan(anyString(), any(), anyInt(), any())).thenReturn(samplePlan());

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class, (mock, context) -> {
            doAnswer(inv -> {
                java.util.function.Consumer<Throwable> handler = inv.getArgument(0);
                handler.accept(new RuntimeException("sse error"));
                return mock;
            }).when(mock).onError(any());
            doAnswer(inv -> null).when(mock).onTimeout(any());
            doNothing().when(mock).send(any(SseEmitter.SseEventBuilder.class));
            doNothing().when(mock).complete();
        })) {
            planServiceNoDify.generatePlanStream("u_1", Map.of());
            Thread.sleep(300);
        }
    }

    @Test
    void generatePlanStream_errorNullMessage() throws Exception {
        when(calorieCalculator.calculateDailyCalories(any())).thenThrow(new RuntimeException());

        try (MockedConstruction<SseEmitter> construction = mockConstruction(SseEmitter.class, (mock, context) -> {
            doAnswer(inv -> null).when(mock).onTimeout(any());
            doAnswer(inv -> null).when(mock).onError(any());
            doNothing().when(mock).send(any(SseEmitter.SseEventBuilder.class));
            doNothing().when(mock).complete();
        })) {
            planServiceNoDify.generatePlanStream("u_1", Map.of());
            Thread.sleep(500);
            verify(construction.constructed().get(0)).complete();
        }
    }

    @Test
    void extractDeepHealthPlan_textNotPlanJson() throws Exception {
        Method method = PlanService.class.getDeclaredMethod("extractDeepHealthPlan", JsonNode.class);
        method.setAccessible(true);
        String inner = objectMapper.writeValueAsString(Map.of("foo", "bar"));
        JsonNode response = objectMapper.readTree(
                "{\"data\":{\"outputs\":{\"text\":" + objectMapper.writeValueAsString(inner) + "}}}");
        assertTrue(((JsonNode) method.invoke(planServiceWithDify, response)).isMissingNode());
    }

    @Test
    void extractDeepHealthPlan_invalidText() throws Exception {
        Method method = PlanService.class.getDeclaredMethod("extractDeepHealthPlan", JsonNode.class);
        method.setAccessible(true);
        JsonNode response = objectMapper.readTree(
                "{\"outputs\":{\"text\":\"not-json\"}}");
        assertTrue(((JsonNode) method.invoke(planServiceWithDify, response)).isMissingNode());
    }

    @Test
    void generatePlanStream_errorSendSuccess() throws Exception {
        when(calorieCalculator.calculateDailyCalories(any())).thenThrow(new RuntimeException("calc fail"));

        try (MockedConstruction<SseEmitter> construction = mockConstruction(SseEmitter.class, (mock, context) -> {
            doAnswer(inv -> null).when(mock).onTimeout(any());
            doAnswer(inv -> null).when(mock).onError(any());
            doNothing().when(mock).send(any(SseEmitter.SseEventBuilder.class));
            doNothing().when(mock).complete();
        })) {
            planServiceNoDify.generatePlanStream("u_1", Map.of());
            Thread.sleep(500);
            verify(construction.constructed().get(0)).complete();
        }
    }

    @Test
    void generatePlanStream_errorSendIOException() throws Exception {
        when(calorieCalculator.calculateDailyCalories(any())).thenThrow(new RuntimeException("calc fail"));

        try (MockedConstruction<SseEmitter> construction = mockConstruction(SseEmitter.class, (mock, context) -> {
            doAnswer(inv -> null).when(mock).onTimeout(any());
            doAnswer(inv -> null).when(mock).onError(any());
            doThrow(new IOException("broken pipe")).when(mock).send(any(SseEmitter.SseEventBuilder.class));
            doAnswer(inv -> null).when(mock).completeWithError(any(Throwable.class));
        })) {
            planServiceNoDify.generatePlanStream("u_1", Map.of());
            Thread.sleep(500);
            verify(construction.constructed().get(0)).completeWithError(any(RuntimeException.class));
        }
    }

    @Test
    void generatePlanStream_success() throws Exception {
        when(calorieCalculator.calculateDailyCalories(any())).thenReturn(1800);
        when(planPersistenceService.savePlan(anyString(), any(), anyInt(), any())).thenReturn(samplePlan());

        planServiceNoDify.generatePlanStream("u_1", Map.of("height", 170, "weight", 65));
        Thread.sleep(1500);

        verify(planPersistenceService).savePlan(anyString(), any(), eq(1800), any());
    }

    @Test
    void generatePlanStream_error() throws Exception {
        when(calorieCalculator.calculateDailyCalories(any())).thenThrow(new RuntimeException("calc fail"));

        planServiceNoDify.generatePlanStream("u_1", Map.of());
        Thread.sleep(1500);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeGeneratePlanContent(PlanService service, String userId,
                                                          Map<String, Object> profile, int calories) throws Exception {
        Method method = PlanService.class.getDeclaredMethod(
                "generatePlanContent", String.class, Map.class, int.class);
        method.setAccessible(true);
        try {
            return (Map<String, Object>) method.invoke(service, userId, profile, calories);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }

    private HealthPlan samplePlan() {
        HealthPlan plan = new HealthPlan();
        plan.setPlanId("plan_1");
        plan.setUserId("u_1");
        plan.setTitle("方案");
        plan.setSummary("摘要");
        plan.setDailyCalories(1800);
        plan.setMedicationNote("用药");
        plan.setVersion(1);
        plan.setIsFavorite(0);
        plan.setGeneratedAt(LocalDateTime.now());
        return plan;
    }
}
