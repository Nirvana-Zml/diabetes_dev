package com.diabetes.home;

import com.diabetes.home.config.KnowledgeMilvusProperties;
import com.diabetes.home.config.QaChatProperties;
import com.diabetes.home.dto.ChatQaRequest;
import com.diabetes.home.entity.Banner;
import com.diabetes.home.entity.Video;
import com.diabetes.home.knowledge.DocumentChunk;
import com.diabetes.home.util.VideoDurationParser;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

class HomeCoreTest {

    @Test
    void applicationMainDelegatesToSpringApplication() {
        assertNotNull(new HomeServiceApplication());
        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            HomeServiceApplication.main(new String[]{"--test"});
            spring.verify(() -> SpringApplication.run(eq(HomeServiceApplication.class), eq(new String[]{"--test"})));
        }
    }

    @Test
    void entityDtoAndPropertiesAccessors() {
        Banner banner = new Banner();
        banner.setBannerId("b1");
        banner.setTitle("banner");
        assertEquals("b1", banner.getBannerId());
        assertEquals("banner", banner.getTitle());

        LocalDateTime created = LocalDateTime.of(2024, 1, 1, 1, 2);
        LocalDateTime updated = LocalDateTime.of(2024, 1, 2, 1, 2);
        Video video = new Video();
        video.setVideoId("v1");
        video.setTitle("video");
        video.setDuration(LocalTime.of(1, 2, 3));
        video.setCreatedAt(created);
        video.setUpdatedAt(updated);
        assertEquals("v1", video.getVideoId());
        assertEquals("video", video.getTitle());
        assertEquals(LocalTime.of(1, 2, 3), video.getDuration());
        assertEquals(created, video.getCreatedAt());
        assertEquals(updated, video.getUpdatedAt());

        ChatQaRequest request = new ChatQaRequest();
        request.setQuery("q");
        request.setConversationId("c1");
        assertEquals("q", request.getQuery());
        assertEquals("c1", request.getConversationId());

        QaChatProperties qa = new QaChatProperties();
        qa.setApiKey("key");
        qa.setQueryMaxLength(12);
        assertEquals("key", qa.getApiKey());
        assertEquals(12, qa.getQueryMaxLength());

        KnowledgeMilvusProperties props = new KnowledgeMilvusProperties();
        props.setEnabled(true);
        props.setHost("127.0.0.1");
        props.setPort(19531);
        props.setCollection("c");
        props.setDimension(8);
        props.setMetricType("IP");
        props.setSearchTopK(3);
        props.setScoreThreshold(0.4);
        props.getEmbedding().setProvider("local");
        props.getEmbedding().setOpenaiBaseUrl("http://e");
        props.getEmbedding().setOpenaiApiKey("ak");
        props.getEmbedding().setOpenaiModel("m");
        assertTrue(props.isEnabled());
        assertEquals("127.0.0.1", props.getHost());
        assertEquals(19531, props.getPort());
        assertEquals("c", props.getCollection());
        assertEquals(8, props.getDimension());
        assertEquals("IP", props.getMetricType());
        assertEquals(3, props.getSearchTopK());
        assertEquals(0.4, props.getScoreThreshold());
        assertEquals("local", props.getEmbedding().getProvider());
        assertEquals("http://e", props.getEmbedding().getOpenaiBaseUrl());
        assertEquals("ak", props.getEmbedding().getOpenaiApiKey());
        assertEquals("m", props.getEmbedding().getOpenaiModel());

        DocumentChunk chunk = new DocumentChunk("id", "content", "title", "source", "type", 0.9);
        assertEquals("id", chunk.id());
        assertEquals("content", chunk.content());
        assertEquals("title", chunk.docTitle());
        assertEquals("source", chunk.docSource());
        assertEquals("type", chunk.docType());
        assertEquals(0.9, chunk.score());
    }

    @Test
    void videoDurationParserPrivateConstructorAndSecondsBounds() throws Exception {
        Constructor<VideoDurationParser> constructor = VideoDurationParser.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        constructor.newInstance();

        Method method = VideoDurationParser.class.getDeclaredMethod("secondsToLocalTime", long.class);
        method.setAccessible(true);
        assertEquals(LocalTime.MIDNIGHT, method.invoke(null, -1L));
        assertEquals(LocalTime.of(0, 1, 5), method.invoke(null, 65L));
        assertEquals(LocalTime.of(23, 59, 59), method.invoke(null, 90000L));
    }
}
