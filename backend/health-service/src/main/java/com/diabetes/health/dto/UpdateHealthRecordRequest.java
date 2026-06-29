package com.diabetes.health.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public class UpdateHealthRecordRequest {

    @DecimalMin("100") @DecimalMax("250")
    private Float height;

    @DecimalMin("30") @DecimalMax("200")
    private Float weight;

    @DecimalMin("3") @DecimalMax("30")
    private Float fastingGlucose;

    private Float postprandialGlucose;
    private Float hba1c;

    @DecimalMin("60") @DecimalMax("250")
    private Integer systolicBp;

    @DecimalMin("40") @DecimalMax("150")
    private Integer diastolicBp;

    private Integer diabetesType;
    private Integer exerciseFreq;
    private String dietType;
    private Integer smoking;
    private Boolean familyHistory;
    private String medicalHistory;
    private String medication;

    public Float getHeight() { return height; }
    public void setHeight(Float height) { this.height = height; }
    public Float getWeight() { return weight; }
    public void setWeight(Float weight) { this.weight = weight; }
    public Float getFastingGlucose() { return fastingGlucose; }
    public void setFastingGlucose(Float fastingGlucose) { this.fastingGlucose = fastingGlucose; }
    public Float getPostprandialGlucose() { return postprandialGlucose; }
    public void setPostprandialGlucose(Float postprandialGlucose) { this.postprandialGlucose = postprandialGlucose; }
    public Float getHba1c() { return hba1c; }
    public void setHba1c(Float hba1c) { this.hba1c = hba1c; }
    public Integer getSystolicBp() { return systolicBp; }
    public void setSystolicBp(Integer systolicBp) { this.systolicBp = systolicBp; }
    public Integer getDiastolicBp() { return diastolicBp; }
    public void setDiastolicBp(Integer diastolicBp) { this.diastolicBp = diastolicBp; }
    public Integer getDiabetesType() { return diabetesType; }
    public void setDiabetesType(Integer diabetesType) { this.diabetesType = diabetesType; }
    public Integer getExerciseFreq() { return exerciseFreq; }
    public void setExerciseFreq(Integer exerciseFreq) { this.exerciseFreq = exerciseFreq; }
    public String getDietType() { return dietType; }
    public void setDietType(String dietType) { this.dietType = dietType; }
    public Integer getSmoking() { return smoking; }
    public void setSmoking(Integer smoking) { this.smoking = smoking; }
    public Boolean getFamilyHistory() { return familyHistory; }
    public void setFamilyHistory(Boolean familyHistory) { this.familyHistory = familyHistory; }
    public String getMedicalHistory() { return medicalHistory; }
    public void setMedicalHistory(String medicalHistory) { this.medicalHistory = medicalHistory; }
    public String getMedication() { return medication; }
    public void setMedication(String medication) { this.medication = medication; }
}
