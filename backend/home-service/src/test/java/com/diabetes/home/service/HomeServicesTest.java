package com.diabetes.home.service;

import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.storage.MinioStorageService;
import com.diabetes.home.entity.Banner;
import com.diabetes.home.entity.Video;
import com.diabetes.home.mapper.ResourceMapper;
import com.diabetes.home.mapper.VideoMapper;
import com.diabetes.home.util.VideoDurationParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HomeServicesTest {

    private VideoMapper videoMapper;
    private MinioStorageService minio;
    private VideoService videoService;

    @BeforeEach
    void setUp() {
        videoMapper = mock(VideoMapper.class);
        minio = mock(MinioStorageService.class);
        videoService = new VideoService(videoMapper, minio);
    }

    @Test
    void homeContentFormatsBannersAndVideos() {
        ResourceMapper mapper = mock(ResourceMapper.class);
        Banner banner = new Banner();
        banner.setBannerId("b1");
        banner.setTitle("banner");
        Video shortVideo = video("video_001", "short", LocalTime.of(0, 2, 3));
        Video longVideo = video("video_002", "long", LocalTime.of(1, 2, 3));
        Video noDuration = video("video_003", "none", null);
        when(mapper.findActiveBanners()).thenReturn(List.of(banner));
        when(mapper.findActiveVideos()).thenReturn(List.of(shortVideo, longVideo, noDuration));

        Map<String, Object> result = new HomeContentService(mapper).getHomeContent();

        assertEquals("b1", ((Map<?, ?>) ((List<?>) result.get("banners")).get(0)).get("bannerId"));
        assertEquals(List.of("02:03", "1:02:03", ""),
                ((List<?>) result.get("videos")).stream().map(v -> ((Map<?, ?>) v).get("duration")).toList());
    }

    @Test
    void videoAdminListAndDetailUseMapperAndMinioUrls() {
        Video first = video("video_001", "one", LocalTime.of(0, 1, 2));
        first.setCreatedAt(LocalDateTime.of(2024, 1, 1, 1, 1));
        first.setUpdatedAt(LocalDateTime.of(2024, 1, 2, 1, 1));
        Video second = video("video_002", "two", LocalTime.of(1, 1, 2));
        when(videoMapper.findAdminList("糖尿病", 20, 20)).thenReturn(List.of(first, second));
        when(videoMapper.countAdminList("糖尿病")).thenReturn(2);
        when(videoMapper.findById("video_001")).thenReturn(first);
        when(minio.buildVideoCoverUrl(anyString())).thenAnswer(inv -> "cover/" + inv.getArgument(0));
        when(minio.buildVideoUrl(anyString())).thenAnswer(inv -> "video/" + inv.getArgument(0));

        Map<String, Object> list = videoService.adminList(" 糖尿病 ", 2, 20);
        Map<?, ?> card = (Map<?, ?>) ((List<?>) list.get("videos")).get(0);
        assertEquals(2, list.get("total"));
        assertEquals("01:02", card.get("duration"));
        assertEquals("cover/video_001", card.get("coverUrl"));
        assertEquals("video/video_001", card.get("videoUrl"));
        assertEquals(first.getCreatedAt(), card.get("createdAt"));
        assertEquals("video_001", videoService.adminDetail("video_001").get("videoId"));

        when(videoMapper.findAdminList(null, 0, 10)).thenReturn(List.of());
        when(videoMapper.countAdminList(null)).thenReturn(0);
        assertEquals(0, videoService.adminList(" ", 1, 10).get("total"));
        assertEquals(0, videoService.adminList(null, 1, 10).get("total"));
        when(videoMapper.findById("video_003")).thenReturn(video("video_003", "none", null));
        assertEquals("", videoService.adminDetail("video_003").get("duration"));
    }

    @Test
    void createUpdateDeleteValidateTitleAndIds() {
        when(videoMapper.findLatestVideoId()).thenReturn(null, "video_009", "bad");
        assertEquals("video_001", videoService.create(Map.of("title", " 新视频 ")).get("videoId"));
        assertEquals("video_010", videoService.create(Map.of("title", "next")).get("videoId"));
        assertEquals("video_001", videoService.create(Map.of("title", "fallback")).get("videoId"));

        Video existing = video("video_001", "old", null);
        when(videoMapper.findById("video_001")).thenReturn(existing);
        assertEquals("new", videoService.update("video_001", Map.of("title", " new ")).get("title"));
        videoService.delete("video_001");
        verify(videoMapper).update("video_001", "new");
        verify(videoMapper).softDelete("video_001");

        assertThrows(BusinessException.class, () -> videoService.create(Map.of("title", " ")));
        assertThrows(BusinessException.class, () -> videoService.create(Map.of()));
        assertThrows(BusinessException.class, () -> videoService.create(Map.of("title", "a".repeat(101))));
        assertThrows(BusinessException.class, () -> videoService.update("video_001", Map.of("title", " ")));
        assertThrows(BusinessException.class, () -> videoService.update("video_001", Map.of("title", "a".repeat(101))));
        assertThrows(BusinessException.class, () -> videoService.adminDetail("missing"));
    }

    @Test
    void uploadCoverValidatesAndWrapsErrors() throws Exception {
        when(videoMapper.findById("video_001")).thenReturn(video("video_001", "v", null));
        when(minio.buildVideoCoverUrl("video_001")).thenReturn("http://cover/video_001.jpg");
        assertTrue(videoService.uploadCover("video_001", file("cover.jpg", "image/jpeg", new byte[]{1, 2}))
                .get("coverUrl").toString().startsWith("http://cover/video_001.jpg?v="));

        assertThrows(BusinessException.class, () -> videoService.uploadCover("video_001", null));
        assertThrows(BusinessException.class, () -> videoService.uploadCover("video_001", file("empty.jpg", "image/jpeg", new byte[]{})));
        assertThrows(BusinessException.class, () -> videoService.uploadCover("video_001", sizedFile("big.jpg", "image/jpeg", 5L * 1024 * 1024 + 1)));
        assertThrows(BusinessException.class, () -> videoService.uploadCover("video_001", file("bad.txt", "text/plain", new byte[]{1})));
        assertThrows(BusinessException.class, () -> videoService.uploadCover("video_001", file("bad", null, new byte[]{1})));

        doThrow(new BusinessException(500, "minio")).when(minio).uploadVideoCover(eq("video_001"), any(), anyLong(), any());
        assertThrows(BusinessException.class, () -> videoService.uploadCover("video_001", file("cover.jpg", "image/jpeg", new byte[]{1})));
        doThrow(new RuntimeException("io")).when(minio).uploadVideoCover(eq("video_001"), any(), anyLong(), any());
        assertEquals(500, assertThrows(BusinessException.class,
                () -> videoService.uploadCover("video_001", file("cover.jpg", "image/jpeg", new byte[]{1}))).getCode());
    }

    @Test
    void uploadVideoFileValidatesMp4AndWrapsParserFailures() throws Exception {
        when(videoMapper.findById("video_001")).thenReturn(video("video_001", "v", null));
        when(minio.buildVideoUrl("video_001")).thenReturn("http://video/video_001.mp4");
        try (var parser = mockStatic(VideoDurationParser.class)) {
            parser.when(() -> VideoDurationParser.parseMp4Duration(any())).thenReturn(LocalTime.of(0, 1, 2));
            Map<String, Object> uploaded = videoService.uploadVideoFile("video_001",
                    file("good.mp4", "application/octet-stream", new byte[]{1, 2, 3}));
            assertEquals("01:02", uploaded.get("duration"));
            assertTrue(uploaded.get("videoUrl").toString().startsWith("http://video/video_001.mp4?v="));
        }

        doThrow(new RuntimeException("upload")).when(minio).uploadVideo(eq("video_001"), any(), anyLong(), any());
        try (var parser = mockStatic(VideoDurationParser.class)) {
            parser.when(() -> VideoDurationParser.parseMp4Duration(any())).thenReturn(LocalTime.of(1, 1, 2));
            assertThrows(BusinessException.class, () -> videoService.uploadVideoFile("video_001",
                    file("good.mp4", "video/mp4", new byte[]{1, 2, 3})));
        }

        try (var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.createTempFile("video-upload-", ".mp4")).thenThrow(new IOException("temp"));
            assertThrows(BusinessException.class, () -> videoService.uploadVideoFile("video_001",
                    file("good.mp4", "video/mp4", new byte[]{1, 2, 3})));
        }

        reset(minio);
        when(minio.buildVideoUrl("video_001")).thenReturn("http://video/video_001.mp4");
        try (var files = mockStatic(Files.class, CALLS_REAL_METHODS);
             var parser = mockStatic(VideoDurationParser.class)) {
            AtomicReference<Path> tempPath = new AtomicReference<>();
            parser.when(() -> VideoDurationParser.parseMp4Duration(any())).thenAnswer(inv -> {
                tempPath.set(inv.getArgument(0));
                return LocalTime.of(0, 1, 2);
            });
            files.when(() -> Files.deleteIfExists(any())).thenAnswer(inv -> {
                Path path = inv.getArgument(0);
                if (path.equals(tempPath.get())) {
                    throw new IOException("delete");
                }
                return inv.callRealMethod();
            });
            Map<String, Object> uploaded = videoService.uploadVideoFile("video_001",
                    file("good.mp4", "video/mp4", new byte[]{1, 2, 3}));
            assertEquals("01:02", uploaded.get("duration"));
        }

        reset(minio);
        when(videoMapper.findById("video_001")).thenReturn(video("video_001", "v", null));
        assertThrows(BusinessException.class, () -> videoService.uploadVideoFile("video_001", null));
        assertThrows(BusinessException.class, () -> videoService.uploadVideoFile("video_001", file("empty.mp4", "video/mp4", new byte[]{})));
        assertThrows(BusinessException.class, () -> videoService.uploadVideoFile("video_001", sizedFile("big.mp4", "video/mp4", 500L * 1024 * 1024 + 1)));
        assertThrows(BusinessException.class, () -> videoService.uploadVideoFile("video_001", file("bad.avi", "video/avi", new byte[]{1})));
        assertThrows(BusinessException.class, () -> videoService.uploadVideoFile("video_001", file(null, null, new byte[]{1})));
        assertThrows(BusinessException.class, () -> videoService.uploadVideoFile("video_001", file("good.mp4", "application/octet-stream", new byte[]{1})));
        assertThrows(BusinessException.class, () -> videoService.uploadVideoFile("video_001", file("good.bin", "video/mp4", new byte[]{1})));
        assertThrows(BusinessException.class, () -> videoService.uploadVideoFile("video_001", nullNameVideoMp4()));

        Method validateVideoFile = VideoService.class.getDeclaredMethod("validateVideoFile", MultipartFile.class);
        validateVideoFile.setAccessible(true);
        assertDoesNotThrow(() -> validateVideoFile.invoke(videoService, nullNameVideoMp4()));
    }

    private static Video video(String id, String title, LocalTime duration) {
        Video video = new Video();
        video.setVideoId(id);
        video.setTitle(title);
        video.setDuration(duration);
        return video;
    }

    private static MockMultipartFile file(String name, String contentType, byte[] bytes) {
        return new MockMultipartFile("file", name, contentType, bytes);
    }

    private static MultipartFile sizedFile(String name, String contentType, long size) {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(size);
        when(file.getContentType()).thenReturn(contentType);
        when(file.getOriginalFilename()).thenReturn(name);
        return file;
    }

    private static MultipartFile nullNameVideoMp4() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1L);
        when(file.getContentType()).thenReturn("video/mp4");
        when(file.getOriginalFilename()).thenReturn(null);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1}));
        return file;
    }
}
