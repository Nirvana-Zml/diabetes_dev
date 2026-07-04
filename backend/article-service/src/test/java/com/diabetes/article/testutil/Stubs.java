package com.diabetes.article.testutil;

import com.diabetes.article.entity.Article;
import com.diabetes.article.entity.ArticleCandidate;
import com.diabetes.article.mapper.ArticleMapper;
import com.diabetes.article.mapper.RecommendMapper;
import org.springframework.data.redis.core.RedisOperations;

import java.time.LocalDateTime;
import java.util.*;

public class Stubs {

    public static class ArticleMapperStub implements ArticleMapper {
        private final Map<String, Article> articles = new HashMap<>();
        private final Map<String, List<String>> tags = new HashMap<>();
        private final Map<String, Map<String, Integer>> favorites = new HashMap<>();

        @Override
        public List<Article> findPublished(Integer category, int offset, int limit) {
            return articles.values().stream()
                    .filter(a -> a.getStatus() != null && a.getStatus() == 3)
                    .filter(a -> category == null || Objects.equals(a.getCategory(), category))
                    .skip(offset).limit(limit).toList();
        }

        @Override
        public int countPublished(Integer category) {
            return (int) articles.values().stream()
                    .filter(a -> a.getStatus() != null && a.getStatus() == 3)
                    .filter(a -> category == null || Objects.equals(a.getCategory(), category))
                    .count();
        }

        @Override
        public List<Article> searchPublished(String keyword, int offset, int limit) {
            return articles.values().stream()
                    .filter(a -> a.getStatus() != null && a.getStatus() == 3)
                    .filter(a -> (a.getTitle() != null && a.getTitle().contains(keyword))
                            || (a.getSummary() != null && a.getSummary().contains(keyword)))
                    .skip(offset).limit(limit).toList();
        }

        @Override
        public int countSearch(String keyword) {
            return (int) articles.values().stream()
                    .filter(a -> a.getStatus() != null && a.getStatus() == 3)
                    .filter(a -> (a.getTitle() != null && a.getTitle().contains(keyword))
                            || (a.getSummary() != null && a.getSummary().contains(keyword)))
                    .count();
        }

        @Override
        public Article findById(String articleId) {
            return articles.get(articleId);
        }

        @Override
        public int insert(Article article) {
            articles.put(article.getArticleId(), article);
            return 1;
        }

        @Override
        public int update(Article article) {
            Article existing = articles.get(article.getArticleId());
            if (existing != null) {
                articles.put(article.getArticleId(), article);
                return 1;
            }
            return 0;
        }

        @Override
        public int softDelete(String articleId) {
            articles.remove(articleId);
            return 1;
        }

        @Override
        public List<Article> findPendingReview(int offset, int limit) {
            return articles.values().stream()
                    .filter(a -> a.getStatus() != null && a.getStatus() == 2)
                    .skip(offset).limit(limit).toList();
        }

        @Override
        public int countPendingReview() {
            return (int) articles.values().stream()
                    .filter(a -> a.getStatus() != null && a.getStatus() == 2)
                    .count();
        }

        @Override
        public int incrementViewCount(String articleId) {
            Article a = articles.get(articleId);
            if (a != null) {
                a.setViewCount((a.getViewCount() != null ? a.getViewCount() : 0) + 1);
                return 1;
            }
            return 0;
        }

        @Override
        public int upsertFavorite(String favId, String userId, String articleId, int isActive) {
            favorites.computeIfAbsent(userId, k -> new HashMap<>()).put(articleId, isActive);
            return 1;
        }

        @Override
        public Integer findFavoriteStatus(String userId, String articleId) {
            Map<String, Integer> userFavs = favorites.get(userId);
            return userFavs != null ? userFavs.get(articleId) : null;
        }

        @Override
        public List<Article> findFavorites(String userId, int offset, int limit) {
            Map<String, Integer> userFavs = favorites.get(userId);
            if (userFavs == null) return List.of();
            return userFavs.entrySet().stream()
                    .filter(e -> e.getValue() == 1)
                    .map(e -> articles.get(e.getKey()))
                    .filter(Objects::nonNull)
                    .skip(offset).limit(limit).toList();
        }

        @Override
        public int countFavorites(String userId) {
            Map<String, Integer> userFavs = favorites.get(userId);
            return userFavs != null ? (int) userFavs.values().stream().filter(v -> v == 1).count() : 0;
        }

        @Override
        public int updateReview(String articleId, int status, String rejectReason) {
            Article a = articles.get(articleId);
            if (a != null) {
                a.setStatus(status);
                a.setRejectReason(rejectReason);
                return 1;
            }
            return 0;
        }

        @Override
        public List<Article> findAdminList(Integer status, String keyword, int offset, int limit) {
            return articles.values().stream()
                    .filter(a -> status == null || Objects.equals(a.getStatus(), status))
                    .filter(a -> keyword == null || (a.getTitle() != null && a.getTitle().contains(keyword)))
                    .skip(offset).limit(limit).toList();
        }

        @Override
        public int countAdminList(Integer status, String keyword) {
            return (int) articles.values().stream()
                    .filter(a -> status == null || Objects.equals(a.getStatus(), status))
                    .filter(a -> keyword == null || (a.getTitle() != null && a.getTitle().contains(keyword)))
                    .count();
        }

        @Override
        public int updateStatus(String articleId, int status) {
            Article a = articles.get(articleId);
            if (a != null) {
                a.setStatus(status);
                return 1;
            }
            return 0;
        }

        @Override
        public int updateCoverImageId(String articleId, String coverImageId) {
            Article a = articles.get(articleId);
            if (a != null) {
                a.setCoverImageId(coverImageId);
                return 1;
            }
            return 0;
        }

        @Override
        public int adjustFavoriteCount(String articleId, int delta) {
            Article a = articles.get(articleId);
            if (a != null) {
                a.setFavoriteCount((a.getFavoriteCount() != null ? a.getFavoriteCount() : 0) + delta);
                return 1;
            }
            return 0;
        }

        private String returnNullTagsForArticleId;

        public void setReturnNullTagsForArticleId(String articleId) {
            this.returnNullTagsForArticleId = articleId;
        }

        @Override
        public List<String> findTagsByArticleId(String articleId) {
            if (returnNullTagsForArticleId != null && returnNullTagsForArticleId.equals(articleId)) {
                return null;
            }
            return tags.getOrDefault(articleId, List.of());
        }

        @Override
        public int softDeleteTagsByArticleId(String articleId) {
            tags.remove(articleId);
            return 1;
        }

        @Override
        public int insertTag(String tagId, String articleId, String tagName) {
            tags.computeIfAbsent(articleId, k -> new ArrayList<>()).add(tagName);
            return 1;
        }

        public void addArticle(Article article) {
            articles.put(article.getArticleId(), article);
        }
    }

    public static class RecommendMapperStub implements RecommendMapper {
        private final List<ArticleCandidate> candidates = new ArrayList<>();
        private final Map<String, List<String>> tags = new HashMap<>();
        private final Map<String, List<String>> userRecentReads = new HashMap<>();
        private final Map<String, List<String>> userFavorites = new HashMap<>();
        private final Map<String, List<Map<String, Object>>> userCategoryWeights = new HashMap<>();
        private final Map<String, List<Map<String, Object>>> coReadArticles = new HashMap<>();
        private final Map<String, List<Map<String, Object>>> coFavoriteArticles = new HashMap<>();
        private final Map<String, List<Map<String, Object>>> activeRecommendations = new HashMap<>();
        private boolean returnDuplicateCandidates = false;

        @Override
        public List<ArticleCandidate> findPublishedCandidates(int limit) {
            if (returnDuplicateCandidates && !candidates.isEmpty()) {
                List<ArticleCandidate> result = new ArrayList<>();
                for (int i = 0; i < limit; i++) {
                    result.add(candidates.get(i % candidates.size()));
                }
                return result;
            }
            return candidates.stream().limit(limit).toList();
        }

        public void setReturnDuplicateCandidates(boolean flag) {
            this.returnDuplicateCandidates = flag;
        }

        @Override
        public List<String> findTagsByArticleId(String articleId) {
            return tags.getOrDefault(articleId, List.of());
        }

        @Override
        public List<Map<String, Object>> findCategoryWeightsByUser(String userId, int days) {
            return userCategoryWeights.getOrDefault(userId, List.of());
        }

        @Override
        public List<String> findRecentReadArticleIds(String userId, int days) {
            return userRecentReads.getOrDefault(userId, List.of());
        }

        @Override
        public List<String> findFavoriteArticleIds(String userId) {
            return userFavorites.getOrDefault(userId, List.of());
        }

        @Override
        public void upsertUserRead(String readId, String userId, String articleId, Integer durationSec, String source) {
        }

        @Override
        public List<Map<String, Object>> findCoReadArticles(String userId, List<String> excludeIds, int limit) {
            return coReadArticles.getOrDefault(userId, List.of());
        }

        @Override
        public List<Map<String, Object>> findCoFavoriteArticles(String userId, List<String> excludeIds, int limit) {
            return coFavoriteArticles.getOrDefault(userId, List.of());
        }

        @Override
        public void softDeleteUserRecommendations(String userId) {
            activeRecommendations.remove(userId);
        }

        @Override
        public void insertRecommendation(String recId, String userId, String articleId, double recScore,
                                          String recReason, String batchId, int recPhase, String difyRunId,
                                          LocalDateTime expiredAt) {
            activeRecommendations.computeIfAbsent(userId, k -> new ArrayList<>()).add(Map.of(
                    "articleId", articleId, "title", "推荐文章", "summary", "推荐摘要",
                    "category", 1, "viewCount", 100, "recScore", recScore,
                    "recReason", recReason, "recPhase", recPhase
            ));
        }

        @Override
        public List<Map<String, Object>> findActiveRecommendations(String userId) {
            return activeRecommendations.getOrDefault(userId, List.of());
        }

        @Override
        public void upsertEmbedding(String articleId, String fingerprint) {
        }

        @Override
        public String findFingerprint(String articleId) {
            return null;
        }

        @Override
        public List<ArticleCandidate> findRelatedCandidates(String articleId, Integer category, int limit) {
            List<ArticleCandidate> sameCategory = candidates.stream()
                    .filter(c -> !articleId.equals(c.getArticleId()))
                    .filter(c -> Objects.equals(c.getCategory(), category))
                    .toList();
            List<ArticleCandidate> differentCategory = candidates.stream()
                    .filter(c -> !articleId.equals(c.getArticleId()))
                    .filter(c -> !Objects.equals(c.getCategory(), category))
                    .limit(Math.max(0, limit - sameCategory.size()))
                    .toList();
            
            List<ArticleCandidate> result = new ArrayList<>(sameCategory);
            result.addAll(differentCategory);
            return result.stream().limit(limit).toList();
        }

        public void addCandidate(ArticleCandidate candidate) {
            candidates.add(candidate);
        }

        public void addTags(String articleId, List<String> tagList) {
            tags.put(articleId, tagList);
        }

        public void setUserRecentReads(String userId, List<String> articleIds) {
            userRecentReads.put(userId, articleIds);
        }

        public void setUserFavorites(String userId, List<String> articleIds) {
            userFavorites.put(userId, articleIds);
        }

        public void setUserCategoryWeights(String userId, List<Map<String, Object>> weights) {
            userCategoryWeights.put(userId, weights);
        }

        public void setCoReadArticles(String userId, List<Map<String, Object>> articles) {
            coReadArticles.put(userId, articles);
        }

        public void setCoFavoriteArticles(String userId, List<Map<String, Object>> articles) {
            coFavoriteArticles.put(userId, articles);
        }

        public void setActiveRecommendations(String userId, List<Map<String, Object>> recs) {
            activeRecommendations.put(userId, recs);
        }
    }

    public static class MinioStorageServiceStub {
        public void uploadArticleCover(String articleId, java.io.InputStream inputStream, long size, String contentType) {
        }

        public String buildArticleCoverUrl(String articleId) {
            return "/images/articles/" + articleId + ".jpg";
        }
    }

    public static class HealthServiceClientStub {
        public Map<String, Object> getLatestHealthProfile(String userId, String internalKey) {
            return Map.of("diabetesType", 2, "bmi", 22.0);
        }

        public Map<String, Object> getLatestRiskAssessment(String userId, String internalKey) {
            return Map.of("riskLevel", "low");
        }
    }

    public static class DifyClientStub {
        public com.fasterxml.jackson.databind.JsonNode runWorkflowBlocking(String apiKey, String userId,
                                                                             Map<String, Object> inputs, String responseMode) {
            return null;
        }
    }

    public static class StubRedisTemplate {
        final Map<String, String> store = new HashMap<>();

        @SuppressWarnings("unchecked")
        public RedisOperations<String, String> build() {
            return (RedisOperations<String, String>) java.lang.reflect.Proxy.newProxyInstance(
                    StubRedisTemplate.class.getClassLoader(),
                    new Class[]{RedisOperations.class},
                    (proxy, method, args) -> {
                        if ("opsForValue".equals(method.getName())) {
                            return buildValueOps();
                        } else if ("keys".equals(method.getName())) {
                            String pattern = (String) args[0];
                            String regex = pattern.replace("*", ".*");
                            return store.keySet().stream()
                                    .filter(k -> k.matches(regex))
                                    .collect(java.util.stream.Collectors.toSet());
                        } else if ("delete".equals(method.getName())) {
                            if (args[0] instanceof String) {
                                return store.remove(args[0]) != null;
                            } else if (args[0] instanceof Collection) {
                                Collection<?> keys = (Collection<?>) args[0];
                                long count = 0;
                                for (Object key : keys) {
                                    if (store.remove(key) != null) count++;
                                }
                                return count;
                            }
                        }
                        return null;
                    }
            );
        }

        @SuppressWarnings("unchecked")
        private org.springframework.data.redis.core.ValueOperations<String, String> buildValueOps() {
            return (org.springframework.data.redis.core.ValueOperations<String, String>) java.lang.reflect.Proxy.newProxyInstance(
                    StubRedisTemplate.class.getClassLoader(),
                    new Class[]{org.springframework.data.redis.core.ValueOperations.class},
                    (proxy, method, args) -> {
                        if ("get".equals(method.getName())) {
                            return store.get(args[0]);
                        } else if ("set".equals(method.getName()) && args.length == 2) {
                            store.put((String) args[0], (String) args[1]);
                            return null;
                        } else if ("set".equals(method.getName()) && args.length >= 3) {
                            store.put((String) args[0], (String) args[1]);
                            return null;
                        }
                        return null;
                    }
            );
        }
    }

    public static Article createArticle(String id, String title, int category, int status) {
        Article a = new Article();
        a.setArticleId(id);
        a.setTitle(title);
        a.setContent(title + " content");
        a.setSummary(title + " summary");
        a.setCategory(category);
        a.setStatus(status);
        a.setViewCount(0);
        a.setFavoriteCount(0);
        a.setCreatedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        return a;
    }

    public static ArticleCandidate createCandidate(String id, String title, int category) {
        ArticleCandidate c = new ArticleCandidate();
        c.setArticleId(id);
        c.setTitle(title);
        c.setSummary(title + " summary");
        c.setCategory(category);
        c.setViewCount(100);
        c.setFavoriteCount(10);
        c.setPublishedAt(LocalDateTime.now());
        return c;
    }
}