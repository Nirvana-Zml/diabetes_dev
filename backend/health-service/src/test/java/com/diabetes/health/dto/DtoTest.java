package com.diabetes.health.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DtoTest {

    @Test
    void riskAssessRequest() {
        RiskAssessRequest dto = new RiskAssessRequest();
        dto.setHeight(170f);
        dto.setWeight(65f);
        dto.setFastingGlucose(5.5f);
        dto.setPostprandialGlucose(7.8f);
        dto.setRandomGlucose(6.1f);
        dto.setHba1c(6.0f);
        dto.setSystolicBp(120);
        dto.setDiastolicBp(80);
        dto.setDiabetesType(1);
        dto.setDiagnosedDate("2023-01-01");
        dto.setIsPregnant(false);
        dto.setFamilyHistory(true);
        dto.setIsInsulinTaken(false);
        dto.setSmoking(0);
        dto.setAlcohol(1);
        dto.setExerciseFreq(3);
        dto.setDietType("balanced");
        dto.setTestSource(1);

        assertEquals(170f, dto.getHeight());
        assertEquals(65f, dto.getWeight());
        assertEquals(5.5f, dto.getFastingGlucose());
        assertEquals(7.8f, dto.getPostprandialGlucose());
        assertEquals(6.1f, dto.getRandomGlucose());
        assertEquals(6.0f, dto.getHba1c());
        assertEquals(120, dto.getSystolicBp());
        assertEquals(80, dto.getDiastolicBp());
        assertEquals(1, dto.getDiabetesType());
        assertEquals("2023-01-01", dto.getDiagnosedDate());
        assertFalse(dto.getIsPregnant());
        assertTrue(dto.getFamilyHistory());
        assertFalse(dto.getIsInsulinTaken());
        assertEquals(0, dto.getSmoking());
        assertEquals(1, dto.getAlcohol());
        assertEquals(3, dto.getExerciseFreq());
        assertEquals("balanced", dto.getDietType());
        assertEquals(1, dto.getTestSource());
    }

    @Test
    void riskAssessRequest_withLists() {
        RiskAssessRequest dto = new RiskAssessRequest();

        MedicalHistoryItem history = new MedicalHistoryItem();
        history.setDiseaseCode("ICD-10");
        history.setDiseaseName("高血压");
        history.setDiagnosedDate("2020-01-01");
        history.setStatus(1);
        history.setNote("备注");
        dto.setMedicalHistories(List.of(history));

        MedicationItem medication = new MedicationItem();
        medication.setDrugName("阿司匹林");
        medication.setGenericName("ASA");
        medication.setDosage("100mg");
        medication.setFrequency("1");
        medication.setFrequencyDesc("每日一次");
        medication.setRoute("口服");
        medication.setPurpose("预防");
        medication.setIsInsulin(false);
        medication.setStartDate("2023-01-01");
        medication.setEndDate("2024-01-01");
        medication.setStatus(1);
        dto.setMedications(List.of(medication));

        FamilyHistoryItem family = new FamilyHistoryItem();
        family.setRelation("父亲");
        family.setMemberAge(65);
        family.setIsAlive(true);
        family.setDiseaseCode("ICD-10");
        family.setDiseaseName("糖尿病");
        family.setDiagnosedAge(55);
        family.setIsDiabetes(true);
        family.setNote("备注");
        dto.setFamilyHistories(List.of(family));

        assertNotNull(dto.getMedicalHistories());
        assertEquals(1, dto.getMedicalHistories().size());
        assertEquals("高血压", dto.getMedicalHistories().get(0).getDiseaseName());

        assertNotNull(dto.getMedications());
        assertEquals(1, dto.getMedications().size());
        assertEquals("阿司匹林", dto.getMedications().get(0).getDrugName());

        assertNotNull(dto.getFamilyHistories());
        assertEquals(1, dto.getFamilyHistories().size());
        assertEquals("父亲", dto.getFamilyHistories().get(0).getRelation());
    }

    @Test
    void updateHealthRecordRequest() {
        UpdateHealthRecordRequest dto = new UpdateHealthRecordRequest();
        dto.setHeight(170f);
        dto.setWeight(65f);
        dto.setFastingGlucose(5.5f);
        dto.setPostprandialGlucose(7.8f);
        dto.setHba1c(6.0f);
        dto.setSystolicBp(120);
        dto.setDiastolicBp(80);
        dto.setDiabetesType(1);
        dto.setExerciseFreq(3);
        dto.setDietType("balanced");
        dto.setSmoking(0);
        dto.setFamilyHistory(true);
        dto.setMedicalHistory("高血压");
        dto.setMedication("阿司匹林");

        assertEquals(170f, dto.getHeight());
        assertEquals(65f, dto.getWeight());
        assertEquals(5.5f, dto.getFastingGlucose());
        assertEquals(7.8f, dto.getPostprandialGlucose());
        assertEquals(6.0f, dto.getHba1c());
        assertEquals(120, dto.getSystolicBp());
        assertEquals(80, dto.getDiastolicBp());
        assertEquals(1, dto.getDiabetesType());
        assertEquals(3, dto.getExerciseFreq());
        assertEquals("balanced", dto.getDietType());
        assertEquals(0, dto.getSmoking());
        assertTrue(dto.getFamilyHistory());
        assertEquals("高血压", dto.getMedicalHistory());
        assertEquals("阿司匹林", dto.getMedication());
    }

    @Test
    void medicalHistoryItem() {
        MedicalHistoryItem item = new MedicalHistoryItem();
        item.setDiseaseCode("ICD-10");
        item.setDiseaseName("高血压");
        item.setDiagnosedDate("2020-01-01");
        item.setStatus(1);
        item.setNote("备注");

        assertEquals("ICD-10", item.getDiseaseCode());
        assertEquals("高血压", item.getDiseaseName());
        assertEquals("2020-01-01", item.getDiagnosedDate());
        assertEquals(1, item.getStatus());
        assertEquals("备注", item.getNote());
    }

    @Test
    void medicationItem() {
        MedicationItem item = new MedicationItem();
        item.setDrugName("阿司匹林");
        item.setGenericName("ASA");
        item.setDosage("100mg");
        item.setFrequency("1");
        item.setFrequencyDesc("每日一次");
        item.setRoute("口服");
        item.setPurpose("预防");
        item.setIsInsulin(false);
        item.setStartDate("2023-01-01");
        item.setEndDate("2024-01-01");
        item.setStatus(1);

        assertEquals("阿司匹林", item.getDrugName());
        assertEquals("ASA", item.getGenericName());
        assertEquals("100mg", item.getDosage());
        assertEquals("1", item.getFrequency());
        assertEquals("每日一次", item.getFrequencyDesc());
        assertEquals("口服", item.getRoute());
        assertEquals("预防", item.getPurpose());
        assertFalse(item.getIsInsulin());
        assertEquals("2023-01-01", item.getStartDate());
        assertEquals("2024-01-01", item.getEndDate());
        assertEquals(1, item.getStatus());
    }

    @Test
    void familyHistoryItem() {
        FamilyHistoryItem item = new FamilyHistoryItem();
        item.setRelation("父亲");
        item.setMemberAge(65);
        item.setIsAlive(true);
        item.setDiseaseCode("ICD-10");
        item.setDiseaseName("糖尿病");
        item.setDiagnosedAge(55);
        item.setIsDiabetes(true);
        item.setNote("备注");

        assertEquals("父亲", item.getRelation());
        assertEquals(65, item.getMemberAge());
        assertTrue(item.getIsAlive());
        assertEquals("ICD-10", item.getDiseaseCode());
        assertEquals("糖尿病", item.getDiseaseName());
        assertEquals(55, item.getDiagnosedAge());
        assertTrue(item.getIsDiabetes());
        assertEquals("备注", item.getNote());
    }
}