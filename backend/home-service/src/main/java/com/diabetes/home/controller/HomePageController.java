package com.diabetes.home.controller;

import com.diabetes.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

/**
 * 科普展示首页模块 — 占位实现，待后续接入 MinIO / Milvus / Dify。
 */
@RestController
@RequestMapping("/api/v1")
public class HomePageController {

    @GetMapping("/home/content")
    public ApiResponse<Map<String, Object>> getHomeContent() {
        return ApiResponse.fail(501, "科普首页模块尚未实现");
    }

    @GetMapping("/home/recommend")
    public ApiResponse<Map<String, Object>> getRecommend(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(Map.of(
                "articles", Collections.emptyList(),
                "total", 0,
                "placeholder", true,
                "message", "科普推荐模块尚未实现"
        ));
    }
}
