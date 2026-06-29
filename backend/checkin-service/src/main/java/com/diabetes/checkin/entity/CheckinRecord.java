package com.diabetes.checkin.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class CheckinRecord {

    private String checkinId;
    private String userId;
    private Integer checkinType;
    private LocalDate checkinDate;
    private LocalDateTime recordTime;
    private Integer pointsEarned;
    private Integer streakDays;
    private Integer delFlag;

    public String getCheckinId() { return checkinId; }
    public void setCheckinId(String checkinId) { this.checkinId = checkinId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public Integer getCheckinType() { return checkinType; }
    public void setCheckinType(Integer checkinType) { this.checkinType = checkinType; }
    public LocalDate getCheckinDate() { return checkinDate; }
    public void setCheckinDate(LocalDate checkinDate) { this.checkinDate = checkinDate; }
    public LocalDateTime getRecordTime() { return recordTime; }
    public void setRecordTime(LocalDateTime recordTime) { this.recordTime = recordTime; }
    public Integer getPointsEarned() { return pointsEarned; }
    public void setPointsEarned(Integer pointsEarned) { this.pointsEarned = pointsEarned; }
    public Integer getStreakDays() { return streakDays; }
    public void setStreakDays(Integer streakDays) { this.streakDays = streakDays; }
    public Integer getDelFlag() { return delFlag; }
    public void setDelFlag(Integer delFlag) { this.delFlag = delFlag; }
}
