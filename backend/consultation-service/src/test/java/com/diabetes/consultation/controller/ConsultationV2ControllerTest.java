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
class ConsultationV2ControllerTest {

    @Mock
    private ConsultationService consultationService;

    @InjectMocks
    private ConsultationV2Controller controller;

    @Test
    void listAiDoctors() {
        when(consultationService.listDoctors("内分泌科", "张", "online"))
                .thenReturn(List.of(Map.of("doctorId", "d1", "name", "张医生")));

        var result = controller.listAiDoctors("内分泌科", "张", "online");

        assertEquals(200, result.code());
        assertEquals(1, result.data().size());
        verify(consultationService).listDoctors("内分泌科", "张", "online");
    }

    @Test
    void listDepartments() {
        when(consultationService.listDepartments())
                .thenReturn(List.of("内分泌科", "心血管科"));

        var result = controller.listDepartments();

        assertEquals(200, result.code());
        assertEquals(2, result.data().size());
        assertTrue(result.data().contains("内分泌科"));
        verify(consultationService).listDepartments();
    }

    @Test
    void listDepartments_empty() {
        when(consultationService.listDepartments()).thenReturn(List.of());

        var result = controller.listDepartments();

        assertEquals(200, result.code());
        assertTrue(result.data().isEmpty());
    }

    @Test
    void createSession() {
        Map<String, Object> body = Map.of("doctorId", "d1");
        when(consultationService.createSession("user1", "d1"))
                .thenReturn(Map.of("sessionId", "sess_001", "status", "active"));

        var result = controller.createSession("user1", body);

        assertEquals(200, result.code());
        assertEquals("sess_001", result.data().get("sessionId"));
        verify(consultationService).createSession("user1", "d1");
    }

    @Test
    void listSessions() {
        when(consultationService.listSessions("user1", "active", 1, 10))
                .thenReturn(Map.of("sessions", List.of(), "total", 0));

        var result = controller.listSessions("user1", "active", 1, 10);

        assertEquals(200, result.code());
        verify(consultationService).listSessions("user1", "active", 1, 10);
    }

    @Test
    void sendMessage() {
        Map<String, Object> body = Map.of("content", "你好医生");
        when(consultationService.sendMessage("user1", "sess_001", "你好医生", null))
                .thenReturn(Map.of("messageId", "msg_001"));

        var result = controller.sendMessage("user1", "sess_001", body);

        assertEquals(200, result.code());
        verify(consultationService).sendMessage("user1", "sess_001", "你好医生", null);
    }

    @Test
    void listMessages() {
        when(consultationService.listMessages("user1", "sess_001", 1, 50))
                .thenReturn(Map.of("messages", List.of(), "total", 0));

        var result = controller.listMessages("user1", "sess_001", 1, 50);

        assertEquals(200, result.code());
        verify(consultationService).listMessages("user1", "sess_001", 1, 50);
    }

    @Test
    void closeSession() {
        Map<String, Object> body = Map.of("rating", 5, "feedback", "很好");
        doNothing().when(consultationService).closeSession("user1", "sess_001", 5, "很好");

        var result = controller.closeSession("user1", "sess_001", body);

        assertEquals(200, result.code());
        verify(consultationService).closeSession("user1", "sess_001", 5, "很好");
    }

    @Test
    void closeSession_nullBody() {
        doNothing().when(consultationService).closeSession("user1", "sess_001", null, null);

        var result = controller.closeSession("user1", "sess_001", null);

        assertEquals(200, result.code());
        verify(consultationService).closeSession("user1", "sess_001", null, null);
    }

    @Test
    void evaluate() {
        Map<String, Object> body = Map.of("rating", 4, "comment", "不错");
        doNothing().when(consultationService).closeSession("user1", "sess_001", 4, "不错");

        var result = controller.evaluate("user1", "sess_001", body);

        assertEquals(200, result.code());
        verify(consultationService).closeSession("user1", "sess_001", 4, "不错");
    }

    @Test
    void aiSuggestion() {
        when(consultationService.getAiSuggestion("user1", "sess_001"))
                .thenReturn(Map.of("possibleDiagnoses", List.of()));

        var result = controller.aiSuggestion("user1", "sess_001");

        assertEquals(200, result.code());
        verify(consultationService).getAiSuggestion("user1", "sess_001");
    }

    @Test
    void difyWorkflowSpec() {
        when(consultationService.getDifyWorkflowSpec())
                .thenReturn(Map.of("workflowUrl", "http://localhost/v1/workflows/run"));

        var result = controller.difyWorkflowSpec();

        assertEquals(200, result.code());
        verify(consultationService).getDifyWorkflowSpec();
    }

    // ==================== firstString 分支覆盖 (通过公共API) ====================

    @Test
    void createSession_nullBody() {
        when(consultationService.createSession("user1", null))
                .thenThrow(new RuntimeException("请选择医生"));

        assertThrows(RuntimeException.class, () -> controller.createSession("user1", null));
    }

    @Test
    void createSession_blankDoctorId() {
        Map<String, Object> body = Map.of("doctorId", "   ");
        when(consultationService.createSession("user1", null))
                .thenThrow(new RuntimeException("请选择医生"));

        assertThrows(RuntimeException.class, () -> controller.createSession("user1", body));
    }

    @Test
    void createSession_doctorIdBlankFallbackToAiDoctorId() {
        Map<String, Object> body = Map.of("aiDoctorId", "   ");
        when(consultationService.createSession("user1", null))
                .thenThrow(new RuntimeException("请选择医生"));

        assertThrows(RuntimeException.class, () -> controller.createSession("user1", body));
    }

    // ==================== intValue 分支覆盖 (通过公共API) ====================

    @Test
    void evaluate_longRating() {
        Map<String, Object> body = Map.of("rating", 100L, "comment", "很好");
        doNothing().when(consultationService).closeSession("user1", "sess_001", 100, "很好");

        var result = controller.evaluate("user1", "sess_001", body);

        assertEquals(200, result.code());
        verify(consultationService).closeSession("user1", "sess_001", 100, "很好");
    }

    @Test
    void evaluate_doubleRating() {
        Map<String, Object> body = Map.of("rating", 4.9, "comment", "很好");
        doNothing().when(consultationService).closeSession("user1", "sess_001", 4, "很好");

        var result = controller.evaluate("user1", "sess_001", body);

        assertEquals(200, result.code());
        verify(consultationService).closeSession("user1", "sess_001", 4, "很好");
    }

    @Test
    void evaluate_floatRating() {
        Map<String, Object> body = Map.of("rating", 3.7f, "comment", "一般");
        doNothing().when(consultationService).closeSession("user1", "sess_001", 3, "一般");

        var result = controller.evaluate("user1", "sess_001", body);

        assertEquals(200, result.code());
        verify(consultationService).closeSession("user1", "sess_001", 3, "一般");
    }

    @Test
    void evaluate_stringRating() {
        Map<String, Object> body = Map.of("rating", "5", "comment", "很好");
        doNothing().when(consultationService).closeSession("user1", "sess_001", 5, "很好");

        var result = controller.evaluate("user1", "sess_001", body);

        assertEquals(200, result.code());
        verify(consultationService).closeSession("user1", "sess_001", 5, "很好");
    }

    @Test
    void evaluate_invalidStringRating() {
        Map<String, Object> body = Map.of("rating", "invalid", "comment", "不错");
        doThrow(new RuntimeException("评分无效"))
                .when(consultationService).closeSession("user1", "sess_001", null, "不错");

        assertThrows(RuntimeException.class, () -> controller.evaluate("user1", "sess_001", body));
    }

    @Test
    void evaluate_ratingFromString() {
        Map<String, Object> body = Map.of("rating", "5", "comment", "很好");
        doNothing().when(consultationService).closeSession("user1", "sess_001", 5, "很好");

        var result = controller.evaluate("user1", "sess_001", body);

        assertEquals(200, result.code());
        verify(consultationService).closeSession("user1", "sess_001", 5, "很好");
    }

    @Test
    void evaluate_nullRatingValue() {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("rating", null);
        body.put("comment", "不错");
        doNothing().when(consultationService).closeSession("user1", "sess_001", null, "不错");

        var result = controller.evaluate("user1", "sess_001", body);

        assertEquals(200, result.code());
        verify(consultationService).closeSession("user1", "sess_001", null, "不错");
    }
}