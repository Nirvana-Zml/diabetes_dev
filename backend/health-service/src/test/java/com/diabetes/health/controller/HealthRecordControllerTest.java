package com.diabetes.health.controller;

import com.diabetes.health.dto.UpdateHealthRecordRequest;
import com.diabetes.health.service.HealthRecordService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthRecordControllerTest {

    @Mock
    private HealthRecordService healthRecordService;

    @InjectMocks
    private HealthRecordController controller;

    @Test
    void latest() {
        Map<String, Object> expected = Map.of("recordId", "hr_001", "height", 170);
        when(healthRecordService.getLatest("user1")).thenReturn(expected);

        var result = controller.latest("user1");

        assertEquals(200, result.code());
        assertEquals(expected, result.data());
        verify(healthRecordService).getLatest("user1");
    }

    @Test
    void latest_noRecord() {
        when(healthRecordService.getLatest("user1")).thenReturn(Map.of());

        var result = controller.latest("user1");

        assertEquals(200, result.code());
        assertEquals(Map.of(), result.data());
    }

    @Test
    void update() {
        UpdateHealthRecordRequest request = new UpdateHealthRecordRequest();
        request.setHeight(170f);
        request.setWeight(65f);
        Map<String, Object> expected = Map.of("recordId", "hr_002", "height", 170);
        when(healthRecordService.save("user1", request)).thenReturn(expected);

        var result = controller.update("user1", request);

        assertEquals(200, result.code());
        assertEquals(expected, result.data());
        verify(healthRecordService).save("user1", request);
    }
}