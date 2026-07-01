package com.diabetes.checkin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class ReminderRulesSaveRequest {

    @Valid
    @NotNull
    private List<ReminderRuleItemRequest> rules;

    public List<ReminderRuleItemRequest> getRules() { return rules; }
    public void setRules(List<ReminderRuleItemRequest> rules) { this.rules = rules; }
}
