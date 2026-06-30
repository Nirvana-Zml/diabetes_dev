package com.diabetes.home.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.storage.MinioProperties;
import com.diabetes.common.storage.MinioStorageService;
import com.diabetes.home.config.QaChatProperties;
import com.diabetes.home.dto.ChatQaRequest;
import com.diabetes.home.knowledge.DocumentChunk;
import com.diabetes.home.knowledge.KnowledgeRetrieval;
import com.diabetes.home.service.AIChatService;
import com.diabetes.home.service.HomeContentService;
import com.diabetes.home.service.VideoService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HomeControllersTest {

    @Test
    void adminVideoControllerDelegatesToService() {
        VideoService service = mock(VideoService.class);
        AdminVideoController controller = new AdminVideoController(service);
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1});
        when(service.adminList("k", 1, 2)).thenReturn(Map.of("total", 0));
        when(service.adminDetail("v1")).thenReturn(Map.of("videoId", "v1"));
        when(service.create(Map.of("title", "t"))).thenReturn(Map.of("videoId", "v2"));
        when(service.update("v1", Map.of("title", "t2"))).thenReturn(Map.of("videoId", "v1"));
        when(service.uploadCover("v1", file)).thenReturn(Map.of("coverUrl", "c"));
        when(service.uploadVideoFile("v1", file)).thenReturn(Map.of("videoUrl", "v"));

        assertEquals(0, controller.list("k", 1, 2).data().get("total"));
        assertEquals("v1", controller.detail("v1").data().get("videoId"));
        assertEquals("v2", controller.create(Map.of("title", "t")).data().get("videoId"));
        assertEquals("v1", controller.update("v1", Map.of("title", "t2")).data().get("videoId"));
        assertEquals("删除成功", controller.delete("v1").data());
        assertEquals("c", controller.uploadCover("v1", file).data().get("coverUrl"));
        assertEquals("v", controller.uploadFile("v1", file).data().get("videoUrl"));
        verify(service).delete("v1");
    }

    @Test
    void chatControllerValidatesAndDelegates() {
        AIChatService service = mock(AIChatService.class);
        QaChatProperties props = new QaChatProperties();
        props.setQueryMaxLength(5);
        ChatController controller = new ChatController(service, props);
        when(service.getDifyWorkflowSpec()).thenReturn(Map.of("workflowUrl", "url"));
        SseEmitter emitter = new SseEmitter();
        when(service.processQuestion(eq("hi"), eq("c1"), eq("u1"))).thenReturn(emitter);

        assertEquals("url", controller.difyWorkflowSpec().data().get("workflowUrl"));
        ChatQaRequest request = new ChatQaRequest();
        request.setQuery(" hi ");
        request.setConversationId("c1");
        assertSame(emitter, controller.chatQa(request, "u1"));

        ChatQaRequest blank = new ChatQaRequest();
        blank.setQuery(" ");
        Object bad = controller.chatQa(blank, "u1");
        assertEquals(400, ((ResponseEntity<?>) bad).getStatusCode().value());
        ChatQaRequest nullQuery = new ChatQaRequest();
        assertEquals(400, ((ResponseEntity<?>) controller.chatQa(nullQuery, "u1")).getStatusCode().value());

        ChatQaRequest tooLong = new ChatQaRequest();
        tooLong.setQuery("123456");
        Object longBad = controller.chatQa(tooLong, "u1");
        assertEquals(400, ((ResponseEntity<?>) longBad).getStatusCode().value());

        ChatQaRequest guestRequest = new ChatQaRequest();
        guestRequest.setQuery("hi");
        when(service.processQuestion(eq("hi"), isNull(), startsWith("guest_"))).thenReturn(emitter);
        assertSame(emitter, controller.chatQa(guestRequest, " "));
        assertSame(emitter, controller.chatQa(guestRequest, null));
        assertEquals("c2", controller.chatHistory("c2").data().get("conversationId"));
        assertEquals(Collections.emptyList(), controller.chatHistory("c2").data().get("messages"));
    }

    @Test
    void homePageControllersReturnExpectedResponses() {
        HomePageController v1 = new HomePageController();
        assertEquals(501, v1.getHomeContent().code());
        assertTrue((Boolean) v1.getRecommend(1, 10).data().get("placeholder"));

        HomeContentService homeContentService = mock(HomeContentService.class);
        when(homeContentService.getHomeContent()).thenReturn(Map.of("videos", List.of()));
        assertEquals(List.of(), new HomePageV2Controller(homeContentService).getHomeContent().data().get("videos"));

        KnowledgeRetrieval retrieval = mock(KnowledgeRetrieval.class);
        DocumentChunk chunk = new DocumentChunk("1", "content", "title", "src", "type", 0.8);
        when(retrieval.semanticSearch("糖尿病", 2)).thenReturn(List.of(chunk));
        when(retrieval.buildKnowledgeContext(List.of(chunk))).thenReturn("ctx");
        when(retrieval.extractSources(List.of(chunk))).thenReturn(List.of("title"));
        InternalHomeController internal = new InternalHomeController(homeContentService, retrieval);
        assertEquals(List.of(), internal.content().data().get("videos"));
        ApiResponse<Map<String, Object>> search = internal.knowledgeSearch("糖尿病", 2);
        assertEquals("ctx", search.data().get("knowledgeContext"));
        assertEquals(1, search.data().get("count"));
    }

    @Test
    void homeMediaControllerResolvesBucketsAndMediaTypes() throws Exception {
        MinioStorageService minio = mock(MinioStorageService.class);
        MinioProperties props = new MinioProperties();
        props.setBannerBucket("b-bucket");
        props.setVideoCoverBucket("vc-bucket");
        props.setVideoBucket("v-bucket");
        props.setAvatarBucket("a-bucket");
        HomeMediaController controller = new HomeMediaController(minio, props);
        when(minio.getObject(anyString(), anyString())).thenReturn(new ByteArrayInputStream(new byte[]{1}));

        assertMedia(controller, "banner", "a.jpg", "image/jpeg", "b-bucket");
        assertMedia(controller, "video-cover", "a.png", MediaType.IMAGE_PNG_VALUE, "vc-bucket");
        assertMedia(controller, "video", "a.mp4", "video/mp4", "v-bucket");
        assertMedia(controller, "avatar", "a.webp", "image/webp", "a-bucket");
        assertThrows(BusinessException.class, () -> controller.getMedia("bad", "a.jpg"));
        Method resolveBucket = HomeMediaController.class.getDeclaredMethod("resolveBucketName", String.class);
        resolveBucket.setAccessible(true);
        assertEquals("other", resolveBucket.invoke(controller, "other"));
    }

    private static void assertMedia(HomeMediaController controller, String bucket, String name,
                                    String mediaType, String resolvedBucket) {
        ResponseEntity<InputStreamResource> response = controller.getMedia(bucket, name);
        assertEquals(mediaType, response.getHeaders().getContentType().toString());
        assertEquals("public, max-age=86400", response.getHeaders().getCacheControl());
        assertNotNull(response.getBody());
    }
}
