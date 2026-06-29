package com.diabetes.article.mapper;

import com.diabetes.article.entity.Article;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ArticleMapper {

    List<Article> findPublished(@Param("category") Integer category,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    int countPublished(@Param("category") Integer category);

    List<Article> searchPublished(@Param("keyword") String keyword,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);

    int countSearch(@Param("keyword") String keyword);

    Article findById(@Param("articleId") String articleId);

    int insert(Article article);

    int update(Article article);

    int softDelete(@Param("articleId") String articleId);

    List<Article> findPendingReview(@Param("offset") int offset, @Param("limit") int limit);

    int countPendingReview();

    int incrementViewCount(@Param("articleId") String articleId);

    int upsertFavorite(@Param("favId") String favId,
                       @Param("userId") String userId,
                       @Param("articleId") String articleId,
                       @Param("isActive") int isActive);

    Integer findFavoriteStatus(@Param("userId") String userId, @Param("articleId") String articleId);

    List<Article> findFavorites(@Param("userId") String userId,
                                @Param("offset") int offset,
                                @Param("limit") int limit);

    int countFavorites(@Param("userId") String userId);

    int updateReview(@Param("articleId") String articleId,
                     @Param("status") int status,
                     @Param("rejectReason") String rejectReason);

    List<Article> findAdminList(@Param("status") Integer status,
                                @Param("keyword") String keyword,
                                @Param("offset") int offset,
                                @Param("limit") int limit);

    int countAdminList(@Param("status") Integer status, @Param("keyword") String keyword);

    int updateStatus(@Param("articleId") String articleId, @Param("status") int status);

    int updateCoverImageId(@Param("articleId") String articleId, @Param("coverImageId") String coverImageId);

    int adjustFavoriteCount(@Param("articleId") String articleId, @Param("delta") int delta);

    List<String> findTagsByArticleId(@Param("articleId") String articleId);

    int softDeleteTagsByArticleId(@Param("articleId") String articleId);

    int insertTag(@Param("tagId") String tagId,
                  @Param("articleId") String articleId,
                  @Param("tagName") String tagName);
}
