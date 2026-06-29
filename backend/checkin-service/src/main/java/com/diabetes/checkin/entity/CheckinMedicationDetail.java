package com.diabetes.checkin.entity;

import java.time.LocalDateTime;

public class CheckinMedicationDetail {

    private String checkinId;
    private Integer sourceType;
    private String drugId;
    private String drugName;
    private String dosage;
    private Integer taken;
    private String imageObjectKey;
    private LocalDateTime recordTime;

    public String getCheckinId() { return checkinId; }
    public void setCheckinId(String checkinId) { this.checkinId = checkinId; }
    public Integer getSourceType() { return sourceType; }
    public void setSourceType(Integer sourceType) { this.sourceType = sourceType; }
    public String getDrugId() { return drugId; }
    public void setDrugId(String drugId) { this.drugId = drugId; }
    public String getDrugName() { return drugName; }
    public void setDrugName(String drugName) { this.drugName = drugName; }
    public String getDosage() { return dosage; }
    public void setDosage(String dosage) { this.dosage = dosage; }
    public Integer getTaken() { return taken; }
    public void setTaken(Integer taken) { this.taken = taken; }
    public String getImageObjectKey() { return imageObjectKey; }
    public void setImageObjectKey(String imageObjectKey) { this.imageObjectKey = imageObjectKey; }
    public LocalDateTime getRecordTime() { return recordTime; }
    public void setRecordTime(LocalDateTime recordTime) { this.recordTime = recordTime; }
}
