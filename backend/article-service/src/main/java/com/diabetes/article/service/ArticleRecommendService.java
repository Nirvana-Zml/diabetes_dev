package com.diabetes.article.service;

import com.diabetes.article.config.RecommendProperties;
import com.diabetes.article.entity.ArticleCandidate;
import com.diabetes.article.entity.ScoredArticle;
import com.diabetes.article.mapper.RecommendMapper;
import com.diabetes.common.client.HealthServiceClient;
import com.diabetes.article.dify.DifyArticleRecommendWorkflowContract;
import com.diabetes.common.dify.DifyClient;
import com.diabetes.common.dify.DifyJsonSchema;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.redis.RedisKeys;
import com.diabetes.common.storage.MinioStorageService;
import com.diabetes.common.util.IdGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ArticleRecommendService {

    private static final Logger log = LoggerFactory.getLogger(ArticleRecommendService.class);

    private static final double W_CATEGORY = 3.0;
    private static final double W_TAG = 2.0;
    private static final double W_PROFILE = 2.0;
    private static final double W_BEHAVIOR = 5.0;
    private static final double W_POPULARITY = 1.0;
    private static final double W_FRESHNESS = 1.0;
    private static final double W_ALREADY_READ = -10.0;
    private static final double W_COLLABORATIVE = 4.0;
    private static final double W_SEMANTIC = 3.0;

    private final RecommendMapper recommendMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MinioStorageService minioStorageService;
    private final HealthServiceClient healthServiceClient;
    private final DifyClient difyClient;
    private final RecommendProperties properties;
    private final MilvusArticleSearchService milvusArticleSearchService;
    private final String difyRecommendApiKey;
    private final String difyBaseUrl;
    private final String difyInputVarName;
    private final String difyInputFormat;
    private final String difyResponseMode;
    private final String difyInternalKey;

    public ArticleRecommendService(RecommendMapper recommendMapper,
                                   StringRedisTemplate redisTemplate,
                                   ObjectMapper objectMapper,
                                   MinioStorageService minioStorageService,
                                   HealthServiceClient healthServiceClient,
                                   DifyClient difyClient,
                                   RecommendProperties properties,
                                   MilvusArticleSearchService milvusArticleSearchService,
                                   @Value("${dify.base-url:http://localhost}") String difyBaseUrl,
                                   @Value("${dify.workflows.article-recommend.api-key:}") String difyRecommendApiKey,
                                   @Value("${dify.workflows.article-recommend.input-var-name:inputs}") String difyInputVarName,
                                   @Value("${dify.workflows.article-recommend.input-format:object}") String difyInputFormat,
                                   @Value("${dify.workflows.article-recommend.response-mode:blocking}") String difyResponseMode,
                                   @Value("${dify-internal.key:}") String difyInternalKey) {
        this.recommendMapper = recommendMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.minioStorageService = minioStorageService;
        this.healthServiceClient = healthServiceClient;
        this.difyClient = difyClient;
        this.properties = properties;
        this.milvusArticleSearchService = milvusArticleSearchService;
        this.difyRecommendApiKey = difyRecommendApiKey;
        this.difyBaseUrl = difyBaseUrl;
        this.difyInputVarName = (difyInputVarName == null || difyInputVarName.isBlank())
                ? DifyArticleRecommendWorkflowContract.INPUT_VARIABLE_NAME : difyInputVarName.trim();
        this.difyInputFormat = difyInputFormat == null ? "object" : difyInputFormat.trim().toLowerCase();
        this.difyResponseMode = difyResponseMode == null ? "blocking" : difyResponseMode.trim();
        this.difyInternalKey = difyInternalKey;
    }

    public Map<String, Object> getDifyWorkflowSpec() {
        return DifyArticleRecommendWorkflowContract.workflowSpec(
                difyBaseUrl, difyRecommendApiKey, difyInputFormat, difyResponseMode);
    }

    public Map<String, Object> recommend(String userId, int page, int size) {
        if (userId == null || userId.isBlank()) {
            return popularRecommend(page, size);
        }
        String cacheKey = RedisKeys.articleRecPersonalized(userId, page, size);
        Map<String, Object> cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<Map<String, Object>> persisted = recommendMapper.findActiveRecommendations(userId);
        List<ScoredArticle> scored;
        String strategy;
        int phase = 1;

        if (!persisted.isEmpty()) {
            scored = fromPersistedRecommendations(persisted);
            strategy = "personalized";
            phase = persisted.get(0).get("recPhase") instanceof Number n ? n.intValue() : 4;
        } else {
            RecommendContext ctx = buildContext(userId);
            scored = scoreCandidates(ctx);
            phase = applyPhase2Collaborative(scored, ctx);
            if (properties.isPhase3Enabled()) {
                int p3 = applyPhase3Semantic(scored, ctx);
                phase = Math.max(phase, p3);
            }
            if (properties.isPhase4DifyEnabled() && !difyRecommendApiKey.isBlank()) {
                int p4 = applyPhase4DifyRerank(userId, scored, ctx);
                phase = Math.max(phase, p4);
            }
            persistRecommendations(userId, scored, phase);
            strategy = "personalized";
        }

        Map<String, Object> result = paginateResult(scored, page, size, strategy, phase);
        writeCache(cacheKey, result);
        return result;
    }

    public Map<String, Object> popularRecommend(int page, int size) {
        String cacheKey = RedisKeys.articleRecPopular(page, size);
        Map<String, Object> cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }
        List<ArticleCandidate> candidates = loadCandidates();
        List<ScoredArticle> scored = candidates.stream().map(c -> {
            ScoredArticle sa = new ScoredArticle(c);
            sa.setScore(popularityScore(c) + freshnessScore(c));
            sa.setReason("热门推荐");
            sa.setPhase(1);
            return sa;
        }).sorted(Comparator.comparingDouble(ScoredArticle::getScore).reversed()).toList();

        Map<String, Object> result = paginateResult(scored, page, size, "popular", 1);
        writeCache(cacheKey, result);
        return result;
    }

    public Map<String, Object> related(String articleId, String userId, int size) {
        ArticleCandidate base = loadCandidates().stream()
                .filter(c -> articleId.equals(c.getArticleId()))
                .findFirst()
                .orElse(null);
        if (base == null) {
            throw new BusinessException(404, "资讯不存在");
        }
        List<ArticleCandidate> relatedCandidates = recommendMapper.findRelatedCandidates(
                articleId, base.getCategory(), Math.max(size * 3, 15));
        for (ArticleCandidate c : relatedCandidates) {
            enrichTags(c);
        }

        RecommendContext ctx = userId != null && !userId.isBlank() ? buildContext(userId) : emptyContext();
        List<ScoredArticle> scored = relatedCandidates.stream().map(c -> {
            ScoredArticle sa = new ScoredArticle(c);
            sa.setScore(tagOverlapScore(toSet(base.getTags()), c.getTags()) * W_TAG
                    + (Objects.equals(base.getCategory(), c.getCategory()) ? W_CATEGORY : 0)
                    + popularityScore(c) * 0.5);
            sa.setReason("相关推荐");
            return sa;
        }).sorted(Comparator.comparingDouble(ScoredArticle::getScore).reversed()).toList();

        if (userId != null && properties.isPhase2Enabled()) {
            applyPhase2Collaborative(scored, ctx);
        }

        List<Map<String, Object>> articles = scored.stream()
                .limit(size)
                .map(this::toRecommendCard)
                .toList();
        return Map.of("articles", articles, "total", articles.size(), "strategy", "related");
    }

    @Transactional
    public void recordRead(String userId, String articleId, Integer durationSec, String source) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(401, "请先登录");
        }
        recommendMapper.upsertUserRead(
                IdGenerator.nextId("read_"), userId, articleId, durationSec, blankToDefault(source, "detail"));
        invalidateUserRecommendCache(userId);
    }

    public void invalidateUserRecommendCache(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        try {
            Set<String> keys = redisTemplate.keys(RedisKeys.articleRecPersonalizedPattern(userId));
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            recommendMapper.softDeleteUserRecommendations(userId);
        } catch (Exception ignored) {
        }
    }

    private RecommendContext buildContext(String userId) {
        RecommendContext ctx = new RecommendContext(userId);
        ctx.recentReadIds = new HashSet<>(recommendMapper.findRecentReadArticleIds(userId, 7));
        ctx.favoriteIds = new HashSet<>(recommendMapper.findFavoriteArticleIds(userId));

        Map<Integer, Double> categoryWeights = new HashMap<>();
        for (Map<String, Object> row : recommendMapper.findCategoryWeightsByUser(userId, 30)) {
            int cat = ((Number) row.get("category")).intValue();
            double cnt = ((Number) row.get("cnt")).doubleValue();
            categoryWeights.merge(cat, cnt, Double::sum);
        }
        ctx.categoryWeights = categoryWeights;

        Map<String, Object> health = healthServiceClient.getLatestHealthProfile(userId, difyInternalKey);
        Map<String, Object> risk = healthServiceClient.getLatestRiskAssessment(userId, difyInternalKey);
        ctx.healthProfile = health;
        ctx.riskProfile = risk;
        ctx.profileCategoryWeights = profileCategoryWeights(health, risk);

        List<ArticleCandidate> candidates = loadCandidates();
        ctx.candidates = candidates;
        ctx.interestTags = buildInterestTags(ctx, candidates);
        ctx.interestText = buildInterestText(ctx, candidates);
        return ctx;
    }

    private RecommendContext emptyContext() {
        RecommendContext ctx = new RecommendContext(null);
        ctx.recentReadIds = Set.of();
        ctx.favoriteIds = Set.of();
        ctx.categoryWeights = Map.of();
        ctx.profileCategoryWeights = Map.of();
        ctx.candidates = loadCandidates();
        ctx.interestTags = Set.of();
        ctx.interestText = "";
        return ctx;
    }

    private List<ArticleCandidate> loadCandidates() {
        List<ArticleCandidate> list = recommendMapper.findPublishedCandidates(properties.getCandidateLimit());
        for (ArticleCandidate c : list) {
            enrichTags(c);
            ensureFingerprint(c);
        }
        return list;
    }

    private void enrichTags(ArticleCandidate c) {
        c.setTags(recommendMapper.findTagsByArticleId(c.getArticleId()));
    }

    private void ensureFingerprint(ArticleCandidate c) {
        if (c.getTextFingerprint() != null && !c.getTextFingerprint().isBlank()) {
            return;
        }
        String fp = buildFingerprint(c);
        c.setTextFingerprint(fp);
        recommendMapper.upsertEmbedding(c.getArticleId(), fp);
    }

    private String buildFingerprint(ArticleCandidate c) {
        String tags = c.getTags() == null ? "" : String.join(" ", c.getTags());
        return ((c.getTitle() == null ? "" : c.getTitle()) + " "
                + (c.getSummary() == null ? "" : c.getSummary()) + " " + tags).trim().toLowerCase();
    }

    private List<ScoredArticle> scoreCandidates(RecommendContext ctx) {
        if (!properties.isPhase1Enabled()) {
            return ctx.candidates.stream().map(ScoredArticle::new).toList();
        }
        List<ScoredArticle> result = new ArrayList<>();
        for (ArticleCandidate c : ctx.candidates) {
            ScoredArticle sa = new ScoredArticle(c);
            double score = 0;
            StringBuilder reason = new StringBuilder();

            double catW = ctx.categoryWeights.getOrDefault(c.getCategory(), 0.0);
            if (catW > 0) {
                score += Math.min(catW, 5) * W_CATEGORY / 5.0;
                reason.append("符合您的阅读偏好；");
            }
            double profileW = ctx.profileCategoryWeights.getOrDefault(c.getCategory(), 0.0);
            if (profileW > 0) {
                score += profileW * W_PROFILE;
                reason.append("匹配您的健康档案；");
            }
            double tagScore = tagOverlapScore(ctx.interestTags, c.getTags());
            if (tagScore > 0) {
                score += tagScore * W_TAG;
                reason.append("标签相似；");
            }
            if (ctx.favoriteIds.contains(c.getArticleId())) {
                score += W_BEHAVIOR;
                reason.append("您已收藏；");
            }
            score += popularityScore(c) * W_POPULARITY;
            score += freshnessScore(c) * W_FRESHNESS;
            if (ctx.recentReadIds.contains(c.getArticleId())) {
                score += W_ALREADY_READ;
            }

            sa.setScore(score);
            sa.setReason(reason.isEmpty() ? "为您精选" : reason.toString());
            sa.setPhase(1);
            result.add(sa);
        }
        result.sort(Comparator.comparingDouble(ScoredArticle::getScore).reversed());
        return result;
    }

    private int applyPhase2Collaborative(List<ScoredArticle> scored, RecommendContext ctx) {
        if (!properties.isPhase2Enabled() || ctx.userId == null) {
            return 1;
        }
        List<String> exclude = new ArrayList<>(ctx.recentReadIds);
        exclude.addAll(ctx.favoriteIds);
        Map<String, Double> boosts = new HashMap<>();

        for (Map<String, Object> row : recommendMapper.findCoReadArticles(ctx.userId, exclude, 30)) {
            String id = row.get("articleId").toString();
            double co = ((Number) row.get("coCount")).doubleValue();
            if (co >= properties.getPhase2MinCoReaders()) {
                boosts.merge(id, co * W_COLLABORATIVE, Double::sum);
            }
        }
        for (Map<String, Object> row : recommendMapper.findCoFavoriteArticles(ctx.userId, exclude, 30)) {
            String id = row.get("articleId").toString();
            double co = ((Number) row.get("coCount")).doubleValue();
            boosts.merge(id, co * W_COLLABORATIVE * 1.2, Double::sum);
        }

        if (boosts.isEmpty()) {
            return 1;
        }
        for (ScoredArticle sa : scored) {
            Double boost = boosts.get(sa.getCandidate().getArticleId());
            if (boost != null) {
                sa.addScore(boost);
                sa.setReason(sa.getReason() + "读过相似内容的用户也在看；");
                sa.setPhase(Math.max(sa.getPhase(), 2));
            }
        }
        scored.sort(Comparator.comparingDouble(ScoredArticle::getScore).reversed());
        return 2;
    }

    private int applyPhase3Semantic(List<ScoredArticle> scored, RecommendContext ctx) {
        if (milvusArticleSearchService.isAvailable()) {
            List<String> ids = scored.stream()
                    .map(s -> s.getCandidate().getArticleId())
                    .toList();
            Map<String, Double> milvusHits = milvusArticleSearchService.searchSimilar(
                    ctx.interestText, ids, 30);
            if (!milvusHits.isEmpty()) {
                for (ScoredArticle sa : scored) {
                    Double sim = milvusHits.get(sa.getCandidate().getArticleId());
                    if (sim != null && sim > 0) {
                        sa.addScore(sim * W_SEMANTIC * 10);
                        sa.setReason(sa.getReason() + "语义相关；");
                        sa.setPhase(Math.max(sa.getPhase(), 3));
                    }
                }
                scored.sort(Comparator.comparingDouble(ScoredArticle::getScore).reversed());
                return 3;
            }
        }
        if (ctx.interestText == null || ctx.interestText.isBlank()) {
            return 1;
        }
        Set<String> interestTokens = tokenize(ctx.interestText);
        for (ScoredArticle sa : scored) {
            String fp = sa.getCandidate().getTextFingerprint();
            if (fp == null || fp.isBlank()) {
                continue;
            }
            double sim = jaccardSimilarity(interestTokens, tokenize(fp));
            if (sim > 0.05) {
                sa.addScore(sim * W_SEMANTIC * 10);
                sa.setReason(sa.getReason() + "内容主题相关；");
                sa.setPhase(Math.max(sa.getPhase(), 3));
            }
        }
        scored.sort(Comparator.comparingDouble(ScoredArticle::getScore).reversed());
        return 3;
    }

    private int applyPhase4DifyRerank(String userId, List<ScoredArticle> scored, RecommendContext ctx) {
        try {
            int topN = Math.min(properties.getDifyTopN(), scored.size());
            List<ScoredArticle> top = scored.subList(0, topN);
            List<Map<String, Object>> candidates = top.stream().map(sa -> {
                ArticleCandidate c = sa.getCandidate();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("article_id", c.getArticleId());
                m.put("title", c.getTitle());
                m.put("summary", c.getSummary());
                m.put("category", c.getCategory());
                m.put("tags", c.getTags());
                m.put("score", sa.getScore());
                return m;
            }).toList();

            Map<String, Object> payload = DifyArticleRecommendWorkflowContract.buildInputObject(
                    userId,
                    new ArrayList<>(ctx.interestTags),
                    ctx.categoryWeights,
                    ctx.healthProfile,
                    ctx.riskProfile,
                    candidates);
            Map<String, Object> inputs = DifyJsonSchema.wrapWorkflowInputs(
                    difyInputVarName, payload, difyInputFormat, objectMapper);

            JsonNode response = difyClient.runWorkflowBlocking(difyRecommendApiKey, userId, inputs, difyResponseMode);
            Map<String, String> reasons = parseDifyRecommendations(response);
            if (reasons.isEmpty()) {
                return 3;
            }

            Map<String, ScoredArticle> byId = scored.stream()
                    .collect(Collectors.toMap(s -> s.getCandidate().getArticleId(), s -> s, (a, b) -> a));
            double rank = reasons.size();
            for (Map.Entry<String, String> e : reasons.entrySet()) {
                ScoredArticle sa = byId.get(e.getKey());
                if (sa != null) {
                    sa.addScore(rank * 2);
                    sa.setReason(e.getValue());
                    sa.setPhase(4);
                    rank--;
                }
            }
            scored.sort(Comparator.comparingDouble(ScoredArticle::getScore).reversed());
            return 4;
        } catch (Exception e) {
            log.warn("Dify 推荐重排失败，降级为本地排序: {}", e.getMessage());
            return 3;
        }
    }

    private Map<String, String> parseDifyRecommendations(JsonNode response) {
        Map<String, String> map = new LinkedHashMap<>();
        JsonNode outputs = response.path("data").path("outputs");
        if (outputs.isMissingNode()) {
            outputs = response.path("outputs");
        }
        JsonNode list = outputs.path("recommendations");
        if (!list.isArray()) {
            list = outputs.path("articles");
        }
        if (!list.isArray()) {
            String text = outputs.path("text").asText("");
            if (!text.isBlank()) {
                try {
                    JsonNode parsed = objectMapper.readTree(text);
                    list = parsed.path("recommendations");
                    if (!list.isArray()) {
                        list = parsed;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (list.isArray()) {
            for (JsonNode item : list) {
                String id = firstText(item, "article_id", "articleId", "id");
                String reason = firstText(item, "rec_reason", "reason", "recReason");
                if (id != null) {
                    map.put(id, reason != null ? reason : "AI 为您推荐");
                }
            }
        }
        return map;
    }

    private String firstText(JsonNode node, String... keys) {
        for (String k : keys) {
            JsonNode v = node.get(k);
            if (v != null && !v.isNull()) {
                return v.asText();
            }
        }
        return null;
    }

    private void persistRecommendations(String userId, List<ScoredArticle> scored, int phase) {
        recommendMapper.softDeleteUserRecommendations(userId);
        String batchId = IdGenerator.nextId("recb_");
        LocalDateTime expiredAt = LocalDateTime.now().plusMinutes(properties.getCacheTtlMinutes());
        int limit = Math.min(scored.size(), 50);
        for (int i = 0; i < limit; i++) {
            ScoredArticle sa = scored.get(i);
            recommendMapper.insertRecommendation(
                    IdGenerator.nextId("rec_"),
                    userId,
                    sa.getCandidate().getArticleId(),
                    sa.getScore(),
                    truncate(sa.getReason(), 500),
                    batchId,
                    sa.getPhase() > 0 ? sa.getPhase() : phase,
                    null,
                    expiredAt
            );
        }
    }

    private List<ScoredArticle> fromPersistedRecommendations(List<Map<String, Object>> rows) {
        List<ScoredArticle> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            ArticleCandidate c = new ArticleCandidate();
            c.setArticleId(stringVal(row, "articleId"));
            c.setTitle(stringVal(row, "title"));
            c.setSummary(stringVal(row, "summary"));
            c.setCoverImageId(stringVal(row, "coverImageId"));
            Object cat = row.get("category");
            c.setCategory(cat instanceof Number n ? n.intValue() : null);
            Object vc = row.get("viewCount");
            c.setViewCount(vc instanceof Number n ? n.intValue() : 0);
            ScoredArticle sa = new ScoredArticle(c);
            Object rs = row.get("recScore");
            sa.setScore(rs instanceof Number n ? n.doubleValue() : 0);
            sa.setReason(stringVal(row, "recReason"));
            Object rp = row.get("recPhase");
            sa.setPhase(rp instanceof Number n ? n.intValue() : 4);
            list.add(sa);
        }
        return list;
    }

    private Map<String, Object> paginateResult(List<ScoredArticle> scored, int page, int size,
                                               String strategy, int phase) {
        int total = scored.size();
        int offset = Math.max(0, (page - 1) * size);
        List<Map<String, Object>> articles = scored.stream()
                .skip(offset)
                .limit(size)
                .map(this::toRecommendCard)
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("articles", articles);
        result.put("total", total);
        result.put("strategy", strategy);
        result.put("phase", phase);
        return result;
    }

    private Map<String, Object> toRecommendCard(ScoredArticle sa) {
        ArticleCandidate c = sa.getCandidate();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("articleId", c.getArticleId());
        map.put("title", c.getTitle());
        map.put("summary", c.getSummary());
        map.put("coverImage", resolveCoverImage(c));
        map.put("category", c.getCategory());
        map.put("viewCount", c.getViewCount() != null ? c.getViewCount() : 0);
        map.put("publishedAt", c.getPublishedAt());
        map.put("recScore", Math.round(sa.getScore() * 100.0) / 100.0);
        map.put("recReason", sa.getReason());
        return map;
    }

    private String resolveCoverImage(ArticleCandidate c) {
        if (c.getCoverImageId() != null && !c.getCoverImageId().isBlank()) {
            String id = c.getCoverImageId().trim();
            if (id.startsWith("http://") || id.startsWith("https://")) {
                return id;
            }
        }
        return minioStorageService.buildArticleCoverUrl(c.getArticleId());
    }

    private Map<Integer, Double> profileCategoryWeights(Map<String, Object> health, Map<String, Object> risk) {
        Map<Integer, Double> w = new HashMap<>();
        Object dt = health.get("diabetesType");
        int diabetesType = dt instanceof Number n ? n.intValue() : 9;
        switch (diabetesType) {
            case 2, 3 -> {
                w.put(2, 2.0);
                w.put(4, 2.0);
                w.put(3, 1.0);
            }
            case 1 -> {
                w.put(4, 2.5);
                w.put(1, 1.5);
            }
            case 4 -> {
                w.put(2, 2.0);
                w.put(5, 1.5);
            }
            default -> w.put(1, 1.5);
        }
        String riskLevel = risk.get("riskLevel") != null ? risk.get("riskLevel").toString() : "";
        if (riskLevel.contains("高") || "high".equalsIgnoreCase(riskLevel)) {
            w.merge(5, 2.0, Double::sum);
            w.merge(4, 1.0, Double::sum);
        }
        Object bmi = health.get("bmi");
        if (bmi instanceof Number n && n.doubleValue() >= 24) {
            w.merge(2, 1.5, Double::sum);
            w.merge(3, 1.5, Double::sum);
        }
        return w;
    }

    private Set<String> buildInterestTags(RecommendContext ctx, List<ArticleCandidate> candidates) {
        Set<String> tags = new HashSet<>();
        Map<String, ArticleCandidate> byId = candidates.stream()
                .collect(Collectors.toMap(ArticleCandidate::getArticleId, c -> c, (a, b) -> a));
        for (String id : ctx.favoriteIds) {
            ArticleCandidate c = byId.get(id);
            if (c != null && c.getTags() != null) {
                tags.addAll(c.getTags());
            }
        }
        for (String id : ctx.recentReadIds) {
            ArticleCandidate c = byId.get(id);
            if (c != null && c.getTags() != null) {
                tags.addAll(c.getTags());
            }
        }
        return tags;
    }

    private String buildInterestText(RecommendContext ctx, List<ArticleCandidate> candidates) {
        Map<String, ArticleCandidate> byId = candidates.stream()
                .collect(Collectors.toMap(ArticleCandidate::getArticleId, c -> c, (a, b) -> a));
        StringBuilder sb = new StringBuilder();
        for (String id : ctx.favoriteIds) {
            ArticleCandidate c = byId.get(id);
            if (c != null) {
                sb.append(buildFingerprint(c)).append(" ");
            }
        }
        for (String id : ctx.recentReadIds) {
            ArticleCandidate c = byId.get(id);
            if (c != null) {
                sb.append(buildFingerprint(c)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private double popularityScore(ArticleCandidate c) {
        int views = c.getViewCount() != null ? c.getViewCount() : 0;
        int favs = c.getFavoriteCount() != null ? c.getFavoriteCount() : 0;
        return Math.log(views + 1) + favs * 0.5;
    }

    private double freshnessScore(ArticleCandidate c) {
        if (c.getPublishedAt() == null) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(c.getPublishedAt(), LocalDateTime.now());
        if (days <= 7) return 1.0;
        if (days <= 30) return 0.5;
        return 0;
    }

    private Set<String> toSet(List<String> list) {
        return list == null ? Set.of() : new HashSet<>(list);
    }

    private double tagOverlapScore(Set<String> a, List<String> b) {
        if (a == null || a.isEmpty() || b == null || b.isEmpty()) {
            return 0;
        }
        Set<String> bs = new HashSet<>(b);
        long overlap = a.stream().filter(bs::contains).count();
        return overlap / (double) Math.max(a.size(), bs.size());
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(text.toLowerCase().split("[\\s,，、；;。.!！?？]+"))
                .filter(t -> t.length() > 1)
                .collect(Collectors.toSet());
    }

    private double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return inter.size() / (double) union.size();
    }

    private Map<String, Object> readCache(String key) {
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<>() {});
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void writeCache(String key, Map<String, Object> result) {
        try {
            Duration ttl = Duration.ofMinutes(properties.getCacheTtlMinutes());
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(result), ttl);
        } catch (Exception ignored) {
        }
    }

    private String stringVal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String blankToDefault(String s, String def) {
        return s == null || s.isBlank() ? def : s.trim();
    }

    private static class RecommendContext {
        final String userId;
        Set<String> recentReadIds = Set.of();
        Set<String> favoriteIds = Set.of();
        Map<Integer, Double> categoryWeights = Map.of();
        Map<Integer, Double> profileCategoryWeights = Map.of();
        Map<String, Object> healthProfile = Map.of();
        Map<String, Object> riskProfile = Map.of();
        List<ArticleCandidate> candidates = List.of();
        Set<String> interestTags = Set.of();
        String interestText = "";

        RecommendContext(String userId) {
            this.userId = userId;
        }
    }
}
