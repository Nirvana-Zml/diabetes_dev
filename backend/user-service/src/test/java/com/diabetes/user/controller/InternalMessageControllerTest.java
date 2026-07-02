package com.diabetes.user.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.user.dto.CreateMessageRequest;
import com.diabetes.user.dto.UserMessageResponse;
import com.diabetes.user.service.UserMessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalMessageControllerTest {

    @Mock
    private UserMessageService userMessageService;

    @InjectMocks
    private InternalMessageController controller;

    @Test
    void create_success() {
        CreateMessageRequest request = new CreateMessageRequest(
                "u_1", "consult", "completed", "Title", "Summary", "biz_1",
                "/path", Map.of("id", "1"), Map.of());
        UserMessageResponse created = new UserMessageResponse(
                "msg_1", "consult", "completed", "Title", "Summary", "biz_1",
                "/path", Map.of("id", "1"), Map.of(), false,
                LocalDateTime.now(), LocalDateTime.now());
        when(userMessageService.createMessage(request)).thenReturn(created);

        ApiResponse<UserMessageResponse> response = controller.create(request);

        assertEquals(created, response.data());
    }

    @Test
    void create_notifyDisabled() {
        CreateMessageRequest request = new CreateMessageRequest(
                "u_1", "consult", "completed", "Title", null, "biz_1",
                "/path", null, null);
        when(userMessageService.createMessage(request)).thenReturn(null);

        ApiResponse<UserMessageResponse> response = controller.create(request);

        assertNull(response.data());
        assertEquals("消息通知已关闭", response.message());
    }
}
