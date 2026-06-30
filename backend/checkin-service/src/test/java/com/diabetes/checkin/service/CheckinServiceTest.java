package com.diabetes.checkin.service;

import com.diabetes.checkin.entity.CheckinRecord;
import com.diabetes.checkin.mapper.CheckinRecordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CheckinServiceTest {

    private final CheckinRecordMapper mapper = mock(CheckinRecordMapper.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final CheckinService service = new CheckinService(mapper, redis, new ObjectMapper());

    @Test
    void getTodayStatus_returnsCachedValue() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn("""
                {"todayCheckins":[],"todayPoints":3,"streakDays":2}
                """);

        Map<String, Object> result = service.getTodayStatus("u1");

        assertEquals(3, result.get("todayPoints"));
        assertEquals(2, result.get("streakDays"));
        verifyNoInteractions(mapper);
    }

    @Test
    void getTodayStatus_buildsStatusAndWritesCache() {
        LocalDate today = LocalDate.now();
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(mapper.findByUserAndDate(eq("u1"), any(LocalDate.class))).thenAnswer(invocation -> {
            LocalDate date = invocation.getArgument(1);
            if (today.equals(date)) {
                return List.of(record(1, today, 5), record(4, today, null));
            }
            if (today.minusDays(1).equals(date)) {
                return List.of(record(2, date, 0));
            }
            return List.of();
        });

        Map<String, Object> result = service.getTodayStatus("u1");

        assertEquals(5, result.get("todayPoints"));
        assertEquals(2, result.get("streakDays"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> status = (List<Map<String, Object>>) result.get("todayCheckins");
        assertEquals(List.of(true, false, false, true),
                status.stream().map(item -> item.get("completed")).toList());
        verify(valueOps).set(anyString(), contains("todayPoints"), any());
    }

    @Test
    void getTodayStatus_degradesWhenRedisFails() {
        LocalDate today = LocalDate.now();
        when(redis.opsForValue()).thenThrow(new RuntimeException("redis down"));
        when(mapper.findByUserAndDate(eq("u1"), any(LocalDate.class))).thenAnswer(invocation -> {
            LocalDate date = invocation.getArgument(1);
            return today.equals(date) ? List.of(record(1, today, 1)) : List.of();
        });

        Map<String, Object> result = service.getTodayStatus("u1");

        assertEquals(1, result.get("todayPoints"));
        assertEquals(1, result.get("streakDays"));
    }

    @Test
    void getStats_usesCacheAndBuildsWeeklyOrMonthlyStats() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(contains("weekly"))).thenReturn("{\"totalCheckins\":9}");
        assertEquals(9, service.getStats("u1", "weekly").get("totalCheckins"));

        reset(valueOps, mapper);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        LocalDate end = LocalDate.now();
        when(mapper.findByUserAndDateRange(eq("u1"), any(), eq(end)))
                .thenReturn(List.of(record(1, end, 0), record(2, end, 0), record(3, end, 0), record(4, end, 0)));
        when(mapper.findByUserAndDate(eq("u1"), any(LocalDate.class))).thenAnswer(invocation -> {
            LocalDate date = invocation.getArgument(1);
            return end.equals(date) ? List.of(record(1, end, 0)) : List.of();
        });
        when(mapper.sumPointsByUser("u1")).thenReturn(10);

        Map<String, Object> result = service.getStats("u1", "monthly");

        assertEquals(4, result.get("totalCheckins"));
        assertEquals(10, result.get("totalPoints"));
        assertEquals(1, result.get("streakDays"));
        verify(valueOps).set(anyString(), contains("totalCheckins"), any());
    }

    @Test
    void getStats_degradesWhenRedisFailsAndExposesStreakCalculation() {
        LocalDate end = LocalDate.now();
        when(redis.opsForValue()).thenThrow(new RuntimeException("redis down"));
        when(mapper.findByUserAndDateRange(eq("u1"), any(), eq(end))).thenReturn(List.of());
        when(mapper.findByUserAndDate("u1", end)).thenReturn(List.of());
        when(mapper.sumPointsByUser("u1")).thenReturn(0);

        Map<String, Object> result = service.getStats("u1", "weekly");

        assertEquals(0, result.get("totalCheckins"));
        assertEquals(0, service.calculateStreakForDate("u1", end));
    }

    @Test
    void buildStatsAndTrends_handleAllTypesAndOther() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 2);
        when(mapper.findByUserAndDateRange("u1", start, end)).thenReturn(List.of(
                record(1, start, 0),
                record(2, start, 0),
                record(3, end, 0),
                record(4, end, 0),
                record(99, end, 0)
        ));
        when(mapper.findByUserAndDate("u1", end)).thenReturn(List.of(record(1, end, 0)));
        when(mapper.findByUserAndDate("u1", end.minusDays(1))).thenReturn(List.of());
        when(mapper.sumPointsByUser("u1")).thenReturn(7);

        Map<String, Object> stats = service.buildStats("u1", start, end);
        Map<String, Object> trends = service.buildTrends("u1", start, end);

        assertEquals(5, stats.get("totalCheckins"));
        assertEquals(0.63, stats.get("completionRate"));
        assertTrue(stats.get("calendarData").toString().contains("other"));
        assertEquals(4, trends.size());
    }

    @Test
    void buildStats_handlesEmptyExpectedRange() {
        LocalDate start = LocalDate.of(2024, 1, 2);
        LocalDate end = LocalDate.of(2024, 1, 1);
        when(mapper.findByUserAndDateRange("u1", start, end)).thenReturn(List.of());
        when(mapper.sumPointsByUser("u1")).thenReturn(0);

        Map<String, Object> stats = service.buildStats("u1", start, end);

        assertEquals(0.0, stats.get("completionRate"));
    }

    @Test
    void achievements_coverLockedAndUnlockedStates() {
        LocalDate today = LocalDate.now();
        when(mapper.sumPointsByUser("u1")).thenReturn(1);
        when(mapper.findByUserAndDate(eq("u1"), any(LocalDate.class))).thenAnswer(invocation -> {
            LocalDate date = invocation.getArgument(1);
            long age = today.toEpochDay() - date.toEpochDay();
            return age >= 0 && age < 30 ? List.of(record(1, date, 0)) : List.of();
        });
        assertTrue((Boolean) service.getAchievements("u1").get(2).get("unlocked"));

        reset(mapper);
        when(mapper.sumPointsByUser("u2")).thenReturn(0);
        when(mapper.findByUserAndDate(eq("u2"), any(LocalDate.class))).thenReturn(List.of());
        assertFalse((Boolean) service.getAchievements("u2").get(0).get("unlocked"));
    }

    @Test
    void invalidateUserCache_ignoresRedisFailures() {
        service.invalidateUserCache("u1", LocalDate.of(2024, 1, 1));
        verify(redis, times(3)).delete(anyString());

        doThrow(new RuntimeException("redis down")).when(redis).delete(anyString());
        assertDoesNotThrow(() -> service.invalidateUserCache("u1", LocalDate.of(2024, 1, 1)));
    }

    @Test
    void privateMapType_rejectsUnsupportedType() throws Exception {
        Method method = CheckinService.class.getDeclaredMethod("mapType", String.class);
        method.setAccessible(true);

        Exception ex = assertThrows(Exception.class, () -> method.invoke(service, "bad"));

        assertTrue(ex.getCause() instanceof IllegalArgumentException);
    }

    private static CheckinRecord record(int type, LocalDate date, Integer points) {
        CheckinRecord record = new CheckinRecord();
        record.setCheckinId("chk_" + type + "_" + date);
        record.setUserId("u1");
        record.setCheckinType(type);
        record.setCheckinDate(date);
        record.setPointsEarned(points);
        record.setStreakDays(1);
        return record;
    }
}
