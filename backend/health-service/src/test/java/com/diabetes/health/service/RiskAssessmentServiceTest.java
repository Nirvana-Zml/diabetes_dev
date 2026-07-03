package com.diabetes.health.service;

import com.diabetes.common.client.UserServiceClient;
import com.diabetes.common.dify.DifyClient;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.health.dto.RiskAssessRequest;
import com.diabetes.health.entity.RiskAssessment;
import com.diabetes.health.entity.RiskAssessmentFactor;
import com.diabetes.health.entity.RiskAssessmentSuggestion;
import com.diabetes.health.mapper.RiskAssessmentMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("RiskAssessmentService 风险评估服务测试")
class RiskAssessmentServiceTest {

    @Mock
    private HealthRecordService healthRecordService;

    @Mock
    private RiskAssessmentMapper riskAssessmentMapper;

    @Mock
    private MedicalCalculator medicalCalculator;

    @Mock
    private DifyClient difyClient;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private ObjectMapper objectMapper;

    private RiskAssessmentService service;

    private ObjectMapper realObjectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        realObjectMapper = new ObjectMapper();
        service = new RiskAssessmentService(
                healthRecordService,
                riskAssessmentMapper,
                medicalCalculator,
                difyClient,
                userServiceClient,
                realObjectMapper,
                "http://localhost",
                "test-api-key",
                "",
                "blocking",
                "api",
                "auto",
                "inputs",
                "object",
                "test-internal-key");
    }

    @Test
    @DisplayName("getHistory - 获取风险评估历史")
    void getHistory() {
        RiskAssessment assessment = new RiskAssessment();
        assessment.setAssessmentId("asmt_001");
        assessment.setRiskLevel(2);
        assessment.setRiskScore(50);

        when(riskAssessmentMapper.findByUserId("user1", 0, 10)).thenReturn(List.of(assessment));
        when(riskAssessmentMapper.countByUserId("user1")).thenReturn(1);
        when(medicalCalculator.riskLevelName(2)).thenReturn("medium");

        Map<String, Object> result = service.getHistory("user1", 1, 10);

        assertEquals(1, result.get("total"));
        List<?> records = (List<?>) result.get("records");
        assertEquals(1, records.size());
        verify(riskAssessmentMapper).findByUserId("user1", 0, 10);
        verify(riskAssessmentMapper).countByUserId("user1");
    }

    @Test
    @DisplayName("getDetail - 获取评估详情")
    void getDetail() {
        RiskAssessment assessment = new RiskAssessment();
        assessment.setAssessmentId("asmt_001");
        assessment.setRiskLevel(2);
        assessment.setRiskScore(50);
        assessment.setBmiSnapshot(BigDecimal.valueOf(25.0));
        assessment.setGlucoseLevel(1);
        assessment.setReportSummary("测试报告");
        assessment.setConfidence(2);

        when(riskAssessmentMapper.findById("asmt_001")).thenReturn(assessment);
        when(riskAssessmentMapper.findFactors("asmt_001")).thenReturn(List.of());
        when(riskAssessmentMapper.findSuggestions("asmt_001")).thenReturn(List.of());
        when(medicalCalculator.riskLevelName(2)).thenReturn("medium");

        Map<String, Object> result = service.getDetail("asmt_001");

        assertNotNull(result);
        assertEquals("asmt_001", result.get("assessmentId"));
        assertEquals("medium", result.get("riskLevel"));
        assertEquals(50, result.get("riskScore"));
    }

    @Test
    @DisplayName("getDetail - 评估不存在返回 null")
    void getDetail_notFound() {
        when(riskAssessmentMapper.findById("asmt_none")).thenReturn(null);

        Map<String, Object> result = service.getDetail("asmt_none");

        assertNull(result);
    }

    @Test
    @DisplayName("getDifyWorkflowSpec - 获取工作流配置")
    void getDifyWorkflowSpec() {
        Map<String, Object> spec = service.getDifyWorkflowSpec();

        assertNotNull(spec);
        assertTrue(spec.containsKey("baseUrl") || spec.containsKey("apiKey") || spec.containsKey("inputFormat"));
    }

    @Test
    @DisplayName("assess - 完整风险评估流程")
    void assess_fullFlow() throws Exception {
        RiskAssessRequest request = createRiskAssessRequest();
        Map<String, Object> userProfile = Map.of("birth_date", "1990-01-01", "gender", 1);

        when(userServiceClient.getUserProfile("user1", "test-internal-key")).thenReturn(userProfile);
        when(medicalCalculator.calculateBmi(170f, 65f))
                .thenReturn(new MedicalCalculator.BmiResult(BigDecimal.valueOf(22.5), "normal"));
        when(medicalCalculator.evaluateGlucose(5.5f)).thenReturn(new MedicalCalculator.GlucoseResult(1, "normal"));
        when(medicalCalculator.calculateBaseRisk(any(), any(), any(), eq(36))).thenReturn(
                new MedicalCalculator.BaseRiskResult(15, List.of()));
        when(healthRecordService.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5)))
                .thenReturn("hr_001");
        when(medicalCalculator.mapRiskLevel(15)).thenReturn(1);
        when(medicalCalculator.mapRiskLevel(20)).thenReturn(1);
        when(medicalCalculator.riskLevelName(1)).thenReturn("low");

        String difyResponseJson = """
                {
                    "risk_assessment": {
                        "risk_score": 20,
                        "risk_level": "low",
                        "confidence": "high",
                        "analysis": "AI分析报告",
                        "glucose_level": "normal",
                        "risk_factors": [{"factor_code": "family", "name": "家族史", "weight": 15}],
                        "suggestions": [{"category": 1, "priority": 1, "content": "定期检查"}]
                    }
                }""";
        JsonNode difyResponse = realObjectMapper.readTree(difyResponseJson);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(difyResponse);

        when(riskAssessmentMapper.insert(any(RiskAssessment.class))).thenReturn(1);
        when(riskAssessmentMapper.insertFactor(any(RiskAssessmentFactor.class))).thenReturn(1);
        when(riskAssessmentMapper.insertSuggestion(any(RiskAssessmentSuggestion.class))).thenReturn(1);

        Map<String, Object> result = service.assess("user1", request);

        assertNotNull(result);
        assertEquals("low", result.get("riskLevel"));
        assertEquals(20, result.get("riskScore"));
        assertEquals("AI分析报告", result.get("reportSummary"));
        assertEquals("high", result.get("confidence"));
        verify(difyClient).runWorkflowBlocking(anyString(), eq("user1"), any(), eq("blocking"));
        verify(riskAssessmentMapper).insert(any(RiskAssessment.class));
    }

    @Test
    @DisplayName("assess - Dify响应缺少risk_assessment字段抛出异常")
    void assess_missingRiskAssessment() throws Exception {
        RiskAssessRequest request = createRiskAssessRequest();
        Map<String, Object> userProfile = Map.of("birth_date", "1990-01-01", "gender", 1);

        when(userServiceClient.getUserProfile("user1", "test-internal-key")).thenReturn(userProfile);
        when(medicalCalculator.calculateBmi(170f, 65f))
                .thenReturn(new MedicalCalculator.BmiResult(BigDecimal.valueOf(22.5), "normal"));
        when(medicalCalculator.evaluateGlucose(5.5f)).thenReturn(new MedicalCalculator.GlucoseResult(1, "normal"));
        when(medicalCalculator.calculateBaseRisk(any(), any(), any(), eq(36))).thenReturn(
                new MedicalCalculator.BaseRiskResult(0, List.of()));
        when(healthRecordService.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5)))
                .thenReturn("hr_001");

        String difyResponseJson = "{\"message\": \"success\"}";
        JsonNode difyResponse = realObjectMapper.readTree(difyResponseJson);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(difyResponse);

        assertThrows(BusinessException.class, () -> service.assess("user1", request));
    }

    @Test
    @DisplayName("resolveAge - 正常出生日期解析")
    void resolveAge_normal() {
        Map<String, Object> profile = Map.of("birth_date", "1990-01-01");
        assertEquals(36, service.resolveAge(profile));
    }

    @Test
    @DisplayName("resolveAge - birth_date为null返回0")
    void resolveAge_null() {
        Map<String, Object> profile = new HashMap<>();
        assertEquals(0, service.resolveAge(profile));
    }

    @Test
    @DisplayName("resolveAge - 无效日期格式返回0")
    void resolveAge_invalidFormat() {
        Map<String, Object> profile = Map.of("birth_date", "invalid-date");
        assertEquals(0, service.resolveAge(profile));
    }

    @Test
    @DisplayName("resolveGender - 男性(1)")
    void resolveGender_male() {
        Map<String, Object> profile = Map.of("gender", 1);
        assertEquals("male", service.resolveGender(profile));
    }

    @Test
    @DisplayName("resolveGender - 女性(2)")
    void resolveGender_female() {
        Map<String, Object> profile = Map.of("gender", 2);
        assertEquals("female", service.resolveGender(profile));
    }

    @Test
    @DisplayName("resolveGender - 未知性别(其他值)")
    void resolveGender_unknown() {
        Map<String, Object> profile = Map.of("gender", 3);
        assertEquals("unknown", service.resolveGender(profile));
    }

    @Test
    @DisplayName("resolveGender - gender为null")
    void resolveGender_null() {
        Map<String, Object> profile = new HashMap<>();
        assertEquals("unknown", service.resolveGender(profile));
    }

    @Test
    @DisplayName("extractRiskAssessment - risk_assessment在根节点")
    void extractRiskAssessment_root() throws Exception {
        String json = """
                {
                    "risk_assessment": {"risk_score": 50}
                }""";
        JsonNode response = realObjectMapper.readTree(json);
        JsonNode result = service.extractRiskAssessment(response);
        assertEquals(50, result.path("risk_score").asInt());
    }

    @Test
    @DisplayName("extractRiskAssessment - risk_assessment在data.outputs下")
    void extractRiskAssessment_dataOutputs() throws Exception {
        String json = """
                {
                    "data": {
                        "outputs": {
                            "risk_assessment": {"risk_score": 60}
                        }
                    }
                }""";
        JsonNode response = realObjectMapper.readTree(json);
        JsonNode result = service.extractRiskAssessment(response);
        assertEquals(60, result.path("risk_score").asInt());
    }

    @Test
    @DisplayName("extractRiskAssessment - risk_assessment在outputs下")
    void extractRiskAssessment_outputs() throws Exception {
        String json = """
                {
                    "outputs": {
                        "risk_assessment": {"risk_score": 70}
                    }
                }""";
        JsonNode response = realObjectMapper.readTree(json);
        JsonNode result = service.extractRiskAssessment(response);
        assertEquals(70, result.path("risk_score").asInt());
    }

    @Test
    @DisplayName("extractRiskAssessment - risk_assessment在text字段中")
    void extractRiskAssessment_inText() throws Exception {
        String textContent = "{\"risk_assessment\":{\"risk_score\":80}}";
        String json = "{\"data\":{\"outputs\":{\"text\":\"" + textContent.replace("\"", "\\\"") + "\"}}}";
        JsonNode response = realObjectMapper.readTree(json);
        JsonNode result = service.extractRiskAssessment(response);
        assertEquals(80, result.path("risk_score").asInt());
    }

    @Test
    @DisplayName("extractRiskAssessment - 直接在text中包含risk_score")
    void extractRiskAssessment_textDirectRiskScore() throws Exception {
        String textContent = "{\"risk_score\":90,\"risk_level\":\"high\"}";
        String json = "{\"outputs\":{\"text\":\"" + textContent.replace("\"", "\\\"") + "\"}}";
        JsonNode response = realObjectMapper.readTree(json);
        JsonNode result = service.extractRiskAssessment(response);
        assertEquals(90, result.path("risk_score").asInt());
    }

    @Test
    @DisplayName("extractRiskAssessment - 无法找到返回missingNode")
    void extractRiskAssessment_notFound() throws Exception {
        String json = "{\"message\": \"hello\"}";
        JsonNode response = realObjectMapper.readTree(json);
        JsonNode result = service.extractRiskAssessment(response);
        assertTrue(result.isMissingNode());
    }

    private RiskAssessRequest createRiskAssessRequest() {
        RiskAssessRequest request = new RiskAssessRequest();
        request.setHeight(170f);
        request.setWeight(65f);
        request.setFastingGlucose(5.5f);
        request.setFamilyHistory(true);
        return request;
    }

    @Test
    @DisplayName("构造函数 - null参数使用默认值")
    void constructor_nullDefaults() {
        RiskAssessmentService serviceWithNulls = new RiskAssessmentService(
                healthRecordService,
                riskAssessmentMapper,
                medicalCalculator,
                difyClient,
                userServiceClient,
                realObjectMapper,
                "http://localhost",
                "test-api-key",
                "",
                "blocking",
                null,
                null,
                null,
                null,
                "test-internal-key");

        assertNotNull(serviceWithNulls);
    }

    @Test
    @DisplayName("assess - AI响应过长被截断")
    void assess_longAiResponse() throws Exception {
        RiskAssessRequest request = createRiskAssessRequest();
        Map<String, Object> userProfile = Map.of("birth_date", "1990-01-01", "gender", 1);

        when(userServiceClient.getUserProfile("user1", "test-internal-key")).thenReturn(userProfile);
        when(medicalCalculator.calculateBmi(170f, 65f))
                .thenReturn(new MedicalCalculator.BmiResult(BigDecimal.valueOf(22.5), "normal"));
        when(medicalCalculator.evaluateGlucose(5.5f)).thenReturn(new MedicalCalculator.GlucoseResult(1, "normal"));
        when(medicalCalculator.calculateBaseRisk(any(), any(), any(), eq(36))).thenReturn(
                new MedicalCalculator.BaseRiskResult(0, List.of()));
        when(healthRecordService.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5)))
                .thenReturn("hr_001");
        when(medicalCalculator.riskLevelName(anyInt())).thenReturn("low");

        StringBuilder longAnalysis = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            longAnalysis.append("x");
        }
        String difyResponseJson = """
                {
                    "risk_assessment": {
                        "risk_score": 20,
                        "risk_level": "low",
                        "confidence": "high",
                        "analysis": "%s",
                        "glucose_level": "normal"
                    }
                }""".formatted(longAnalysis);
        JsonNode difyResponse = realObjectMapper.readTree(difyResponseJson);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(difyResponse);

        when(riskAssessmentMapper.insert(any(RiskAssessment.class))).thenAnswer(invocation -> {
            RiskAssessment assessment = invocation.getArgument(0);
            assertEquals(500, assessment.getReportSummary().length());
            return 1;
        });

        Map<String, Object> result = service.assess("user1", request);

        assertNotNull(result);
    }

    @Test
    @DisplayName("getDetail - 评估包含风险因素")
    void getDetail_withFactors() {
        RiskAssessment assessment = new RiskAssessment();
        assessment.setAssessmentId("asmt_001");
        assessment.setRiskLevel(2);
        assessment.setRiskScore(50);
        assessment.setBmiSnapshot(BigDecimal.valueOf(25.0));
        assessment.setGlucoseLevel(1);
        assessment.setReportSummary("测试报告");
        assessment.setConfidence(2);

        RiskAssessmentFactor factor = new RiskAssessmentFactor();
        factor.setFactorName("BMI超标");
        factor.setWeight(BigDecimal.valueOf(20));
        factor.setDescription("BMI超过24");

        when(riskAssessmentMapper.findById("asmt_001")).thenReturn(assessment);
        when(riskAssessmentMapper.findFactors("asmt_001")).thenReturn(List.of(factor));
        when(riskAssessmentMapper.findSuggestions("asmt_001")).thenReturn(List.of());
        when(medicalCalculator.riskLevelName(2)).thenReturn("medium");

        Map<String, Object> result = service.getDetail("asmt_001");

        assertNotNull(result);
        List<?> factors = (List<?>) result.get("factors");
        assertEquals(1, factors.size());
    }

    @Test
    @DisplayName("getDetail - 风险因素description为null")
    void getDetail_factorNullDescription() {
        RiskAssessment assessment = new RiskAssessment();
        assessment.setAssessmentId("asmt_001");
        assessment.setRiskLevel(2);
        assessment.setRiskScore(50);
        assessment.setBmiSnapshot(BigDecimal.valueOf(25.0));
        assessment.setGlucoseLevel(1);
        assessment.setConfidence(2);

        RiskAssessmentFactor factor = new RiskAssessmentFactor();
        factor.setFactorName("测试因素");
        factor.setWeight(null);
        factor.setDescription(null);

        when(riskAssessmentMapper.findById("asmt_001")).thenReturn(assessment);
        when(riskAssessmentMapper.findFactors("asmt_001")).thenReturn(List.of(factor));
        when(riskAssessmentMapper.findSuggestions("asmt_001")).thenReturn(List.of());
        when(medicalCalculator.riskLevelName(2)).thenReturn("medium");

        Map<String, Object> result = service.getDetail("asmt_001");

        assertNotNull(result);
        List<?> factors = (List<?>) result.get("factors");
        assertEquals("", ((Map<?, ?>) factors.get(0)).get("description"));
    }

    @Test
    @DisplayName("invokeWorkflowApi - apiKey为空抛出异常")
    void invokeWorkflowApi_emptyApiKey() {
        RiskAssessmentService serviceWithoutApiKey = new RiskAssessmentService(
                healthRecordService,
                riskAssessmentMapper,
                medicalCalculator,
                difyClient,
                userServiceClient,
                realObjectMapper,
                "http://localhost",
                "",
                "",
                "blocking",
                "api",
                "auto",
                "inputs",
                "object",
                "test-internal-key");

        assertThrows(BusinessException.class, () -> {
            try {
                String json = "{\"risk_assessment\":{\"risk_score\":50}}";
                JsonNode response = realObjectMapper.readTree(json);
                when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(response);
                RiskAssessRequest request = createRiskAssessRequest();
                Map<String, Object> userProfile = Map.of("birth_date", "1990-01-01", "gender", 1);
                when(userServiceClient.getUserProfile("user1", "test-internal-key")).thenReturn(userProfile);
                when(medicalCalculator.calculateBmi(170f, 65f))
                        .thenReturn(new MedicalCalculator.BmiResult(BigDecimal.valueOf(22.5), "normal"));
                when(medicalCalculator.evaluateGlucose(5.5f))
                        .thenReturn(new MedicalCalculator.GlucoseResult(1, "normal"));
                when(medicalCalculator.calculateBaseRisk(any(), any(), any(), eq(36))).thenReturn(
                        new MedicalCalculator.BaseRiskResult(0, List.of()));
                when(healthRecordService.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5)))
                        .thenReturn("hr_001");
                serviceWithoutApiKey.assess("user1", request);
            } catch (BusinessException e) {
                assertTrue(e.getMessage().contains("api-key"));
                throw e;
            }
        });
    }

    @Test
    @DisplayName("extractRiskAssessment - text字段解析异常被捕获")
    void extractRiskAssessment_textParseError() throws Exception {
        String json = "{\"data\":{\"outputs\":{\"text\":\"not a json\"}}}";
        JsonNode response = realObjectMapper.readTree(json);
        JsonNode result = service.extractRiskAssessment(response);
        assertTrue(result.isMissingNode());
    }

    @Test
    @DisplayName("assess - 风险因素字段为空")
    void assess_emptyRiskFactors() throws Exception {
        RiskAssessRequest request = createRiskAssessRequest();
        Map<String, Object> userProfile = Map.of("birth_date", "1990-01-01", "gender", 1);

        when(userServiceClient.getUserProfile("user1", "test-internal-key")).thenReturn(userProfile);
        when(medicalCalculator.calculateBmi(170f, 65f))
                .thenReturn(new MedicalCalculator.BmiResult(BigDecimal.valueOf(22.5), "normal"));
        when(medicalCalculator.evaluateGlucose(5.5f)).thenReturn(new MedicalCalculator.GlucoseResult(1, "normal"));
        when(medicalCalculator.calculateBaseRisk(any(), any(), any(), eq(36))).thenReturn(
                new MedicalCalculator.BaseRiskResult(0, List.of()));
        when(healthRecordService.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5)))
                .thenReturn("hr_001");
        when(medicalCalculator.riskLevelName(1)).thenReturn("low");

        String difyResponseJson = """
                {
                    "risk_assessment": {
                        "risk_score": 20,
                        "risk_level": "low"
                    }
                }""";
        JsonNode difyResponse = realObjectMapper.readTree(difyResponseJson);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(difyResponse);

        when(riskAssessmentMapper.insert(any(RiskAssessment.class))).thenReturn(1);

        Map<String, Object> result = service.assess("user1", request);

        assertNotNull(result);
        List<?> factors = (List<?>) result.get("factors");
        assertTrue(factors.isEmpty());
    }

    @Test
    @DisplayName("extractRiskAssessment - text字段包含risk_score但无risk_assessment")
    void extractRiskAssessment_textHasRiskScoreOnly() throws Exception {
        String textContent = "{\"risk_score\":95,\"risk_level\":\"high\",\"analysis\":\"直接风险评估\"}";
        String json = "{\"data\":{\"outputs\":{\"text\":\"" + textContent.replace("\"", "\\\"") + "\"}}}";
        JsonNode response = realObjectMapper.readTree(json);
        JsonNode result = service.extractRiskAssessment(response);
        assertEquals(95, result.path("risk_score").asInt());
        assertEquals("high", result.path("risk_level").asText());
    }

    @Test
    @DisplayName("buildWorkflowInputs - flat模式")
    void buildWorkflowInputs_flatMode() {
        RiskAssessmentService serviceFlat = new RiskAssessmentService(
                healthRecordService,
                riskAssessmentMapper,
                medicalCalculator,
                difyClient,
                userServiceClient,
                realObjectMapper,
                "http://localhost",
                "test-api-key",
                "",
                "blocking",
                "api",
                "auto",
                "flat",
                "object",
                "test-internal-key");

        Map<String, Object> spec = serviceFlat.getDifyWorkflowSpec();
        assertNotNull(spec);
    }

    @Test
    @DisplayName("buildWorkflowInputs - empty inputVarName")
    void buildWorkflowInputs_emptyVarName() {
        RiskAssessmentService serviceEmpty = new RiskAssessmentService(
                healthRecordService,
                riskAssessmentMapper,
                medicalCalculator,
                difyClient,
                userServiceClient,
                realObjectMapper,
                "http://localhost",
                "test-api-key",
                "",
                "blocking",
                "api",
                "auto",
                "",
                "object",
                "test-internal-key");

        Map<String, Object> spec = serviceEmpty.getDifyWorkflowSpec();
        assertNotNull(spec);
    }

    @Test
    @DisplayName("assess - 风险因素字段为空或null")
    void assess_factorFieldsNull() throws Exception {
        RiskAssessRequest request = createRiskAssessRequest();
        Map<String, Object> userProfile = Map.of("birth_date", "1990-01-01", "gender", 1);

        when(userServiceClient.getUserProfile("user1", "test-internal-key")).thenReturn(userProfile);
        when(medicalCalculator.calculateBmi(170f, 65f))
                .thenReturn(new MedicalCalculator.BmiResult(BigDecimal.valueOf(22.5), "normal"));
        when(medicalCalculator.evaluateGlucose(5.5f)).thenReturn(new MedicalCalculator.GlucoseResult(1, "normal"));
        when(medicalCalculator.calculateBaseRisk(any(), any(), any(), eq(36))).thenReturn(
                new MedicalCalculator.BaseRiskResult(0, List.of()));
        when(healthRecordService.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5)))
                .thenReturn("hr_001");
        when(medicalCalculator.riskLevelName(1)).thenReturn("low");

        String difyResponseJson = """
                {
                    "risk_assessment": {
                        "risk_score": 20,
                        "risk_level": "low",
                        "risk_factors": [{"factor_code": null, "name": "测试", "weight": null, "factor_level": null}]
                    }
                }""";
        JsonNode difyResponse = realObjectMapper.readTree(difyResponseJson);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(difyResponse);

        when(riskAssessmentMapper.insert(any(RiskAssessment.class))).thenReturn(1);
        when(riskAssessmentMapper.insertFactor(any(RiskAssessmentFactor.class))).thenReturn(1);

        Map<String, Object> result = service.assess("user1", request);

        assertNotNull(result);
    }

    @Test
    @DisplayName("callDify - webhook模式调用成功")
    void callDify_webhookMode() throws Exception {
        RiskAssessmentService serviceWebhook = new RiskAssessmentService(
                healthRecordService,
                riskAssessmentMapper,
                medicalCalculator,
                difyClient,
                userServiceClient,
                realObjectMapper,
                "http://localhost",
                "test-api-key",
                "/webhook/test",
                "blocking",
                "webhook",
                "auto",
                "inputs",
                "object",
                "test-internal-key");

        RiskAssessRequest request = createRiskAssessRequest();
        Map<String, Object> userProfile = Map.of("birth_date", "1990-01-01", "gender", 1);

        when(userServiceClient.getUserProfile("user1", "test-internal-key")).thenReturn(userProfile);
        when(medicalCalculator.calculateBmi(170f, 65f))
                .thenReturn(new MedicalCalculator.BmiResult(BigDecimal.valueOf(22.5), "normal"));
        when(medicalCalculator.evaluateGlucose(5.5f)).thenReturn(new MedicalCalculator.GlucoseResult(1, "normal"));
        when(medicalCalculator.calculateBaseRisk(any(), any(), any(), eq(36))).thenReturn(
                new MedicalCalculator.BaseRiskResult(0, List.of()));
        when(healthRecordService.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5)))
                .thenReturn("hr_001");
        when(medicalCalculator.riskLevelName(1)).thenReturn("low");

        String difyResponseJson = """
                {
                    "risk_assessment": {
                        "risk_score": 20,
                        "risk_level": "low"
                    }
                }""";
        JsonNode difyResponse = realObjectMapper.readTree(difyResponseJson);
        when(difyClient.triggerWebhook(eq("/webhook/test"), any())).thenReturn(difyResponse);

        when(riskAssessmentMapper.insert(any(RiskAssessment.class))).thenReturn(1);

        Map<String, Object> result = serviceWebhook.assess("user1", request);

        assertNotNull(result);
        verify(difyClient).triggerWebhook(eq("/webhook/test"), any());
    }

    @Test
    @DisplayName("callDify - webhook模式webhookPath为空抛出异常")
    void callDify_webhookEmptyPath() {
        RiskAssessmentService serviceWebhook = new RiskAssessmentService(
                healthRecordService,
                riskAssessmentMapper,
                medicalCalculator,
                difyClient,
                userServiceClient,
                realObjectMapper,
                "http://localhost",
                "test-api-key",
                "",
                "blocking",
                "webhook",
                "auto",
                "inputs",
                "object",
                "test-internal-key");

        assertThrows(BusinessException.class, () -> {
            RiskAssessRequest request = createRiskAssessRequest();
            Map<String, Object> userProfile = Map.of("birth_date", "1990-01-01", "gender", 1);
            when(userServiceClient.getUserProfile("user1", "test-internal-key")).thenReturn(userProfile);
            when(medicalCalculator.calculateBmi(170f, 65f))
                    .thenReturn(new MedicalCalculator.BmiResult(BigDecimal.valueOf(22.5), "normal"));
            when(medicalCalculator.evaluateGlucose(5.5f)).thenReturn(new MedicalCalculator.GlucoseResult(1, "normal"));
            when(medicalCalculator.calculateBaseRisk(any(), any(), any(), eq(36))).thenReturn(
                    new MedicalCalculator.BaseRiskResult(0, List.of()));
            when(healthRecordService.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5)))
                    .thenReturn("hr_001");
            serviceWebhook.assess("user1", request);
        });
    }

    @Test
    @DisplayName("callDify - webhook-first模式webhook失败后重试API")
    void callDify_webhookFirstRetryApi() throws Exception {
        RiskAssessmentService serviceWebhookFirst = new RiskAssessmentService(
                healthRecordService,
                riskAssessmentMapper,
                medicalCalculator,
                difyClient,
                userServiceClient,
                realObjectMapper,
                "http://localhost",
                "test-api-key",
                "/webhook/test",
                "blocking",
                "webhook-first",
                "auto",
                "inputs",
                "object",
                "test-internal-key");

        RiskAssessRequest request = createRiskAssessRequest();
        Map<String, Object> userProfile = Map.of("birth_date", "1990-01-01", "gender", 1);

        when(userServiceClient.getUserProfile("user1", "test-internal-key")).thenReturn(userProfile);
        when(medicalCalculator.calculateBmi(170f, 65f))
                .thenReturn(new MedicalCalculator.BmiResult(BigDecimal.valueOf(22.5), "normal"));
        when(medicalCalculator.evaluateGlucose(5.5f)).thenReturn(new MedicalCalculator.GlucoseResult(1, "normal"));
        when(medicalCalculator.calculateBaseRisk(any(), any(), any(), eq(36))).thenReturn(
                new MedicalCalculator.BaseRiskResult(0, List.of()));
        when(healthRecordService.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5)))
                .thenReturn("hr_001");
        when(medicalCalculator.riskLevelName(1)).thenReturn("low");

        when(difyClient.triggerWebhook(eq("/webhook/test"), any())).thenThrow(new RuntimeException("webhook error"));

        String difyResponseJson = """
                {
                    "risk_assessment": {
                        "risk_score": 20,
                        "risk_level": "low"
                    }
                }""";
        JsonNode difyResponse = realObjectMapper.readTree(difyResponseJson);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(difyResponse);

        when(riskAssessmentMapper.insert(any(RiskAssessment.class))).thenReturn(1);

        Map<String, Object> result = serviceWebhookFirst.assess("user1", request);

        assertNotNull(result);
        verify(difyClient).triggerWebhook(eq("/webhook/test"), any());
        verify(difyClient).runWorkflowBlocking(anyString(), eq("user1"), any(), eq("blocking"));
    }

    @Test
    @DisplayName("callDify - webhook模式webhook失败抛出异常")
    void callDify_webhookModeFailure() {
        RiskAssessmentService serviceWebhook = new RiskAssessmentService(
                healthRecordService,
                riskAssessmentMapper,
                medicalCalculator,
                difyClient,
                userServiceClient,
                realObjectMapper,
                "http://localhost",
                "test-api-key",
                "/webhook/test",
                "blocking",
                "webhook",
                "auto",
                "inputs",
                "object",
                "test-internal-key");

        assertThrows(BusinessException.class, () -> {
            RiskAssessRequest request = createRiskAssessRequest();
            Map<String, Object> userProfile = Map.of("birth_date", "1990-01-01", "gender", 1);
            when(userServiceClient.getUserProfile("user1", "test-internal-key")).thenReturn(userProfile);
            when(medicalCalculator.calculateBmi(170f, 65f))
                    .thenReturn(new MedicalCalculator.BmiResult(BigDecimal.valueOf(22.5), "normal"));
            when(medicalCalculator.evaluateGlucose(5.5f)).thenReturn(new MedicalCalculator.GlucoseResult(1, "normal"));
            when(medicalCalculator.calculateBaseRisk(any(), any(), any(), eq(36))).thenReturn(
                    new MedicalCalculator.BaseRiskResult(0, List.of()));
            when(healthRecordService.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5)))
                    .thenReturn("hr_001");
            when(difyClient.triggerWebhook(eq("/webhook/test"), any()))
                    .thenThrow(new RuntimeException("webhook error"));
            serviceWebhook.assess("user1", request);
        });
    }

    @Test
    @DisplayName("assess - Dify返回high风险等级")
    void assess_highRiskLevel() throws Exception {
        RiskAssessRequest request = createRiskAssessRequest();
        Map<String, Object> userProfile = Map.of("birth_date", "1990-01-01", "gender", 1);

        when(userServiceClient.getUserProfile("user1", "test-internal-key")).thenReturn(userProfile);
        when(medicalCalculator.calculateBmi(170f, 65f))
                .thenReturn(new MedicalCalculator.BmiResult(BigDecimal.valueOf(22.5), "normal"));
        when(medicalCalculator.evaluateGlucose(5.5f)).thenReturn(new MedicalCalculator.GlucoseResult(1, "normal"));
        when(medicalCalculator.calculateBaseRisk(any(), any(), any(), eq(36))).thenReturn(
                new MedicalCalculator.BaseRiskResult(0, List.of()));
        when(healthRecordService.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5)))
                .thenReturn("hr_001");
        when(medicalCalculator.riskLevelName(3)).thenReturn("high");

        String difyResponseJson = """
                {
                    "risk_assessment": {
                        "risk_score": 80,
                        "risk_level": "high",
                        "confidence": "high",
                        "analysis": "高风险",
                        "glucose_level": "diabetes"
                    }
                }""";
        JsonNode difyResponse = realObjectMapper.readTree(difyResponseJson);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(difyResponse);

        when(riskAssessmentMapper.insert(any(RiskAssessment.class))).thenReturn(1);

        Map<String, Object> result = service.assess("user1", request);

        assertNotNull(result);
        assertEquals("high", result.get("riskLevel"));
        assertEquals(80, result.get("riskScore"));
        assertEquals("diabetes", result.get("glucoseLevel"));
    }

    @Test
    @DisplayName("assess - Dify返回medium风险等级和low置信度")
    void assess_mediumRiskLowConfidence() throws Exception {
        RiskAssessRequest request = createRiskAssessRequest();
        Map<String, Object> userProfile = Map.of("birth_date", "1990-01-01", "gender", 1);

        when(userServiceClient.getUserProfile("user1", "test-internal-key")).thenReturn(userProfile);
        when(medicalCalculator.calculateBmi(170f, 65f))
                .thenReturn(new MedicalCalculator.BmiResult(BigDecimal.valueOf(22.5), "normal"));
        when(medicalCalculator.evaluateGlucose(5.5f)).thenReturn(new MedicalCalculator.GlucoseResult(1, "normal"));
        when(medicalCalculator.calculateBaseRisk(any(), any(), any(), eq(36))).thenReturn(
                new MedicalCalculator.BaseRiskResult(0, List.of()));
        when(healthRecordService.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5)))
                .thenReturn("hr_001");
        when(medicalCalculator.riskLevelName(2)).thenReturn("medium");

        String difyResponseJson = """
                {
                    "risk_assessment": {
                        "risk_score": 50,
                        "risk_level": "medium",
                        "confidence": "low",
                        "analysis": "中等风险",
                        "glucose_level": "prediabetes"
                    }
                }""";
        JsonNode difyResponse = realObjectMapper.readTree(difyResponseJson);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(difyResponse);

        when(riskAssessmentMapper.insert(any(RiskAssessment.class))).thenReturn(1);

        Map<String, Object> result = service.assess("user1", request);

        assertNotNull(result);
        assertEquals("medium", result.get("riskLevel"));
        assertEquals("low", result.get("confidence"));
        assertEquals("prediabetes", result.get("glucoseLevel"));
    }

    @Test
    @DisplayName("assess - Dify返回risk_factors包含factor_level")
    void assess_factorLevel() throws Exception {
        RiskAssessRequest request = createRiskAssessRequest();
        Map<String, Object> userProfile = Map.of("birth_date", "1990-01-01", "gender", 1);

        when(userServiceClient.getUserProfile("user1", "test-internal-key")).thenReturn(userProfile);
        when(medicalCalculator.calculateBmi(170f, 65f))
                .thenReturn(new MedicalCalculator.BmiResult(BigDecimal.valueOf(22.5), "normal"));
        when(medicalCalculator.evaluateGlucose(5.5f)).thenReturn(new MedicalCalculator.GlucoseResult(1, "normal"));
        when(medicalCalculator.calculateBaseRisk(any(), any(), any(), eq(36))).thenReturn(
                new MedicalCalculator.BaseRiskResult(0, List.of()));
        when(healthRecordService.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5)))
                .thenReturn("hr_001");
        when(medicalCalculator.riskLevelName(1)).thenReturn("low");

        String difyResponseJson = """
                {
                    "risk_assessment": {
                        "risk_score": 20,
                        "risk_level": "low",
                        "risk_factors": [{"factor_code": "bmi", "name": "BMI超标", "weight": 10, "factor_level": 2, "description": "BMI超过24"}]
                    }
                }""";
        JsonNode difyResponse = realObjectMapper.readTree(difyResponseJson);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(difyResponse);

        when(riskAssessmentMapper.insert(any(RiskAssessment.class))).thenReturn(1);
        when(riskAssessmentMapper.insertFactor(any(RiskAssessmentFactor.class))).thenReturn(1);

        Map<String, Object> result = service.assess("user1", request);

        assertNotNull(result);
    }

    @Test
    @DisplayName("getDetail - confidence为1和3的情况")
    void getDetail_confidenceLevels() {
        RiskAssessment assessment = new RiskAssessment();
        assessment.setAssessmentId("asmt_confidence");
        assessment.setRiskLevel(1);
        assessment.setRiskScore(30);
        assessment.setBmiSnapshot(BigDecimal.valueOf(22.0));
        assessment.setGlucoseLevel(1);
        assessment.setReportSummary("测试报告");
        assessment.setConfidence(1);

        when(riskAssessmentMapper.findById("asmt_confidence")).thenReturn(assessment);
        when(riskAssessmentMapper.findFactors("asmt_confidence")).thenReturn(List.of());
        when(riskAssessmentMapper.findSuggestions("asmt_confidence")).thenReturn(List.of());
        when(medicalCalculator.riskLevelName(1)).thenReturn("low");

        Map<String, Object> result = service.getDetail("asmt_confidence");
        assertEquals("low", result.get("confidence"));

        assessment.setConfidence(3);
        result = service.getDetail("asmt_confidence");
        assertEquals("high", result.get("confidence"));

        assessment.setConfidence(null);
        result = service.getDetail("asmt_confidence");
        assertEquals("medium", result.get("confidence"));
    }

    @Test
    @DisplayName("getDetail - glucoseLevel为2和3的情况")
    void getDetail_glucoseLevels() {
        RiskAssessment assessment = new RiskAssessment();
        assessment.setAssessmentId("asmt_glucose");
        assessment.setRiskLevel(1);
        assessment.setRiskScore(30);
        assessment.setBmiSnapshot(BigDecimal.valueOf(22.0));
        assessment.setGlucoseLevel(2);
        assessment.setReportSummary("测试报告");
        assessment.setConfidence(2);

        when(riskAssessmentMapper.findById("asmt_glucose")).thenReturn(assessment);
        when(riskAssessmentMapper.findFactors("asmt_glucose")).thenReturn(List.of());
        when(riskAssessmentMapper.findSuggestions("asmt_glucose")).thenReturn(List.of());
        when(medicalCalculator.riskLevelName(1)).thenReturn("low");

        Map<String, Object> result = service.getDetail("asmt_glucose");
        assertEquals("prediabetes", result.get("glucoseLevel"));

        assessment.setGlucoseLevel(3);
        result = service.getDetail("asmt_glucose");
        assertEquals("diabetes", result.get("glucoseLevel"));

        assessment.setGlucoseLevel(null);
        result = service.getDetail("asmt_glucose");
        assertEquals("normal", result.get("glucoseLevel"));
    }

    @Test
    @DisplayName("assess - Dify返回纯字符串suggestions")
    void assess_suggestionsAsString() throws Exception {
        RiskAssessRequest request = createRiskAssessRequest();
        Map<String, Object> userProfile = Map.of("birth_date", "1990-01-01", "gender", 1);

        when(userServiceClient.getUserProfile("user1", "test-internal-key")).thenReturn(userProfile);
        when(medicalCalculator.calculateBmi(170f, 65f))
                .thenReturn(new MedicalCalculator.BmiResult(BigDecimal.valueOf(22.5), "normal"));
        when(medicalCalculator.evaluateGlucose(5.5f)).thenReturn(new MedicalCalculator.GlucoseResult(1, "normal"));
        when(medicalCalculator.calculateBaseRisk(any(), any(), any(), eq(36))).thenReturn(
                new MedicalCalculator.BaseRiskResult(0, List.of()));
        when(healthRecordService.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5)))
                .thenReturn("hr_001");
        when(medicalCalculator.riskLevelName(1)).thenReturn("low");

        String difyResponseJson = """
                {
                    "risk_assessment": {
                        "risk_score": 20,
                        "risk_level": "low",
                        "suggestions": ["建议1", "建议2"]
                    }
                }""";
        JsonNode difyResponse = realObjectMapper.readTree(difyResponseJson);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(difyResponse);

        when(riskAssessmentMapper.insert(any(RiskAssessment.class))).thenReturn(1);
        when(riskAssessmentMapper.insertSuggestion(any(RiskAssessmentSuggestion.class))).thenReturn(1);

        Map<String, Object> result = service.assess("user1", request);

        assertNotNull(result);
        List<?> suggestions = (List<?>) result.get("suggestions");
        assertEquals(2, suggestions.size());
    }

    @Test
    @DisplayName("callDify - webhook模式下webhookPath为空字符串抛出异常")
    void callDify_webhookPathEmpty() {
        RiskAssessmentService serviceWebhookEmptyPath = new RiskAssessmentService(
                healthRecordService,
                riskAssessmentMapper,
                medicalCalculator,
                difyClient,
                userServiceClient,
                realObjectMapper,
                "http://localhost",
                "test-api-key",
                "",
                "blocking",
                "webhook",
                "auto",
                "inputs",
                "object",
                "test-internal-key");

        assertThrows(BusinessException.class, () -> {
            RiskAssessRequest request = createRiskAssessRequest();
            Map<String, Object> userProfile = Map.of("birth_date", "1990-01-01", "gender", 1);
            when(userServiceClient.getUserProfile("user1", "test-internal-key")).thenReturn(userProfile);
            when(medicalCalculator.calculateBmi(170f, 65f))
                    .thenReturn(new MedicalCalculator.BmiResult(BigDecimal.valueOf(22.5), "normal"));
            when(medicalCalculator.evaluateGlucose(5.5f)).thenReturn(new MedicalCalculator.GlucoseResult(1, "normal"));
            when(medicalCalculator.calculateBaseRisk(any(), any(), any(), eq(36))).thenReturn(
                    new MedicalCalculator.BaseRiskResult(0, List.of()));
            when(healthRecordService.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5)))
                    .thenReturn("hr_001");
            serviceWebhookEmptyPath.assess("user1", request);
        });
    }

    @Test
    @DisplayName("callDify - webhook模式下webhookPath为null抛出异常")
    void callDify_webhookPathNull() {
        RiskAssessmentService serviceWebhookNullPath = new RiskAssessmentService(
                healthRecordService,
                riskAssessmentMapper,
                medicalCalculator,
                difyClient,
                userServiceClient,
                realObjectMapper,
                "http://localhost",
                "test-api-key",
                null,
                "blocking",
                "webhook",
                "auto",
                "inputs",
                "object",
                "test-internal-key");

        assertThrows(BusinessException.class, () -> {
            RiskAssessRequest request = createRiskAssessRequest();
            Map<String, Object> userProfile = Map.of("birth_date", "1990-01-01", "gender", 1);
            when(userServiceClient.getUserProfile("user1", "test-internal-key")).thenReturn(userProfile);
            when(medicalCalculator.calculateBmi(170f, 65f))
                    .thenReturn(new MedicalCalculator.BmiResult(BigDecimal.valueOf(22.5), "normal"));
            when(medicalCalculator.evaluateGlucose(5.5f)).thenReturn(new MedicalCalculator.GlucoseResult(1, "normal"));
            when(medicalCalculator.calculateBaseRisk(any(), any(), any(), eq(36))).thenReturn(
                    new MedicalCalculator.BaseRiskResult(0, List.of()));
            when(healthRecordService.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5)))
                    .thenReturn("hr_001");
            serviceWebhookNullPath.assess("user1", request);
        });
    }

    @Test
    @DisplayName("assess - suggestions字段为非数组类型返回空列表")
    void assess_suggestionsNotArray() throws Exception {
        RiskAssessRequest request = createRiskAssessRequest();
        Map<String, Object> userProfile = Map.of("birth_date", "1990-01-01", "gender", 1);

        when(userServiceClient.getUserProfile("user1", "test-internal-key")).thenReturn(userProfile);
        when(medicalCalculator.calculateBmi(170f, 65f))
                .thenReturn(new MedicalCalculator.BmiResult(BigDecimal.valueOf(22.5), "normal"));
        when(medicalCalculator.evaluateGlucose(5.5f)).thenReturn(new MedicalCalculator.GlucoseResult(1, "normal"));
        when(medicalCalculator.calculateBaseRisk(any(), any(), any(), eq(36))).thenReturn(
                new MedicalCalculator.BaseRiskResult(0, List.of()));
        when(healthRecordService.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5)))
                .thenReturn("hr_001");
        when(medicalCalculator.riskLevelName(1)).thenReturn("low");

        String difyResponseJson = """
                {
                    "risk_assessment": {
                        "risk_score": 20,
                        "risk_level": "low",
                        "suggestions": "非数组类型"
                    }
                }""";
        JsonNode difyResponse = realObjectMapper.readTree(difyResponseJson);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(difyResponse);

        when(riskAssessmentMapper.insert(any(RiskAssessment.class))).thenReturn(1);

        Map<String, Object> result = service.assess("user1", request);

        assertNotNull(result);
        List<?> suggestions = (List<?>) result.get("suggestions");
        assertEquals(0, suggestions.size());
    }

    @Test
    @DisplayName("extractRiskAssessment - text字段包含risk_score但无risk_assessment对象")
    void extractRiskAssessment_textHasRiskScore() throws Exception {
        String textContent = "{\"risk_score\":95,\"risk_level\":\"high\",\"analysis\":\"直接风险评估\"}";
        String json = "{\"data\":{\"outputs\":{\"text\":\"" + textContent.replace("\"", "\\\"") + "\"}}}";
        JsonNode response = realObjectMapper.readTree(json);
        JsonNode result = service.extractRiskAssessment(response);
        assertEquals(95, result.path("risk_score").asInt());
        assertEquals("high", result.path("risk_level").asText());
    }

    @Test
    @DisplayName("assess - Dify返回空suggestions数组")
    void assess_emptySuggestions() throws Exception {
        RiskAssessRequest request = createRiskAssessRequest();
        Map<String, Object> userProfile = Map.of("birth_date", "1990-01-01", "gender", 1);

        when(userServiceClient.getUserProfile("user1", "test-internal-key")).thenReturn(userProfile);
        when(medicalCalculator.calculateBmi(170f, 65f))
                .thenReturn(new MedicalCalculator.BmiResult(BigDecimal.valueOf(22.5), "normal"));
        when(medicalCalculator.evaluateGlucose(5.5f)).thenReturn(new MedicalCalculator.GlucoseResult(1, "normal"));
        when(medicalCalculator.calculateBaseRisk(any(), any(), any(), eq(36))).thenReturn(
                new MedicalCalculator.BaseRiskResult(0, List.of()));
        when(healthRecordService.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5)))
                .thenReturn("hr_001");
        when(medicalCalculator.riskLevelName(1)).thenReturn("low");

        String difyResponseJson = """
                {
                    "risk_assessment": {
                        "risk_score": 20,
                        "risk_level": "low",
                        "suggestions": []
                    }
                }""";
        JsonNode difyResponse = realObjectMapper.readTree(difyResponseJson);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(difyResponse);

        when(riskAssessmentMapper.insert(any(RiskAssessment.class))).thenReturn(1);
        when(riskAssessmentMapper.insertSuggestion(any(RiskAssessmentSuggestion.class))).thenReturn(1);

        Map<String, Object> result = service.assess("user1", request);

        assertNotNull(result);
        List<?> suggestions = (List<?>) result.get("suggestions");
        assertEquals(3, suggestions.size());
    }

    @Test
    @DisplayName("callDify - buildDifyPayload异常导致payload为空")
    void callDify_payloadEmpty() {
        RiskAssessmentService serviceWithMockMapper = new RiskAssessmentService(
                healthRecordService,
                riskAssessmentMapper,
                medicalCalculator,
                difyClient,
                userServiceClient,
                objectMapper,
                "http://localhost",
                "test-api-key",
                "",
                "blocking",
                "api",
                "auto",
                "inputs",
                "object",
                "test-internal-key");

        when(objectMapper.convertValue(any(), eq(Map.class))).thenThrow(new RuntimeException("转换失败"));

        assertThrows(BusinessException.class, () -> {
            RiskAssessRequest request = createRiskAssessRequest();
            Map<String, Object> userProfile = Map.of("birth_date", "1990-01-01", "gender", 1);
            when(userServiceClient.getUserProfile("user1", "test-internal-key")).thenReturn(userProfile);
            when(medicalCalculator.calculateBmi(170f, 65f))
                    .thenReturn(new MedicalCalculator.BmiResult(BigDecimal.valueOf(22.5), "normal"));
            when(medicalCalculator.evaluateGlucose(5.5f)).thenReturn(new MedicalCalculator.GlucoseResult(1, "normal"));
            when(medicalCalculator.calculateBaseRisk(any(), any(), any(), eq(36))).thenReturn(
                    new MedicalCalculator.BaseRiskResult(0, List.of()));
            when(healthRecordService.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5)))
                    .thenReturn("hr_001");
            serviceWithMockMapper.assess("user1", request);
        });
    }

    @Test
    @DisplayName("assess - Dify返回difyRunId和workflow_run_id")
    void assess_difyRunId() throws Exception {
        RiskAssessRequest request = createRiskAssessRequest();
        Map<String, Object> userProfile = Map.of("birth_date", "1990-01-01", "gender", 1);

        when(userServiceClient.getUserProfile("user1", "test-internal-key")).thenReturn(userProfile);
        when(medicalCalculator.calculateBmi(170f, 65f))
                .thenReturn(new MedicalCalculator.BmiResult(BigDecimal.valueOf(22.5), "normal"));
        when(medicalCalculator.evaluateGlucose(5.5f)).thenReturn(new MedicalCalculator.GlucoseResult(1, "normal"));
        when(medicalCalculator.calculateBaseRisk(any(), any(), any(), eq(36))).thenReturn(
                new MedicalCalculator.BaseRiskResult(0, List.of()));
        when(healthRecordService.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5)))
                .thenReturn("hr_001");
        when(medicalCalculator.riskLevelName(1)).thenReturn("low");

        String difyResponseJson = """
                {
                    "id": "run_001",
                    "risk_assessment": {
                        "risk_score": 20,
                        "risk_level": "low"
                    }
                }""";
        JsonNode difyResponse = realObjectMapper.readTree(difyResponseJson);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(difyResponse);

        when(riskAssessmentMapper.insert(any(RiskAssessment.class))).thenAnswer(invocation -> {
            RiskAssessment assessment = invocation.getArgument(0);
            assertEquals("run_001", assessment.getDifyWorkflowId());
            return 1;
        });

        Map<String, Object> result = service.assess("user1", request);
        assertNotNull(result);
    }

    @Test
    @DisplayName("assess - Dify返回workflow_run_id")
    void assess_workflowRunId() throws Exception {
        RiskAssessRequest request = createRiskAssessRequest();
        Map<String, Object> userProfile = Map.of("birth_date", "1990-01-01", "gender", 1);

        when(userServiceClient.getUserProfile("user1", "test-internal-key")).thenReturn(userProfile);
        when(medicalCalculator.calculateBmi(170f, 65f))
                .thenReturn(new MedicalCalculator.BmiResult(BigDecimal.valueOf(22.5), "normal"));
        when(medicalCalculator.evaluateGlucose(5.5f)).thenReturn(new MedicalCalculator.GlucoseResult(1, "normal"));
        when(medicalCalculator.calculateBaseRisk(any(), any(), any(), eq(36))).thenReturn(
                new MedicalCalculator.BaseRiskResult(0, List.of()));
        when(healthRecordService.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5)))
                .thenReturn("hr_001");
        when(medicalCalculator.riskLevelName(1)).thenReturn("low");

        String difyResponseJson = """
                {
                    "workflow_run_id": "workflow_001",
                    "risk_assessment": {
                        "risk_score": 20,
                        "risk_level": "low"
                    }
                }""";
        JsonNode difyResponse = realObjectMapper.readTree(difyResponseJson);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(difyResponse);

        when(riskAssessmentMapper.insert(any(RiskAssessment.class))).thenAnswer(invocation -> {
            RiskAssessment assessment = invocation.getArgument(0);
            assertEquals("workflow_001", assessment.getDifyWorkflowId());
            return 1;
        });

        Map<String, Object> result = service.assess("user1", request);
        assertNotNull(result);
    }

    @Test
    @DisplayName("notifyAssessmentFailed - 发送失败通知")
    void notifyAssessmentFailed() {
        service.notifyAssessmentFailed("user1", "评估失败");

        verify(userServiceClient).createMessage(eq("test-internal-key"), org.mockito.ArgumentMatchers.argThat(body ->
                "user1".equals(body.get("userId"))
                        && "failed".equals(body.get("status"))
                        && String.valueOf(body.get("summary")).contains("评估失败")));
    }

    @Test
    @DisplayName("buildWorkflowInputs - flat 模式走平铺 inputs")
    void buildWorkflowInputs_flatReturn() throws Exception {
        RiskAssessmentService flatService = new RiskAssessmentService(
                healthRecordService,
                riskAssessmentMapper,
                medicalCalculator,
                difyClient,
                userServiceClient,
                realObjectMapper,
                "http://localhost",
                "test-api-key",
                "",
                "blocking",
                "api",
                "auto",
                "flat",
                "object",
                "test-internal-key");

        java.lang.reflect.Method method = RiskAssessmentService.class.getDeclaredMethod(
                "buildWorkflowInputs", Map.class);
        method.setAccessible(true);
        Map<String, Object> payload = Map.of(
                "user_id", "user1",
                "user_profile", "{\"age\":30}",
                "questionnaire", "{}",
                "medical_calc_results", "{}",
                "risk_factors", "[]");

        @SuppressWarnings("unchecked")
        Map<String, Object> inputs = (Map<String, Object>) method.invoke(flatService, payload);

        assertEquals("user1", inputs.get("user_id"));
        assertTrue(inputs.containsKey("user_profile"));
    }

    @Test
    @DisplayName("assess - 缺少 risk_assessment 且响应较短")
    void assess_missingAssessmentShortPreview() throws Exception {
        RiskAssessRequest request = createRiskAssessRequest();
        Map<String, Object> userProfile = Map.of("birth_date", "1990-01-01", "gender", 1);

        when(userServiceClient.getUserProfile("user1", "test-internal-key")).thenReturn(userProfile);
        when(medicalCalculator.calculateBmi(170f, 65f))
                .thenReturn(new MedicalCalculator.BmiResult(BigDecimal.valueOf(22.5), "normal"));
        when(medicalCalculator.evaluateGlucose(5.5f)).thenReturn(new MedicalCalculator.GlucoseResult(1, "normal"));
        when(medicalCalculator.calculateBaseRisk(any(), any(), any(), eq(36))).thenReturn(
                new MedicalCalculator.BaseRiskResult(0, List.of()));
        when(healthRecordService.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5)))
                .thenReturn("hr_001");

        JsonNode difyResponse = realObjectMapper.readTree("{\"data\":{\"outputs\":{}}}");
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(difyResponse);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.assess("user1", request));
        assertTrue(ex.getMessage().contains("risk_assessment"));
        assertFalse(ex.getMessage().contains("..."));
    }

    @Test
    @DisplayName("assess - Dify 未返回 run id")
    void assess_difyRunIdBothBlank() throws Exception {
        RiskAssessRequest request = createRiskAssessRequest();
        Map<String, Object> userProfile = Map.of("birth_date", "1990-01-01", "gender", 1);

        when(userServiceClient.getUserProfile("user1", "test-internal-key")).thenReturn(userProfile);
        when(medicalCalculator.calculateBmi(170f, 65f))
                .thenReturn(new MedicalCalculator.BmiResult(BigDecimal.valueOf(22.5), "normal"));
        when(medicalCalculator.evaluateGlucose(5.5f)).thenReturn(new MedicalCalculator.GlucoseResult(1, "normal"));
        when(medicalCalculator.calculateBaseRisk(any(), any(), any(), eq(36))).thenReturn(
                new MedicalCalculator.BaseRiskResult(0, List.of()));
        when(healthRecordService.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5)))
                .thenReturn("hr_001");
        when(medicalCalculator.riskLevelName(1)).thenReturn("low");

        JsonNode difyResponse = realObjectMapper.readTree("""
                {"risk_assessment":{"risk_score":20,"risk_level":"low"}}
                """);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(difyResponse);
        when(riskAssessmentMapper.insert(any(RiskAssessment.class))).thenAnswer(invocation -> {
            RiskAssessment assessment = invocation.getArgument(0);
            assertNull(assessment.getDifyWorkflowId());
            return 1;
        });

        Map<String, Object> result = service.assess("user1", request);
        assertNotNull(result);
    }

    @Test
    @DisplayName("buildWorkflowInputs - wrap 模式包装 inputs")
    void buildWorkflowInputs_wrapReturn() throws Exception {
        java.lang.reflect.Method method = RiskAssessmentService.class.getDeclaredMethod(
                "buildWorkflowInputs", Map.class);
        method.setAccessible(true);
        Map<String, Object> payload = Map.of(
                "user_id", "user1",
                "user_profile", "{\"age\":30}",
                "questionnaire", "{}",
                "medical_calc_results", "{}",
                "risk_factors", "[]");

        @SuppressWarnings("unchecked")
        Map<String, Object> inputs = (Map<String, Object>) method.invoke(service, payload);

        assertTrue(inputs.containsKey("inputs"));
    }

    @Test
    @DisplayName("assess - Dify id 为空字符串时使用 workflow_run_id")
    void assess_difyRunIdEmptyUsesWorkflowRunId() throws Exception {
        RiskAssessRequest request = createRiskAssessRequest();
        Map<String, Object> userProfile = Map.of("birth_date", "1990-01-01", "gender", 1);

        when(userServiceClient.getUserProfile("user1", "test-internal-key")).thenReturn(userProfile);
        when(medicalCalculator.calculateBmi(170f, 65f))
                .thenReturn(new MedicalCalculator.BmiResult(BigDecimal.valueOf(22.5), "normal"));
        when(medicalCalculator.evaluateGlucose(5.5f)).thenReturn(new MedicalCalculator.GlucoseResult(1, "normal"));
        when(medicalCalculator.calculateBaseRisk(any(), any(), any(), eq(36))).thenReturn(
                new MedicalCalculator.BaseRiskResult(0, List.of()));
        when(healthRecordService.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5)))
                .thenReturn("hr_001");
        when(medicalCalculator.riskLevelName(1)).thenReturn("low");

        JsonNode difyResponse = realObjectMapper.readTree("""
                {"id":"","workflow_run_id":"workflow_blank_id","risk_assessment":{"risk_score":20,"risk_level":"low"}}
                """);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(difyResponse);
        when(riskAssessmentMapper.insert(any(RiskAssessment.class))).thenAnswer(invocation -> {
            RiskAssessment assessment = invocation.getArgument(0);
            assertEquals("workflow_blank_id", assessment.getDifyWorkflowId());
            return 1;
        });

        Map<String, Object> result = service.assess("user1", request);
        assertNotNull(result);
    }

    @Test
    @DisplayName("assess - 缺少 risk_assessment 且响应较长时截断预览")
    void assess_missingAssessmentLongPreview() throws Exception {
        RiskAssessRequest request = createRiskAssessRequest();
        Map<String, Object> userProfile = Map.of("birth_date", "1990-01-01", "gender", 1);

        when(userServiceClient.getUserProfile("user1", "test-internal-key")).thenReturn(userProfile);
        when(medicalCalculator.calculateBmi(170f, 65f))
                .thenReturn(new MedicalCalculator.BmiResult(BigDecimal.valueOf(22.5), "normal"));
        when(medicalCalculator.evaluateGlucose(5.5f)).thenReturn(new MedicalCalculator.GlucoseResult(1, "normal"));
        when(medicalCalculator.calculateBaseRisk(any(), any(), any(), eq(36))).thenReturn(
                new MedicalCalculator.BaseRiskResult(0, List.of()));
        when(healthRecordService.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5)))
                .thenReturn("hr_001");

        String padding = "x".repeat(900);
        JsonNode difyResponse = realObjectMapper.readTree("{\"noise\":\"" + padding + "\"}");
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(difyResponse);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.assess("user1", request));
        assertTrue(ex.getMessage().contains("..."));
    }

    @Test
    @DisplayName("extractRiskAssessment - text 为合法 JSON 但无风险字段")
    void extractRiskAssessment_textWithoutRiskFields() throws Exception {
        String json = "{\"outputs\":{\"text\":\"{\\\"analysis\\\":\\\"only\\\"}\"}}";
        JsonNode response = realObjectMapper.readTree(json);
        JsonNode result = service.extractRiskAssessment(response);
        assertTrue(result.isMissingNode());
    }

    @Test
    @DisplayName("assess - 未配置 api-key 为 null")
    void assess_nullApiKey() {
        RiskAssessmentService nullKeyService = new RiskAssessmentService(
                healthRecordService,
                riskAssessmentMapper,
                medicalCalculator,
                difyClient,
                userServiceClient,
                realObjectMapper,
                "http://localhost",
                null,
                "",
                "blocking",
                "api",
                "auto",
                "inputs",
                "object",
                "test-internal-key");

        RiskAssessRequest request = createRiskAssessRequest();
        Map<String, Object> userProfile = Map.of("birth_date", "1990-01-01", "gender", 1);
        when(userServiceClient.getUserProfile("user1", "test-internal-key")).thenReturn(userProfile);
        when(medicalCalculator.calculateBmi(170f, 65f))
                .thenReturn(new MedicalCalculator.BmiResult(BigDecimal.valueOf(22.5), "normal"));
        when(medicalCalculator.evaluateGlucose(5.5f)).thenReturn(new MedicalCalculator.GlucoseResult(1, "normal"));
        when(medicalCalculator.calculateBaseRisk(any(), any(), any(), eq(36))).thenReturn(
                new MedicalCalculator.BaseRiskResult(0, List.of()));
        when(healthRecordService.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5)))
                .thenReturn("hr_001");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> nullKeyService.assess("user1", request));
        assertTrue(ex.getMessage().contains("api-key"));
    }

    @Test
    @DisplayName("buildWorkflowInputs - inputVarName 为 null 时使用 flat 分支")
    void buildWorkflowInputs_nullInputVarNameUsesFlat() throws Exception {
        RiskAssessmentService localService = new RiskAssessmentService(
                healthRecordService,
                riskAssessmentMapper,
                medicalCalculator,
                difyClient,
                userServiceClient,
                realObjectMapper,
                "http://localhost",
                "test-api-key",
                "",
                "blocking",
                "api",
                "auto",
                "inputs",
                "object",
                "test-internal-key");
        java.lang.reflect.Field field = RiskAssessmentService.class.getDeclaredField("difyInputVarName");
        field.setAccessible(true);
        field.set(localService, null);

        java.lang.reflect.Method method = RiskAssessmentService.class.getDeclaredMethod(
                "buildWorkflowInputs", Map.class);
        method.setAccessible(true);
        Map<String, Object> payload = Map.of(
                "user_id", "user1",
                "user_profile", "{\"age\":30}",
                "questionnaire", "{}",
                "medical_calc_results", "{}",
                "risk_factors", "[]");

        @SuppressWarnings("unchecked")
        Map<String, Object> inputs = (Map<String, Object>) method.invoke(localService, payload);

        assertEquals("user1", inputs.get("user_id"));
    }

    @Test
    @DisplayName("buildWorkflowInputs - inputVarName 为 blank 时使用 flat 分支")
    void buildWorkflowInputs_blankInputVarNameUsesFlat() throws Exception {
        RiskAssessmentService localService = new RiskAssessmentService(
                healthRecordService,
                riskAssessmentMapper,
                medicalCalculator,
                difyClient,
                userServiceClient,
                realObjectMapper,
                "http://localhost",
                "test-api-key",
                "",
                "blocking",
                "api",
                "auto",
                "inputs",
                "object",
                "test-internal-key");
        java.lang.reflect.Field field = RiskAssessmentService.class.getDeclaredField("difyInputVarName");
        field.setAccessible(true);
        field.set(localService, "   ");

        java.lang.reflect.Method method = RiskAssessmentService.class.getDeclaredMethod(
                "buildWorkflowInputs", Map.class);
        method.setAccessible(true);
        Map<String, Object> payload = Map.of(
                "user_id", "user1",
                "user_profile", "{\"age\":30}",
                "questionnaire", "{}",
                "medical_calc_results", "{}",
                "risk_factors", "[]");

        @SuppressWarnings("unchecked")
        Map<String, Object> inputs = (Map<String, Object>) method.invoke(localService, payload);

        assertEquals("user1", inputs.get("user_id"));
    }

    @Test
    @DisplayName("extractRiskAssessment - outputs.text 解析失败进入 catch")
    void extractRiskAssessment_outputsTextParseError() throws Exception {
        String json = "{\"outputs\":{\"text\":\"{invalid json\"}}";
        JsonNode response = realObjectMapper.readTree(json);
        JsonNode result = service.extractRiskAssessment(response);
        assertTrue(result.isMissingNode());
    }
}