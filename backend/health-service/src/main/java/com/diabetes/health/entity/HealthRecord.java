package com.diabetes.health.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class HealthRecord {

    private String recordId;
    private String userId;
    private BigDecimal height;
    private BigDecimal weight;
    private BigDecimal bmi;
    private BigDecimal fastingGlucose;
    private BigDecimal postprandialGlucose;
    private BigDecimal randomGlucose;
    private BigDecimal hba1c;
    private BigDecimal systolicBp;
    private BigDecimal diastolicBp;
    private Integer diabetesType;
    private LocalDate diagnosedDate;
    private Integer isPregnant;
    private Integer hasDiabetesFamily;
    private Integer isInsulinTaken;
    private Integer smoking;
    private Integer alcohol;
    private Integer exerciseFreq;
    private String dietType;
    private Integer testSource;
    private Integer delFlag;
    private LocalDateTime recordedAt;

    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public BigDecimal getHeight() { return height; }
    public void setHeight(BigDecimal height) { this.height = height; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public BigDecimal getBmi() { return bmi; }
    public void setBmi(BigDecimal bmi) { this.bmi = bmi; }
    public BigDecimal getFastingGlucose() { return fastingGlucose; }
    public void setFastingGlucose(BigDecimal fastingGlucose) { this.fastingGlucose = fastingGlucose; }
    public BigDecimal getPostprandialGlucose() { return postprandialGlucose; }
    public void setPostprandialGlucose(BigDecimal postprandialGlucose) { this.postprandialGlucose = postprandialGlucose; }
    public BigDecimal getRandomGlucose() { return randomGlucose; }
    public void setRandomGlucose(BigDecimal randomGlucose) { this.randomGlucose = randomGlucose; }
    public BigDecimal getHba1c() { return hba1c; }
    public void setHba1c(BigDecimal hba1c) { this.hba1c = hba1c; }
    public BigDecimal getSystolicBp() { return systolicBp; }
    public void setSystolicBp(BigDecimal systolicBp) { this.systolicBp = systolicBp; }
    public BigDecimal getDiastolicBp() { return diastolicBp; }
    public void setDiastolicBp(BigDecimal diastolicBp) { this.diastolicBp = diastolicBp; }
    public Integer getDiabetesType() { return diabetesType; }
    public void setDiabetesType(Integer diabetesType) { this.diabetesType = diabetesType; }
    public LocalDate getDiagnosedDate() { return diagnosedDate; }
    public void setDiagnosedDate(LocalDate diagnosedDate) { this.diagnosedDate = diagnosedDate; }
    public Integer getIsPregnant() { return isPregnant; }
    public void setIsPregnant(Integer isPregnant) { this.isPregnant = isPregnant; }
    public Integer getHasDiabetesFamily() { return hasDiabetesFamily; }
    public void setHasDiabetesFamily(Integer hasDiabetesFamily) { this.hasDiabetesFamily = hasDiabetesFamily; }
    public Integer getIsInsulinTaken() { return isInsulinTaken; }
    public void setIsInsulinTaken(Integer isInsulinTaken) { this.isInsulinTaken = isInsulinTaken; }
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
    public Integer getDelFlag() { return delFlag; }
    public void setDelFlag(Integer delFlag) { this.delFlag = delFlag; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
}
