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
import com.diabetes.common.util.IdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CheckinReminderService {

    static final int STATUS_SENT = 0;
    static final int STATUS_CLICKED = 1;
    static final int STATUS_IGNORED = 2;
    static final int STATUS_SNOOZE = 3;
    static final int MAX_SNOOZE_PER_DAY = 2;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final CheckinReminderRuleMapper ruleMapper;
    private final CheckinReminderLogMapper logMapper;
    private final CheckinRecordMapper recordMapper;
    private final UserServiceClient userServiceClient;
    private final String difyInternalKey;

    public CheckinReminderService(CheckinReminderRuleMapper ruleMapper,
                                  CheckinReminderLogMapper logMapper,
                                  CheckinRecordMapper recordMapper,
                                  UserServiceClient userServiceClient,
                                  @Value("${dify-internal.key:}") String difyInternalKey) {
        this.ruleMapper = ruleMapper;
        this.logMapper = logMapper;
        this.recordMapper = recordMapper;
        this.userServiceClient = userServiceClient;
        this.difyInternalKey = difyInternalKey;
    }

    public List<Map<String, Object>> getRules(String userId) {
        return ruleMapper.findByUserId(userId).stream().map(this::toRuleMap).toList();
    }

    @Transactional
    public List<Map<String, Object>> saveRules(String userId, ReminderRulesSaveRequest request) {
        ruleMapper.deleteByUserIdAndSource(userId, "user");
        int order = 0;
        for (ReminderRuleItemRequest item : request.getRules()) {
            CheckinReminderRule rule = new CheckinReminderRule();
            rule.setRuleId(IdGenerator.nextId("rul_"));
            rule.setUserId(userId);
            rule.setCheckinType(item.getCheckinType());
            rule.setRemindTime(parseRemindTime(item.getRemindTime()));
            rule.setEnabled(item.getEnabled() == null || item.getEnabled());
            rule.setRuleSource("user");
            rule.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : order++);
            ruleMapper.insert(rule);
        }
        return getRules(userId);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public void applySystemAdjust(String userId, String interventionId,
                                    List<Map<String, Object>> adjustments, LocalDateTime expiresAt) {
        ruleMapper.deleteExpiredSystemRules(userId, LocalDateTime.now());
        if (adjustments == null || adjustments.isEmpty()) {
            return;
        }
        int order = 100;
        for (Map<String, Object> adjustment : adjustments) {
            int checkinType = ((Number) adjustment.getOrDefault("checkin_type", adjustment.get("checkinType"))).intValue();
            String action = String.valueOf(adjustment.getOrDefault("action", "add_times"));
            int durationDays = adjustment.containsKey("duration_days")
                    ? ((Number) adjustment.get("duration_days")).intValue()
                    : 7;
            LocalDateTime ruleExpires = expiresAt != null ? expiresAt : LocalDateTime.now().plusDays(durationDays);

            if ("apply_defaults".equals(action)) {
                Map<String, Object> defaults = getDefaults();
                Object rulesObj = defaults.get("rules");
                if (rulesObj instanceof List<?> defaultRules) {
                    for (Object item : defaultRules) {
                        if (item instanceof Map<?, ?> map) {
                            int type = ((Number) map.get("checkinType")).intValue();
                            insertSystemRuleIfAbsent(userId, interventionId, type,
                                    parseRemindTime(String.valueOf(map.get("remindTime"))),
                                    ruleExpires, order++);
                        }
                    }
                }
                continue;
            }

            Object timesObj = adjustment.get("times");
            if (!(timesObj instanceof List<?> times)) {
                continue;
            }
            for (Object timeObj : times) {
                LocalTime remindTime = parseRemindTime(String.valueOf(timeObj));
                insertSystemRuleIfAbsent(userId, interventionId, checkinType, remindTime, ruleExpires, order++);
            }
        }
    }

    private void insertSystemRuleIfAbsent(String userId, String interventionId, int checkinType,
                                          LocalTime remindTime, LocalDateTime expiresAt, int sortOrder) {
        if (ruleMapper.findUserRule(userId, checkinType, remindTime) != null) {
            return;
        }
        CheckinReminderRule rule = new CheckinReminderRule();
        rule.setRuleId(IdGenerator.nextId("rul_"));
        rule.setUserId(userId);
        rule.setCheckinType(checkinType);
        rule.setRemindTime(remindTime);
        rule.setEnabled(true);
        rule.setRuleSource("system");
        rule.setInterventionId(interventionId);
        rule.setExpiresAt(expiresAt);
        rule.setSortOrder(sortOrder);
        ruleMapper.insert(rule);
    }

    public Map<String, Object> getDefaults() {
        List<Map<String, Object>> rules = new ArrayList<>();
        addDefault(rules, CheckinConstants.TYPE_DIET, "08:00", 0);
        addDefault(rules, CheckinConstants.TYPE_DIET, "12:00", 1);
        addDefault(rules, CheckinConstants.TYPE_DIET, "18:00", 2);
        addDefault(rules, CheckinConstants.TYPE_MEDICATION, "08:00", 0);
        addDefault(rules, CheckinConstants.TYPE_MEDICATION, "20:00", 1);
        addDefault(rules, CheckinConstants.TYPE_EXERCISE, "17:30", 0);
        addDefault(rules, CheckinConstants.TYPE_GLUCOSE, "07:00", 0);
        addDefault(rules, CheckinConstants.TYPE_GLUCOSE, "10:00", 1);
        return Map.of("rules", rules);
    }

    public List<Map<String, Object>> getPending(String userId) {
        if (!isCheckinNotifyEnabled(userId)) {
            return List.of();
        }
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        List<Map<String, Object>> pending = new ArrayList<>();

        for (CheckinReminderRule rule : ruleMapper.findByUserId(userId)) {
            if (rule.getEnabled() == null || !rule.getEnabled()) {
                continue;
            }
            if (now.isBefore(rule.getRemindTime())) {
                continue;
            }
            if (recordMapper.countByUserTypeDate(userId, rule.getCheckinType(), today) > 0) {
                continue;
            }

            CheckinReminderLog log = logMapper.findByRuleAndDate(rule.getRuleId(), today);
            if (log != null) {
                if (log.getStatus() == STATUS_SNOOZE) {
                    if (log.getSnoozeUntil() != null && log.getSnoozeUntil().isAfter(LocalDateTime.now())) {
                        continue;
                    }
                    if (log.getSnoozeCount() != null && log.getSnoozeCount() >= MAX_SNOOZE_PER_DAY) {
                        pending.add(buildPendingItem(rule, log, true));
                        continue;
                    }
                } else {
                    continue;
                }
            }

            if (log == null) {
                log = createLog(userId, rule, today);
            }

            pending.add(buildPendingItem(rule, log, false));
        }
        return pending;
    }

    public void ack(String userId, String logId) {
        CheckinReminderLog log = requireLog(userId, logId);
        logMapper.updateStatus(log.getLogId(), userId, STATUS_IGNORED);
    }

    public void snooze(String userId, String logId, int minutes) {
        CheckinReminderLog log = requireLog(userId, logId);
        int count = log.getSnoozeCount() == null ? 0 : log.getSnoozeCount();
        if (count >= MAX_SNOOZE_PER_DAY) {
            throw new BusinessException(400, "今日稍后提醒次数已达上限");
        }
        LocalDateTime until = LocalDateTime.now().plusMinutes(minutes);
        logMapper.updateSnooze(log.getLogId(), userId, STATUS_SNOOZE, until, count + 1);
    }

    public void markClicked(String userId, String logId) {
        CheckinReminderLog log = requireLog(userId, logId);
        logMapper.updateStatus(log.getLogId(), userId, STATUS_CLICKED);
    }

    private CheckinReminderLog createLog(String userId, CheckinReminderRule rule, LocalDate today) {
        CheckinReminderLog log = new CheckinReminderLog();
        log.setLogId(IdGenerator.nextId("rlog_"));
        log.setUserId(userId);
        log.setRuleId(rule.getRuleId());
        log.setCheckinType(rule.getCheckinType());
        log.setRemindDate(today);
        log.setChannel("in_app");
        log.setStatus(STATUS_SENT);
        log.setSnoozeCount(0);
        logMapper.insert(log);
        return log;
    }

    private Map<String, Object> buildPendingItem(CheckinReminderRule rule,
                                                   CheckinReminderLog log,
                                                   boolean bannerOnly) {
        int type = rule.getCheckinType();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("logId", log.getLogId());
        item.put("ruleId", rule.getRuleId());
        item.put("checkinType", type);
        item.put("checkinTypeLabel", typeLabel(type));
        item.put("tab", tabForType(type));
        item.put("title", typeLabel(type) + "打卡提醒");
        item.put("body", "尚未记录今日" + typeLabel(type).replace("打卡", "") + "，点击去完成打卡");
        item.put("remindTime", rule.getRemindTime().format(TIME_FMT));
        item.put("bannerOnly", bannerOnly);
        item.put("snoozeCount", log.getSnoozeCount() == null ? 0 : log.getSnoozeCount());
        return item;
    }

    private CheckinReminderLog requireLog(String userId, String logId) {
        CheckinReminderLog log = logMapper.findByIdAndUser(logId, userId);
        if (log == null) {
            throw new BusinessException(404, "提醒记录不存在");
        }
        return log;
    }

    private boolean isCheckinNotifyEnabled(String userId) {
        Map<String, Object> profile = userServiceClient.getUserProfile(userId, difyInternalKey);
        Object privacy = profile.get("privacy_settings");
        if (!(privacy instanceof Map<?, ?> settings)) {
            return true;
        }
        Object notify = settings.get("checkin_notify");
        if (notify == null) {
            return true;
        }
        if (notify instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(notify));
    }

    private Map<String, Object> toRuleMap(CheckinReminderRule rule) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ruleId", rule.getRuleId());
        map.put("checkinType", rule.getCheckinType());
        map.put("checkinTypeLabel", typeLabel(rule.getCheckinType()));
        map.put("remindTime", rule.getRemindTime().format(TIME_FMT));
        map.put("enabled", rule.getEnabled());
        map.put("sortOrder", rule.getSortOrder());
        map.put("ruleSource", rule.getRuleSource() == null ? "user" : rule.getRuleSource());
        map.put("interventionId", rule.getInterventionId());
        map.put("expiresAt", rule.getExpiresAt());
        if ("system".equals(rule.getRuleSource())) {
            map.put("systemSuggested", true);
        }
        return map;
    }

    private void addDefault(List<Map<String, Object>> rules, int type, String time, int sortOrder) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("checkinType", type);
        item.put("checkinTypeLabel", typeLabel(type));
        item.put("remindTime", time);
        item.put("enabled", true);
        item.put("sortOrder", sortOrder);
        rules.add(item);
    }

    static String typeLabel(int type) {
        return switch (type) {
            case CheckinConstants.TYPE_DIET -> "饮食打卡";
            case CheckinConstants.TYPE_EXERCISE -> "运动打卡";
            case CheckinConstants.TYPE_MEDICATION -> "用药打卡";
            case CheckinConstants.TYPE_GLUCOSE -> "血糖打卡";
            default -> "打卡";
        };
    }

    static String tabForType(int type) {
        return switch (type) {
            case CheckinConstants.TYPE_DIET -> "food";
            case CheckinConstants.TYPE_EXERCISE -> "exercise";
            case CheckinConstants.TYPE_MEDICATION -> "medication";
            case CheckinConstants.TYPE_GLUCOSE -> "glucose";
            default -> "food";
        };
    }

    private LocalTime parseRemindTime(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(400, "提醒时间不能为空");
        }
        String trimmed = value.trim();
        try {
            if (trimmed.length() <= 5) {
                return LocalTime.parse(trimmed, TIME_FMT);
            }
            return LocalTime.parse(trimmed);
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, "提醒时间格式错误，应为 HH:mm");
        }
    }
}
