package com.diabetes.article.service;

import com.diabetes.article.dify.DifyArticleDraftWorkflowContract;
import com.diabetes.article.entity.Article;
import com.diabetes.article.mapper.ArticleMapper;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.redis.RedisKeys;
import com.diabetes.common.storage.MinioStorageService;
import com.diabetes.common.util.IdGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ArticleService {

    private static final Duration LIST_CACHE_TTL = Duration.ofMinutes(5);
    private static final long MAX_COVER_BYTES = 5 * 1024 * 1024;

    private final ArticleMapper articleMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ArticleRecommendService articleRecommendService;
    private final ArticleVectorSyncService articleVectorSyncService;
    private final MinioStorageService minioStorageService;
    private final String difyBaseUrl;
    private final String difyWorkflowUrl;
    private final String difyApiKey;

    public ArticleService(ArticleMapper articleMapper,
                          StringRedisTemplate redisTemplate,
                          ObjectMapper objectMapper,
                          MinioStorageService minioStorageService,
                          ArticleRecommendService articleRecommendService,
                          ArticleVectorSyncService articleVectorSyncService,
                          @Value("${dify.base-url:http://localhost}") String difyBaseUrl,
                          @Value("${dify.workflows.article-draft.workflow-url:}") String difyWorkflowUrl,
                          @Value("${dify.workflows.article-draft.api-key:}") String difyApiKey) {
        this.articleMapper = articleMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.minioStorageService = minioStorageService;
        this.articleRecommendService = articleRecommendService;
        this.articleVectorSyncService = articleVectorSyncService;
        this.difyBaseUrl = difyBaseUrl;
        this.difyWorkflowUrl = difyWorkflowUrl;
        this.difyApiKey = difyApiKey;
    }

    public Map<String, Object> recommend(String userId, int page, int size, String strategy) {
        if (strategy != null && "popular".equalsIgnoreCase(strategy.trim())) {
            return articleRecommendService.popularRecommend(page, size);
        }
        return articleRecommendService.recommend(userId, page, size);
    }

    public Map<String, Object> getDifyRecommendWorkflowSpec() {
        return articleRecommendService.getDifyWorkflowSpec();
    }

    public Map<String, Object> related(String articleId, String userId, int size) {
        return articleRecommendService.related(articleId, userId, size);
    }

    public void recordRead(String userId, String articleId, Integer durationSec, String source) {
        articleRecommendService.recordRead(userId, articleId, durationSec, source);
    }

    public Map<String, Object> list(Integer category, int page, int size) {
        return getCachedList(RedisKeys.articleList(category, page, size), category, page, size);
    }

    public Map<String, Object> search(String keyword, int page, int size) {
        if (keyword == null || keyword.isBlank()) {
            throw new BusinessException(400, "关键字不能为空");
        }
        int offset = (page - 1) * size;
        List<Article> articles = articleMapper.searchPublished(keyword.trim(), offset, size);
        return Map.of(
                "articles", articles.stream().map(this::toCard).toList(),
                "total", articleMapper.countSearch(keyword.trim())
        );
    }

    public Map<String, Object> detail(String articleId, String userId) {
        Article article = articleMapper.findById(articleId);
        if (article == null || article.getStatus() == null || article.getStatus() != 3) {
            throw new BusinessException(404, "资讯不存在");
        }
        articleMapper.incrementViewCount(articleId);
        invalidateListCache();
        Article refreshed = articleMapper.findById(articleId);
        Map<String, Object> map = toDetail(refreshed != null ? refreshed : article);
        map.put("tags", articleMapper.findTagsByArticleId(articleId));
        if (userId != null) {
            Integer fav = articleMapper.findFavoriteStatus(userId, articleId);
            map.put("favorited", fav != null && fav == 1);
        } else {
            map.put("favorited", false);
        }
        return map;
    }

    @Transactional
    public Map<String, Object> toggleFavorite(String userId, String articleId) {
        Article article = articleMapper.findById(articleId);
        if (article == null || article.getStatus() == null || article.getStatus() != 3) {
            throw new BusinessException(404, "资讯不存在或未发布");
        }
        Integer current = articleMapper.findFavoriteStatus(userId, articleId);
        boolean favorited = current == null || current == 0;
        articleMapper.upsertFavorite(IdGenerator.nextId("fav_"), userId, articleId, favorited ? 1 : 0);
        articleMapper.adjustFavoriteCount(articleId, favorited ? 1 : -1);
        articleRecommendService.invalidateUserRecommendCache(userId);
        return Map.of("favorited", favorited);
    }

    public Map<String, Object> favorites(String userId, int page, int size) {
        int offset = (page - 1) * size;
        List<Article> articles = articleMapper.findFavorites(userId, offset, size);
        return Map.of(
                "articles", articles.stream().map(this::toCard).toList(),
                "total", articleMapper.countFavorites(userId)
        );
    }

    public Map<String, Object> adminList(Integer status, String keyword, int page, int size) {
        int offset = (page - 1) * size;
        List<Article> articles = articleMapper.findAdminList(status, blankToNull(keyword), offset, size);
        return Map.of(
                "articles", articles.stream().map(this::toAdminCard).toList(),
                "total", articleMapper.countAdminList(status, blankToNull(keyword))
        );
    }

    public Map<String, Object> adminDetail(String articleId) {
        Article article = articleMapper.findById(articleId);
        if (article == null) {
            throw new BusinessException(404, "资讯不存在");
        }
        return toAdminDetail(article, articleMapper.findTagsByArticleId(articleId));
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        Article article = fromBody(body);
        article.setArticleId(IdGenerator.nextId("art_"));
        article.setStatus(1);
        articleMapper.insert(article);
        saveTags(article.getArticleId(), parseTags(body));
        return Map.of("articleId", article.getArticleId(), "status", "draft");
    }

    @Transactional
    public Map<String, Object> update(String articleId, Map<String, Object> body) {
        Article existing = articleMapper.findById(articleId);
        if (existing == null) {
            throw new BusinessException(404, "资讯不存在");
        }
        Article article = fromBody(body);
        article.setArticleId(articleId);
        article.setCoverImageId(existing.getCoverImageId());
        articleMapper.update(article);
        saveTags(articleId, parseTags(body));
        invalidateListCache();
        minioStorageService.deleteArticleAudio(articleId);
        if (existing.getStatus() != null && existing.getStatus() == 3) {
            articleVectorSyncService.syncArticle(articleId);
        }
        return Map.of("articleId", articleId, "status", statusName(existing.getStatus()));
    }

    @Transactional
    public void delete(String articleId) {
        if (articleMapper.findById(articleId) == null) {
            throw new BusinessException(404, "资讯不存在");
        }
        articleMapper.softDelete(articleId);
        articleVectorSyncService.removeArticle(articleId);
        invalidateListCache();
    }

    @Transactional
    public Map<String, Object> submitReview(String articleId) {
        Article article = articleMapper.findById(articleId);
        if (article == null) {
            throw new BusinessException(404, "资讯不存在");
        }
        if (article.getStatus() == null || (article.getStatus() != 1 && article.getStatus() != 4)) {
            throw new BusinessException(400, "仅草稿或已驳回的资讯可提交审核");
        }
        articleMapper.updateStatus(articleId, 2);
        return Map.of("articleId", articleId, "status", "pending");
    }

    @Transactional
    public Map<String, Object> review(String articleId, String action, String reason) {
        Article article = articleMapper.findById(articleId);
        if (article == null) {
            throw new BusinessException(404, "资讯不存在");
        }
        if (article.getStatus() == null || article.getStatus() != 2) {
            throw new BusinessException(400, "仅待审核资讯可执行审核操作");
        }
        if ("approve".equals(action)) {
            articleMapper.updateReview(articleId, 3, null);
            invalidateListCache();
            articleVectorSyncService.syncArticle(articleId);
            return Map.of("articleId", articleId, "status", "published");
        } else if ("reject".equals(action)) {
            if (reason == null || reason.isBlank()) {
                throw new BusinessException(400, "驳回原因必填");
            }
            articleMapper.updateReview(articleId, 4, reason);
            return Map.of("articleId", articleId, "status", "rejected");
        } else {
            throw new BusinessException(400, "审核动作无效");
        }
    }

    public Map<String, Object> pendingReview(int page, int size) {
        int offset = (page - 1) * size;
        List<Article> articles = articleMapper.findPendingReview(offset, size);
        return Map.of(
                "articles", articles.stream().map(this::toAdminCard).toList(),
                "total", articleMapper.countPendingReview()
        );
    }

    public Map<String, Object> aiDraftConfig() {
        return DifyArticleDraftWorkflowContract.workflowConfig(difyBaseUrl, difyApiKey, difyWorkflowUrl);
    }

    @Transactional
    public Map<String, Object> uploadCover(String articleId, MultipartFile file) {
        if (articleMapper.findById(articleId) == null) {
            throw new BusinessException(404, "资讯不存在");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择封面图片");
        }
        if (file.getSize() > MAX_COVER_BYTES) {
            throw new BusinessException(400, "图片大小不能超过 5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new BusinessException(400, "仅支持图片格式（JPG、PNG、WebP、GIF 等）");
        }
        try {
            minioStorageService.uploadArticleCover(
                    articleId, file.getInputStream(), file.getSize(), contentType);
            String coverImageId = articleId + ".jpg";
            articleMapper.updateCoverImageId(articleId, coverImageId);
            invalidateListCache();
            String coverUrl = minioStorageService.buildArticleCoverUrl(articleId);
            return Map.of("coverImage", coverUrl + "?v=" + System.currentTimeMillis());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "封面图上传失败: " + e.getMessage());
        }
    }

    private Map<String, Object> getCachedList(String cacheKey, Integer category, int page, int size) {
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<>() {});
            }
        } catch (Exception ignored) {
        }

        Map<String, Object> result = loadList(category, page, size);
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result), LIST_CACHE_TTL);
        } catch (Exception ignored) {
        }
        return result;
    }

    private Map<String, Object> loadList(Integer category, int page, int size) {
        int offset = (page - 1) * size;
        List<Article> articles = articleMapper.findPublished(category, offset, size);
        int total = articleMapper.countPublished(category);
        return Map.of("articles", articles.stream().map(this::toCard).toList(), "total", total);
    }

    private void invalidateListCache() {
        try {
            Set<String> keys = redisTemplate.keys(RedisKeys.articleRecommendPattern());
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            keys = redisTemplate.keys(RedisKeys.articleListPattern());
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            keys = redisTemplate.keys(RedisKeys.articleRecPopularPattern());
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception ignored) {
        }
    }

    private Article fromBody(Map<String, Object> body) {
        Article article = new Article();
        article.setTitle(stringVal(body, "title"));
        article.setContent(stringVal(body, "content"));
        article.setSummary(stringVal(body, "summary"));
        Object category = body.get("category");
        if (category == null) {
            category = body.get("category_id");
        }
        if (category instanceof Number n) {
            article.setCategory(n.intValue());
        } else if (category != null) {
            article.setCategory(mapCategory(category.toString()));
        } else {
            article.setCategory(1);
        }
        return article;
    }

    private String stringVal(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private int mapCategory(String category) {
        return switch (category) {
            case "diet", "饮食", "饮食管理", "2" -> 2;
            case "exercise", "运动", "运动康复", "3" -> 3;
            case "medication", "用药", "用药指导", "4" -> 4;
            case "complications", "并发症", "5" -> 5;
            case "diabetes_basics", "糖尿病基础", "1" -> 1;
            default -> {
                try {
                    yield Integer.parseInt(category);
                } catch (NumberFormatException e) {
                    yield 1;
                }
            }
        };
    }

    private Map<String, Object> toCard(Article article) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("articleId", article.getArticleId());
        map.put("title", article.getTitle());
        map.put("summary", article.getSummary());
        map.put("coverImage", resolveCoverImage(article));
        map.put("category", article.getCategory());
        map.put("viewCount", article.getViewCount() != null ? article.getViewCount() : 0);
        map.put("publishedAt", article.getPublishedAt());
        return map;
    }

    private Map<String, Object> toDetail(Article article) {
        Map<String, Object> map = new LinkedHashMap<>(toCard(article));
        map.put("content", article.getContent());
        map.put("status", statusName(article.getStatus()));
        return map;
    }

    private Map<String, Object> toAdminCard(Article article) {
        Map<String, Object> map = new LinkedHashMap<>(toCard(article));
        map.put("status", statusName(article.getStatus()));
        map.put("rejectReason", article.getRejectReason());
        map.put("createdAt", article.getCreatedAt());
        map.put("updatedAt", article.getUpdatedAt());
        return map;
    }

    private Map<String, Object> toAdminDetail(Article article, List<String> tags) {
        Map<String, Object> map = new LinkedHashMap<>(toAdminCard(article));
        map.put("content", article.getContent());
        map.put("tags", tags != null ? tags : List.of());
        return map;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseTags(Map<String, Object> body) {
        Object tags = body.get("tags");
        if (!(tags instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item == null) continue;
            String name = item.toString().trim();
            if (!name.isEmpty() && !result.contains(name)) {
                result.add(name);
            }
        }
        return result;
    }

    private void saveTags(String articleId, List<String> tags) {
        articleMapper.softDeleteTagsByArticleId(articleId);
        for (String tag : tags) {
            articleMapper.insertTag(IdGenerator.nextId("tag_"), articleId, tag);
        }
    }

    private String statusName(Integer status) {
        if (status == null) return "draft";
        return switch (status) {
            case 2 -> "pending";
            case 3 -> "published";
            case 4 -> "rejected";
            default -> "draft";
        };
    }

    private String resolveCoverImage(Article article) {
        return resolveCoverImageUrl(article.getCoverImageId(), article.getArticleId());
    }

    private String resolveCoverImageUrl(String coverImageId, String articleId) {
        if (coverImageId != null && !coverImageId.isBlank()) {
            String id = coverImageId.trim();
            if (id.startsWith("http://") || id.startsWith("https://")) {
                return id;
            }
        }
        return minioStorageService.buildArticleCoverUrl(articleId);
    }
}
