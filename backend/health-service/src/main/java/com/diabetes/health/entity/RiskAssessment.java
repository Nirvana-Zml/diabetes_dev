package com.diabetes.health.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class RiskAssessment {

    private String assessmentId;
    private String userId;
    private String healthRecordId;
    private String difyWorkflowId;
    private Integer riskLevel;
    private Integer riskScore;
    private BigDecimal bmiSnapshot;
    private Integer glucoseLevel;
    private Integer confidence;
    private Integer generationStatus;
    private String reportSummary;
    private String aiRawResponse;
    private Integer delFlag;
    private LocalDateTime assessedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getAssessmentId() { return assessmentId; }
    public void setAssessmentId(String assessmentId) { this.assessmentId = assessmentId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getHealthRecordId() { return healthRecordId; }
    public void setHealthRecordId(String healthRecordId) { this.healthRecordId = healthRecordId; }
    public String getDifyWorkflowId() { return difyWorkflowId; }
    public void setDifyWorkflowId(String difyWorkflowId) { this.difyWorkflowId = difyWorkflowId; }
    public Integer getRiskLevel() { return riskLevel; }
    public void setRiskLevel(Integer riskLevel) { this.riskLevel = riskLevel; }
    public Integer getRiskScore() { return riskScore; }
    public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }
    public BigDecimal getBmiSnapshot() { return bmiSnapshot; }
    public void setBmiSnapshot(BigDecimal bmiSnapshot) { this.bmiSnapshot = bmiSnapshot; }
    public Integer getGlucoseLevel() { return glucoseLevel; }
    public void setGlucoseLevel(Integer glucoseLevel) { this.glucoseLevel = glucoseLevel; }
    public Integer getConfidence() { return confidence; }
    public void setConfidence(Integer confidence) { this.confidence = confidence; }
    public Integer getGenerationStatus() { return generationStatus; }
    public void setGenerationStatus(Integer generationStatus) { this.generationStatus = generationStatus; }
    public String getReportSummary() { return reportSummary; }
    public void setReportSummary(String reportSummary) { this.reportSummary = reportSummary; }
    public String getAiRawResponse() { return aiRawResponse; }
    public void setAiRawResponse(String aiRawResponse) { this.aiRawResponse = aiRawResponse; }
    public Integer getDelFlag() { return delFlag; }
    public void setDelFlag(Integer delFlag) { this.delFlag = delFlag; }
    public LocalDateTime getAssessedAt() { return assessedAt; }
    public void setAssessedAt(LocalDateTime assessedAt) { this.assessedAt = assessedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
