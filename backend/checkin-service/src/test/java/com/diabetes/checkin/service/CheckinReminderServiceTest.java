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
        when(userServiceClient.getUserProfile(anyString(), eq("test-key")))
                .thenReturn(Map.of("privacy_settings", Map.of("checkin_notify", true)));
    }

    @Test
    void getRulesMapsUserAndSystemRules() {
        CheckinReminderRule userRule = rule("r1", CheckinConstants.TYPE_DIET, LocalTime.of(8, 0));
        userRule.setRuleSource("user");
        CheckinReminderRule nullSourceRule = rule("r0", CheckinConstants.TYPE_EXERCISE, LocalTime.of(9, 0));
        CheckinReminderRule systemRule = rule("r2", CheckinConstants.TYPE_GLUCOSE, LocalTime.of(7, 0));
        systemRule.setRuleSource("system");
        systemRule.setInterventionId("int_1");
        systemRule.setExpiresAt(LocalDateTime.of(2024, 12, 31, 0, 0));
        when(ruleMapper.findByUserId("u1")).thenReturn(List.of(nullSourceRule, userRule, systemRule));

        List<Map<String, Object>> rules = service.getRules("u1");

        assertEquals("user", rules.get(0).get("ruleSource"));
        assertEquals("r1", rules.get(1).get("ruleId"));
        assertEquals("user", rules.get(1).get("ruleSource"));
        assertNull(rules.get(1).get("systemSuggested"));
        assertEquals("system", rules.get(2).get("ruleSource"));
        assertTrue((Boolean) rules.get(2).get("systemSuggested"));
        assertEquals("int_1", rules.get(2).get("interventionId"));
    }

    @Test
    void saveRulesReplacesExistingRulesAndParsesOptions() {
        ReminderRulesSaveRequest request = new ReminderRulesSaveRequest();
        ReminderRuleItemRequest enabled = item(CheckinConstants.TYPE_GLUCOSE, "07:00", true, null);
        ReminderRuleItemRequest disabled = item(CheckinConstants.TYPE_DIET, "08:30:00", false, 5);
        ReminderRuleItemRequest defaultEnabled = item(CheckinConstants.TYPE_MEDICATION, "20:00", null, null);
        request.setRules(List.of(enabled, disabled, defaultEnabled));

        when(ruleMapper.findByUserId("u1")).thenReturn(List.of());

        service.saveRules("u1", request);

        verify(ruleMapper).deleteByUserIdAndSource("u1", "user");
        verify(ruleMapper, times(3)).insert(any(CheckinReminderRule.class));
    }

    @Test
    void applySystemAdjustIgnoresMalformedDefaultPayload() {
        CheckinReminderService spy = spy(service);
        doReturn(Map.of("rules", "bad")).when(spy).getDefaults();
        spy.applySystemAdjust("u1", "int_1", List.of(Map.of("action", "apply_defaults", "checkinType", 1)), null);

        doReturn(Map.of("rules", List.of("bad-item"))).when(spy).getDefaults();
        spy.applySystemAdjust("u1", "int_1", List.of(Map.of("action", "apply_defaults", "checkinType", 1)), null);

        verify(ruleMapper, never()).insert(any());
    }

    @Test
    void saveRulesRejectsInvalidRemindTime() {
        ReminderRulesSaveRequest request = new ReminderRulesSaveRequest();
        ReminderRuleItemRequest blank = item(CheckinConstants.TYPE_DIET, " ", true, 0);
        request.setRules(List.of(blank));

        assertThrows(BusinessException.class, () -> service.saveRules("u1", request));

        ReminderRuleItemRequest nullTime = item(CheckinConstants.TYPE_DIET, null, true, 0);
        request.setRules(List.of(nullTime));
        assertThrows(BusinessException.class, () -> service.saveRules("u1", request));

        ReminderRuleItemRequest bad = item(CheckinConstants.TYPE_DIET, "bad", true, 0);
        request.setRules(List.of(bad));
        assertThrows(BusinessException.class, () -> service.saveRules("u1", request));
    }

    @Test
    void applySystemAdjustHandlesDefaultsAddTimesAndSkipsExisting() {
        when(ruleMapper.findUserRule(eq("u1"), anyInt(), any(LocalTime.class))).thenReturn(null);

        List<Map<String, Object>> adjustments = List.of(
                Map.of("action", "apply_defaults", "checkinType", 1),
                Map.of("checkin_type", 2, "times", List.of("17:30", "18:00"), "duration_days", 3),
                Map.of("checkinType", 3, "times", "not-a-list")
        );

        service.applySystemAdjust("u1", "int_1", adjustments, LocalDateTime.of(2024, 6, 1, 0, 0));

        verify(ruleMapper).deleteExpiredSystemRules(eq("u1"), any(LocalDateTime.class));
        verify(ruleMapper, atLeast(8)).insert(any(CheckinReminderRule.class));
    }

    @Test
    void applySystemAdjustNoOpsForNullOrEmptyAdjustments() {
        service.applySystemAdjust("u1", "int_1", null, null);
        service.applySystemAdjust("u1", "int_1", List.of(), null);

        verify(ruleMapper, times(2)).deleteExpiredSystemRules(eq("u1"), any(LocalDateTime.class));
        verify(ruleMapper, never()).insert(any());
    }

    @Test
    void applySystemAdjustSkipsWhenUserRuleAlreadyExists() {
        when(ruleMapper.findUserRule("u1", CheckinConstants.TYPE_EXERCISE, LocalTime.of(17, 30)))
                .thenReturn(rule("existing", CheckinConstants.TYPE_EXERCISE, LocalTime.of(17, 30)));

        service.applySystemAdjust("u1", "int_1",
                List.of(Map.of("checkinType", CheckinConstants.TYPE_EXERCISE, "times", List.of("17:30"))),
                null);

        verify(ruleMapper, never()).insert(any());
    }

    @Test
    void getPendingReturnsEmptyWhenNotifyDisabled() {
        when(userServiceClient.getUserProfile("u1", "test-key"))
                .thenReturn(Map.of("privacy_settings", Map.of("checkin_notify", false)));

        assertTrue(service.getPending("u1").isEmpty());
        verifyNoInteractions(ruleMapper);
    }

    @Test
    void getPendingRespectsNotifyFallbacks() {
        when(userServiceClient.getUserProfile("u1", "test-key")).thenReturn(Map.of());
        when(ruleMapper.findByUserId("u1")).thenReturn(List.of());
        assertTrue(service.getPending("u1").isEmpty());

        when(userServiceClient.getUserProfile("u1", "test-key"))
                .thenReturn(Map.of("privacy_settings", Map.of()));
        assertTrue(service.getPending("u1").isEmpty());

        when(userServiceClient.getUserProfile("u1", "test-key"))
                .thenReturn(Map.of("privacy_settings", Map.of("checkin_notify", "false")));
        assertTrue(service.getPending("u1").isEmpty());
    }

    @Test
    void getPendingCreatesLogWhenRuleDueAndNotCheckedIn() {
        CheckinReminderRule rule = rule("r1", CheckinConstants.TYPE_DIET, LocalTime.now().minusMinutes(5));
        when(ruleMapper.findByUserId("u1")).thenReturn(List.of(rule));
        when(recordMapper.countByUserTypeDate("u1", CheckinConstants.TYPE_DIET, LocalDate.now())).thenReturn(0);
        when(logMapper.findByRuleAndDate("r1", LocalDate.now())).thenReturn(null);
        when(logMapper.insert(any(CheckinReminderLog.class))).thenReturn(1);

        List<Map<String, Object>> pending = service.getPending("u1");

        assertEquals(1, pending.size());
        assertEquals("food", pending.get(0).get("tab"));
        assertFalse((Boolean) pending.get(0).get("bannerOnly"));
        verify(logMapper).insert(any(CheckinReminderLog.class));
    }

    @Test
    void getPendingSkipsDisabledFutureOrAlreadyCheckedInRules() {
        CheckinReminderRule disabled = rule("r1", CheckinConstants.TYPE_DIET, LocalTime.now().minusMinutes(5));
        disabled.setEnabled(false);
        CheckinReminderRule future = rule("r2", CheckinConstants.TYPE_EXERCISE, LocalTime.now().plusHours(2));
        CheckinReminderRule checkedIn = rule("r3", CheckinConstants.TYPE_MEDICATION, LocalTime.now().minusMinutes(5));
        CheckinReminderRule pendingRule = rule("r4", CheckinConstants.TYPE_GLUCOSE, LocalTime.now().minusMinutes(5));
        when(ruleMapper.findByUserId("u1")).thenReturn(List.of(disabled, future, checkedIn, pendingRule));
        when(recordMapper.countByUserTypeDate("u1", CheckinConstants.TYPE_MEDICATION, LocalDate.now())).thenReturn(1);
        when(recordMapper.countByUserTypeDate(eq("u1"), eq(CheckinConstants.TYPE_GLUCOSE), any())).thenReturn(0);
        when(logMapper.findByRuleAndDate("r4", LocalDate.now())).thenReturn(null);
        when(logMapper.insert(any(CheckinReminderLog.class))).thenReturn(1);

        List<Map<String, Object>> pending = service.getPending("u1");

        assertEquals(1, pending.size());
        assertEquals("glucose", pending.get(0).get("tab"));
    }

    @Test
    void getPendingSkipsRulesWithNullEnabled() {
        CheckinReminderRule enabledNull = rule("r4", CheckinConstants.TYPE_GLUCOSE, LocalTime.now().minusMinutes(5));
        enabledNull.setEnabled(null);
        when(ruleMapper.findByUserId("u1")).thenReturn(List.of(enabledNull));

        assertTrue(service.getPending("u1").isEmpty());
    }

    @Test
    void getPendingHandlesSnoozeClickedAndBannerOnlyStates() {
        CheckinReminderRule rule = rule("r1", CheckinConstants.TYPE_MEDICATION, LocalTime.now().minusMinutes(5));
        when(ruleMapper.findByUserId("u1")).thenReturn(List.of(rule));
        when(recordMapper.countByUserTypeDate("u1", CheckinConstants.TYPE_MEDICATION, LocalDate.now())).thenReturn(0);

        CheckinReminderLog activeSnooze = log("l1", CheckinReminderService.STATUS_SNOOZE);
        activeSnooze.setSnoozeUntil(LocalDateTime.now().plusMinutes(10));
        when(logMapper.findByRuleAndDate("r1", LocalDate.now())).thenReturn(activeSnooze);
        assertTrue(service.getPending("u1").isEmpty());

        CheckinReminderLog maxSnooze = log("l2", CheckinReminderService.STATUS_SNOOZE);
        maxSnooze.setSnoozeCount(2);
        maxSnooze.setSnoozeUntil(LocalDateTime.now().minusMinutes(1));
        when(logMapper.findByRuleAndDate("r1", LocalDate.now())).thenReturn(maxSnooze);
        assertTrue((Boolean) service.getPending("u1").get(0).get("bannerOnly"));

        CheckinReminderLog clicked = log("l3", CheckinReminderService.STATUS_CLICKED);
        when(logMapper.findByRuleAndDate("r1", LocalDate.now())).thenReturn(clicked);
        assertTrue(service.getPending("u1").isEmpty());

        CheckinReminderLog expiredSnooze = log("l4", CheckinReminderService.STATUS_SNOOZE);
        expiredSnooze.setSnoozeCount(1);
        expiredSnooze.setSnoozeUntil(LocalDateTime.now().minusMinutes(1));
        when(logMapper.findByRuleAndDate("r1", LocalDate.now())).thenReturn(expiredSnooze);
        assertFalse((Boolean) service.getPending("u1").get(0).get("bannerOnly"));

        CheckinReminderLog sent = log("l5", CheckinReminderService.STATUS_SENT);
        when(logMapper.findByRuleAndDate("r1", LocalDate.now())).thenReturn(sent);
        assertTrue(service.getPending("u1").isEmpty());

        CheckinReminderLog snoozeNoUntil = log("l6", CheckinReminderService.STATUS_SNOOZE);
        snoozeNoUntil.setSnoozeCount(1);
        when(logMapper.findByRuleAndDate("r1", LocalDate.now())).thenReturn(snoozeNoUntil);
        assertFalse((Boolean) service.getPending("u1").get(0).get("bannerOnly"));

        CheckinReminderLog snoozeNullCount = log("l7", CheckinReminderService.STATUS_SNOOZE);
        snoozeNullCount.setSnoozeUntil(LocalDateTime.now().minusMinutes(1));
        when(logMapper.findByRuleAndDate("r1", LocalDate.now())).thenReturn(snoozeNullCount);
        assertEquals(0, service.getPending("u1").get(0).get("snoozeCount"));
    }

    @Test
    void getPendingCoversAllTypeLabelsAndDefaultTab() {
        List<CheckinReminderRule> rules = List.of(
                rule("r1", CheckinConstants.TYPE_EXERCISE, LocalTime.now().minusMinutes(1)),
                rule("r2", CheckinConstants.TYPE_MEDICATION, LocalTime.now().minusMinutes(1)),
                rule("r3", CheckinConstants.TYPE_GLUCOSE, LocalTime.now().minusMinutes(1)),
                rule("r4", 99, LocalTime.now().minusMinutes(1))
        );
        when(ruleMapper.findByUserId("u1")).thenReturn(rules);
        when(recordMapper.countByUserTypeDate(eq("u1"), anyInt(), any())).thenReturn(0);
        when(logMapper.findByRuleAndDate(anyString(), any())).thenReturn(null);
        when(logMapper.insert(any(CheckinReminderLog.class))).thenReturn(1);

        List<Map<String, Object>> pending = service.getPending("u1");

        assertEquals(List.of("exercise", "medication", "glucose", "food"),
                pending.stream().map(item -> item.get("tab")).toList());
        assertEquals("打卡", pending.get(3).get("checkinTypeLabel"));
    }

    @Test
    void ackSnoozeAndClickUpdateLogs() {
        CheckinReminderLog log = log("l1", CheckinReminderService.STATUS_SENT);
        when(logMapper.findByIdAndUser("l1", "u1")).thenReturn(log);

        service.ack("u1", "l1");
        verify(logMapper).updateStatus("l1", "u1", CheckinReminderService.STATUS_IGNORED);

        service.snooze("u1", "l1", 20);
        verify(logMapper).updateSnooze(eq("l1"), eq("u1"), eq(CheckinReminderService.STATUS_SNOOZE),
                any(LocalDateTime.class), eq(1));

        service.markClicked("u1", "l1");
        verify(logMapper).updateStatus("l1", "u1", CheckinReminderService.STATUS_CLICKED);
    }

    @Test
    void snoozeRejectsWhenMaxReached() {
        CheckinReminderLog log = log("l1", CheckinReminderService.STATUS_SENT);
        log.setSnoozeCount(2);
        when(logMapper.findByIdAndUser("l1", "u1")).thenReturn(log);

        assertThrows(BusinessException.class, () -> service.snooze("u1", "l1", 15));
    }

    @Test
    void requireLogThrowsWhenMissing() {
        when(logMapper.findByIdAndUser("missing", "u1")).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.ack("u1", "missing"));
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

    private CheckinReminderLog log(String id, int status) {
        CheckinReminderLog log = new CheckinReminderLog();
        log.setLogId(id);
        log.setUserId("u1");
        log.setRuleId("r1");
        log.setStatus(status);
        return log;
    }

    private ReminderRuleItemRequest item(int type, String time, Boolean enabled, Integer sortOrder) {
        ReminderRuleItemRequest item = new ReminderRuleItemRequest();
        item.setCheckinType(type);
        item.setRemindTime(time);
        item.setEnabled(enabled);
        item.setSortOrder(sortOrder);
        return item;
    }
}
