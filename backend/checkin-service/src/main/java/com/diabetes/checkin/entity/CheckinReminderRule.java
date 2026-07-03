package com.diabetes.checkin.entity;

import java.time.LocalDateTime;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class CheckinReminderRule {

    private String ruleId;
    private String userId;
    private Integer checkinType;
    private LocalTime remindTime;
    private Boolean enabled;
    private String ruleSource;
    private String interventionId;
    private LocalDateTime expiresAt;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public Integer getCheckinType() { return checkinType; }
    public void setCheckinType(Integer checkinType) { this.checkinType = checkinType; }
    public LocalTime getRemindTime() { return remindTime; }
    public void setRemindTime(LocalTime remindTime) { this.remindTime = remindTime; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getRuleSource() { return ruleSource; }
    public void setRuleSource(String ruleSource) { this.ruleSource = ruleSource; }
    public String getInterventionId() { return interventionId; }
    public void setInterventionId(String interventionId) { this.interventionId = interventionId; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
