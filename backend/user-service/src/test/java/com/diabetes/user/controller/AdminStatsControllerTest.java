package com.diabetes.user.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.user.service.AdminStatsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminStatsControllerTest {

    @Mock
    private AdminStatsService adminStatsService;

    @InjectMocks
    private AdminStatsController adminStatsController;

    @Test
    void overview() {
        Map<String, Object> data = Map.of("users", Map.of("total", 10));
        when(adminStatsService.getOverview()).thenReturn(data);

        ApiResponse<Map<String, Object>> response = adminStatsController.overview();

        assertEquals(200, response.code());
        assertEquals(data, response.data());
    }

    @Test
    void trends() {
        Map<String, Object> data = Map.of("days", 30);
        when(adminStatsService.getTrends(30)).thenReturn(data);

        ApiResponse<Map<String, Object>> response = adminStatsController.trends(30);

        assertEquals(data, response.data());
        verify(adminStatsService).getTrends(30);
    }

    @Test
    void users() {
        Map<String, Object> data = Map.of("total", 3);
        when(adminStatsService.listUsers(1, 20)).thenReturn(data);

        ApiResponse<Map<String, Object>> response = adminStatsController.users(1, 20);

        assertEquals(data, response.data());
        verify(adminStatsService).listUsers(1, 20);
    }
}
