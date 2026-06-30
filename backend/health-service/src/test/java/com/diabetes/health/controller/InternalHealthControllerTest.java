package com.diabetes.health.controller;

import com.diabetes.common.exception.BusinessException;
import com.diabetes.health.entity.HealthRecord;
import com.diabetes.health.entity.RiskAssessment;
import com.diabetes.health.entity.RiskAssessmentFactor;
import com.diabetes.health.entity.RiskAssessmentSuggestion;
import com.diabetes.health.mapper.HealthRecordMapper;
import com.diabetes.health.mapper.RiskAssessmentMapper;
import com.diabetes.health.service.MedicalCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalHealthControllerTest {

    @Mock
    private HealthRecordMapper healthRecordMapper;

    @Mock
    private RiskAssessmentMapper riskAssessmentMapper;

    @Mock
    private MedicalCalculator medicalCalculator;

    @InjectMocks
    private InternalHealthController controller;

    private InternalHealthController controllerWithKey;

    @Test
    void latestRecord() {
        controllerWithKey = new InternalHealthController(
                healthRecordMapper, riskAssessmentMapper, medicalCalculator, "test-secret-key");

        HealthRecord record = new HealthRecord();
        record.setRecordId("hr_001");
        record.setHeight(BigDecimal.valueOf(170));
        record.setWeight(BigDecimal.valueOf(65));
        when(healthRecordMapper.findLatestByUserId("user1")).thenReturn(record);

        var result = controller.latestRecord("user1", null);

        assertEquals(200, result.code());
        assertNotNull(result.data());
        assertEquals("hr_001", result.data().get("recordId"));
        verify(healthRecordMapper).findLatestByUserId("user1");
    }

    @Test
    void latestRecord_noRecord() {
        when(healthRecordMapper.findLatestByUserId("user1")).thenReturn(null);

        var result = controller.latestRecord("user1", null);

        assertEquals(200, result.code());
        assertEquals(Map.of(), result.data());
    }

    @Test
    void latestRecord_validKey() {
        controllerWithKey = new InternalHealthController(
                healthRecordMapper, riskAssessmentMapper, medicalCalculator, "test-secret-key");

        HealthRecord record = new HealthRecord();
        record.setRecordId("hr_001");
        when(healthRecordMapper.findLatestByUserId("user1")).thenReturn(record);

        var result = controllerWithKey.latestRecord("user1", "test-secret-key");

        assertEquals(200, result.code());
        assertEquals("hr_001", result.data().get("recordId"));
    }

    @Test
    void latestRecord_invalidKey() {
        controllerWithKey = new InternalHealthController(
                healthRecordMapper, riskAssessmentMapper, medicalCalculator, "test-secret-key");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> controllerWithKey.latestRecord("user1", "wrong-key"));

        assertEquals(401, exception.getCode());
        assertEquals("Dify 内部密钥无效", exception.getMessage());
    }

    @Test
    void latestAssessment() {
        RiskAssessment assessment = new RiskAssessment();
        assessment.setAssessmentId("ra_001");
        assessment.setRiskLevel(1);
        assessment.setRiskScore(30);
        assessment.setBmiSnapshot(BigDecimal.valueOf(22.5));
        assessment.setGlucoseLevel(0);
        assessment.setConfidence(2);
        assessment.setReportSummary("测试报告");
        assessment.setAssessedAt(LocalDateTime.now());

        when(riskAssessmentMapper.findLatestByUserId("user1")).thenReturn(assessment);
        when(riskAssessmentMapper.findFactors("ra_001")).thenReturn(List.of());
        when(riskAssessmentMapper.findSuggestions("ra_001")).thenReturn(List.of());
        when(medicalCalculator.riskLevelName(1)).thenReturn("low");

        var result = controller.latestAssessment("user1", null);

        assertEquals(200, result.code());
        assertNotNull(result.data());
        assertEquals("ra_001", result.data().get("assessmentId"));
        assertEquals("low", result.data().get("riskLevel"));
        assertEquals("normal", result.data().get("glucoseLevel"));
        assertEquals("medium", result.data().get("confidence"));
    }

    @Test
    void latestAssessment_noRecord() {
        when(riskAssessmentMapper.findLatestByUserId("user1")).thenReturn(null);

        var result = controller.latestAssessment("user1", null);

        assertEquals(200, result.code());
        assertEquals(Map.of(), result.data());
    }

    @Test
    void latestAssessment_withFactors() {
        RiskAssessment assessment = new RiskAssessment();
        assessment.setAssessmentId("ra_001");
        assessment.setRiskLevel(2);
        assessment.setRiskScore(60);

        RiskAssessmentFactor factor = new RiskAssessmentFactor();
        factor.setFactorName("家族史");
        factor.setWeight(BigDecimal.valueOf(15));
        factor.setDescription("父母患病");

        RiskAssessmentSuggestion suggestion = new RiskAssessmentSuggestion();
        suggestion.setContent("定期检查");

        when(riskAssessmentMapper.findLatestByUserId("user1")).thenReturn(assessment);
        when(riskAssessmentMapper.findFactors("ra_001")).thenReturn(List.of(factor));
        when(riskAssessmentMapper.findSuggestions("ra_001")).thenReturn(List.of(suggestion));
        when(medicalCalculator.riskLevelName(2)).thenReturn("medium");

        var result = controller.latestAssessment("user1", null);

        assertEquals(200, result.code());
        assertNotNull(result.data().get("factors"));
        assertNotNull(result.data().get("suggestions"));
    }

    @Test
    void latestAssessment_nullRiskLevel() {
        RiskAssessment assessment = new RiskAssessment();
        assessment.setAssessmentId("ra_001");
        assessment.setRiskLevel(null);

        when(riskAssessmentMapper.findLatestByUserId("user1")).thenReturn(assessment);
        when(riskAssessmentMapper.findFactors("ra_001")).thenReturn(List.of());
        when(riskAssessmentMapper.findSuggestions("ra_001")).thenReturn(List.of());

        var result = controller.latestAssessment("user1", null);

        assertEquals("unknown", result.data().get("riskLevel"));
    }

    @Test
    void latestRisk() {
        HealthRecord record = new HealthRecord();
        record.setRecordId("hr_001");
        when(healthRecordMapper.findLatestByUserId("user1")).thenReturn(record);

        var result = controller.latestRisk("user1", null);

        assertEquals(200, result.code());
        assertEquals("hr_001", result.data().get("recordId"));
    }

    @Test
    void glucoseLevelName_various() {
        InternalHealthController testController = new InternalHealthController(
                healthRecordMapper, riskAssessmentMapper, medicalCalculator, "");

        RiskAssessment assessment = new RiskAssessment();
        assessment.setAssessmentId("ra_001");
        assessment.setRiskLevel(1);

        when(riskAssessmentMapper.findLatestByUserId("user1")).thenReturn(assessment);
        when(riskAssessmentMapper.findFactors("ra_001")).thenReturn(List.of());
        when(riskAssessmentMapper.findSuggestions("ra_001")).thenReturn(List.of());
        when(medicalCalculator.riskLevelName(1)).thenReturn("low");

        assessment.setGlucoseLevel(1);
        var result1 = testController.latestAssessment("user1", null);
        assertEquals("prediabetes", result1.data().get("glucoseLevel"));

        assessment.setGlucoseLevel(2);
        var result2 = testController.latestAssessment("user1", null);
        assertEquals("diabetes", result2.data().get("glucoseLevel"));

        assessment.setGlucoseLevel(0);
        var result3 = testController.latestAssessment("user1", null);
        assertEquals("normal", result3.data().get("glucoseLevel"));

        assessment.setGlucoseLevel(null);
        var result4 = testController.latestAssessment("user1", null);
        assertEquals("unknown", result4.data().get("glucoseLevel"));
    }

    @Test
    void confidenceName_various() {
        InternalHealthController testController = new InternalHealthController(
                healthRecordMapper, riskAssessmentMapper, medicalCalculator, "");

        RiskAssessment assessment = new RiskAssessment();
        assessment.setAssessmentId("ra_001");
        assessment.setRiskLevel(1);

        when(riskAssessmentMapper.findLatestByUserId("user1")).thenReturn(assessment);
        when(riskAssessmentMapper.findFactors("ra_001")).thenReturn(List.of());
        when(riskAssessmentMapper.findSuggestions("ra_001")).thenReturn(List.of());
        when(medicalCalculator.riskLevelName(1)).thenReturn("low");

        assessment.setConfidence(1);
        var result1 = testController.latestAssessment("user1", null);
        assertEquals("low", result1.data().get("confidence"));

        assessment.setConfidence(3);
        var result2 = testController.latestAssessment("user1", null);
        assertEquals("high", result2.data().get("confidence"));

        assessment.setConfidence(2);
        var result3 = testController.latestAssessment("user1", null);
        assertEquals("medium", result3.data().get("confidence"));

        assessment.setConfidence(null);
        var result4 = testController.latestAssessment("user1", null);
        assertEquals("medium", result4.data().get("confidence"));
    }

    @Test
    void latestAssessment_factorDescriptionNull() {
        RiskAssessment assessment = new RiskAssessment();
        assessment.setAssessmentId("ra_factor_null");
        assessment.setRiskLevel(1);

        RiskAssessmentFactor factor = new RiskAssessmentFactor();
        factor.setFactorName("测试因素");
        factor.setWeight(BigDecimal.valueOf(10));
        factor.setDescription(null);

        when(riskAssessmentMapper.findLatestByUserId("user1")).thenReturn(assessment);
        when(riskAssessmentMapper.findFactors("ra_factor_null")).thenReturn(List.of(factor));
        when(riskAssessmentMapper.findSuggestions("ra_factor_null")).thenReturn(List.of());
        when(medicalCalculator.riskLevelName(1)).thenReturn("low");

        var result = controller.latestAssessment("user1", null);

        assertEquals(200, result.code());
        var factors = (List<?>) result.data().get("factors");
        assertEquals(1, factors.size());
        assertEquals("", ((Map<?, ?>) factors.get(0)).get("description"));
    }

    @Test
    void validateDifyKey_noKeyConfigured() {
        InternalHealthController noKeyController = new InternalHealthController(
                healthRecordMapper, riskAssessmentMapper, medicalCalculator, null);

        HealthRecord record = new HealthRecord();
        record.setRecordId("hr_001");
        when(healthRecordMapper.findLatestByUserId("user1")).thenReturn(record);

        var result = noKeyController.latestRecord("user1", "any-key");

        assertEquals(200, result.code());
        assertEquals("hr_001", result.data().get("recordId"));
    }

    @Test
    void validateDifyKey_emptyKeyConfigured() {
        InternalHealthController emptyKeyController = new InternalHealthController(
                healthRecordMapper, riskAssessmentMapper, medicalCalculator, "");

        HealthRecord record = new HealthRecord();
        record.setRecordId("hr_001");
        when(healthRecordMapper.findLatestByUserId("user1")).thenReturn(record);

        var result = emptyKeyController.latestRecord("user1", "any-key");

        assertEquals(200, result.code());
        assertEquals("hr_001", result.data().get("recordId"));
    }

    @Test
    void validateDifyKey_keyIsNullWithConfiguredKey() {
        InternalHealthController controllerWithKey = new InternalHealthController(
                healthRecordMapper, riskAssessmentMapper, medicalCalculator, "test-secret-key");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> controllerWithKey.latestRecord("user1", null));

        assertEquals(401, exception.getCode());
        assertEquals("Dify 内部密钥无效", exception.getMessage());
    }
}