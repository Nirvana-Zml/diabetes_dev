package com.diabetes.checkin.service;

import com.diabetes.checkin.dto.ExerciseCheckinRequest;
import com.diabetes.checkin.entity.CheckinExerciseDetail;
import com.diabetes.checkin.entity.ExercisePreset;
import com.diabetes.checkin.mapper.CheckinExerciseDetailMapper;
import com.diabetes.checkin.mapper.PresetMapper;
import com.diabetes.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExerciseCheckinService {

    private final CheckinRecordWriter recordWriter;
    private final CheckinExerciseDetailMapper exerciseDetailMapper;
    private final PresetMapper presetMapper;

    public ExerciseCheckinService(CheckinRecordWriter recordWriter,
                                  CheckinExerciseDetailMapper exerciseDetailMapper,
                                  PresetMapper presetMapper) {
        this.recordWriter = recordWriter;
        this.exerciseDetailMapper = exerciseDetailMapper;
        this.presetMapper = presetMapper;
    }

    @Transactional
    public Map<String, Object> createCheckin(String userId, ExerciseCheckinRequest request) {
        LocalDate date = parseDate(request.getCheckinDate());

        CheckinExerciseDetail detail = new CheckinExerciseDetail();
        detail.setSourceType(request.getSourceType());
        detail.setDurationMinutes(request.getDurationMinutes());

        if (request.getSourceType() == CheckinConstants.SOURCE_PRESET) {
            if (request.getExerciseId() == null || request.getExerciseId().isBlank()) {
                throw new BusinessException(400, "选用预设运动时 exerciseId 必填");
            }
            ExercisePreset preset = presetMapper.findExercisePresetById(request.getExerciseId());
            if (preset == null) {
                throw new BusinessException(404, "预设运动不存在");
            }
            detail.setExerciseId(preset.getExerciseId());
            detail.setExerciseName(preset.getExerciseName());
            detail.setCaloriesPerMinute(preset.getCaloriesPerMinute());
        } else if (request.getSourceType() == CheckinConstants.SOURCE_CUSTOM) {
            if (request.getExerciseName() == null || request.getExerciseName().isBlank()) {
                throw new BusinessException(400, "自定义运动时 exerciseName 必填");
            }
            if (request.getCaloriesPerMinute() == null
                    || request.getCaloriesPerMinute().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(400, "自定义运动时 caloriesPerMinute 必填且大于 0");
            }
            detail.setExerciseName(request.getExerciseName().trim());
            detail.setCaloriesPerMinute(request.getCaloriesPerMinute());
        } else {
            throw new BusinessException(400, "sourceType 无效");
        }

        int caloriesBurned = detail.getCaloriesPerMinute()
                .multiply(BigDecimal.valueOf(detail.getDurationMinutes()))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
        detail.setCaloriesBurned(caloriesBurned);

        String checkinId = recordWriter.createRecord(userId, CheckinConstants.TYPE_EXERCISE, date);
        detail.setCheckinId(checkinId);
        exerciseDetailMapper.insert(detail);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checkinId", checkinId);
        result.put("checkinDate", date.toString());
        result.put("caloriesBurned", caloriesBurned);
        return result;
    }

    public List<Map<String, Object>> listRecords(String userId, String dateStr) {
        LocalDate date = parseDate(dateStr);
        return exerciseDetailMapper.findByUserAndDate(userId, date).stream()
                .map(this::toRecordMap)
                .toList();
    }

    private Map<String, Object> toRecordMap(CheckinExerciseDetail detail) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("checkinId", detail.getCheckinId());
        map.put("sourceType", detail.getSourceType());
        map.put("exerciseId", detail.getExerciseId());
        map.put("exerciseName", detail.getExerciseName());
        map.put("caloriesPerMinute", detail.getCaloriesPerMinute());
        map.put("durationMinutes", detail.getDurationMinutes());
        map.put("caloriesBurned", detail.getCaloriesBurned());
        map.put("recordTime", detail.getRecordTime() != null ? detail.getRecordTime().toString() : null);
        return map;
    }

    private LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, "日期格式错误，应为 yyyy-MM-dd");
        }
    }
}
