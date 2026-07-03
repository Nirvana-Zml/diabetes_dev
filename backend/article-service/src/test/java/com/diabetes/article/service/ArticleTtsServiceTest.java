package com.diabetes.article.service;

import com.diabetes.article.config.DashScopeTtsProperties;
import com.diabetes.article.entity.Article;
import com.diabetes.article.mapper.ArticleMapper;
import com.diabetes.article.tts.QwenTtsClient;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.storage.MinioStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ArticleTtsServiceTest {

    private final ArticleMapper articleMapper = mock(ArticleMapper.class);
    private final MinioStorageService minioStorageService = mock(MinioStorageService.class);
    private final QwenTtsClient qwenTtsClient = mock(QwenTtsClient.class);
    private final DashScopeTtsProperties ttsProperties = new DashScopeTtsProperties();

    private ArticleTtsService service;

    @BeforeEach
    void setUp() {
        ttsProperties.setEnabled(true);
        ttsProperties.setApiKey("sk-test");
        ttsProperties.setMaxChunkChars(400);
        service = new ArticleTtsService(articleMapper, minioStorageService, qwenTtsClient, ttsProperties);
    }

    @Test
    void getOrGenerateAudio_rejectsWhenDisabled() {
        ttsProperties.setEnabled(false);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getOrGenerateAudio("art_1"));
        assertEquals(503, ex.getCode());
    }

    @Test
    void getOrGenerateAudio_rejectsMissingArticle() {
        when(articleMapper.findById("art_1")).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getOrGenerateAudio("art_1"));
        assertEquals(404, ex.getCode());
    }

    @Test
    void getOrGenerateAudio_returnsCachedUrl() {
        Article article = publishedArticle();
        when(articleMapper.findById("art_1")).thenReturn(article);
        when(minioStorageService.articleAudioExists("art_1")).thenReturn(true);
        when(minioStorageService.buildArticleAudioUrl("art_1")).thenReturn("http://minio/article/art_1-audio.wav");

        Map<String, Object> result = service.getOrGenerateAudio("art_1");

        assertEquals("cached", result.get("source"));
        assertEquals("http://minio/article/art_1-audio.wav", result.get("audioUrl"));
        verify(qwenTtsClient, never()).synthesize(anyString());
    }

    @Test
    void getOrGenerateAudio_generatesAndUploads() {
        Article article = publishedArticle();
        article.setTitle("测试标题");
        article.setContent("这是正文第一句。这是第二句。");
        when(articleMapper.findById("art_1")).thenReturn(article);
        when(minioStorageService.articleAudioExists("art_1")).thenReturn(false);
        when(qwenTtsClient.synthesize(anyString())).thenReturn(minimalWav());
        when(minioStorageService.buildArticleAudioUrl("art_1")).thenReturn("http://minio/article/art_1-audio.wav");

        Map<String, Object> result = service.getOrGenerateAudio("art_1");

        assertEquals("generated", result.get("source"));
        verify(minioStorageService).uploadArticleAudio(eq("art_1"), any(), anyLong());
    }

    @Test
    void invalidateAudio_delegatesToMinio() {
        service.invalidateAudio("art_1");
        verify(minioStorageService).deleteArticleAudio("art_1");
    }

    private static Article publishedArticle() {
        Article article = new Article();
        article.setArticleId("art_1");
        article.setStatus(3);
        article.setTitle("标题");
        article.setContent("正文");
        return article;
    }

    private static byte[] minimalWav() {
        byte[] pcm = new byte[]{1, 2, 3, 4};
        byte[] wav = new byte[44 + pcm.length];
        wav[0] = 'R';
        wav[1] = 'I';
        wav[2] = 'F';
        wav[3] = 'F';
        wav[8] = 'W';
        wav[9] = 'A';
        wav[10] = 'V';
        wav[11] = 'E';
        wav[36] = 'd';
        wav[37] = 'a';
        wav[38] = 't';
        wav[39] = 'a';
        wav[40] = (byte) pcm.length;
        System.arraycopy(pcm, 0, wav, 44, pcm.length);
        return wav;
    }
}
