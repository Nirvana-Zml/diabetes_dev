package com.diabetes.health.dto;

import jakarta.validation.constraints.NotBlank;

public class FamilyHistoryItem {

    @NotBlank
    private String relation;
    private Integer memberAge;
    private Boolean isAlive;
    private String diseaseCode;
    @NotBlank
    private String diseaseName;
    private Integer diagnosedAge;
    private Boolean isDiabetes;
    private String note;

    public String getRelation() { return relation; }
    public void setRelation(String relation) { this.relation = relation; }
    public Integer getMemberAge() { return memberAge; }
    public void setMemberAge(Integer memberAge) { this.memberAge = memberAge; }
    public Boolean getIsAlive() { return isAlive; }
    public void setIsAlive(Boolean isAlive) { this.isAlive = isAlive; }
    public String getDiseaseCode() { return diseaseCode; }
    public void setDiseaseCode(String diseaseCode) { this.diseaseCode = diseaseCode; }
    public String getDiseaseName() { return diseaseName; }
    public void setDiseaseName(String diseaseName) { this.diseaseName = diseaseName; }
    public Integer getDiagnosedAge() { return diagnosedAge; }
    public void setDiagnosedAge(Integer diagnosedAge) { this.diagnosedAge = diagnosedAge; }
    public Boolean getIsDiabetes() { return isDiabetes; }
    public void setIsDiabetes(Boolean isDiabetes) { this.isDiabetes = isDiabetes; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
