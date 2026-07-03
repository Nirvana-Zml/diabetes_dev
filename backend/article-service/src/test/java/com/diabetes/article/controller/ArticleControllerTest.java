package com.diabetes.article.controller;

import com.diabetes.article.config.OptionalJwtInterceptor;
import com.diabetes.article.service.ArticleService;
import com.diabetes.article.service.ArticleTtsService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArticleControllerTest {

    @Mock
    private ArticleService articleService;

    @Mock
    private ArticleTtsService articleTtsService;

    @InjectMocks
    private ArticleController controller;

    @Test
    void recommend_delegatesToService() {
        Map<String, Object> expected = Map.of("articles", java.util.List.of(), "total", 0);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(OptionalJwtInterceptor.ATTR_USER_ID)).thenReturn("u1");
        when(articleService.recommend("u1", 1, 10, null)).thenReturn(expected);

        var result = controller.recommend(1, 10, null, request);

        assertEquals(200, result.code());
        assertEquals(expected, result.data());
    }

    @Test
    void list_search_detailAndFavorite() {
        Map<String, Object> list = Map.of("articles", java.util.List.of(), "total", 0);
        when(articleService.list(2, 1, 10)).thenReturn(list);
        assertEquals(list, controller.list(2, 1, 10).data());

        Map<String, Object> search = Map.of("articles", java.util.List.of(), "total", 1);
        when(articleService.search("糖尿病", 1, 10)).thenReturn(search);
        assertEquals(search, controller.search("糖尿病", 1, 10).data());

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(OptionalJwtInterceptor.ATTR_USER_ID)).thenReturn("u1");
        Map<String, Object> detail = Map.of("articleId", "art_1");
        when(articleService.detail("art_1", "u1")).thenReturn(detail);
        assertEquals(detail, controller.detail("art_1", request).data());

        Map<String, Object> favorite = Map.of("favorited", true);
        when(articleService.toggleFavorite("u1", "art_1")).thenReturn(favorite);
        assertEquals(favorite, controller.favorite("u1", "art_1").data());
    }

    @Test
    void related_readEventAndFavorites() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(OptionalJwtInterceptor.ATTR_USER_ID)).thenReturn("u1");
        Map<String, Object> related = Map.of("articles", java.util.List.of(), "total", 0, "strategy", "related");
        when(articleService.related("art_1", "u1", 5)).thenReturn(related);
        assertEquals(related, controller.related("art_1", 5, request).data());

        doNothing().when(articleService).recordRead("u1", "art_1", 30, "detail");
        var readResult = controller.readEvent("u1", "art_1", Map.of("durationSec", 30));
        assertEquals(200, readResult.code());
        assertEquals(true, readResult.data().get("recorded"));
        verify(articleService).recordRead("u1", "art_1", 30, "detail");

        Map<String, Object> favorites = Map.of("articles", java.util.List.of(), "total", 0);
        when(articleService.favorites("u1", 1, 10)).thenReturn(favorites);
        assertEquals(favorites, controller.favorites("u1", 1, 10).data());
    }

    @Test
    void audio_delegatesToTtsService() {
        Map<String, Object> audio = Map.of("audioUrl", "http://minio/art_1-audio.wav", "source", "cached");
        when(articleTtsService.getOrGenerateAudio("art_1")).thenReturn(audio);
        assertEquals(audio, controller.audio("art_1").data());
    }

    @Test
    void difyRecommendWorkflowSpec() {
        Map<String, Object> spec = Map.of("workflowUrl", "http://dify/v1/workflows/run");
        when(articleService.getDifyRecommendWorkflowSpec()).thenReturn(spec);
        assertEquals(spec, controller.difyRecommendWorkflowSpec().data());
    }

    @Test
    void readEventWithDurationSecNumber() {
        doNothing().when(articleService).recordRead("u1", "art_1", 20, "detail");
        controller.readEvent("u1", "art_1", Map.of("durationSec", 20));
        verify(articleService).recordRead("u1", "art_1", 20, "detail");
    }

    @Test
    void readEventWithNonNumberDuration() {
        doNothing().when(articleService).recordRead("u1", "art_1", null, "detail");
        controller.readEvent("u1", "art_1", Map.of("durationSec", "not-a-number"));
        verify(articleService).recordRead("u1", "art_1", null, "detail");
    }

    @Test
    void readEventWithAlternateBodyFields() {
        doNothing().when(articleService).recordRead("u1", "art_1", 45, "search");
        var result = controller.readEvent("u1", "art_1", Map.of("duration_sec", 45, "source", "search"));
        assertEquals(true, result.data().get("recorded"));

        doNothing().when(articleService).recordRead("u1", "art_1", null, "detail");
        controller.readEvent("u1", "art_1", null);
        verify(articleService).recordRead("u1", "art_1", null, "detail");
    }
}
