package com.diabetes.checkin.controller;

import com.diabetes.checkin.dto.ReminderRulesSaveRequest;
import com.diabetes.checkin.dto.ReminderSnoozeRequest;
import com.diabetes.checkin.service.CheckinReminderService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CheckinReminderControllerTest {

    @Test
    void controllerDelegatesToService() {
        CheckinReminderService service = mock(CheckinReminderService.class);
        CheckinReminderController controller = new CheckinReminderController(service);

        when(service.getRules("u1")).thenReturn(List.of(Map.of("ruleId", "r1")));
        when(service.getDefaults()).thenReturn(Map.of("rules", List.of()));
        when(service.getPending("u1")).thenReturn(List.of(Map.of("logId", "l1")));
        when(service.saveRules(eq("u1"), any(ReminderRulesSaveRequest.class)))
                .thenReturn(List.of(Map.of("ruleId", "r2")));

        assertEquals("r1", controller.rules("u1").data().get(0).get("ruleId"));
        assertEquals("l1", controller.pending("u1").data().get(0).get("logId"));
        assertEquals("r2", controller.saveRules("u1", new ReminderRulesSaveRequest()).data().get(0).get("ruleId"));

        controller.ack("u1", "l1");
        controller.click("u1", "l1");
        ReminderSnoozeRequest snooze = new ReminderSnoozeRequest();
        snooze.setMinutes(15);
        controller.snooze("u1", "l1", snooze);

        verify(service).ack("u1", "l1");
        verify(service).markClicked("u1", "l1");
        verify(service).snooze("u1", "l1", 15);
    }
}
