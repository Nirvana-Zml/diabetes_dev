package com.diabetes.checkin.service;

import com.diabetes.checkin.dto.GlucoseCheckinRequest;
import com.diabetes.checkin.entity.CheckinGlucoseDetail;
import com.diabetes.checkin.entity.CheckinRecord;
import com.diabetes.checkin.mapper.CheckinGlucoseDetailMapper;
import com.diabetes.checkin.mapper.CheckinRecordMapper;
import com.diabetes.common.client.InterventionClientHelper;
import com.diabetes.common.client.UserServiceClient;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.util.IdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GlucoseCheckinService {

    private static final Map<Integer, String> CONTEXT_LABELS = Map.of(
            1, "空腹", 2, "餐后2h", 3, "睡前", 4, "随机"
    );

    private final CheckinRecordMapper checkinRecordMapper;
    private final CheckinGlucoseDetailMapper glucoseDetailMapper;
    private final CheckinService checkinService;
    private final UserServiceClient userServiceClient;
    private final String difyInternalKey;

    public GlucoseCheckinService(CheckinRecordMapper checkinRecordMapper,
                                 CheckinGlucoseDetailMapper glucoseDetailMapper,
                                 CheckinService checkinService,
                                 UserServiceClient userServiceClient,
                                 @Value("${dify-internal.key:}") String difyInternalKey) {
        this.checkinRecordMapper = checkinRecordMapper;
        this.glucoseDetailMapper = glucoseDetailMapper;
        this.checkinService = checkinService;
        this.userServiceClient = userServiceClient;
        this.difyInternalKey = difyInternalKey;
    }

    @Transactional
    public Map<String, Object> createCheckin(String userId, GlucoseCheckinRequest request) {
        LocalDate date = parseDate(request.getCheckinDate());

        int streak = checkinService.calculateStreakForDate(userId, date);
        CheckinRecord record = new CheckinRecord();
        record.setCheckinId(IdGenerator.nextId("chk_"));
        record.setUserId(userId);
        record.setCheckinType(CheckinConstants.TYPE_GLUCOSE);
        record.setCheckinDate(date);
        record.setRecordTime(LocalDateTime.now());
        record.setPointsEarned(15);
        record.setStreakDays(streak);
        checkinRecordMapper.insert(record);

        CheckinGlucoseDetail detail = new CheckinGlucoseDetail();
        detail.setCheckinId(record.getCheckinId());
        detail.setGlucoseValue(request.getGlucoseValue());
        detail.setMeasureContext(request.getMeasureContext() != null ? request.getMeasureContext() : 4);
        detail.setUnit(request.getUnit() != null ? request.getUnit() : 1);
        glucoseDetailMapper.insert(detail);

        checkinService.invalidateUserCache(userId, date);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checkinId", record.getCheckinId());
        result.put("checkinDate", date.toString());
        result.put("glucoseValue", detail.getGlucoseValue());
        result.put("measureContext", detail.getMeasureContext());
        result.put("measureContextLabel", CONTEXT_LABELS.getOrDefault(detail.getMeasureContext(), "随机"));
        result.put("pointsEarned", 15);
        result.put("streakDays", streak);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("checkin_type", CheckinConstants.TYPE_GLUCOSE);
        context.put("checkinType", CheckinConstants.TYPE_GLUCOSE);
        context.put("checkin_date", date.toString());
        context.put("glucose_value", detail.getGlucoseValue());
        context.put("glucoseValue", detail.getGlucoseValue());
        InterventionClientHelper.triggerEvaluate(userServiceClient, difyInternalKey,
                userId, "checkin_created", context);
        return result;
    }

    public List<Map<String, Object>> listRecords(String userId, String dateStr) {
        LocalDate date = parseDate(dateStr);
        return glucoseDetailMapper.findByUserAndDate(userId, date).stream()
                .map(this::toRecordMap)
                .toList();
    }

    public Map<String, Object> getHistory(String userId, LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new BusinessException(400, "开始日期不能晚于结束日期");
        }
        List<CheckinGlucoseDetail> records = glucoseDetailMapper.findByUserAndDateRange(userId, startDate, endDate);
        List<Map<String, Object>> items = records.stream().map(this::toRecordMap).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("startDate", startDate.toString());
        result.put("endDate", endDate.toString());
        result.put("records", items);
        result.put("summary", buildSummary(records));
        return result;
    }

    private Map<String, Object> buildSummary(List<CheckinGlucoseDetail> records) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("count", records.size());
        if (records.isEmpty()) {
            summary.put("avg", null);
            summary.put("max", null);
            summary.put("min", null);
            return summary;
        }
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal max = null;
        BigDecimal min = null;
        for (CheckinGlucoseDetail r : records) {
            BigDecimal v = r.getGlucoseValue();
            if (v == null) {
                continue;
            }
            sum = sum.add(v);
            max = max == null || v.compareTo(max) > 0 ? v : max;
            min = min == null || v.compareTo(min) < 0 ? v : min;
        }
        summary.put("avg", sum.divide(BigDecimal.valueOf(records.size()), 1, RoundingMode.HALF_UP));
        summary.put("max", max);
        summary.put("min", min);
        return summary;
    }

    private Map<String, Object> toRecordMap(CheckinGlucoseDetail detail) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("checkinId", detail.getCheckinId());
        map.put("glucoseValue", detail.getGlucoseValue());
        map.put("measureContext", detail.getMeasureContext());
        map.put("measureContextLabel", CONTEXT_LABELS.getOrDefault(detail.getMeasureContext(), "随机"));
        map.put("unit", detail.getUnit());
        map.put("unitLabel", detail.getUnit() != null && detail.getUnit() == 2 ? "mg/dL" : "mmol/L");
        map.put("checkinDate", detail.getCheckinDate() != null ? detail.getCheckinDate().toString() : null);
        map.put("recordTime", detail.getRecordTime() != null ? detail.getRecordTime().toString() : null);
        map.put("status", classifyGlucose(detail.getGlucoseValue()));
        return map;
    }

    private String classifyGlucose(BigDecimal value) {
        if (value == null) {
            return "unknown";
        }
        if (value.compareTo(BigDecimal.valueOf(3.9)) < 0) {
            return "low";
        }
        if (value.compareTo(BigDecimal.valueOf(7.0)) >= 0) {
            return "high";
        }
        if (value.compareTo(BigDecimal.valueOf(6.1)) >= 0) {
            return "elevated";
        }
        return "normal";
    }

    private LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, "日期格式错误，应为 yyyy-MM-dd");
        }
    }
}
