package com.diabetes.article.controller;

import com.diabetes.article.config.OptionalJwtInterceptor;
import com.diabetes.common.api.ApiResponse;
import com.diabetes.common.auth.CurrentUserId;
import com.diabetes.article.service.ArticleService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/articles")
public class ArticleController {

    private final ArticleService articleService;

    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @GetMapping("/recommend/dify-workflow-spec")
    public ApiResponse<Map<String, Object>> difyRecommendWorkflowSpec() {
        return ApiResponse.ok(articleService.getDifyRecommendWorkflowSpec());
    }

    @GetMapping("/recommend")
    public ApiResponse<Map<String, Object>> recommend(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size,
                                                    HttpServletRequest request) {
        String userId = (String) request.getAttribute(OptionalJwtInterceptor.ATTR_USER_ID);
        return ApiResponse.ok(articleService.recommend(userId, page, size));
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) Integer category,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(articleService.list(category, page, size));
    }

    @GetMapping("/search")
    public ApiResponse<Map<String, Object>> search(@RequestParam String keyword,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(articleService.search(keyword, page, size));
    }

    @GetMapping("/{articleId}/related")
    public ApiResponse<Map<String, Object>> related(@PathVariable String articleId,
                                                    @RequestParam(defaultValue = "5") int size,
                                                    HttpServletRequest request) {
        String userId = (String) request.getAttribute(OptionalJwtInterceptor.ATTR_USER_ID);
        return ApiResponse.ok(articleService.related(articleId, userId, size));
    }

    @PostMapping("/{articleId}/read-event")
    public ApiResponse<Map<String, Object>> readEvent(@CurrentUserId String userId,
                                                    @PathVariable String articleId,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        Integer duration = null;
        String source = "detail";
        if (body != null) {
            Object d = body.get("durationSec");
            if (d == null) {
                d = body.get("duration_sec");
            }
            if (d instanceof Number n) {
                duration = n.intValue();
            }
            Object s = body.get("source");
            if (s != null) {
                source = s.toString();
            }
        }
        articleService.recordRead(userId, articleId, duration, source);
        return ApiResponse.ok(Map.of("recorded", true));
    }

    @GetMapping("/{articleId}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String articleId,
                                                  HttpServletRequest request) {
        String userId = (String) request.getAttribute(OptionalJwtInterceptor.ATTR_USER_ID);
        return ApiResponse.ok(articleService.detail(articleId, userId));
    }

    @PostMapping("/{articleId}/favorite")
    public ApiResponse<Map<String, Object>> favorite(@CurrentUserId String userId,
                                                   @PathVariable String articleId) {
        return ApiResponse.ok(articleService.toggleFavorite(userId, articleId));
    }

    @GetMapping("/favorites")
    public ApiResponse<Map<String, Object>> favorites(@CurrentUserId String userId,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(articleService.favorites(userId, page, size));
    }
}
