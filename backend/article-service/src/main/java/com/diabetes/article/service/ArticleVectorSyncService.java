package com.diabetes.article.service;

import com.diabetes.article.entity.Article;
import com.diabetes.article.entity.ArticleCandidate;
import com.diabetes.article.mapper.ArticleMapper;
import com.diabetes.article.mapper.RecommendMapper;
import com.diabetes.article.milvus.ArticleEmbeddingService;
import com.diabetes.article.milvus.MilvusArticleClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ArticleVectorSyncService {

    private static final Logger log = LoggerFactory.getLogger(ArticleVectorSyncService.class);

    private final MilvusArticleClient milvusClient;
    private final ArticleEmbeddingService embeddingService;
    private final ArticleMapper articleMapper;
    private final RecommendMapper recommendMapper;

    public ArticleVectorSyncService(MilvusArticleClient milvusClient,
                                    ArticleEmbeddingService embeddingService,
                                    ArticleMapper articleMapper,
                                    RecommendMapper recommendMapper) {
        this.milvusClient = milvusClient;
        this.embeddingService = embeddingService;
        this.articleMapper = articleMapper;
        this.recommendMapper = recommendMapper;
    }

    /** 资讯发布/更新后同步；非发布状态则从 Milvus 删除 */
    public void syncArticle(String articleId) {
        if (!milvusClient.isReady()) {
            return;
        }
        Article article = articleMapper.findById(articleId);
        if (article == null || article.getStatus() == null || article.getStatus() != 3) {
            milvusClient.delete(articleId);
            return;
        }
        List<String> tags = recommendMapper.findTagsByArticleId(articleId);
        String fingerprint = buildFingerprint(article, tags);
        float[] vector = embeddingService.embed(fingerprint);
        milvusClient.upsert(articleId, article.getCategory() != null ? article.getCategory() : 1, vector);
        recommendMapper.upsertEmbedding(articleId, fingerprint);
        log.debug("Milvus 已同步资讯向量: {}", articleId);
    }

    public void syncAllPublished() {
        if (!milvusClient.isReady()) {
            return;
        }
        List<ArticleCandidate> published = recommendMapper.findPublishedCandidates(500);
        int ok = 0;
        for (ArticleCandidate c : published) {
            try {
                syncArticle(c.getArticleId());
                ok++;
            } catch (Exception e) {
                log.warn("同步资讯 {} 至 Milvus 失败: {}", c.getArticleId(), e.getMessage());
            }
        }
        log.info("Milvus 向量同步完成: {}/{}", ok, published.size());
    }

    public void removeArticle(String articleId) {
        if (milvusClient.isReady()) {
            milvusClient.delete(articleId);
        }
    }

    private String buildFingerprint(Article article, List<String> tags) {
        String tagStr = tags == null ? "" : String.join(" ", tags);
        return ((article.getTitle() == null ? "" : article.getTitle()) + " "
                + (article.getSummary() == null ? "" : article.getSummary()) + " "
                + tagStr).trim().toLowerCase();
    }
}
