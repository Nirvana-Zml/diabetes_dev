package com.diabetes.user.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.user.config.JwtAuthInterceptor;
import com.diabetes.user.dto.UserMessageListResponse;
import com.diabetes.user.service.UserMessageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/user/messages")
public class UserMessageController {

    private final UserMessageService userMessageService;

    public UserMessageController(UserMessageService userMessageService) {
        this.userMessageService = userMessageService;
    }

    @GetMapping
    public ApiResponse<UserMessageListResponse> list(HttpServletRequest request,
                                                     @RequestParam(defaultValue = "false") boolean unreadOnly,
                                                     @RequestParam(defaultValue = "20") int limit,
                                                     @RequestParam(defaultValue = "0") int offset) {
        String userId = currentUserId(request);
        return ApiResponse.ok(userMessageService.listMessages(userId, unreadOnly, limit, offset));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Object>> unreadCount(HttpServletRequest request) {
        String userId = currentUserId(request);
        return ApiResponse.ok(userMessageService.unreadCount(userId));
    }

    @PostMapping("/{messageId}/read")
    public ApiResponse<Void> markRead(HttpServletRequest request, @PathVariable String messageId) {
        userMessageService.markRead(currentUserId(request), messageId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> markAllRead(HttpServletRequest request) {
        userMessageService.markAllRead(currentUserId(request));
        return ApiResponse.ok(null);
    }

    @PostMapping("/read-by-biz")
    public ApiResponse<Void> markReadByBiz(HttpServletRequest request,
                                           @RequestBody Map<String, String> body) {
        String userId = currentUserId(request);
        userMessageService.markReadByBiz(userId, body.get("messageType"), body.get("bizId"));
        return ApiResponse.ok(null);
    }

    private String currentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID);
        if (userId == null || userId.toString().isBlank()) {
            throw new com.diabetes.common.exception.BusinessException(401, "未登录或 Token 无效");
        }
        return userId.toString();
    }
}
