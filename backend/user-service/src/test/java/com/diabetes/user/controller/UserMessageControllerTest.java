package com.diabetes.user.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.user.config.JwtAuthInterceptor;
import com.diabetes.user.dto.UserMessageListResponse;
import com.diabetes.user.dto.UserMessageResponse;
import com.diabetes.user.service.UserMessageService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserMessageControllerTest {

    @Mock
    private UserMessageService userMessageService;
    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private UserMessageController controller;

    @BeforeEach
    void setUp() {
        when(request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID)).thenReturn("u_1");
    }

    @Test
    void list() {
        UserMessageListResponse payload = new UserMessageListResponse(1, 1, List.of(
                new UserMessageResponse(
                        "msg_1", "consult", "completed", "Title", "Summary", "biz_1",
                        "/path", Map.of(), Map.of(), false,
                        LocalDateTime.now(), LocalDateTime.now())));
        when(userMessageService.listMessages("u_1", false, 20, 0)).thenReturn(payload);

        ApiResponse<UserMessageListResponse> response = controller.list(request, false, 20, 0);

        assertEquals(payload, response.data());
    }

    @Test
    void unreadCount() {
        when(userMessageService.unreadCount("u_1")).thenReturn(Map.of("unread_count", 2));

        ApiResponse<Map<String, Object>> response = controller.unreadCount(request);

        assertEquals(2, response.data().get("unread_count"));
    }

    @Test
    void markRead() {
        ApiResponse<Void> response = controller.markRead(request, "msg_1");

        verify(userMessageService).markRead("u_1", "msg_1");
        assertEquals(200, response.code());
    }

    @Test
    void markAllRead() {
        ApiResponse<Void> response = controller.markAllRead(request);

        verify(userMessageService).markAllRead("u_1");
        assertEquals(200, response.code());
    }

    @Test
    void markReadByBiz() {
        Map<String, String> body = Map.of("messageType", "consult", "bizId", "biz_1");

        controller.markReadByBiz(request, body);

        verify(userMessageService).markReadByBiz("u_1", "consult", "biz_1");
    }

    @Test
    void currentUserId_missing() {
        when(request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID)).thenReturn(null);
        assertThrows(BusinessException.class, () -> controller.list(request, false, 20, 0));
    }

    @Test
    void currentUserId_blank() {
        when(request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID)).thenReturn("  ");
        assertThrows(BusinessException.class, () -> controller.unreadCount(request));
    }
}
