package com.diabetes.user.service;

import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.util.IdGenerator;
import com.diabetes.user.dto.CreateMessageRequest;
import com.diabetes.user.dto.UserMessageListResponse;
import com.diabetes.user.dto.UserMessageResponse;
import com.diabetes.user.entity.User;
import com.diabetes.user.entity.UserMessage;
import com.diabetes.user.mapper.UserMapper;
import com.diabetes.user.mapper.UserMessageMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserMessageService {

    private static final int SUMMARY_MAX = 500;

    private final UserMessageMapper userMessageMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public UserMessageService(UserMessageMapper userMessageMapper,
                              UserMapper userMapper,
                              ObjectMapper objectMapper) {
        this.userMessageMapper = userMessageMapper;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    public UserMessageListResponse listMessages(String userId, boolean unreadOnly, int limit, int offset) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        int safeOffset = Math.max(offset, 0);
        List<UserMessage> rows = userMessageMapper.listByUser(userId, unreadOnly, safeLimit, safeOffset);
        int total = userMessageMapper.countByUser(userId, unreadOnly);
        int unreadCount = userMessageMapper.countUnread(userId);
        List<UserMessageResponse> list = rows.stream().map(this::toResponse).toList();
        return new UserMessageListResponse(total, unreadCount, list);
    }

    public Map<String, Object> unreadCount(String userId) {
        return Map.of("unread_count", userMessageMapper.countUnread(userId));
    }

    @Transactional
    public void markRead(String userId, String messageId) {
        UserMessage message = userMessageMapper.findById(messageId);
        if (message == null || !userId.equals(message.getUserId())) {
            throw new BusinessException(404, "消息不存在");
        }
        userMessageMapper.markRead(userId, messageId);
    }

    @Transactional
    public void markAllRead(String userId) {
        userMessageMapper.markAllRead(userId);
    }

    @Transactional
    public void markReadByBiz(String userId, String messageType, String bizId) {
        if (messageType == null || messageType.isBlank() || bizId == null || bizId.isBlank()) {
            return;
        }
        userMessageMapper.markReadByBiz(userId, messageType, bizId);
    }

    @Transactional
    public UserMessageResponse createMessage(CreateMessageRequest request) {
        if (!isMessageNotifyEnabled(request.userId())) {
            return null;
        }
        String bizId = request.bizId() == null ? IdGenerator.nextId("biz_") : request.bizId();
        UserMessage existing = userMessageMapper.findByUserTypeBiz(
                request.userId(), request.messageType(), bizId);
        UserMessage entity = buildEntity(request, bizId);
        if (existing != null) {
            entity.setMessageId(existing.getMessageId());
            userMessageMapper.updateByBiz(entity);
            UserMessage updated = userMessageMapper.findById(existing.getMessageId());
            return toResponse(updated);
        }
        entity.setMessageId(IdGenerator.nextId("msg_"));
        userMessageMapper.insert(entity);
        return toResponse(userMessageMapper.findById(entity.getMessageId()));
    }

    public boolean isMessageNotifyEnabled(String userId) {
        User user = userMapper.findById(userId);
        if (user == null || user.getPrivacySettings() == null || user.getPrivacySettings().isBlank()) {
            return true;
        }
        try {
            Map<String, Object> settings = objectMapper.readValue(
                    user.getPrivacySettings(), new TypeReference<>() {});
            if (settings.containsKey("message_notify")) {
                return !Boolean.FALSE.equals(settings.get("message_notify"));
            }
            if (settings.containsKey("consult_notify")) {
                return !Boolean.FALSE.equals(settings.get("consult_notify"));
            }
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    private UserMessage buildEntity(CreateMessageRequest request, String bizId) {
        UserMessage entity = new UserMessage();
        entity.setUserId(request.userId());
        entity.setMessageType(request.messageType());
        entity.setStatus(normalizeStatus(request.status()));
        entity.setTitle(truncate(request.title(), 100));
        entity.setSummary(truncate(request.summary(), SUMMARY_MAX));
        entity.setBizId(bizId);
        entity.setLinkPath(request.linkPath());
        entity.setLinkQuery(toJson(request.linkQuery()));
        entity.setExtra(toJson(request.extra()));
        return entity;
    }

    private UserMessageResponse toResponse(UserMessage message) {
        if (message == null) {
            return null;
        }
        return new UserMessageResponse(
                message.getMessageId(),
                message.getMessageType(),
                message.getStatus(),
                message.getTitle(),
                message.getSummary(),
                message.getBizId(),
                message.getLinkPath(),
                parseJsonMap(message.getLinkQuery()),
                parseJsonMap(message.getExtra()),
                Boolean.TRUE.equals(message.getRead()),
                message.getCreatedAt(),
                message.getUpdatedAt()
        );
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeStatus(String status) {
        if ("failed".equalsIgnoreCase(status)) {
            return "failed";
        }
        return "completed";
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
