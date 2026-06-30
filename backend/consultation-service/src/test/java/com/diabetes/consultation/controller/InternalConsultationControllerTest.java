package com.diabetes.consultation.controller;

import com.diabetes.consultation.service.ConsultationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalConsultationControllerTest {

    @Mock
    private ConsultationService consultationService;

    @InjectMocks
    private InternalConsultationController controller;

    @Test
    void activeSession() {
        when(consultationService.getActiveSession("user1"))
                .thenReturn(Map.of("session", Map.of("sessionId", "sess_001", "status", "active")));

        var result = controller.activeSession("user1");

        assertEquals(200, result.code());
        assertNotNull(result.data().get("session"));
        verify(consultationService).getActiveSession("user1");
    }

    @Test
    void activeSession_noActive() {
        when(consultationService.getActiveSession("user1"))
                .thenReturn(Map.of("session", Map.of()));

        var result = controller.activeSession("user1");

        assertEquals(200, result.code());
        assertTrue(((Map<?, ?>) result.data().get("session")).isEmpty());
    }

    @Test
    void sessions() {
        when(consultationService.listSessions("user1", null, 1, 50))
                .thenReturn(Map.of("sessions", List.of(), "total", 0));

        var result = controller.sessions("user1", 1, 50);

        assertEquals(200, result.code());
        verify(consultationService).listSessions("user1", null, 1, 50);
    }

    @Test
    void sessions_customPage() {
        when(consultationService.listSessions("user1", null, 2, 20))
                .thenReturn(Map.of("sessions", List.of(), "total", 0));

        var result = controller.sessions("user1", 2, 20);

        assertEquals(200, result.code());
        verify(consultationService).listSessions("user1", null, 2, 20);
    }
}