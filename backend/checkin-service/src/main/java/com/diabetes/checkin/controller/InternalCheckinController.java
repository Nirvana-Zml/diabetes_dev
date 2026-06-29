package com.diabetes.checkin.controller;

import com.diabetes.checkin.entity.CheckinRecord;
import com.diabetes.checkin.mapper.CheckinRecordMapper;
import com.diabetes.common.api.ApiResponse;
import com.diabetes.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/internal/checkin")
public class InternalCheckinController {

    private final CheckinRecordMapper checkinRecordMapper;
    private final String difyInternalKey;

    public InternalCheckinController(CheckinRecordMapper checkinRecordMapper,
                                     @Value("${dify-internal.key:}") String difyInternalKey) {
        this.checkinRecordMapper = checkinRecordMapper;
        this.difyInternalKey = difyInternalKey;
    }

    @GetMapping("/user/{userId}/recent")
    public ApiResponse<List<Map<String, Object>>> recent(@PathVariable String userId,
                                                         @RequestParam(defaultValue = "14") int days,
                                                         @RequestHeader(value = "X-Dify-Key", required = false) String key) {
        validateDifyKey(key);
        LocalDate start = LocalDate.now().minusDays(Math.max(days, 1) - 1L);
        List<CheckinRecord> records = checkinRecordMapper.findRecentByUser(userId, start);
        List<Map<String, Object>> list = records.stream().map(r -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("checkinId", r.getCheckinId());
            item.put("checkinType", typeName(r.getCheckinType()));
            item.put("checkinDate", r.getCheckinDate());
            item.put("pointsEarned", r.getPointsEarned());
            item.put("streakDays", r.getStreakDays());
            return item;
        }).toList();
        return ApiResponse.ok(list);
    }

    private String typeName(Integer type) {
        if (type == null) return "unknown";
        return switch (type) {
            case 1 -> "diet";
            case 2 -> "exercise";
            case 3 -> "medication";
            case 4 -> "glucose";
            default -> "unknown";
        };
    }

    private void validateDifyKey(String key) {
        if (difyInternalKey != null && !difyInternalKey.isBlank()
                && (key == null || !difyInternalKey.equals(key))) {
            throw new BusinessException(401, "Dify 内部密钥无效");
        }
    }
}
