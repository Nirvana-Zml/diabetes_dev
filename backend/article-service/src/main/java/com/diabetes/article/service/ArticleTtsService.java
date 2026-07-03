package com.diabetes.article.service;

import com.diabetes.article.config.DashScopeTtsProperties;
import com.diabetes.article.entity.Article;
import com.diabetes.article.mapper.ArticleMapper;
import com.diabetes.article.tts.ArticleReadAloudText;
import com.diabetes.article.tts.QwenTtsClient;
import com.diabetes.article.tts.WavMerger;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.storage.MinioStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ArticleTtsService {

    private static final Logger log = LoggerFactory.getLogger(ArticleTtsService.class);

    private final ArticleMapper articleMapper;
    private final MinioStorageService minioStorageService;
    private final QwenTtsClient qwenTtsClient;
    private final DashScopeTtsProperties ttsProperties;

    public ArticleTtsService(ArticleMapper articleMapper,
                             MinioStorageService minioStorageService,
                             QwenTtsClient qwenTtsClient,
                             DashScopeTtsProperties ttsProperties) {
        this.articleMapper = articleMapper;
        this.minioStorageService = minioStorageService;
        this.qwenTtsClient = qwenTtsClient;
        this.ttsProperties = ttsProperties;
    }

    /**
     * 获取资讯朗读音频 URL；若 MinIO 中尚无缓存则调用 qwen3-tts-flash（blocking）合成并持久化。
     */
    public Map<String, Object> getOrGenerateAudio(String articleId) {
        if (!ttsProperties.isEnabled()) {
            throw new BusinessException(503, "语音朗读功能未启用");
        }

        Article article = articleMapper.findById(articleId);
        if (article == null || article.getStatus() == null || article.getStatus() != 3) {
            throw new BusinessException(404, "资讯不存在");
        }

        if (minioStorageService.articleAudioExists(articleId)) {
            return buildResponse(articleId, article, "cached");
        }

        String readAloudText = ArticleReadAloudText.prepare(article.getTitle(), article.getContent());
        if (readAloudText.isBlank()) {
            throw new BusinessException(400, "资讯正文为空，无法生成朗读");
        }

        List<String> chunks = ArticleReadAloudText.chunk(readAloudText, ttsProperties.getMaxChunkChars());
        log.info("开始合成资讯朗读: articleId={}, chunks={}", articleId, chunks.size());

        try {
            List<byte[]> wavSegments = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                wavSegments.add(qwenTtsClient.synthesize(chunks.get(i)));
            }
            byte[] merged = WavMerger.merge(wavSegments);
            minioStorageService.uploadArticleAudio(articleId, new ByteArrayInputStream(merged), merged.length);
            return buildResponse(articleId, article, "generated");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("资讯朗读合成失败: articleId={}, err={}", articleId, e.getMessage(), e);
            throw new BusinessException(500, "语音朗读生成失败: " + e.getMessage());
        }
    }

    public void invalidateAudio(String articleId) {
        if (articleId == null || articleId.isBlank()) {
            return;
        }
        minioStorageService.deleteArticleAudio(articleId);
    }

    private Map<String, Object> buildResponse(String articleId, Article article, String source) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("articleId", articleId);
        map.put("audioUrl", minioStorageService.buildArticleAudioUrl(articleId));
        map.put("source", source);
        map.put("updatedAt", article.getUpdatedAt());
        return map;
    }
}
