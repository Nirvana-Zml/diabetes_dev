package com.diabetes.home.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.home.service.HomeContentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v2/home")
public class HomePageV2Controller {

    private final HomeContentService homeContentService;

    public HomePageV2Controller(HomeContentService homeContentService) {
        this.homeContentService = homeContentService;
    }

    @GetMapping("/content")
    public ApiResponse<Map<String, Object>> getHomeContent() {
        return ApiResponse.ok(homeContentService.getHomeContent());
    }
}
