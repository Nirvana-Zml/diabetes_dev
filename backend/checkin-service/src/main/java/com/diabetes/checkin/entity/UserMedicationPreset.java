package com.diabetes.checkin.entity;

public class UserMedicationPreset {

    private String drugId;
    private String userId;
    private String drugName;
    private String defaultDosage;
    private String imageObjectKey;

    public String getDrugId() { return drugId; }
    public void setDrugId(String drugId) { this.drugId = drugId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getDrugName() { return drugName; }
    public void setDrugName(String drugName) { this.drugName = drugName; }
    public String getDefaultDosage() { return defaultDosage; }
    public void setDefaultDosage(String defaultDosage) { this.defaultDosage = defaultDosage; }
    public String getImageObjectKey() { return imageObjectKey; }
    public void setImageObjectKey(String imageObjectKey) { this.imageObjectKey = imageObjectKey; }
}
