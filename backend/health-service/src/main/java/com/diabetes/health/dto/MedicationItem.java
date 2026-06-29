package com.diabetes.health.dto;

import jakarta.validation.constraints.NotBlank;

public class MedicationItem {

    @NotBlank
    private String drugName;
    private String genericName;
    private String dosage;
    private String frequency;
    private String frequencyDesc;
    private String route;
    private String purpose;
    private Boolean isInsulin;
    private String startDate;
    private String endDate;
    private Integer status;

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
    public Boolean getIsInsulin() { return isInsulin; }
    public void setIsInsulin(Boolean isInsulin) { this.isInsulin = isInsulin; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
