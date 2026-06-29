package com.diabetes.health.entity;

import java.math.BigDecimal;

public class RiskAssessmentFactor {

    private String factorId;
    private String assessmentId;
    private String factorCode;
    private String factorName;
    private BigDecimal weight;
    private Integer factorLevel;
    private String description;
    private Integer sortOrder;

    public String getFactorId() { return factorId; }
    public void setFactorId(String factorId) { this.factorId = factorId; }
    public String getAssessmentId() { return assessmentId; }
    public void setAssessmentId(String assessmentId) { this.assessmentId = assessmentId; }
    public String getFactorCode() { return factorCode; }
    public void setFactorCode(String factorCode) { this.factorCode = factorCode; }
    public String getFactorName() { return factorName; }
    public void setFactorName(String factorName) { this.factorName = factorName; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public Integer getFactorLevel() { return factorLevel; }
    public void setFactorLevel(Integer factorLevel) { this.factorLevel = factorLevel; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
