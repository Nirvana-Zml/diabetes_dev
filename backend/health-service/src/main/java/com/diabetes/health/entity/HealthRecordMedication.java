package com.diabetes.health.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class HealthRecordMedication {

    private String medicationId;
    private String recordId;
    private String userId;
    private String drugName;
    private String genericName;
    private String dosage;
    private String frequency;
    private String frequencyDesc;
    private String route;
    private String purpose;
    private Integer isInsulin;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer status;
    private Integer source;
    private Integer delFlag;
    private LocalDateTime createdAt;

    public String getMedicationId() { return medicationId; }
    public void setMedicationId(String medicationId) { this.medicationId = medicationId; }
    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getDrugName() { return drugName; }
    public void setDrugName(String drugName) { this.drugName = drugName; }
    public String getGenericName() { return genericName; }
    public void setGenericName(String genericName) { this.genericName = genericName; }
    public String getDosage() { return dosage; }
    public void setDosage(String dosage) { this.dosage = dosage; }
    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }
    public String getFrequencyDesc() { return frequencyDesc; }
    public void setFrequencyDesc(String frequencyDesc) { this.frequencyDesc = frequencyDesc; }
    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public Integer getIsInsulin() { return isInsulin; }
    public void setIsInsulin(Integer isInsulin) { this.isInsulin = isInsulin; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getSource() { return source; }
    public void setSource(Integer source) { this.source = source; }
    public Integer getDelFlag() { return delFlag; }
    public void setDelFlag(Integer delFlag) { this.delFlag = delFlag; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
