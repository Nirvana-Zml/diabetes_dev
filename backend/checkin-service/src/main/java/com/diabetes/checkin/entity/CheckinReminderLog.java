package com.diabetes.checkin.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class CheckinReminderLog {

    private String logId;
    private String userId;
    private String ruleId;
    private Integer checkinType;
    private LocalDate remindDate;
    private String channel;
    private Integer status;
    private LocalDateTime snoozeUntil;
    private Integer snoozeCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getLogId() { return logId; }
    public void setLogId(String logId) { this.logId = logId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public Integer getCheckinType() { return checkinType; }
    public void setCheckinType(Integer checkinType) { this.checkinType = checkinType; }
    public LocalDate getRemindDate() { return remindDate; }
    public void setRemindDate(LocalDate remindDate) { this.remindDate = remindDate; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getSnoozeUntil() { return snoozeUntil; }
    public void setSnoozeUntil(LocalDateTime snoozeUntil) { this.snoozeUntil = snoozeUntil; }
    public Integer getSnoozeCount() { return snoozeCount; }
    public void setSnoozeCount(Integer snoozeCount) { this.snoozeCount = snoozeCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
