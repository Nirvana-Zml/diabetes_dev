package com.diabetes.plan.service;

import com.diabetes.common.util.IdGenerator;
import com.diabetes.plan.entity.HealthPlan;
import com.diabetes.plan.mapper.HealthPlanMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class PlanPersistenceService {

    private final HealthPlanMapper healthPlanMapper;
    private final PlanDetailPersistence planDetailPersistence;
    private final ObjectMapper objectMapper;

    public PlanPersistenceService(HealthPlanMapper healthPlanMapper,
                                  PlanDetailPersistence planDetailPersistence,
                                  ObjectMapper objectMapper) {
        this.healthPlanMapper = healthPlanMapper;
        this.planDetailPersistence = planDetailPersistence;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public HealthPlan savePlan(String userId, Map<String, Object> profile, int dailyCalories,
                               Map<String, Object> content) throws Exception {
        healthPlanMapper.deactivateActivePlans(userId);
        int version = healthPlanMapper.countByUserId(userId) + 1;

        HealthPlan plan = new HealthPlan();
        plan.setPlanId(IdGenerator.nextId("plan_"));
        plan.setUserId(userId);
        plan.setHealthRecordId(stringValue(profile.get("health_record_id")));
        plan.setRiskAssessmentId(stringValue(profile.get("risk_assessment_id")));
        plan.setTitle("个性化健康管理方案");
        plan.setSummary(stringValue(content.getOrDefault("summary", "基于用户画像自动生成的健康管理方案")));
        plan.setDailyCalories(dailyCalories);
        plan.setMedicationNote(String.valueOf(content.getOrDefault("medicationNote", "请遵医嘱用药")));
        plan.setSource(1);
        plan.setVersion(version);
        plan.setIsActive(1);
        plan.setIsFavorite(0);
        plan.setAiRawResponse(objectMapper.writeValueAsString(content));
        healthPlanMapper.insert(plan);
        planDetailPersistence.saveDetails(plan.getPlanId(), content);
        return plan;
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
