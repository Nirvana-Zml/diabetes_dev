package com.diabetes.checkin.controller;

import com.diabetes.checkin.service.CheckinReminderService;
import com.diabetes.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InternalReminderControllerTest {

    @Test
    void systemAdjustValidatesKeyUserAndDelegates() {
        CheckinReminderService service = mock(CheckinReminderService.class);
        InternalReminderController secured = new InternalReminderController(service, "secret");
        InternalReminderController open = new InternalReminderController(service, "");

        Map<String, Object> body = Map.of(
                "userId", "u1",
                "interventionId", "int_1",
                "expiresAt", "2024-06-01 12:00:00",
                "adjustments", List.of(Map.of("checkinType", 1, "times", List.of("08:00")))
        );

        assertThrows(BusinessException.class, () -> secured.systemAdjust(body, "bad"));
        assertThrows(BusinessException.class, () -> secured.systemAdjust(body, null));
        assertThrows(BusinessException.class, () -> secured.systemAdjust(Map.of("adjustments", List.of()), "secret"));
        assertNull(secured.systemAdjust(body, "secret").data());
        verify(service).applySystemAdjust(eq("u1"), eq("int_1"), anyList(),
                eq(LocalDateTime.parse("2024-06-01T12:00:00")));

        Map<String, Object> snakeBody = Map.of(
                "user_id", " ",
                "userId", "u2",
                "intervention_id", "int_2",
                "expires_at", "2024-07-01 08:00:00",
                "adjustments", List.of()
        );
        assertNull(open.systemAdjust(snakeBody, null).data());
        verify(service).applySystemAdjust(eq("u2"), eq("int_2"), eq(List.of()), eq(LocalDateTime.parse("2024-07-01T08:00:00")));

        Map<String, Object> blankExpiry = new java.util.LinkedHashMap<>(snakeBody);
        blankExpiry.put("userId", "u3");
        blankExpiry.put("expires_at", " ");
        assertNull(open.systemAdjust(blankExpiry, null).data());
        verify(service).applySystemAdjust(eq("u3"), eq("int_2"), eq(List.of()), isNull());
        assertNull(new InternalReminderController(service, null).systemAdjust(snakeBody, "any").data());
    }
}
