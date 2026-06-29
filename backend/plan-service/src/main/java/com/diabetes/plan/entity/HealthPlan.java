package com.diabetes.plan.entity;

import java.time.LocalDateTime;

public class HealthPlan {

    private String planId;
    private String userId;
    private String healthRecordId;
    private String riskAssessmentId;
    private String title;
    private String summary;
    private Integer dailyCalories;
    private String medicationNote;
    private Integer source;
    private Integer version;
    private Integer isActive;
    private Integer isFavorite;
    private String aiRawResponse;
    private Integer delFlag;
    private LocalDateTime generatedAt;

    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getHealthRecordId() { return healthRecordId; }
    public void setHealthRecordId(String healthRecordId) { this.healthRecordId = healthRecordId; }
    public String getRiskAssessmentId() { return riskAssessmentId; }
    public void setRiskAssessmentId(String riskAssessmentId) { this.riskAssessmentId = riskAssessmentId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public Integer getDailyCalories() { return dailyCalories; }
    public void setDailyCalories(Integer dailyCalories) { this.dailyCalories = dailyCalories; }
    public String getMedicationNote() { return medicationNote; }
    public void setMedicationNote(String medicationNote) { this.medicationNote = medicationNote; }
    public Integer getSource() { return source; }
    public void setSource(Integer source) { this.source = source; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }
    public Integer getIsFavorite() { return isFavorite; }
    public void setIsFavorite(Integer isFavorite) { this.isFavorite = isFavorite; }
    public String getAiRawResponse() { return aiRawResponse; }
    public void setAiRawResponse(String aiRawResponse) { this.aiRawResponse = aiRawResponse; }
    public Integer getDelFlag() { return delFlag; }
    public void setDelFlag(Integer delFlag) { this.delFlag = delFlag; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
}
