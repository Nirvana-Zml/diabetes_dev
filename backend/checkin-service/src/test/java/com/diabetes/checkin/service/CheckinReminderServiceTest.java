package com.diabetes.checkin.service;

import com.diabetes.checkin.dto.ReminderRuleItemRequest;
import com.diabetes.checkin.dto.ReminderRulesSaveRequest;
import com.diabetes.checkin.entity.CheckinReminderLog;
import com.diabetes.checkin.entity.CheckinReminderRule;
import com.diabetes.checkin.mapper.CheckinRecordMapper;
import com.diabetes.checkin.mapper.CheckinReminderLogMapper;
import com.diabetes.checkin.mapper.CheckinReminderRuleMapper;
import com.diabetes.common.client.UserServiceClient;
import com.diabetes.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CheckinReminderServiceTest {

    private CheckinReminderRuleMapper ruleMapper;
    private CheckinReminderLogMapper logMapper;
    private CheckinRecordMapper recordMapper;
    private UserServiceClient userServiceClient;
    private CheckinReminderService service;

    @BeforeEach
    void setUp() {
        ruleMapper = mock(CheckinReminderRuleMapper.class);
        logMapper = mock(CheckinReminderLogMapper.class);
        recordMapper = mock(CheckinRecordMapper.class);
        userServiceClient = mock(UserServiceClient.class);
        service = new CheckinReminderService(ruleMapper, logMapper, recordMapper, userServiceClient, "test-key");
    }

    @Test
    void getPendingReturnsEmptyWhenNotifyDisabled() {
        when(userServiceClient.getUserProfile("u1", "test-key"))
                .thenReturn(Map.of("privacy_settings", Map.of("checkin_notify", false)));

        assertTrue(service.getPending("u1").isEmpty());
        verifyNoInteractions(ruleMapper);
    }

    @Test
    void getPendingCreatesLogWhenRuleDueAndNotCheckedIn() {
        when(userServiceClient.getUserProfile("u1", "test-key"))
                .thenReturn(Map.of("privacy_settings", Map.of("checkin_notify", true)));

        CheckinReminderRule rule = rule("r1", CheckinConstants.TYPE_DIET, LocalTime.now().minusMinutes(5));
        when(ruleMapper.findByUserId("u1")).thenReturn(List.of(rule));
        when(recordMapper.countByUserTypeDate("u1", CheckinConstants.TYPE_DIET, LocalDate.now())).thenReturn(0);
        when(logMapper.findByRuleAndDate("r1", LocalDate.now())).thenReturn(null);
        when(logMapper.insert(any(CheckinReminderLog.class))).thenReturn(1);

        List<Map<String, Object>> pending = service.getPending("u1");

        assertEquals(1, pending.size());
        assertEquals("food", pending.get(0).get("tab"));
        verify(logMapper).insert(any(CheckinReminderLog.class));
    }

    @Test
    void saveRulesReplacesExistingRules() {
        ReminderRulesSaveRequest request = new ReminderRulesSaveRequest();
        ReminderRuleItemRequest item = new ReminderRuleItemRequest();
        item.setCheckinType(CheckinConstants.TYPE_GLUCOSE);
        item.setRemindTime("07:00");
        request.setRules(List.of(item));

        when(ruleMapper.findByUserId("u1")).thenReturn(List.of());

        service.saveRules("u1", request);

        verify(ruleMapper).deleteByUserId("u1");
        verify(ruleMapper).insert(any(CheckinReminderRule.class));
    }

    @Test
    void snoozeRejectsWhenMaxReached() {
        CheckinReminderLog log = new CheckinReminderLog();
        log.setLogId("l1");
        log.setSnoozeCount(2);
        when(logMapper.findByIdAndUser("l1", "u1")).thenReturn(log);

        assertThrows(BusinessException.class, () -> service.snooze("u1", "l1", 15));
    }

    @Test
    void getDefaultsContainsRecommendedSlots() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) service.getDefaults().get("rules");
        assertFalse(rules.isEmpty());
        assertTrue(rules.stream().anyMatch(r -> Integer.valueOf(1).equals(r.get("checkinType"))));
    }

    private CheckinReminderRule rule(String id, int type, LocalTime time) {
        CheckinReminderRule rule = new CheckinReminderRule();
        rule.setRuleId(id);
        rule.setUserId("u1");
        rule.setCheckinType(type);
        rule.setRemindTime(time);
        rule.setEnabled(true);
        rule.setSortOrder(0);
        return rule;
    }
}
