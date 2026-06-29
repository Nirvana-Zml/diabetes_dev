package com.diabetes.plan.entity;

public class HealthPlanRestItem {

    private String itemId;
    private String planId;
    private Integer scheduleType;
    private String timePoint;
    private String suggestion;
    private Integer sortOrder;

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
    public Integer getScheduleType() { return scheduleType; }
    public void setScheduleType(Integer scheduleType) { this.scheduleType = scheduleType; }
    public String getTimePoint() { return timePoint; }
    public void setTimePoint(String timePoint) { this.timePoint = timePoint; }
    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
