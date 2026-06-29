package com.diabetes.health.entity;

import java.time.LocalDateTime;

public class HealthRecordFamilyHistory {

    private String familyHistoryId;
    private String recordId;
    private String userId;
    private String relation;
    private Integer memberAge;
    private Integer isAlive;
    private String diseaseCode;
    private String diseaseName;
    private Integer diagnosedAge;
    private Integer isDiabetes;
    private String note;
    private Integer source;
    private Integer delFlag;
    private LocalDateTime createdAt;

    public String getFamilyHistoryId() { return familyHistoryId; }
    public void setFamilyHistoryId(String familyHistoryId) { this.familyHistoryId = familyHistoryId; }
    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getRelation() { return relation; }
    public void setRelation(String relation) { this.relation = relation; }
    public Integer getMemberAge() { return memberAge; }
    public void setMemberAge(Integer memberAge) { this.memberAge = memberAge; }
    public Integer getIsAlive() { return isAlive; }
    public void setIsAlive(Integer isAlive) { this.isAlive = isAlive; }
    public String getDiseaseCode() { return diseaseCode; }
    public void setDiseaseCode(String diseaseCode) { this.diseaseCode = diseaseCode; }
    public String getDiseaseName() { return diseaseName; }
    public void setDiseaseName(String diseaseName) { this.diseaseName = diseaseName; }
    public Integer getDiagnosedAge() { return diagnosedAge; }
    public void setDiagnosedAge(Integer diagnosedAge) { this.diagnosedAge = diagnosedAge; }
    public Integer getIsDiabetes() { return isDiabetes; }
    public void setIsDiabetes(Integer isDiabetes) { this.isDiabetes = isDiabetes; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Integer getSource() { return source; }
    public void setSource(Integer source) { this.source = source; }
    public Integer getDelFlag() { return delFlag; }
    public void setDelFlag(Integer delFlag) { this.delFlag = delFlag; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
