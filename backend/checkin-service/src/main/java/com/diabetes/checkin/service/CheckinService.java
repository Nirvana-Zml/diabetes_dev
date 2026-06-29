package com.diabetes.checkin.service;

import com.diabetes.checkin.entity.CheckinRecord;
import com.diabetes.checkin.mapper.CheckinRecordMapper;
import com.diabetes.common.redis.RedisKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

@Service
public class CheckinService {

    private static final List<String> TYPES = List.of("diet", "exercise", "medication", "glucose");
    private static final Duration TODAY_CACHE_TTL = Duration.ofMinutes(10);
    private static final Duration STATS_CACHE_TTL = Duration.ofMinutes(5);

    private final CheckinRecordMapper checkinRecordMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public CheckinService(CheckinRecordMapper checkinRecordMapper,
                          StringRedisTemplate redisTemplate,
                          ObjectMapper objectMapper) {
        this.checkinRecordMapper = checkinRecordMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> getTodayStatus(String userId) {
        LocalDate today = LocalDate.now();
        String cacheKey = RedisKeys.checkinToday(userId, today.toString());
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<>() {});
            }
        } catch (Exception ignored) {
            // Redis 不可用时降级查库
        }

        List<CheckinRecord> todayRecords = checkinRecordMapper.findByUserAndDate(userId, today);
        List<Map<String, Object>> status = TYPES.stream().map(type -> {
            int code = mapType(type);
            boolean completed = todayRecords.stream().anyMatch(r -> Objects.equals(r.getCheckinType(), code));
            return Map.<String, Object>of("checkinType", type, "completed", completed);
        }).toList();
        int todayPoints = todayRecords.stream().mapToInt(r -> r.getPointsEarned() == null ? 0 : r.getPointsEarned()).sum();
        int streak = calculateStreak(userId, today);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todayCheckins", status);
        result.put("todayPoints", todayPoints);
        result.put("streakDays", streak);

        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result), TODAY_CACHE_TTL);
        } catch (Exception ignored) {
        }
        return result;
    }

    public Map<String, Object> getStats(String userId, String range) {
        String cacheKey = RedisKeys.checkinStats(userId, range);
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<>() {});
            }
        } catch (Exception ignored) {
        }

        LocalDate end = LocalDate.now();
        LocalDate start = "weekly".equals(range) ? end.minusDays(6) : end.minusDays(29);
        Map<String, Object> result = buildStats(userId, start, end);
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result), STATS_CACHE_TTL);
        } catch (Exception ignored) {
        }
        return result;
    }

    public List<Map<String, Object>> getAchievements(String userId) {
        int streak = calculateStreak(userId, LocalDate.now());
        int total = checkinRecordMapper.sumPointsByUser(userId);
        return List.of(
                achievement("首次打卡", total > 0),
                achievement("连续打卡达人", streak >= 7),
                achievement("打卡王者", streak >= 30)
        );
    }

    /** 供血糖打卡等服务复用连续天数计算 */
    public int calculateStreakForDate(String userId, LocalDate date) {
        return calculateStreak(userId, date);
    }

    /** 供各打卡模块在写入记录后刷新缓存 */
    public void invalidateUserCache(String userId, LocalDate date) {
        try {
            redisTemplate.delete(RedisKeys.checkinToday(userId, date.toString()));
            redisTemplate.delete(RedisKeys.checkinStats(userId, "weekly"));
            redisTemplate.delete(RedisKeys.checkinStats(userId, "monthly"));
        } catch (Exception ignored) {
        }
    }

    private Map<String, Object> achievement(String name, boolean unlocked) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("badgeUrl", "");
        map.put("unlocked", unlocked);
        return map;
    }

    private int calculateStreak(String userId, LocalDate date) {
        int streak = 0;
        LocalDate cursor = date;
        while (true) {
            List<CheckinRecord> records = checkinRecordMapper.findByUserAndDate(userId, cursor);
            if (records.isEmpty()) {
                break;
            }
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private int mapType(String type) {
        return switch (type) {
            case "diet" -> 1;
            case "exercise" -> 2;
            case "medication" -> 3;
            case "glucose" -> 4;
            default -> throw new IllegalArgumentException("不支持的打卡类型: " + type);
        };
    }

    Map<String, Object> buildStats(String userId, LocalDate start, LocalDate end) {
        List<CheckinRecord> records = checkinRecordMapper.findByUserAndDateRange(userId, start, end);
        long days = end.toEpochDay() - start.toEpochDay() + 1;
        int expected = (int) days * TYPES.size();
        int totalCheckins = records.size();
        double completionRate = expected == 0 ? 0 : (double) totalCheckins / expected;
        int totalPoints = checkinRecordMapper.sumPointsByUser(userId);
        int streak = calculateStreak(userId, end);

        Map<String, Object> calendar = new LinkedHashMap<>();
        for (CheckinRecord record : records) {
            String date = record.getCheckinDate().toString();
            @SuppressWarnings("unchecked")
            Map<String, Boolean> day = (Map<String, Boolean>) calendar.computeIfAbsent(date, k -> new LinkedHashMap<>());
            day.put(typeName(record.getCheckinType()), true);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCheckins", totalCheckins);
        result.put("completionRate", Math.round(completionRate * 100.0) / 100.0);
        result.put("totalPoints", totalPoints);
        result.put("streakDays", streak);
        result.put("calendarData", calendar);
        return result;
    }

    Map<String, Object> buildTrends(String userId, LocalDate start, LocalDate end) {
        List<CheckinRecord> records = checkinRecordMapper.findByUserAndDateRange(userId, start, end);
        return Map.of(
                "dietTrend", trendByType(records, 1),
                "exerciseTrend", trendByType(records, 2),
                "medicationTrend", trendByType(records, 3),
                "glucoseTrend", trendByType(records, 4)
        );
    }

    private List<Map<String, Object>> trendByType(List<CheckinRecord> records, int type) {
        Map<LocalDate, Long> grouped = new TreeMap<>();
        for (CheckinRecord record : records) {
            if (Objects.equals(record.getCheckinType(), type)) {
                grouped.merge(record.getCheckinDate(), 1L, Long::sum);
            }
        }
        return grouped.entrySet().stream()
                .map(e -> Map.<String, Object>of("date", e.getKey().toString(), "count", e.getValue()))
                .toList();
    }

    private String typeName(int type) {
        return switch (type) {
            case 1 -> "diet";
            case 2 -> "exercise";
            case 3 -> "medication";
            case 4 -> "glucose";
            default -> "other";
        };
    }
}
