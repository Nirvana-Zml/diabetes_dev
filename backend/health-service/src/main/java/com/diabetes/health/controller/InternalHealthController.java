package com.diabetes.health.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.health.entity.HealthRecord;
import com.diabetes.health.entity.RiskAssessment;
import com.diabetes.health.entity.RiskAssessmentFactor;
import com.diabetes.health.entity.RiskAssessmentSuggestion;
import com.diabetes.health.mapper.HealthRecordMapper;
import com.diabetes.health.mapper.RiskAssessmentMapper;
import com.diabetes.health.service.MedicalCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/internal/health")
public class InternalHealthController {

    private final HealthRecordMapper healthRecordMapper;
    private final RiskAssessmentMapper riskAssessmentMapper;
    private final MedicalCalculator medicalCalculator;
    private final String difyInternalKey;

    public InternalHealthController(HealthRecordMapper healthRecordMapper,
                                    RiskAssessmentMapper riskAssessmentMapper,
                                    MedicalCalculator medicalCalculator,
                                    @Value("${dify-internal.key:}") String difyInternalKey) {
        this.healthRecordMapper = healthRecordMapper;
        this.riskAssessmentMapper = riskAssessmentMapper;
        this.medicalCalculator = medicalCalculator;
        this.difyInternalKey = difyInternalKey;
    }

    @GetMapping("/user/{userId}/latest-record")
    public ApiResponse<Map<String, Object>> latestRecord(@PathVariable String userId,
                                                           @RequestHeader(value = "X-Dify-Key", required = false) String key) {
        validateDifyKey(key);
        HealthRecord record = healthRecordMapper.findLatestByUserId(userId);
        if (record == null) {
            return ApiResponse.ok(Map.of());
        }
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("recordId", record.getRecordId());
        profile.put("height", record.getHeight());
        profile.put("weight", record.getWeight());
        profile.put("bmi", record.getBmi());
        profile.put("fastingGlucose", record.getFastingGlucose());
        profile.put("postprandialGlucose", record.getPostprandialGlucose());
        profile.put("randomGlucose", record.getRandomGlucose());
        profile.put("hba1c", record.getHba1c());
        profile.put("systolicBp", record.getSystolicBp());
        profile.put("diastolicBp", record.getDiastolicBp());
        profile.put("diabetesType", record.getDiabetesType());
        profile.put("isPregnant", record.getIsPregnant());
        profile.put("familyHistory", record.getHasDiabetesFamily());
        profile.put("isInsulinTaken", record.getIsInsulinTaken());
        profile.put("smoking", record.getSmoking());
        profile.put("alcohol", record.getAlcohol());
        profile.put("exerciseFreq", record.getExerciseFreq());
        profile.put("dietType", record.getDietType());
        profile.put("recordedAt", record.getRecordedAt());
        return ApiResponse.ok(profile);
    }

    @GetMapping("/user/{userId}/latest-assessment")
    public ApiResponse<Map<String, Object>> latestAssessment(@PathVariable String userId,
                                                               @RequestHeader(value = "X-Dify-Key", required = false) String key) {
        validateDifyKey(key);
        RiskAssessment assessment = riskAssessmentMapper.findLatestByUserId(userId);
        if (assessment == null) {
            return ApiResponse.ok(Map.of());
        }
        List<RiskAssessmentFactor> factors = riskAssessmentMapper.findFactors(assessment.getAssessmentId());
        List<RiskAssessmentSuggestion> suggestions = riskAssessmentMapper.findSuggestions(assessment.getAssessmentId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("assessmentId", assessment.getAssessmentId());
        data.put("healthRecordId", assessment.getHealthRecordId());
        data.put("riskLevel", assessment.getRiskLevel() == null ? "unknown" : medicalCalculator.riskLevelName(assessment.getRiskLevel()));
        data.put("riskScore", assessment.getRiskScore());
        data.put("bmi", assessment.getBmiSnapshot());
        data.put("glucoseLevel", glucoseLevelName(assessment.getGlucoseLevel()));
        data.put("confidence", confidenceName(assessment.getConfidence()));
        data.put("reportSummary", assessment.getReportSummary());
        data.put("assessedAt", assessment.getAssessedAt());
        data.put("factors", factors.stream().map(f -> Map.of(
                "name", f.getFactorName(),
                "weight", f.getWeight(),
                "description", f.getDescription() == null ? "" : f.getDescription()
        )).toList());
        data.put("suggestions", suggestions.stream().map(RiskAssessmentSuggestion::getContent).toList());
        return ApiResponse.ok(data);
    }

    @GetMapping("/user/{userId}/risk-history")
    public ApiResponse<Map<String, Object>> riskHistory(@PathVariable String userId,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "50") int size,
                                                         @RequestHeader(value = "X-Dify-Key", required = false) String key) {
        validateDifyKey(key);
        int offset = Math.max(0, (page - 1) * size);
        List<RiskAssessment> records = riskAssessmentMapper.findByUserId(userId, offset, size);
        int total = riskAssessmentMapper.countByUserId(userId);
        List<Map<String, Object>> list = records.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("assessmentId", r.getAssessmentId());
            m.put("riskLevel", medicalCalculator.riskLevelName(r.getRiskLevel()));
            m.put("riskScore", r.getRiskScore());
            m.put("bmi", r.getBmiSnapshot());
            m.put("reportSummary", r.getReportSummary());
            m.put("assessedAt", r.getAssessedAt());
            return m;
        }).toList();
        return ApiResponse.ok(Map.of("records", list, "total", total));
    }

    /** @deprecated 兼容旧路径，请使用 latest-record */
    @GetMapping("/user/{userId}/latest-risk")
    public ApiResponse<Map<String, Object>> latestRisk(@PathVariable String userId,
                                                       @RequestHeader(value = "X-Dify-Key", required = false) String key) {
        return latestRecord(userId, key);
    }

    private String glucoseLevelName(Integer level) {
        if (level == null) return "unknown";
        return switch (level) {
            case 1 -> "prediabetes";
            case 2 -> "diabetes";
            default -> "normal";
        };
    }

    private String confidenceName(Integer confidence) {
        if (confidence == null) return "medium";
        return switch (confidence) {
            case 1 -> "low";
            case 3 -> "high";
            default -> "medium";
        };
    }

    private void validateDifyKey(String key) {
        if (difyInternalKey != null && !difyInternalKey.isBlank()
                && (key == null || !difyInternalKey.equals(key))) {
            throw new BusinessException(401, "Dify 内部密钥无效");
        }
    }
}
