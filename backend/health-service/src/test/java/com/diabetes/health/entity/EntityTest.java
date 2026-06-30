package com.diabetes.health.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class EntityTest {

    @Test
    void healthRecord() {
        HealthRecord entity = new HealthRecord();
        entity.setRecordId("hr_001");
        entity.setUserId("user1");
        entity.setHeight(BigDecimal.valueOf(170));
        entity.setWeight(BigDecimal.valueOf(65));
        entity.setBmi(BigDecimal.valueOf(22.5));
        entity.setFastingGlucose(BigDecimal.valueOf(5.5));
        entity.setPostprandialGlucose(BigDecimal.valueOf(7.8));
        entity.setRandomGlucose(BigDecimal.valueOf(6.1));
        entity.setHba1c(BigDecimal.valueOf(6.0));
        entity.setSystolicBp(BigDecimal.valueOf(120));
        entity.setDiastolicBp(BigDecimal.valueOf(80));
        entity.setDiabetesType(1);
        entity.setDiagnosedDate(LocalDate.parse("2023-01-01"));
        entity.setIsPregnant(0);
        entity.setHasDiabetesFamily(1);
        entity.setIsInsulinTaken(0);
        entity.setSmoking(0);
        entity.setAlcohol(1);
        entity.setExerciseFreq(3);
        entity.setDietType("balanced");
        entity.setTestSource(1);
        entity.setRecordedAt(LocalDateTime.now());

        assertEquals("hr_001", entity.getRecordId());
        assertEquals("user1", entity.getUserId());
        assertEquals(BigDecimal.valueOf(170), entity.getHeight());
        assertEquals(BigDecimal.valueOf(65), entity.getWeight());
        assertEquals(BigDecimal.valueOf(22.5), entity.getBmi());
        assertEquals(BigDecimal.valueOf(5.5), entity.getFastingGlucose());
        assertEquals(BigDecimal.valueOf(7.8), entity.getPostprandialGlucose());
        assertEquals(BigDecimal.valueOf(6.1), entity.getRandomGlucose());
        assertEquals(BigDecimal.valueOf(6.0), entity.getHba1c());
        assertEquals(BigDecimal.valueOf(120), entity.getSystolicBp());
        assertEquals(BigDecimal.valueOf(80), entity.getDiastolicBp());
        assertEquals(1, entity.getDiabetesType());
        assertEquals(LocalDate.parse("2023-01-01"), entity.getDiagnosedDate());
        assertEquals(0, entity.getIsPregnant());
        assertEquals(1, entity.getHasDiabetesFamily());
        assertEquals(0, entity.getIsInsulinTaken());
        assertEquals(0, entity.getSmoking());
        assertEquals(1, entity.getAlcohol());
        assertEquals(3, entity.getExerciseFreq());
        assertEquals("balanced", entity.getDietType());
        assertEquals(1, entity.getTestSource());
        assertNotNull(entity.getRecordedAt());
    }

    @Test
    void healthRecordMedicalHistory() {
        HealthRecordMedicalHistory entity = new HealthRecordMedicalHistory();
        entity.setHistoryId("mh_001");
        entity.setRecordId("hr_001");
        entity.setUserId("user1");
        entity.setDiseaseCode("ICD-10-CM:E11");
        entity.setDiseaseName("糖尿病");
        entity.setDiagnosedDate(LocalDate.parse("2023-01-01"));
        entity.setStatus(1);
        entity.setNote("备注");
        entity.setSource(1);

        assertEquals("mh_001", entity.getHistoryId());
        assertEquals("hr_001", entity.getRecordId());
        assertEquals("user1", entity.getUserId());
        assertEquals("ICD-10-CM:E11", entity.getDiseaseCode());
        assertEquals("糖尿病", entity.getDiseaseName());
        assertEquals(LocalDate.parse("2023-01-01"), entity.getDiagnosedDate());
        assertEquals(1, entity.getStatus());
        assertEquals("备注", entity.getNote());
        assertEquals(1, entity.getSource());
    }

    @Test
    void healthRecordMedication() {
        HealthRecordMedication entity = new HealthRecordMedication();
        entity.setMedicationId("med_001");
        entity.setRecordId("hr_001");
        entity.setUserId("user1");
        entity.setDrugName("阿司匹林");
        entity.setGenericName("acetylsalicylic acid");
        entity.setDosage("100mg");
        entity.setFrequency("1");
        entity.setFrequencyDesc("每日一次");
        entity.setRoute("口服");
        entity.setPurpose("预防血栓");
        entity.setIsInsulin(0);
        entity.setStartDate(LocalDate.parse("2023-01-01"));
        entity.setEndDate(LocalDate.parse("2024-01-01"));
        entity.setStatus(1);
        entity.setSource(1);

        assertEquals("med_001", entity.getMedicationId());
        assertEquals("hr_001", entity.getRecordId());
        assertEquals("user1", entity.getUserId());
        assertEquals("阿司匹林", entity.getDrugName());
        assertEquals("acetylsalicylic acid", entity.getGenericName());
        assertEquals("100mg", entity.getDosage());
        assertEquals("1", entity.getFrequency());
        assertEquals("每日一次", entity.getFrequencyDesc());
        assertEquals("口服", entity.getRoute());
        assertEquals("预防血栓", entity.getPurpose());
        assertEquals(0, entity.getIsInsulin());
        assertEquals(LocalDate.parse("2023-01-01"), entity.getStartDate());
        assertEquals(LocalDate.parse("2024-01-01"), entity.getEndDate());
        assertEquals(1, entity.getStatus());
        assertEquals(1, entity.getSource());
    }

    @Test
    void healthRecordFamilyHistory() {
        HealthRecordFamilyHistory entity = new HealthRecordFamilyHistory();
        entity.setFamilyHistoryId("fh_001");
        entity.setRecordId("hr_001");
        entity.setUserId("user1");
        entity.setRelation("父亲");
        entity.setMemberAge(65);
        entity.setIsAlive(1);
        entity.setDiseaseCode("ICD-10-CM:E11");
        entity.setDiseaseName("糖尿病");
        entity.setDiagnosedAge(55);
        entity.setIsDiabetes(1);
        entity.setNote("备注");
        entity.setSource(1);

        assertEquals("fh_001", entity.getFamilyHistoryId());
        assertEquals("hr_001", entity.getRecordId());
        assertEquals("user1", entity.getUserId());
        assertEquals("父亲", entity.getRelation());
        assertEquals(65, entity.getMemberAge());
        assertEquals(1, entity.getIsAlive());
        assertEquals("ICD-10-CM:E11", entity.getDiseaseCode());
        assertEquals("糖尿病", entity.getDiseaseName());
        assertEquals(55, entity.getDiagnosedAge());
        assertEquals(1, entity.getIsDiabetes());
        assertEquals("备注", entity.getNote());
        assertEquals(1, entity.getSource());
    }

    @Test
    void riskAssessment() {
        RiskAssessment entity = new RiskAssessment();
        entity.setAssessmentId("ra_001");
        entity.setUserId("user1");
        entity.setHealthRecordId("hr_001");
        entity.setDifyWorkflowId("wf_001");
        entity.setRiskLevel(1);
        entity.setRiskScore(30);
        entity.setBmiSnapshot(BigDecimal.valueOf(22.5));
        entity.setGlucoseLevel(0);
        entity.setConfidence(2);
        entity.setGenerationStatus(1);
        entity.setReportSummary("风险评估报告");
        entity.setAiRawResponse("AI原始响应");
        entity.setAssessedAt(LocalDateTime.now());

        assertEquals("ra_001", entity.getAssessmentId());
        assertEquals("user1", entity.getUserId());
        assertEquals("hr_001", entity.getHealthRecordId());
        assertEquals("wf_001", entity.getDifyWorkflowId());
        assertEquals(1, entity.getRiskLevel());
        assertEquals(30, entity.getRiskScore());
        assertEquals(BigDecimal.valueOf(22.5), entity.getBmiSnapshot());
        assertEquals(0, entity.getGlucoseLevel());
        assertEquals(2, entity.getConfidence());
        assertEquals(1, entity.getGenerationStatus());
        assertEquals("风险评估报告", entity.getReportSummary());
        assertEquals("AI原始响应", entity.getAiRawResponse());
        assertNotNull(entity.getAssessedAt());
    }

    @Test
    void riskAssessmentFactor() {
        RiskAssessmentFactor entity = new RiskAssessmentFactor();
        entity.setFactorId("rf_001");
        entity.setAssessmentId("ra_001");
        entity.setFactorCode("family");
        entity.setFactorName("家族史");
        entity.setWeight(BigDecimal.valueOf(15));
        entity.setFactorLevel(1);
        entity.setDescription("父母患病");

        assertEquals("rf_001", entity.getFactorId());
        assertEquals("ra_001", entity.getAssessmentId());
        assertEquals("family", entity.getFactorCode());
        assertEquals("家族史", entity.getFactorName());
        assertEquals(BigDecimal.valueOf(15), entity.getWeight());
        assertEquals(1, entity.getFactorLevel());
        assertEquals("父母患病", entity.getDescription());
    }

    @Test
    void riskAssessmentSuggestion() {
        RiskAssessmentSuggestion entity = new RiskAssessmentSuggestion();
        entity.setSuggestionId("rs_001");
        entity.setAssessmentId("ra_001");
        entity.setCategory(1);
        entity.setPriority(1);
        entity.setContent("定期检查血糖");

        assertEquals("rs_001", entity.getSuggestionId());
        assertEquals("ra_001", entity.getAssessmentId());
        assertEquals(1, entity.getCategory());
        assertEquals(1, entity.getPriority());
        assertEquals("定期检查血糖", entity.getContent());
    }
}