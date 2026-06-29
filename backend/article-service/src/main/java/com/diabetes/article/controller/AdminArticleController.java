package com.diabetes.article.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.article.service.ArticleService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/articles")
public class AdminArticleController {

    private final ArticleService articleService;

    public AdminArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) Integer status,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(articleService.adminList(status, keyword, page, size));
    }

    @GetMapping("/{articleId}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String articleId) {
        return ApiResponse.ok(articleService.adminDetail(articleId));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(articleService.create(body));
    }

    @PutMapping("/{articleId}")
    public ApiResponse<Map<String, Object>> update(@PathVariable String articleId,
                                                 @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(articleService.update(articleId, body));
    }

    /** 上传封面图（MinIO article bucket，对象名 {articleId}.jpg） */
    @PostMapping(value = "/{articleId}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> uploadCover(@PathVariable String articleId,
                                                        @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(articleService.uploadCover(articleId, file));
    }

    @DeleteMapping("/{articleId}")
    public ApiResponse<String> delete(@PathVariable String articleId) {
        articleService.delete(articleId);
        return ApiResponse.ok("删除成功", "删除成功");
    }

    @PutMapping("/{articleId}/submit")
    public ApiResponse<Map<String, Object>> submit(@PathVariable String articleId) {
        return ApiResponse.ok(articleService.submitReview(articleId));
    }

    @PutMapping("/{articleId}/review")
    public ApiResponse<Map<String, Object>> review(@PathVariable String articleId,
                                                 @RequestBody Map<String, String> body) {
        return ApiResponse.ok(articleService.review(articleId, body.get("action"), body.get("reason")));
    }

    @GetMapping("/pending-review")
    public ApiResponse<Map<String, Object>> pendingReview(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(articleService.pendingReview(page, size));
    }

    @GetMapping("/ai-draft/config")
    public ApiResponse<Map<String, Object>> aiDraftConfig() {
        return ApiResponse.ok(articleService.aiDraftConfig());
    }
}
