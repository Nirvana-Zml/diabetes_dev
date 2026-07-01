package com.diabetes.checkin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class ReminderSnoozeRequest {

    @Min(1)
    @Max(120)
    private int minutes = 15;

    public int getMinutes() { return minutes; }
    public void setMinutes(int minutes) { this.minutes = minutes; }
}
