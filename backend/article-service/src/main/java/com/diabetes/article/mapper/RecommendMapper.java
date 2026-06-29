package com.diabetes.article.mapper;

import com.diabetes.article.entity.ArticleCandidate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface RecommendMapper {

    List<ArticleCandidate> findPublishedCandidates(@Param("limit") int limit);

    List<String> findTagsByArticleId(@Param("articleId") String articleId);

    List<Map<String, Object>> findCategoryWeightsByUser(@Param("userId") String userId, @Param("days") int days);

    List<String> findRecentReadArticleIds(@Param("userId") String userId, @Param("days") int days);

    List<String> findFavoriteArticleIds(@Param("userId") String userId);

    void upsertUserRead(@Param("readId") String readId,
                        @Param("userId") String userId,
                        @Param("articleId") String articleId,
                        @Param("durationSec") Integer durationSec,
                        @Param("source") String source);

    List<Map<String, Object>> findCoReadArticles(@Param("userId") String userId,
                                                  @Param("excludeIds") List<String> excludeIds,
                                                  @Param("limit") int limit);

    List<Map<String, Object>> findCoFavoriteArticles(@Param("userId") String userId,
                                                       @Param("excludeIds") List<String> excludeIds,
                                                       @Param("limit") int limit);

    void softDeleteUserRecommendations(@Param("userId") String userId);

    void insertRecommendation(@Param("recId") String recId,
                              @Param("userId") String userId,
                              @Param("articleId") String articleId,
                              @Param("recScore") double recScore,
                              @Param("recReason") String recReason,
                              @Param("batchId") String batchId,
                              @Param("recPhase") int recPhase,
                              @Param("difyRunId") String difyRunId,
                              @Param("expiredAt") LocalDateTime expiredAt);

    List<Map<String, Object>> findActiveRecommendations(@Param("userId") String userId);

    void upsertEmbedding(@Param("articleId") String articleId, @Param("fingerprint") String fingerprint);

    String findFingerprint(@Param("articleId") String articleId);

    List<ArticleCandidate> findRelatedCandidates(@Param("articleId") String articleId,
                                                   @Param("category") Integer category,
                                                   @Param("limit") int limit);
}
