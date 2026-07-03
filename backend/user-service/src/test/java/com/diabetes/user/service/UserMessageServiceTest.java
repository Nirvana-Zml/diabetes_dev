package com.diabetes.user.service;

import com.diabetes.common.exception.BusinessException;
import com.diabetes.user.dto.CreateMessageRequest;
import com.diabetes.user.dto.UserMessageListResponse;
import com.diabetes.user.dto.UserMessageResponse;
import com.diabetes.user.entity.User;
import com.diabetes.user.entity.UserMessage;
import com.diabetes.user.mapper.UserMapper;
import com.diabetes.user.mapper.UserMessageMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserMessageServiceTest {

    @Mock
    private UserMessageMapper userMessageMapper;
    @Mock
    private UserMapper userMapper;

    private ObjectMapper objectMapper;
    private UserMessageService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new UserMessageService(userMessageMapper, userMapper, objectMapper);
    }

    @Test
    void listMessages_clampsLimitAndOffset() {
        UserMessage row = message("msg_1", "u_1");
        when(userMessageMapper.listByUser("u_1", false, 1, 0)).thenReturn(List.of(row));
        when(userMessageMapper.countByUser("u_1", false)).thenReturn(1);
        when(userMessageMapper.countUnread("u_1")).thenReturn(0);

        UserMessageListResponse result = service.listMessages("u_1", false, 0, -1);

        assertEquals(1, result.total());
        assertEquals(1, result.list().size());
        verify(userMessageMapper).listByUser("u_1", false, 1, 0);
    }

    @Test
    void listMessages_capsLimitAt50() {
        when(userMessageMapper.listByUser("u_1", true, 50, 0)).thenReturn(List.of());
        when(userMessageMapper.countByUser("u_1", true)).thenReturn(0);
        when(userMessageMapper.countUnread("u_1")).thenReturn(0);

        service.listMessages("u_1", true, 100, 0);

        verify(userMessageMapper).listByUser("u_1", true, 50, 0);
    }

    @Test
    void listMessages_handlesBlankJsonFields() {
        UserMessage row = message("msg_1", "u_1");
        row.setLinkQuery("");
        row.setExtra("   ");
        when(userMessageMapper.listByUser("u_1", false, 20, 0)).thenReturn(List.of(row));
        when(userMessageMapper.countByUser("u_1", false)).thenReturn(1);
        when(userMessageMapper.countUnread("u_1")).thenReturn(0);

        UserMessageResponse item = service.listMessages("u_1", false, 20, 0).list().get(0);

        assertTrue(item.linkQuery().isEmpty());
        assertTrue(item.extra().isEmpty());
    }

    @Test
    void listMessages_parsesInvalidJsonFields() {
        UserMessage row = message("msg_1", "u_1");
        row.setLinkQuery("{bad");
        row.setExtra("not-json");
        when(userMessageMapper.listByUser("u_1", false, 20, 0)).thenReturn(List.of(row));
        when(userMessageMapper.countByUser("u_1", false)).thenReturn(1);
        when(userMessageMapper.countUnread("u_1")).thenReturn(1);

        UserMessageListResponse result = service.listMessages("u_1", false, 20, 0);

        assertEquals(1, result.unreadCount());
        assertTrue(result.list().get(0).linkQuery().isEmpty());
        assertTrue(result.list().get(0).extra().isEmpty());
    }

    @Test
    void listMessages_parsesValidJsonFields() throws Exception {
        UserMessage row = message("msg_1", "u_1");
        row.setLinkQuery(objectMapper.writeValueAsString(Map.of("id", "1")));
        row.setExtra(objectMapper.writeValueAsString(Map.of("k", "v")));
        row.setRead(true);
        when(userMessageMapper.listByUser("u_1", false, 20, 0)).thenReturn(List.of(row));
        when(userMessageMapper.countByUser("u_1", false)).thenReturn(1);
        when(userMessageMapper.countUnread("u_1")).thenReturn(0);

        UserMessageResponse item = service.listMessages("u_1", false, 20, 0).list().get(0);

        assertEquals("1", item.linkQuery().get("id"));
        assertEquals("v", item.extra().get("k"));
        assertTrue(item.isRead());
    }

    @Test
    void unreadCount() {
        when(userMessageMapper.countUnread("u_1")).thenReturn(3);

        assertEquals(3, service.unreadCount("u_1").get("unread_count"));
    }

    @Test
    void markRead_success() {
        UserMessage existing = message("msg_1", "u_1");
        when(userMessageMapper.findById("msg_1")).thenReturn(existing);

        service.markRead("u_1", "msg_1");

        verify(userMessageMapper).markRead("u_1", "msg_1");
    }

    @Test
    void markRead_notFound() {
        when(userMessageMapper.findById("msg_x")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.markRead("u_1", "msg_x"));
        assertEquals(404, ex.getCode());
    }

    @Test
    void markRead_wrongUser() {
        UserMessage existing = message("msg_1", "u_2");
        when(userMessageMapper.findById("msg_1")).thenReturn(existing);

        assertThrows(BusinessException.class, () -> service.markRead("u_1", "msg_1"));
    }

    @Test
    void markAllRead() {
        service.markAllRead("u_1");
        verify(userMessageMapper).markAllRead("u_1");
    }

    @Test
    void markReadByBiz_skipsBlankParams() {
        service.markReadByBiz("u_1", null, "biz_1");
        service.markReadByBiz("u_1", "  ", "biz_1");
        service.markReadByBiz("u_1", "consult", null);
        service.markReadByBiz("u_1", "consult", "  ");

        verifyNoInteractions(userMessageMapper);
    }

    @Test
    void markReadByBiz_success() {
        service.markReadByBiz("u_1", "consult", "biz_1");
        verify(userMessageMapper).markReadByBiz("u_1", "consult", "biz_1");
    }

    @Test
    void createMessage_returnsNullWhenPersistedRowMissing() {
        stubNotifyEnabled("u_1");
        CreateMessageRequest request = request("u_1", "consult", "completed", "biz_1");
        when(userMessageMapper.findByUserTypeBiz("u_1", "consult", "biz_1")).thenReturn(null);
        when(userMessageMapper.findById(anyString())).thenReturn(null);

        assertNull(service.createMessage(request));
    }

    @Test
    void createMessage_skipsNullAndEmptyJsonMaps() {
        stubNotifyEnabled("u_1");
        CreateMessageRequest request = new CreateMessageRequest(
                "u_1", "consult", "completed", "Title", null, "biz_1",
                "/path", null, Map.of());
        when(userMessageMapper.findByUserTypeBiz("u_1", "consult", "biz_1")).thenReturn(null);
        when(userMessageMapper.findById(anyString())).thenReturn(message("msg_1", "u_1"));

        service.createMessage(request);

        ArgumentCaptor<UserMessage> captor = ArgumentCaptor.forClass(UserMessage.class);
        verify(userMessageMapper).insert(captor.capture());
        assertNull(captor.getValue().getLinkQuery());
        assertNull(captor.getValue().getExtra());
    }

    @Test
    void createMessage_insertsNewMessage() {
        stubNotifyEnabled("u_1");
        CreateMessageRequest request = request("u_1", "consult", "completed", "biz_1");
        UserMessage saved = message("msg_new", "u_1");
        when(userMessageMapper.findByUserTypeBiz("u_1", "consult", "biz_1")).thenReturn(null);
        when(userMessageMapper.findById(anyString())).thenReturn(saved);

        UserMessageResponse created = service.createMessage(request);

        assertNotNull(created);
        ArgumentCaptor<UserMessage> captor = ArgumentCaptor.forClass(UserMessage.class);
        verify(userMessageMapper).insert(captor.capture());
        assertEquals("u_1", captor.getValue().getUserId());
        assertEquals("completed", captor.getValue().getStatus());
    }

    @Test
    void createMessage_generatesBizIdWhenMissing() {
        stubNotifyEnabled("u_1");
        CreateMessageRequest request = new CreateMessageRequest(
                "u_1", "consult", "failed", "Title", "Summary", null,
                "/path", Map.of("q", 1), Map.of("e", 2));
        UserMessage saved = message("msg_new", "u_1");
        when(userMessageMapper.findByUserTypeBiz(eq("u_1"), eq("consult"), anyString())).thenReturn(null);
        when(userMessageMapper.findById(anyString())).thenReturn(saved);

        service.createMessage(request);

        ArgumentCaptor<UserMessage> captor = ArgumentCaptor.forClass(UserMessage.class);
        verify(userMessageMapper).insert(captor.capture());
        assertEquals("failed", captor.getValue().getStatus());
        assertNotNull(captor.getValue().getBizId());
        assertTrue(captor.getValue().getBizId().startsWith("biz_"));
    }

    @Test
    void createMessage_truncatesLongTitleAndSummary() {
        stubNotifyEnabled("u_1");
        String longTitle = "T".repeat(120);
        String longSummary = "S".repeat(600);
        CreateMessageRequest request = new CreateMessageRequest(
                "u_1", "consult", "completed", longTitle, longSummary, "biz_1",
                "/path", null, null);
        when(userMessageMapper.findByUserTypeBiz("u_1", "consult", "biz_1")).thenReturn(null);
        when(userMessageMapper.findById(anyString())).thenReturn(message("msg_1", "u_1"));

        service.createMessage(request);

        ArgumentCaptor<UserMessage> captor = ArgumentCaptor.forClass(UserMessage.class);
        verify(userMessageMapper).insert(captor.capture());
        assertEquals(100, captor.getValue().getTitle().length());
        assertEquals(500, captor.getValue().getSummary().length());
    }

    @Test
    void createMessage_updatesExisting() {
        stubNotifyEnabled("u_1");
        CreateMessageRequest request = request("u_1", "consult", "completed", "biz_1");
        UserMessage existing = message("msg_old", "u_1");
        UserMessage updated = message("msg_old", "u_1");
        when(userMessageMapper.findByUserTypeBiz("u_1", "consult", "biz_1")).thenReturn(existing);
        when(userMessageMapper.findById("msg_old")).thenReturn(updated);

        UserMessageResponse result = service.createMessage(request);

        assertNotNull(result);
        verify(userMessageMapper).updateByBiz(any(UserMessage.class));
        verify(userMessageMapper, never()).insert(any());
    }

    @Test
    void createMessage_returnsNullWhenNotifyDisabled() {
        User user = new User();
        user.setUserId("u_1");
        user.setPrivacySettings("{\"message_notify\":false}");
        when(userMapper.findById("u_1")).thenReturn(user);

        CreateMessageRequest request = request("u_1", "consult", "completed", "biz_1");
        assertNull(service.createMessage(request));
        verifyNoInteractions(userMessageMapper);
    }

    @Test
    void createMessage_toJsonFailureReturnsNullExtra() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(anyMap())).thenThrow(new JsonProcessingException("fail") {});
        UserMessageService failingService = new UserMessageService(userMessageMapper, userMapper, failingMapper);
        stubNotifyEnabled("u_1");
        CreateMessageRequest request = new CreateMessageRequest(
                "u_1", "consult", "completed", "Title", null, "biz_1",
                "/path", Map.of("q", 1), Map.of("e", 2));
        when(userMessageMapper.findByUserTypeBiz("u_1", "consult", "biz_1")).thenReturn(null);
        when(userMessageMapper.findById(anyString())).thenReturn(message("msg_1", "u_1"));

        failingService.createMessage(request);

        ArgumentCaptor<UserMessage> captor = ArgumentCaptor.forClass(UserMessage.class);
        verify(userMessageMapper).insert(captor.capture());
        assertNull(captor.getValue().getLinkQuery());
        assertNull(captor.getValue().getExtra());
    }

    @Test
    void isMessageNotifyEnabled_defaultsWhenUserMissing() {
        when(userMapper.findById("u_x")).thenReturn(null);
        assertTrue(service.isMessageNotifyEnabled("u_x"));
    }

    @Test
    void isMessageNotifyEnabled_defaultsWhenPrivacyBlank() {
        User user = new User();
        user.setPrivacySettings("  ");
        when(userMapper.findById("u_1")).thenReturn(user);
        assertTrue(service.isMessageNotifyEnabled("u_1"));
    }

    @Test
    void isMessageNotifyEnabled_defaultsWhenPrivacyNull() {
        User user = new User();
        user.setUserId("u_1");
        user.setPrivacySettings(null);
        when(userMapper.findById("u_1")).thenReturn(user);
        assertTrue(service.isMessageNotifyEnabled("u_1"));
    }

    @Test
    void isMessageNotifyEnabled_respectsMessageNotifyFlag() {
        User user = new User();
        user.setPrivacySettings("{\"message_notify\":false}");
        when(userMapper.findById("u_1")).thenReturn(user);
        assertFalse(service.isMessageNotifyEnabled("u_1"));

        user.setPrivacySettings("{\"message_notify\":true}");
        assertTrue(service.isMessageNotifyEnabled("u_1"));
    }

    @Test
    void isMessageNotifyEnabled_fallsBackToConsultNotify() {
        User user = new User();
        user.setPrivacySettings("{\"consult_notify\":false}");
        when(userMapper.findById("u_1")).thenReturn(user);
        assertFalse(service.isMessageNotifyEnabled("u_1"));

        user.setPrivacySettings("{\"consult_notify\":true}");
        assertTrue(service.isMessageNotifyEnabled("u_1"));
    }

    @Test
    void isMessageNotifyEnabled_defaultsOnInvalidJson() {
        User user = new User();
        user.setPrivacySettings("{bad");
        when(userMapper.findById("u_1")).thenReturn(user);
        assertTrue(service.isMessageNotifyEnabled("u_1"));
    }

    @Test
    void isMessageNotifyEnabled_defaultsWhenNoKnownKeys() {
        User user = new User();
        user.setPrivacySettings("{\"other\":true}");
        when(userMapper.findById("u_1")).thenReturn(user);
        assertTrue(service.isMessageNotifyEnabled("u_1"));
    }

    @Test
    void createMessage_routesHealthAlert() {
        stubHealthAlertEnabled("u_1");
        CreateMessageRequest request = new CreateMessageRequest(
                "u_1", "health_alert", "completed", "健康提醒", "Summary", "ivp_1",
                "/user-center", Map.of("section", "health-alert"), Map.of("severity", "warning"));
        when(userMessageMapper.findByUserTypeBiz("u_1", "health_alert", "ivp_1")).thenReturn(null);
        when(userMessageMapper.findById(anyString())).thenReturn(message("msg_1", "u_1"));

        UserMessageResponse created = service.createMessage(request);

        assertNotNull(created);
        verify(userMessageMapper).insert(any(UserMessage.class));
    }

    @Test
    void createHealthAlertMessage_updatesExisting() {
        stubHealthAlertEnabled("u_1");
        CreateMessageRequest request = request("u_1", "health_alert", "completed", "ivp_1");
        UserMessage existing = message("msg_old", "u_1");
        when(userMessageMapper.findByUserTypeBiz("u_1", "health_alert", "ivp_1")).thenReturn(existing);
        when(userMessageMapper.findById("msg_old")).thenReturn(existing);

        UserMessageResponse result = service.createHealthAlertMessage(request);

        assertNotNull(result);
        verify(userMessageMapper).updateByBiz(any(UserMessage.class));
        verify(userMessageMapper, never()).insert(any());
    }

    @Test
    void createHealthAlertMessage_returnsNullWhenNotifyDisabled() {
        User user = new User();
        user.setPrivacySettings("{\"health_alert_notify\":false}");
        when(userMapper.findById("u_1")).thenReturn(user);

        CreateMessageRequest request = request("u_1", "health_alert", "completed", "ivp_1");
        assertNull(service.createHealthAlertMessage(request));
        verifyNoInteractions(userMessageMapper);
    }

    @Test
    void isHealthAlertNotifyEnabled_fallsBackToMessageAndConsultNotify() {
        User user = new User();
        user.setPrivacySettings("{\"message_notify\":false}");
        when(userMapper.findById("u_1")).thenReturn(user);
        assertFalse(service.isHealthAlertNotifyEnabled("u_1"));

        user.setPrivacySettings("{\"consult_notify\":false}");
        assertFalse(service.isHealthAlertNotifyEnabled("u_1"));

        user.setPrivacySettings("{\"consult_notify\":true}");
        assertTrue(service.isHealthAlertNotifyEnabled("u_1"));
    }

    @Test
    void isHealthAlertNotifyEnabled_defaultsOnInvalidJson() {
        User user = new User();
        user.setPrivacySettings("{bad");
        when(userMapper.findById("u_1")).thenReturn(user);
        assertTrue(service.isHealthAlertNotifyEnabled("u_1"));
    }

    @Test
    void isHealthAlertNotifyEnabled_defaultsWhenNoKnownKeys() {
        User user = new User();
        user.setPrivacySettings("{\"other\":true}");
        when(userMapper.findById("u_1")).thenReturn(user);
        assertTrue(service.isHealthAlertNotifyEnabled("u_1"));
    }

    @Test
    void isHealthAlertNotifyEnabled_respectsExplicitHealthAlertFlag() {
        User user = new User();
        user.setPrivacySettings("{\"health_alert_notify\":true}");
        when(userMapper.findById("u_1")).thenReturn(user);
        assertTrue(service.isHealthAlertNotifyEnabled("u_1"));
    }

    @Test
    void isHealthAlertNotifyEnabled_defaultsWhenUserMissing() {
        when(userMapper.findById("u_x")).thenReturn(null);
        assertTrue(service.isHealthAlertNotifyEnabled("u_x"));
    }

    @Test
    void isHealthAlertNotifyEnabled_defaultsWhenPrivacyBlank() {
        User user = new User();
        user.setPrivacySettings("  ");
        when(userMapper.findById("u_1")).thenReturn(user);
        assertTrue(service.isHealthAlertNotifyEnabled("u_1"));
    }

    @Test
    void isHealthAlertNotifyEnabled_defaultsWhenPrivacyNull() {
        User user = new User();
        user.setPrivacySettings(null);
        when(userMapper.findById("u_1")).thenReturn(user);
        assertTrue(service.isHealthAlertNotifyEnabled("u_1"));
    }

    @Test
    void isHealthAlertNotifyEnabled_usesMessageNotifyWhenHealthAlertMissing() {
        User user = new User();
        user.setPrivacySettings("{\"message_notify\":true}");
        when(userMapper.findById("u_1")).thenReturn(user);
        assertTrue(service.isHealthAlertNotifyEnabled("u_1"));
    }

    @Test
    void createHealthAlertMessage_generatesBizIdWhenMissing() {
        stubHealthAlertEnabled("u_1");
        CreateMessageRequest request = new CreateMessageRequest(
                "u_1", "health_alert", "completed", "Title", "Summary", null,
                "/user-center", null, null);
        when(userMessageMapper.findByUserTypeBiz(eq("u_1"), eq("health_alert"), anyString())).thenReturn(null);
        when(userMessageMapper.findById(anyString())).thenReturn(message("msg_1", "u_1"));

        service.createHealthAlertMessage(request);

        ArgumentCaptor<UserMessage> captor = ArgumentCaptor.forClass(UserMessage.class);
        verify(userMessageMapper).insert(captor.capture());
        assertTrue(captor.getValue().getBizId().startsWith("biz_"));
    }

    private void stubHealthAlertEnabled(String userId) {
        when(userMapper.findById(userId)).thenReturn(null);
    }

    private void stubNotifyEnabled(String userId) {
        when(userMapper.findById(userId)).thenReturn(null);
    }

    private static CreateMessageRequest request(String userId, String type, String status, String bizId) {
        return new CreateMessageRequest(
                userId, type, status, "Title", "Summary", bizId,
                "/consult/detail", Map.of("id", bizId), Map.of("source", "test"));
    }

    private static UserMessage message(String messageId, String userId) {
        UserMessage message = new UserMessage();
        message.setMessageId(messageId);
        message.setUserId(userId);
        message.setMessageType("consult");
        message.setStatus("completed");
        message.setTitle("Title");
        message.setSummary("Summary");
        message.setBizId("biz_1");
        message.setLinkPath("/path");
        message.setRead(false);
        message.setCreatedAt(LocalDateTime.of(2024, 1, 1, 0, 0));
        message.setUpdatedAt(LocalDateTime.of(2024, 1, 2, 0, 0));
        return message;
    }
}
