package com.diabetes.health.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 糖尿病风险问卷（不含年龄/性别，由后端从 USERS 表拉取后一并提交 Dify）
 */
public class RiskAssessRequest {

    @NotNull @DecimalMin("100") @DecimalMax("250")
    private Float height;

    @NotNull @DecimalMin("30") @DecimalMax("200")
    private Float weight;

    @NotNull @DecimalMin("3") @DecimalMax("30")
    private Float fastingGlucose;

    private Float postprandialGlucose;
    private Float randomGlucose;
    private Float hba1c;

    @NotNull @Min(60) @Max(250)
    private Integer systolicBp;

    @NotNull @Min(40) @Max(150)
    private Integer diastolicBp;

    private Integer diabetesType;
    private String diagnosedDate;
    private Boolean isPregnant;

    @NotNull
    private Boolean familyHistory;

    private Boolean isInsulinTaken;
    private Integer smoking;
    private Integer alcohol;
    private Integer exerciseFreq;
    private String dietType;
    private Integer testSource;

    @Valid
    private List<MedicalHistoryItem> medicalHistories = new ArrayList<>();

    @Valid
    private List<MedicationItem> medications = new ArrayList<>();

    @Valid
    private List<FamilyHistoryItem> familyHistories = new ArrayList<>();

    public Float getHeight() { return height; }
    public void setHeight(Float height) { this.height = height; }
    public Float getWeight() { return weight; }
    public void setWeight(Float weight) { this.weight = weight; }
    public Float getFastingGlucose() { return fastingGlucose; }
    public void setFastingGlucose(Float fastingGlucose) { this.fastingGlucose = fastingGlucose; }
    public Float getPostprandialGlucose() { return postprandialGlucose; }
    public void setPostprandialGlucose(Float postprandialGlucose) { this.postprandialGlucose = postprandialGlucose; }
    public Float getRandomGlucose() { return randomGlucose; }
    public void setRandomGlucose(Float randomGlucose) { this.randomGlucose = randomGlucose; }
    public Float getHba1c() { return hba1c; }
    public void setHba1c(Float hba1c) { this.hba1c = hba1c; }
    public Integer getSystolicBp() { return systolicBp; }
    public void setSystolicBp(Integer systolicBp) { this.systolicBp = systolicBp; }
    public Integer getDiastolicBp() { return diastolicBp; }
    public void setDiastolicBp(Integer diastolicBp) { this.diastolicBp = diastolicBp; }
    public Integer getDiabetesType() { return diabetesType; }
    public void setDiabetesType(Integer diabetesType) { this.diabetesType = diabetesType; }
    public String getDiagnosedDate() { return diagnosedDate; }
    public void setDiagnosedDate(String diagnosedDate) { this.diagnosedDate = diagnosedDate; }
    public Boolean getIsPregnant() { return isPregnant; }
    public void setIsPregnant(Boolean isPregnant) { this.isPregnant = isPregnant; }
    public Boolean getFamilyHistory() { return familyHistory; }
    public void setFamilyHistory(Boolean familyHistory) { this.familyHistory = familyHistory; }
    public Boolean getIsInsulinTaken() { return isInsulinTaken; }
    public void setIsInsulinTaken(Boolean isInsulinTaken) { this.isInsulinTaken = isInsulinTaken; }
    public Integer getSmoking() { return smoking; }
    public void setSmoking(Integer smoking) { this.smoking = smoking; }
    public Integer getAlcohol() { return alcohol; }
    public void setAlcohol(Integer alcohol) { this.alcohol = alcohol; }
    public Integer getExerciseFreq() { return exerciseFreq; }
    public void setExerciseFreq(Integer exerciseFreq) { this.exerciseFreq = exerciseFreq; }
    public String getDietType() { return dietType; }
    public void setDietType(String dietType) { this.dietType = dietType; }
    public Integer getTestSource() { return testSource; }
    public void setTestSource(Integer testSource) { this.testSource = testSource; }
    public List<MedicalHistoryItem> getMedicalHistories() { return medicalHistories; }
    public void setMedicalHistories(List<MedicalHistoryItem> medicalHistories) { this.medicalHistories = medicalHistories; }
    public List<MedicationItem> getMedications() { return medications; }
    public void setMedications(List<MedicationItem> medications) { this.medications = medications; }
    public List<FamilyHistoryItem> getFamilyHistories() { return familyHistories; }
    public void setFamilyHistories(List<FamilyHistoryItem> familyHistories) { this.familyHistories = familyHistories; }
}
