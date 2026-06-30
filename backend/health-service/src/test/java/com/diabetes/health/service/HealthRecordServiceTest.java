package com.diabetes.health.service;

import com.diabetes.health.dto.*;
import com.diabetes.health.entity.*;
import com.diabetes.health.mapper.HealthRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("HealthRecordService 健康记录服务测试")
class HealthRecordServiceTest {

    @Mock
    private HealthRecordMapper healthRecordMapper;

    private HealthRecordService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new HealthRecordService(healthRecordMapper);
    }

    @Test
    @DisplayName("getLatest - 用户无健康记录返回空 Map")
    void getLatest_noRecord() {
        when(healthRecordMapper.findLatestByUserId("user1")).thenReturn(null);

        Map<String, Object> result = service.getLatest("user1");

        assertTrue(result.isEmpty());
        verify(healthRecordMapper).findLatestByUserId("user1");
    }

    @Test
    @DisplayName("getLatest - 返回完整健康记录详情")
    void getLatest_withRecord() {
        HealthRecord record = createHealthRecord("hr_001", "user1");
        when(healthRecordMapper.findLatestByUserId("user1")).thenReturn(record);
        when(healthRecordMapper.findMedicalHistoriesByRecordId("hr_001")).thenReturn(List.of());
        when(healthRecordMapper.findMedicationsByRecordId("hr_001")).thenReturn(List.of());
        when(healthRecordMapper.findFamilyHistoriesByRecordId("hr_001")).thenReturn(List.of());

        Map<String, Object> result = service.getLatest("user1");

        assertEquals("hr_001", result.get("recordId"));
        assertEquals(BigDecimal.valueOf(170), result.get("height"));
        assertEquals(BigDecimal.valueOf(65), result.get("weight"));
        assertEquals(BigDecimal.valueOf(22.5), result.get("bmi"));
        assertFalse((Boolean) result.get("familyHistory"));
        verify(healthRecordMapper).findLatestByUserId("user1");
    }

    @Test
    @DisplayName("save - 保存健康记录")
    void save() {
        UpdateHealthRecordRequest request = new UpdateHealthRecordRequest();
        request.setHeight(170f);
        request.setWeight(65f);
        request.setFastingGlucose(5.5f);
        request.setSystolicBp(120);
        request.setDiastolicBp(80);
        request.setFamilyHistory(true);
        request.setSmoking(0);

        when(healthRecordMapper.insert(any(HealthRecord.class))).thenReturn(1);
        when(healthRecordMapper.findMedicalHistoriesByRecordId(anyString())).thenReturn(List.of());
        when(healthRecordMapper.findMedicationsByRecordId(anyString())).thenReturn(List.of());
        when(healthRecordMapper.findFamilyHistoriesByRecordId(anyString())).thenReturn(List.of());

        Map<String, Object> result = service.save("user1", request);

        assertNotNull(result.get("recordId"));
        assertEquals(BigDecimal.valueOf(22.5), result.get("bmi"));
        assertTrue((Boolean) result.get("familyHistory"));
        verify(healthRecordMapper).insert(any(HealthRecord.class));
    }

    @Test
    @DisplayName("saveFromQuestionnaire - 从问卷保存完整记录")
    void saveFromQuestionnaire() {
        RiskAssessRequest request = createRiskAssessRequest();

        when(healthRecordMapper.insert(any(HealthRecord.class))).thenReturn(1);
        when(healthRecordMapper.insertMedicalHistory(any(HealthRecordMedicalHistory.class))).thenReturn(1);
        when(healthRecordMapper.insertMedication(any(HealthRecordMedication.class))).thenReturn(1);
        when(healthRecordMapper.insertFamilyHistory(any(HealthRecordFamilyHistory.class))).thenReturn(1);

        String recordId = service.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5));

        assertNotNull(recordId);
        assertTrue(recordId.startsWith("hr_"));
        verify(healthRecordMapper).insert(any(HealthRecord.class));
        verify(healthRecordMapper).insertMedicalHistory(any(HealthRecordMedicalHistory.class));
        verify(healthRecordMapper).insertMedication(any(HealthRecordMedication.class));
        verify(healthRecordMapper).insertFamilyHistory(any(HealthRecordFamilyHistory.class));
    }

    @Test
    @DisplayName("saveFromQuestionnaire - 空的子表数据不插入")
    void saveFromQuestionnaire_emptySubTables() {
        RiskAssessRequest request = createRiskAssessRequest();
        request.setMedicalHistories(null);
        request.setMedications(null);
        request.setFamilyHistories(null);

        when(healthRecordMapper.insert(any(HealthRecord.class))).thenReturn(1);

        String recordId = service.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5));

        assertNotNull(recordId);
        verify(healthRecordMapper).insert(any(HealthRecord.class));
        verify(healthRecordMapper, never()).insertMedicalHistory(any());
        verify(healthRecordMapper, never()).insertMedication(any());
        verify(healthRecordMapper, never()).insertFamilyHistory(any());
    }

    @Test
    @DisplayName("getLatest - 返回带非空子表数据的健康记录")
    void getLatest_withSubTables() {
        HealthRecord record = createHealthRecord("hr_001", "user1");
        when(healthRecordMapper.findLatestByUserId("user1")).thenReturn(record);
        when(healthRecordMapper.findMedicalHistoriesByRecordId("hr_001")).thenReturn(createMedicalHistoryList());
        when(healthRecordMapper.findMedicationsByRecordId("hr_001")).thenReturn(createMedicationList());
        when(healthRecordMapper.findFamilyHistoriesByRecordId("hr_001")).thenReturn(createFamilyHistoryList());

        Map<String, Object> result = service.getLatest("user1");

        assertEquals("hr_001", result.get("recordId"));
        List<?> medicalHistories = (List<?>) result.get("medicalHistories");
        assertEquals(1, medicalHistories.size());
        List<?> medications = (List<?>) result.get("medications");
        assertEquals(1, medications.size());
        List<?> familyHistories = (List<?>) result.get("familyHistories");
        assertEquals(1, familyHistories.size());
        assertEquals("高血压", result.get("medicalHistory"));
        assertEquals("阿司匹林（每日一次）", result.get("medication"));
    }

    @Test
    @DisplayName("saveFromQuestionnaire - 跳过空 diseaseName 的病史记录")
    void saveFromQuestionnaire_skipBlankDiseaseName() {
        RiskAssessRequest request = createRiskAssessRequestWithEmptyDiseaseName();

        when(healthRecordMapper.insert(any(HealthRecord.class))).thenReturn(1);
        when(healthRecordMapper.insertMedicalHistory(any(HealthRecordMedicalHistory.class))).thenReturn(1);
        when(healthRecordMapper.insertMedication(any(HealthRecordMedication.class))).thenReturn(1);
        when(healthRecordMapper.insertFamilyHistory(any(HealthRecordFamilyHistory.class))).thenReturn(1);

        String recordId = service.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5));

        assertNotNull(recordId);
        verify(healthRecordMapper).insert(any(HealthRecord.class));
        verify(healthRecordMapper, times(1)).insertMedicalHistory(any());
    }

    @Test
    @DisplayName("saveFromQuestionnaire - 跳过空 drugName 的药物记录")
    void saveFromQuestionnaire_skipBlankDrugName() {
        RiskAssessRequest request = createRiskAssessRequestWithEmptyDrugName();

        when(healthRecordMapper.insert(any(HealthRecord.class))).thenReturn(1);
        when(healthRecordMapper.insertMedicalHistory(any(HealthRecordMedicalHistory.class))).thenReturn(1);
        when(healthRecordMapper.insertMedication(any(HealthRecordMedication.class))).thenReturn(1);
        when(healthRecordMapper.insertFamilyHistory(any(HealthRecordFamilyHistory.class))).thenReturn(1);

        String recordId = service.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5));

        assertNotNull(recordId);
        verify(healthRecordMapper).insert(any(HealthRecord.class));
        verify(healthRecordMapper, times(1)).insertMedication(any());
    }

    @Test
    @DisplayName("saveFromQuestionnaire - 跳过空 diseaseName 的家族病史记录")
    void saveFromQuestionnaire_skipBlankFamilyDiseaseName() {
        RiskAssessRequest request = createRiskAssessRequestWithEmptyFamilyDiseaseName();

        when(healthRecordMapper.insert(any(HealthRecord.class))).thenReturn(1);
        when(healthRecordMapper.insertMedicalHistory(any(HealthRecordMedicalHistory.class))).thenReturn(1);
        when(healthRecordMapper.insertMedication(any(HealthRecordMedication.class))).thenReturn(1);
        when(healthRecordMapper.insertFamilyHistory(any(HealthRecordFamilyHistory.class))).thenReturn(1);

        String recordId = service.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5));

        assertNotNull(recordId);
        verify(healthRecordMapper).insert(any(HealthRecord.class));
        verify(healthRecordMapper, times(1)).insertFamilyHistory(any());
    }

    @Test
    @DisplayName("saveFromQuestionnaire - 家族病史 isAlive=false")
    void saveFromQuestionnaire_familyHistoryIsAliveFalse() {
        RiskAssessRequest request = new RiskAssessRequest();
        request.setHeight(170f);
        request.setWeight(65f);
        request.setFastingGlucose(5.5f);
        request.setSystolicBp(120);
        request.setDiastolicBp(80);
        request.setFamilyHistory(true);

        FamilyHistoryItem familyHistory = new FamilyHistoryItem();
        familyHistory.setRelation("父亲");
        familyHistory.setDiseaseName("糖尿病");
        familyHistory.setIsAlive(false);
        request.setFamilyHistories(List.of(familyHistory));

        when(healthRecordMapper.insert(any(HealthRecord.class))).thenReturn(1);
        when(healthRecordMapper.insertFamilyHistory(any(HealthRecordFamilyHistory.class))).thenReturn(1);

        String recordId = service.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5));

        assertNotNull(recordId);
        verify(healthRecordMapper).insert(any(HealthRecord.class));
        verify(healthRecordMapper).insertFamilyHistory(argThat(h -> h.getIsAlive() == 0));
    }

    private HealthRecord createHealthRecord(String recordId, String userId) {
        HealthRecord record = new HealthRecord();
        record.setRecordId(recordId);
        record.setUserId(userId);
        record.setHeight(BigDecimal.valueOf(170));
        record.setWeight(BigDecimal.valueOf(65));
        record.setBmi(BigDecimal.valueOf(22.5));
        record.setFastingGlucose(BigDecimal.valueOf(5.5));
        record.setSystolicBp(BigDecimal.valueOf(120));
        record.setDiastolicBp(BigDecimal.valueOf(80));
        record.setHasDiabetesFamily(0);
        record.setSmoking(0);
        record.setRecordedAt(LocalDateTime.now());
        return record;
    }

    private RiskAssessRequest createRiskAssessRequest() {
        RiskAssessRequest request = new RiskAssessRequest();
        request.setHeight(170f);
        request.setWeight(65f);
        request.setFastingGlucose(5.5f);
        request.setSystolicBp(120);
        request.setDiastolicBp(80);
        request.setFamilyHistory(true);

        MedicalHistoryItem medicalHistory = new MedicalHistoryItem();
        medicalHistory.setDiseaseName("高血压");
        request.setMedicalHistories(List.of(medicalHistory));

        MedicationItem medication = new MedicationItem();
        medication.setDrugName("阿司匹林");
        medication.setFrequencyDesc("每日一次");
        request.setMedications(List.of(medication));

        FamilyHistoryItem familyHistory = new FamilyHistoryItem();
        familyHistory.setRelation("父亲");
        familyHistory.setDiseaseName("糖尿病");
        request.setFamilyHistories(List.of(familyHistory));

        return request;
    }

    private List<HealthRecordMedicalHistory> createMedicalHistoryList() {
        HealthRecordMedicalHistory history = new HealthRecordMedicalHistory();
        history.setDiseaseName("高血压");
        history.setStatus(1);
        return List.of(history);
    }

    private List<HealthRecordMedication> createMedicationList() {
        HealthRecordMedication med = new HealthRecordMedication();
        med.setDrugName("阿司匹林");
        med.setFrequencyDesc("每日一次");
        med.setStatus(1);
        return List.of(med);
    }

    private List<HealthRecordFamilyHistory> createFamilyHistoryList() {
        HealthRecordFamilyHistory family = new HealthRecordFamilyHistory();
        family.setRelation("父亲");
        family.setDiseaseName("糖尿病");
        return List.of(family);
    }

    private RiskAssessRequest createRiskAssessRequestWithEmptyDiseaseName() {
        RiskAssessRequest request = new RiskAssessRequest();
        request.setHeight(170f);
        request.setWeight(65f);
        request.setFastingGlucose(5.5f);
        request.setSystolicBp(120);
        request.setDiastolicBp(80);
        request.setFamilyHistory(true);

        MedicalHistoryItem medicalHistory = new MedicalHistoryItem();
        medicalHistory.setDiseaseName("高血压");
        MedicalHistoryItem emptyHistory = new MedicalHistoryItem();
        emptyHistory.setDiseaseName("");
        request.setMedicalHistories(List.of(medicalHistory, emptyHistory));

        MedicationItem medication = new MedicationItem();
        medication.setDrugName("阿司匹林");
        request.setMedications(List.of(medication));

        FamilyHistoryItem familyHistory = new FamilyHistoryItem();
        familyHistory.setRelation("父亲");
        familyHistory.setDiseaseName("糖尿病");
        request.setFamilyHistories(List.of(familyHistory));

        return request;
    }

    private RiskAssessRequest createRiskAssessRequestWithEmptyDrugName() {
        RiskAssessRequest request = new RiskAssessRequest();
        request.setHeight(170f);
        request.setWeight(65f);
        request.setFastingGlucose(5.5f);
        request.setSystolicBp(120);
        request.setDiastolicBp(80);
        request.setFamilyHistory(true);

        MedicalHistoryItem medicalHistory = new MedicalHistoryItem();
        medicalHistory.setDiseaseName("高血压");
        request.setMedicalHistories(List.of(medicalHistory));

        MedicationItem medication = new MedicationItem();
        medication.setDrugName("阿司匹林");
        MedicationItem emptyMed = new MedicationItem();
        emptyMed.setDrugName("");
        request.setMedications(List.of(medication, emptyMed));

        FamilyHistoryItem familyHistory = new FamilyHistoryItem();
        familyHistory.setRelation("父亲");
        familyHistory.setDiseaseName("糖尿病");
        request.setFamilyHistories(List.of(familyHistory));

        return request;
    }

    private RiskAssessRequest createRiskAssessRequestWithEmptyFamilyDiseaseName() {
        RiskAssessRequest request = new RiskAssessRequest();
        request.setHeight(170f);
        request.setWeight(65f);
        request.setFastingGlucose(5.5f);
        request.setSystolicBp(120);
        request.setDiastolicBp(80);
        request.setFamilyHistory(true);

        MedicalHistoryItem medicalHistory = new MedicalHistoryItem();
        medicalHistory.setDiseaseName("高血压");
        request.setMedicalHistories(List.of(medicalHistory));

        MedicationItem medication = new MedicationItem();
        medication.setDrugName("阿司匹林");
        request.setMedications(List.of(medication));

        FamilyHistoryItem familyHistory = new FamilyHistoryItem();
        familyHistory.setRelation("父亲");
        familyHistory.setDiseaseName("糖尿病");
        FamilyHistoryItem emptyFamily = new FamilyHistoryItem();
        emptyFamily.setDiseaseName("");
        request.setFamilyHistories(List.of(familyHistory, emptyFamily));

        return request;
    }

    @Test
    @DisplayName("saveFromQuestionnaire - 测试非空可选字段")
    void saveFromQuestionnaire_withOptionalFields() {
        RiskAssessRequest request = createRiskAssessRequest();
        request.setPostprandialGlucose(7.8f);
        request.setRandomGlucose(8.5f);
        request.setHba1c(6.5f);
        request.setDiabetesType(1);
        request.setDiagnosedDate("2023-01-15");
        request.setIsPregnant(true);
        request.setIsInsulinTaken(true);
        request.setTestSource(2);

        when(healthRecordMapper.insert(any(HealthRecord.class))).thenReturn(1);
        when(healthRecordMapper.insertMedicalHistory(any(HealthRecordMedicalHistory.class))).thenReturn(1);
        when(healthRecordMapper.insertMedication(any(HealthRecordMedication.class))).thenReturn(1);
        when(healthRecordMapper.insertFamilyHistory(any(HealthRecordFamilyHistory.class))).thenReturn(1);

        String recordId = service.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5));

        assertNotNull(recordId);
        verify(healthRecordMapper).insert(any(HealthRecord.class));
    }

    @Test
    @DisplayName("saveFromQuestionnaire - 测试子表详细日期和状态字段")
    void saveFromQuestionnaire_subTablesWithDates() {
        RiskAssessRequest request = new RiskAssessRequest();
        request.setHeight(170f);
        request.setWeight(65f);
        request.setFastingGlucose(5.5f);
        request.setSystolicBp(120);
        request.setDiastolicBp(80);

        MedicalHistoryItem medicalHistory = new MedicalHistoryItem();
        medicalHistory.setDiseaseName("高血压");
        medicalHistory.setDiagnosedDate("2020-06-10");
        medicalHistory.setStatus(2);
        request.setMedicalHistories(List.of(medicalHistory));

        MedicationItem medication = new MedicationItem();
        medication.setDrugName("二甲双胍");
        medication.setStartDate("2023-01-01");
        medication.setEndDate("2024-01-01");
        medication.setIsInsulin(false);
        medication.setStatus(2);
        request.setMedications(List.of(medication));

        FamilyHistoryItem familyHistory = new FamilyHistoryItem();
        familyHistory.setRelation("母亲");
        familyHistory.setDiseaseName("糖尿病");
        familyHistory.setIsAlive(false);
        familyHistory.setIsDiabetes(true);
        request.setFamilyHistories(List.of(familyHistory));

        when(healthRecordMapper.insert(any(HealthRecord.class))).thenReturn(1);
        when(healthRecordMapper.insertMedicalHistory(any(HealthRecordMedicalHistory.class))).thenReturn(1);
        when(healthRecordMapper.insertMedication(any(HealthRecordMedication.class))).thenReturn(1);
        when(healthRecordMapper.insertFamilyHistory(any(HealthRecordFamilyHistory.class))).thenReturn(1);

        String recordId = service.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5));

        assertNotNull(recordId);
        verify(healthRecordMapper).insert(any(HealthRecord.class));
    }

    @Test
    @DisplayName("save - 测试空 height/weight 不计算 BMI")
    void save_nullHeightWeight() {
        UpdateHealthRecordRequest request = new UpdateHealthRecordRequest();
        request.setFastingGlucose(5.5f);
        request.setSystolicBp(120);
        request.setDiastolicBp(80);
        request.setFamilyHistory(false);
        request.setSmoking(0);

        when(healthRecordMapper.insert(any(HealthRecord.class))).thenReturn(1);
        when(healthRecordMapper.findMedicalHistoriesByRecordId(anyString())).thenReturn(List.of());
        when(healthRecordMapper.findMedicationsByRecordId(anyString())).thenReturn(List.of());
        when(healthRecordMapper.findFamilyHistoriesByRecordId(anyString())).thenReturn(List.of());

        Map<String, Object> result = service.save("user1", request);

        assertNotNull(result.get("recordId"));
        assertNull(result.get("bmi"));
        assertFalse((Boolean) result.get("familyHistory"));
    }

    @Test
    @DisplayName("toDetailView - 测试 null 值转换")
    void toDetailView_nullValues() {
        HealthRecord record = new HealthRecord();
        record.setRecordId("hr_null_test");
        record.setIsPregnant(null);
        record.setHasDiabetesFamily(null);
        record.setIsInsulinTaken(null);

        when(healthRecordMapper.findLatestByUserId("user1")).thenReturn(record);
        when(healthRecordMapper.findMedicalHistoriesByRecordId("hr_null_test")).thenReturn(List.of());
        when(healthRecordMapper.findMedicationsByRecordId("hr_null_test")).thenReturn(List.of());
        when(healthRecordMapper.findFamilyHistoriesByRecordId("hr_null_test")).thenReturn(List.of());

        Map<String, Object> result = service.getLatest("user1");

        assertEquals("hr_null_test", result.get("recordId"));
        assertFalse((Boolean) result.get("isPregnant"));
        assertFalse((Boolean) result.get("familyHistory"));
        assertFalse((Boolean) result.get("isInsulinTaken"));
    }

    @Test
    @DisplayName("summarizeMedicalHistories - 空列表返回空字符串")
    void summarizeMedicalHistories_empty() {
        HealthRecord record = new HealthRecord();
        record.setRecordId("hr_summary_test");

        when(healthRecordMapper.findLatestByUserId("user1")).thenReturn(record);
        when(healthRecordMapper.findMedicalHistoriesByRecordId("hr_summary_test")).thenReturn(List.of());
        when(healthRecordMapper.findMedicationsByRecordId("hr_summary_test")).thenReturn(List.of());
        when(healthRecordMapper.findFamilyHistoriesByRecordId("hr_summary_test")).thenReturn(List.of());

        Map<String, Object> result = service.getLatest("user1");

        assertEquals("", result.get("medicalHistory"));
        assertEquals("", result.get("medication"));
    }

    @Test
    @DisplayName("summarizeMedications - 药物带剂量和频率")
    void summarizeMedications_withDosage() {
        HealthRecord record = new HealthRecord();
        record.setRecordId("hr_med_test");

        HealthRecordMedication med = new HealthRecordMedication();
        med.setDrugName("二甲双胍");
        med.setDosage("500mg");
        med.setFrequencyDesc("每日两次");

        when(healthRecordMapper.findLatestByUserId("user1")).thenReturn(record);
        when(healthRecordMapper.findMedicalHistoriesByRecordId("hr_med_test")).thenReturn(List.of());
        when(healthRecordMapper.findMedicationsByRecordId("hr_med_test")).thenReturn(List.of(med));
        when(healthRecordMapper.findFamilyHistoriesByRecordId("hr_med_test")).thenReturn(List.of());

        Map<String, Object> result = service.getLatest("user1");

        assertEquals("二甲双胍（500mg 每日两次）", result.get("medication"));
    }

    @Test
    @DisplayName("saveFromQuestionnaire - 日期字段为空字符串不解析")
    void saveFromQuestionnaire_blankDateFields() {
        RiskAssessRequest request = new RiskAssessRequest();
        request.setHeight(170f);
        request.setWeight(65f);
        request.setFastingGlucose(5.5f);
        request.setSystolicBp(120);
        request.setDiastolicBp(80);
        request.setDiagnosedDate("");

        MedicalHistoryItem medicalHistory = new MedicalHistoryItem();
        medicalHistory.setDiseaseName("高血压");
        medicalHistory.setDiagnosedDate("");
        request.setMedicalHistories(List.of(medicalHistory));

        MedicationItem medication = new MedicationItem();
        medication.setDrugName("阿司匹林");
        medication.setStartDate("");
        medication.setEndDate("");
        medication.setIsInsulin(true);
        request.setMedications(List.of(medication));

        FamilyHistoryItem familyHistory = new FamilyHistoryItem();
        familyHistory.setRelation("父亲");
        familyHistory.setDiseaseName("糖尿病");
        familyHistory.setIsAlive(false);
        request.setFamilyHistories(List.of(familyHistory));

        when(healthRecordMapper.insert(any(HealthRecord.class))).thenReturn(1);
        when(healthRecordMapper.insertMedicalHistory(any(HealthRecordMedicalHistory.class))).thenReturn(1);
        when(healthRecordMapper.insertMedication(any(HealthRecordMedication.class))).thenReturn(1);
        when(healthRecordMapper.insertFamilyHistory(any(HealthRecordFamilyHistory.class))).thenReturn(1);

        String recordId = service.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5));

        assertNotNull(recordId);
        verify(healthRecordMapper).insert(any(HealthRecord.class));
    }

    @Test
    @DisplayName("saveFromQuestionnaire - 子表日期字段为null不解析")
    void saveFromQuestionnaire_nullDateFields() {
        RiskAssessRequest request = new RiskAssessRequest();
        request.setHeight(170f);
        request.setWeight(65f);
        request.setFastingGlucose(5.5f);
        request.setSystolicBp(120);
        request.setDiastolicBp(80);

        MedicalHistoryItem medicalHistory = new MedicalHistoryItem();
        medicalHistory.setDiseaseName("高血压");
        medicalHistory.setDiagnosedDate(null);
        request.setMedicalHistories(List.of(medicalHistory));

        MedicationItem medication = new MedicationItem();
        medication.setDrugName("阿司匹林");
        medication.setStartDate(null);
        medication.setEndDate(null);
        medication.setIsInsulin(null);
        request.setMedications(List.of(medication));

        FamilyHistoryItem familyHistory = new FamilyHistoryItem();
        familyHistory.setRelation("父亲");
        familyHistory.setDiseaseName("糖尿病");
        familyHistory.setIsAlive(null);
        familyHistory.setIsDiabetes(false);
        request.setFamilyHistories(List.of(familyHistory));

        when(healthRecordMapper.insert(any(HealthRecord.class))).thenReturn(1);
        when(healthRecordMapper.insertMedicalHistory(any(HealthRecordMedicalHistory.class))).thenReturn(1);
        when(healthRecordMapper.insertMedication(any(HealthRecordMedication.class))).thenReturn(1);
        when(healthRecordMapper.insertFamilyHistory(any(HealthRecordFamilyHistory.class))).thenReturn(1);

        String recordId = service.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5));

        assertNotNull(recordId);
        verify(healthRecordMapper).insert(any(HealthRecord.class));
    }

    @Test
    @DisplayName("save - 空血糖和血压字段")
    void save_nullGlucoseAndBp() {
        UpdateHealthRecordRequest request = new UpdateHealthRecordRequest();
        request.setHeight(170f);
        request.setWeight(65f);
        request.setFastingGlucose(null);
        request.setSystolicBp(null);
        request.setDiastolicBp(null);
        request.setFamilyHistory(false);
        request.setSmoking(0);

        when(healthRecordMapper.insert(any(HealthRecord.class))).thenReturn(1);
        when(healthRecordMapper.findMedicalHistoriesByRecordId(anyString())).thenReturn(List.of());
        when(healthRecordMapper.findMedicationsByRecordId(anyString())).thenReturn(List.of());
        when(healthRecordMapper.findFamilyHistoriesByRecordId(anyString())).thenReturn(List.of());

        Map<String, Object> result = service.save("user1", request);

        assertNotNull(result.get("recordId"));
        assertNull(result.get("fastingGlucose"));
        assertNull(result.get("systolicBp"));
        assertNull(result.get("diastolicBp"));
    }

    @Test
    @DisplayName("toDetailView - recordId为null返回空子表")
    void toDetailView_nullRecordId() {
        HealthRecord record = new HealthRecord();
        record.setRecordId(null);
        record.setHeight(BigDecimal.valueOf(170));
        record.setWeight(BigDecimal.valueOf(65));
        record.setIsPregnant(1);
        record.setHasDiabetesFamily(1);
        record.setIsInsulinTaken(1);

        when(healthRecordMapper.findLatestByUserId("user1")).thenReturn(record);

        Map<String, Object> result = service.getLatest("user1");

        assertNull(result.get("recordId"));
        assertEquals(List.of(), result.get("medicalHistories"));
        assertEquals(List.of(), result.get("medications"));
        assertEquals(List.of(), result.get("familyHistories"));
        assertEquals("", result.get("medicalHistory"));
        assertEquals("", result.get("medication"));
        assertTrue((Boolean) result.get("isPregnant"));
        assertTrue((Boolean) result.get("familyHistory"));
        assertTrue((Boolean) result.get("isInsulinTaken"));
    }

    @Test
    @DisplayName("summarizeMedicalHistories - 列表包含空diseaseName")
    void summarizeMedicalHistories_withBlankDiseaseName() {
        HealthRecord record = new HealthRecord();
        record.setRecordId("hr_summary_blank");

        HealthRecordMedicalHistory history1 = new HealthRecordMedicalHistory();
        history1.setDiseaseName("高血压");
        HealthRecordMedicalHistory history2 = new HealthRecordMedicalHistory();
        history2.setDiseaseName("");

        when(healthRecordMapper.findLatestByUserId("user1")).thenReturn(record);
        when(healthRecordMapper.findMedicalHistoriesByRecordId("hr_summary_blank"))
                .thenReturn(List.of(history1, history2));
        when(healthRecordMapper.findMedicationsByRecordId("hr_summary_blank")).thenReturn(List.of());
        when(healthRecordMapper.findFamilyHistoriesByRecordId("hr_summary_blank")).thenReturn(List.of());

        Map<String, Object> result = service.getLatest("user1");

        assertEquals("高血压", result.get("medicalHistory"));
    }

    @Test
    @DisplayName("summarizeMedications - 只有剂量没有频率")
    void summarizeMedications_dosageOnly() {
        HealthRecord record = new HealthRecord();
        record.setRecordId("hr_med_dosage");

        HealthRecordMedication med = new HealthRecordMedication();
        med.setDrugName("二甲双胍");
        med.setDosage("500mg");
        med.setFrequencyDesc(null);

        when(healthRecordMapper.findLatestByUserId("user1")).thenReturn(record);
        when(healthRecordMapper.findMedicationsByRecordId("hr_med_dosage")).thenReturn(List.of(med));

        Map<String, Object> result = service.getLatest("user1");

        assertEquals("二甲双胍（500mg）", result.get("medication"));
    }

    @Test
    @DisplayName("summarizeMedications - 只有频率没有剂量")
    void summarizeMedications_frequencyOnly() {
        HealthRecord record = new HealthRecord();
        record.setRecordId("hr_med_freq");

        HealthRecordMedication med = new HealthRecordMedication();
        med.setDrugName("二甲双胍");
        med.setDosage(null);
        med.setFrequencyDesc("每日两次");

        when(healthRecordMapper.findLatestByUserId("user1")).thenReturn(record);
        when(healthRecordMapper.findMedicationsByRecordId("hr_med_freq")).thenReturn(List.of(med));

        Map<String, Object> result = service.getLatest("user1");

        assertEquals("二甲双胍（每日两次）", result.get("medication"));
    }

    @Test
    @DisplayName("summarizeMedications - 药物名称为空被过滤")
    void summarizeMedications_blankDrugName() {
        HealthRecord record = new HealthRecord();
        record.setRecordId("hr_med_blank");

        HealthRecordMedication med1 = new HealthRecordMedication();
        med1.setDrugName("二甲双胍");
        HealthRecordMedication med2 = new HealthRecordMedication();
        med2.setDrugName("");

        when(healthRecordMapper.findLatestByUserId("user1")).thenReturn(record);
        when(healthRecordMapper.findMedicationsByRecordId("hr_med_blank")).thenReturn(List.of(med1, med2));

        Map<String, Object> result = service.getLatest("user1");

        assertEquals("二甲双胍", result.get("medication"));
    }

    @Test
    @DisplayName("toDetailView - 布尔字段为1的情况")
    void toDetailView_booleanFieldsTrue() {
        HealthRecord record = new HealthRecord();
        record.setRecordId("hr_bool_true");
        record.setIsPregnant(1);
        record.setHasDiabetesFamily(1);
        record.setIsInsulinTaken(1);

        when(healthRecordMapper.findLatestByUserId("user1")).thenReturn(record);
        when(healthRecordMapper.findMedicalHistoriesByRecordId("hr_bool_true")).thenReturn(List.of());
        when(healthRecordMapper.findMedicationsByRecordId("hr_bool_true")).thenReturn(List.of());
        when(healthRecordMapper.findFamilyHistoriesByRecordId("hr_bool_true")).thenReturn(List.of());

        Map<String, Object> result = service.getLatest("user1");

        assertTrue((Boolean) result.get("isPregnant"));
        assertTrue((Boolean) result.get("familyHistory"));
        assertTrue((Boolean) result.get("isInsulinTaken"));
    }

    @Test
    @DisplayName("medicationView - isInsulin为1")
    void medicationView_isInsulinTrue() {
        HealthRecord record = new HealthRecord();
        record.setRecordId("hr_insulin_true");

        HealthRecordMedication med = new HealthRecordMedication();
        med.setDrugName("胰岛素");
        med.setIsInsulin(1);

        when(healthRecordMapper.findLatestByUserId("user1")).thenReturn(record);
        when(healthRecordMapper.findMedicationsByRecordId("hr_insulin_true")).thenReturn(List.of(med));

        Map<String, Object> result = service.getLatest("user1");

        List<?> medications = (List<?>) result.get("medications");
        assertTrue((Boolean) ((Map<?, ?>) medications.get(0)).get("isInsulin"));
    }

    @Test
    @DisplayName("familyHistoryView - isAlive为0和isDiabetes为1")
    void familyHistoryView_isAliveFalse() {
        HealthRecord record = new HealthRecord();
        record.setRecordId("hr_family_alive");

        HealthRecordFamilyHistory family = new HealthRecordFamilyHistory();
        family.setRelation("父亲");
        family.setDiseaseName("糖尿病");
        family.setIsAlive(0);
        family.setIsDiabetes(1);

        when(healthRecordMapper.findLatestByUserId("user1")).thenReturn(record);
        when(healthRecordMapper.findFamilyHistoriesByRecordId("hr_family_alive")).thenReturn(List.of(family));

        Map<String, Object> result = service.getLatest("user1");

        List<?> familyHistories = (List<?>) result.get("familyHistories");
        assertFalse((Boolean) ((Map<?, ?>) familyHistories.get(0)).get("isAlive"));
        assertTrue((Boolean) ((Map<?, ?>) familyHistories.get(0)).get("isDiabetes"));
    }

    @Test
    @DisplayName("saveFromQuestionnaire - 子表item为null被跳过")
    void saveFromQuestionnaire_nullItems() {
        RiskAssessRequest request = new RiskAssessRequest();
        request.setHeight(170f);
        request.setWeight(65f);
        request.setFastingGlucose(5.5f);
        request.setSystolicBp(120);
        request.setDiastolicBp(80);

        MedicalHistoryItem medicalHistory = new MedicalHistoryItem();
        medicalHistory.setDiseaseName(null);
        request.setMedicalHistories(List.of(medicalHistory));

        MedicationItem medication = new MedicationItem();
        medication.setDrugName(null);
        request.setMedications(List.of(medication));

        FamilyHistoryItem familyHistory = new FamilyHistoryItem();
        familyHistory.setDiseaseName(null);
        familyHistory.setIsAlive(null);
        request.setFamilyHistories(List.of(familyHistory));

        when(healthRecordMapper.insert(any(HealthRecord.class))).thenReturn(1);

        String recordId = service.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5));

        assertNotNull(recordId);
        verify(healthRecordMapper).insert(any(HealthRecord.class));
        verify(healthRecordMapper, never()).insertMedicalHistory(any());
        verify(healthRecordMapper, never()).insertMedication(any());
        verify(healthRecordMapper, never()).insertFamilyHistory(any());
    }

    @Test
    @DisplayName("saveFromQuestionnaire - isAlive为false")
    void saveFromQuestionnaire_isAliveFalse() {
        RiskAssessRequest request = new RiskAssessRequest();
        request.setHeight(170f);
        request.setWeight(65f);
        request.setFastingGlucose(5.5f);
        request.setSystolicBp(120);
        request.setDiastolicBp(80);

        FamilyHistoryItem familyHistory = new FamilyHistoryItem();
        familyHistory.setRelation("父亲");
        familyHistory.setDiseaseName("糖尿病");
        familyHistory.setIsAlive(false);
        request.setFamilyHistories(List.of(familyHistory));

        when(healthRecordMapper.insert(any(HealthRecord.class))).thenReturn(1);
        when(healthRecordMapper.insertFamilyHistory(any(HealthRecordFamilyHistory.class))).thenReturn(1);

        String recordId = service.saveFromQuestionnaire("user1", request, BigDecimal.valueOf(22.5));

        assertNotNull(recordId);
        verify(healthRecordMapper).insertFamilyHistory(any());
    }
}