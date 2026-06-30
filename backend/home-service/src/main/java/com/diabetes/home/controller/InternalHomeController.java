package com.diabetes.home.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.home.knowledge.DocumentChunk;
import com.diabetes.home.knowledge.KnowledgeRetrieval;
import com.diabetes.home.service.HomeContentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 供其他微服务内部调用的科普首页接口。
 */
@RestController
@RequestMapping("/api/v1/internal/home")
public class InternalHomeController {

    private final HomeContentService homeContentService;
    private final KnowledgeRetrieval knowledgeRetrieval;

    public InternalHomeController(HomeContentService homeContentService,
                                  KnowledgeRetrieval knowledgeRetrieval) {
        this.homeContentService = homeContentService;
        this.knowledgeRetrieval = knowledgeRetrieval;
    }

    @GetMapping("/content")
    public ApiResponse<Map<String, Object>> content() {
        return ApiResponse.ok(homeContentService.getHomeContent());
    }

    /** 供 consultation-service 等微服务检索医学知识上下文 */
    @GetMapping("/knowledge/search")
    public ApiResponse<Map<String, Object>> knowledgeSearch(@RequestParam String query,
                                                            @RequestParam(defaultValue = "5") int topK) {
        List<DocumentChunk> chunks = knowledgeRetrieval.semanticSearch(query, topK);
        String context = knowledgeRetrieval.buildKnowledgeContext(chunks);
        return ApiResponse.ok(Map.of(
                "knowledgeContext", context,
                "sources", knowledgeRetrieval.extractSources(chunks),
                "count", chunks.size()
        ));
    }
}
