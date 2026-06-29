package com.diabetes.health.service;

import com.diabetes.health.dto.RiskAssessRequest;
import com.diabetes.health.dto.UpdateHealthRecordRequest;
import com.diabetes.health.entity.*;
import com.diabetes.health.mapper.HealthRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class HealthRecordService {

    private final HealthRecordMapper healthRecordMapper;

    public HealthRecordService(HealthRecordMapper healthRecordMapper) {
        this.healthRecordMapper = healthRecordMapper;
    }

    public Map<String, Object> getLatest(String userId) {
        HealthRecord record = healthRecordMapper.findLatestByUserId(userId);
        if (record == null) {
            return Map.of();
        }
        return toDetailView(record);
    }

    @Transactional
    public Map<String, Object> save(String userId, UpdateHealthRecordRequest request) {
        HealthRecord record = buildRecord(userId, request);
        healthRecordMapper.insert(record);
        return toDetailView(record);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String saveFromQuestionnaire(String userId, RiskAssessRequest request, BigDecimal bmi) {
        String recordId = com.diabetes.common.util.IdGenerator.nextId("hr_");
        HealthRecord record = new HealthRecord();
        record.setRecordId(recordId);
        record.setUserId(userId);
        record.setHeight(BigDecimal.valueOf(request.getHeight()));
        record.setWeight(BigDecimal.valueOf(request.getWeight()));
        record.setBmi(bmi);
        record.setFastingGlucose(BigDecimal.valueOf(request.getFastingGlucose()));
        if (request.getPostprandialGlucose() != null) {
            record.setPostprandialGlucose(BigDecimal.valueOf(request.getPostprandialGlucose()));
        }
        if (request.getRandomGlucose() != null) {
            record.setRandomGlucose(BigDecimal.valueOf(request.getRandomGlucose()));
        }
        if (request.getHba1c() != null) {
            record.setHba1c(BigDecimal.valueOf(request.getHba1c()));
        }
        record.setSystolicBp(BigDecimal.valueOf(request.getSystolicBp()));
        record.setDiastolicBp(BigDecimal.valueOf(request.getDiastolicBp()));
        record.setDiabetesType(request.getDiabetesType() != null ? request.getDiabetesType() : 9);
        if (request.getDiagnosedDate() != null && !request.getDiagnosedDate().isBlank()) {
            record.setDiagnosedDate(LocalDate.parse(request.getDiagnosedDate()));
        }
        record.setIsPregnant(Boolean.TRUE.equals(request.getIsPregnant()) ? 1 : 0);
        record.setHasDiabetesFamily(Boolean.TRUE.equals(request.getFamilyHistory()) ? 1 : 0);
        record.setIsInsulinTaken(Boolean.TRUE.equals(request.getIsInsulinTaken()) ? 1 : 0);
        record.setSmoking(request.getSmoking());
        record.setAlcohol(request.getAlcohol());
        record.setExerciseFreq(request.getExerciseFreq());
        record.setDietType(request.getDietType());
        record.setTestSource(request.getTestSource() != null ? request.getTestSource() : 1);
        healthRecordMapper.insert(record);

        saveSubTables(userId, recordId, request);
        return recordId;
    }

    private void saveSubTables(String userId, String recordId, RiskAssessRequest request) {
        if (request.getMedicalHistories() != null) {
            for (var item : request.getMedicalHistories()) {
                if (item.getDiseaseName() == null || item.getDiseaseName().isBlank()) continue;
                HealthRecordMedicalHistory h = new HealthRecordMedicalHistory();
                h.setHistoryId(com.diabetes.common.util.IdGenerator.nextId("mh_"));
                h.setRecordId(recordId);
                h.setUserId(userId);
                h.setDiseaseCode(item.getDiseaseCode());
                h.setDiseaseName(item.getDiseaseName());
                if (item.getDiagnosedDate() != null && !item.getDiagnosedDate().isBlank()) {
                    h.setDiagnosedDate(LocalDate.parse(item.getDiagnosedDate()));
                }
                h.setStatus(item.getStatus() != null ? item.getStatus() : 1);
                h.setNote(item.getNote());
                h.setSource(1);
                healthRecordMapper.insertMedicalHistory(h);
            }
        }
        if (request.getMedications() != null) {
            for (var item : request.getMedications()) {
                if (item.getDrugName() == null || item.getDrugName().isBlank()) continue;
                HealthRecordMedication m = new HealthRecordMedication();
                m.setMedicationId(com.diabetes.common.util.IdGenerator.nextId("med_"));
                m.setRecordId(recordId);
                m.setUserId(userId);
                m.setDrugName(item.getDrugName());
                m.setGenericName(item.getGenericName());
                m.setDosage(item.getDosage());
                m.setFrequency(item.getFrequency());
                m.setFrequencyDesc(item.getFrequencyDesc());
                m.setRoute(item.getRoute());
                m.setPurpose(item.getPurpose());
                m.setIsInsulin(Boolean.TRUE.equals(item.getIsInsulin()) ? 1 : 0);
                if (item.getStartDate() != null && !item.getStartDate().isBlank()) {
                    m.setStartDate(LocalDate.parse(item.getStartDate()));
                }
                if (item.getEndDate() != null && !item.getEndDate().isBlank()) {
                    m.setEndDate(LocalDate.parse(item.getEndDate()));
                }
                m.setStatus(item.getStatus() != null ? item.getStatus() : 1);
                m.setSource(1);
                healthRecordMapper.insertMedication(m);
            }
        }
        if (request.getFamilyHistories() != null) {
            for (var item : request.getFamilyHistories()) {
                if (item.getDiseaseName() == null || item.getDiseaseName().isBlank()) continue;
                HealthRecordFamilyHistory f = new HealthRecordFamilyHistory();
                f.setFamilyHistoryId(com.diabetes.common.util.IdGenerator.nextId("fh_"));
                f.setRecordId(recordId);
                f.setUserId(userId);
                f.setRelation(item.getRelation());
                f.setMemberAge(item.getMemberAge());
                f.setIsAlive(item.getIsAlive() == null || item.getIsAlive() ? 1 : 0);
                f.setDiseaseCode(item.getDiseaseCode());
                f.setDiseaseName(item.getDiseaseName());
                f.setDiagnosedAge(item.getDiagnosedAge());
                f.setIsDiabetes(Boolean.TRUE.equals(item.getIsDiabetes()) ? 1 : 0);
                f.setNote(item.getNote());
                f.setSource(1);
                healthRecordMapper.insertFamilyHistory(f);
            }
        }
    }

    private HealthRecord buildRecord(String userId, UpdateHealthRecordRequest request) {
        String recordId = com.diabetes.common.util.IdGenerator.nextId("hr_");
        BigDecimal bmi = null;
        if (request.getHeight() != null && request.getWeight() != null) {
            float h = request.getHeight() / 100f;
            bmi = BigDecimal.valueOf(request.getWeight() / (h * h)).setScale(1, RoundingMode.HALF_UP);
        }
        HealthRecord record = new HealthRecord();
        record.setRecordId(recordId);
        record.setUserId(userId);
        record.setHeight(request.getHeight() == null ? null : BigDecimal.valueOf(request.getHeight()));
        record.setWeight(request.getWeight() == null ? null : BigDecimal.valueOf(request.getWeight()));
        record.setBmi(bmi);
        record.setFastingGlucose(request.getFastingGlucose() == null ? null : BigDecimal.valueOf(request.getFastingGlucose()));
        record.setSystolicBp(request.getSystolicBp() == null ? null : BigDecimal.valueOf(request.getSystolicBp()));
        record.setDiastolicBp(request.getDiastolicBp() == null ? null : BigDecimal.valueOf(request.getDiastolicBp()));
        record.setHasDiabetesFamily(Boolean.TRUE.equals(request.getFamilyHistory()) ? 1 : 0);
        record.setSmoking(request.getSmoking());
        return record;
    }

    private Map<String, Object> toDetailView(HealthRecord record) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("recordId", record.getRecordId());
        map.put("height", record.getHeight());
        map.put("weight", record.getWeight());
        map.put("bmi", record.getBmi());
        map.put("fastingGlucose", record.getFastingGlucose());
        map.put("postprandialGlucose", record.getPostprandialGlucose());
        map.put("randomGlucose", record.getRandomGlucose());
        map.put("hba1c", record.getHba1c());
        map.put("systolicBp", record.getSystolicBp());
        map.put("diastolicBp", record.getDiastolicBp());
        map.put("diabetesType", record.getDiabetesType());
        map.put("diagnosedDate", record.getDiagnosedDate());
        map.put("isPregnant", record.getIsPregnant() != null && record.getIsPregnant() == 1);
        map.put("familyHistory", record.getHasDiabetesFamily() != null && record.getHasDiabetesFamily() == 1);
        map.put("isInsulinTaken", record.getIsInsulinTaken() != null && record.getIsInsulinTaken() == 1);
        map.put("smoking", record.getSmoking());
        map.put("alcohol", record.getAlcohol());
        map.put("exerciseFreq", record.getExerciseFreq());
        map.put("dietType", record.getDietType());
        map.put("testSource", record.getTestSource());
        map.put("recordedAt", record.getRecordedAt());

        if (record.getRecordId() != null) {
            List<Map<String, Object>> medicalHistories = healthRecordMapper.findMedicalHistoriesByRecordId(record.getRecordId())
                    .stream().map(this::medicalHistoryView).toList();
            List<Map<String, Object>> medications = healthRecordMapper.findMedicationsByRecordId(record.getRecordId())
                    .stream().map(this::medicationView).toList();
            List<Map<String, Object>> familyHistories = healthRecordMapper.findFamilyHistoriesByRecordId(record.getRecordId())
                    .stream().map(this::familyHistoryView).toList();
            map.put("medicalHistories", medicalHistories);
            map.put("medications", medications);
            map.put("familyHistories", familyHistories);
            map.put("medicalHistory", summarizeMedicalHistories(medicalHistories));
            map.put("medication", summarizeMedications(medications));
        } else {
            map.put("medicalHistories", List.of());
            map.put("medications", List.of());
            map.put("familyHistories", List.of());
            map.put("medicalHistory", "");
            map.put("medication", "");
        }
        return map;
    }

    private String summarizeMedicalHistories(List<Map<String, Object>> histories) {
        if (histories == null || histories.isEmpty()) {
            return "";
        }
        return histories.stream()
                .map(h -> String.valueOf(h.getOrDefault("diseaseName", "")).trim())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("；"));
    }

    private String summarizeMedications(List<Map<String, Object>> medications) {
        if (medications == null || medications.isEmpty()) {
            return "";
        }
        return medications.stream()
                .map(m -> {
                    String name = String.valueOf(m.getOrDefault("drugName", "")).trim();
                    if (name.isEmpty()) return "";
                    String dosage = m.get("dosage") == null ? "" : String.valueOf(m.get("dosage")).trim();
                    String freq = m.get("frequencyDesc") == null ? "" : String.valueOf(m.get("frequencyDesc")).trim();
                    String detail = dosage.isEmpty() ? freq : (freq.isEmpty() ? dosage : dosage + " " + freq);
                    return detail.isEmpty() ? name : name + "（" + detail + "）";
                })
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("；"));
    }

    private Map<String, Object> medicalHistoryView(HealthRecordMedicalHistory h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("diseaseCode", h.getDiseaseCode());
        m.put("diseaseName", h.getDiseaseName());
        m.put("diagnosedDate", h.getDiagnosedDate());
        m.put("status", h.getStatus());
        m.put("note", h.getNote());
        return m;
    }

    private Map<String, Object> medicationView(HealthRecordMedication med) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("drugName", med.getDrugName());
        m.put("genericName", med.getGenericName());
        m.put("dosage", med.getDosage());
        m.put("frequency", med.getFrequency());
        m.put("frequencyDesc", med.getFrequencyDesc());
        m.put("route", med.getRoute());
        m.put("purpose", med.getPurpose());
        m.put("isInsulin", med.getIsInsulin() != null && med.getIsInsulin() == 1);
        m.put("startDate", med.getStartDate());
        m.put("endDate", med.getEndDate());
        m.put("status", med.getStatus());
        return m;
    }

    private Map<String, Object> familyHistoryView(HealthRecordFamilyHistory f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("relation", f.getRelation());
        m.put("memberAge", f.getMemberAge());
        m.put("isAlive", f.getIsAlive() == null || f.getIsAlive() == 1);
        m.put("diseaseCode", f.getDiseaseCode());
        m.put("diseaseName", f.getDiseaseName());
        m.put("diagnosedAge", f.getDiagnosedAge());
        m.put("isDiabetes", f.getIsDiabetes() != null && f.getIsDiabetes() == 1);
        m.put("note", f.getNote());
        return m;
    }
}
