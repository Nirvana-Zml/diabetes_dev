package com.diabetes.checkin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class MedicationCheckinRequest {

    @NotBlank
    private String checkinDate;

    @NotNull
    @Min(1)
    @Max(2)
    private Integer sourceType;

    private String drugId;

    private String drugName;

    @NotBlank
    private String dosage;

    @NotNull
    private Boolean taken;

    private String imageObjectKey;

    public String getCheckinDate() { return checkinDate; }
    public void setCheckinDate(String checkinDate) { this.checkinDate = checkinDate; }
    public Integer getSourceType() { return sourceType; }
    public void setSourceType(Integer sourceType) { this.sourceType = sourceType; }
    public String getDrugId() { return drugId; }
    public void setDrugId(String drugId) { this.drugId = drugId; }
    public String getDrugName() { return drugName; }
    public void setDrugName(String drugName) { this.drugName = drugName; }
    public String getDosage() { return dosage; }
    public void setDosage(String dosage) { this.dosage = dosage; }
    public Boolean getTaken() { return taken; }
    public void setTaken(Boolean taken) { this.taken = taken; }
    public String getImageObjectKey() { return imageObjectKey; }
    public void setImageObjectKey(String imageObjectKey) { this.imageObjectKey = imageObjectKey; }
}
