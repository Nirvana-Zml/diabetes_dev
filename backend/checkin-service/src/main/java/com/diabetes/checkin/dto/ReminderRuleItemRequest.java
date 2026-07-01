package com.diabetes.checkin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ReminderRuleItemRequest {

    @NotNull
    @Min(1)
    @Max(4)
    private Integer checkinType;

    @NotBlank
    private String remindTime;

    private Boolean enabled = true;

    private Integer sortOrder = 0;

    public Integer getCheckinType() { return checkinType; }
    public void setCheckinType(Integer checkinType) { this.checkinType = checkinType; }
    public String getRemindTime() { return remindTime; }
    public void setRemindTime(String remindTime) { this.remindTime = remindTime; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
