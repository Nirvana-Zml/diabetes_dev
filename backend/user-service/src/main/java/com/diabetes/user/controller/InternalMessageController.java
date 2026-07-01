package com.diabetes.user.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.user.dto.CreateMessageRequest;
import com.diabetes.user.dto.UserMessageResponse;
import com.diabetes.user.service.UserMessageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/messages")
public class InternalMessageController {

    private final UserMessageService userMessageService;

    public InternalMessageController(UserMessageService userMessageService) {
        this.userMessageService = userMessageService;
    }

    @PostMapping
    public ApiResponse<UserMessageResponse> create(@Valid @RequestBody CreateMessageRequest request) {
        UserMessageResponse created = userMessageService.createMessage(request);
        if (created == null) {
            return ApiResponse.ok("消息通知已关闭", null);
        }
        return ApiResponse.ok(created);
    }
}
