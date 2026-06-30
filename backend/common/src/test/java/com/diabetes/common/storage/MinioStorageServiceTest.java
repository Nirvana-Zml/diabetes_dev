package com.diabetes.common.storage;

import com.diabetes.common.exception.BusinessException;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MinioStorageServiceTest {

    @Test
    @DisplayName("MinioProperties 默认值和 setter")
    void shouldAccessMinioProperties() {
        MinioProperties properties = new MinioProperties();

        assertEquals("http://localhost:9000", properties.getEndpoint());
        assertEquals("minio", properties.getAccessKey());
        assertEquals("minio123456", properties.getSecretKey());
        assertEquals("profile", properties.getProfileBucket());
        assertEquals("checkin", properties.getCheckinBucket());
        assertEquals("article", properties.getArticleBucket());
        assertEquals("banner", properties.getBannerBucket());
        assertEquals("video-cover", properties.getVideoCoverBucket());
        assertEquals("avatar", properties.getAvatarBucket());
        assertEquals("http://localhost:9000", properties.getPublicBaseUrl());

        properties.setEndpoint("http://minio:9000/");
        properties.setAccessKey("ak");
        properties.setSecretKey("sk");
        properties.setProfileBucket("p");
        properties.setCheckinBucket("c");
        properties.setArticleBucket("a");
        properties.setBannerBucket("b");
        properties.setVideoCoverBucket("v");
        properties.setAvatarBucket("doctor");
        properties.setPublicBaseUrl("http://public/");

        assertEquals("http://minio:9000/", properties.getEndpoint());
        assertEquals("ak", properties.getAccessKey());
        assertEquals("sk", properties.getSecretKey());
        assertEquals("p", properties.getProfileBucket());
        assertEquals("c", properties.getCheckinBucket());
        assertEquals("a", properties.getArticleBucket());
        assertEquals("b", properties.getBannerBucket());
        assertEquals("v", properties.getVideoCoverBucket());
        assertEquals("doctor", properties.getAvatarBucket());
        assertEquals("http://public/", properties.getPublicBaseUrl());
    }

    @Test
    @DisplayName("构建各类公开 URL")
    void shouldBuildPublicUrls() {
        MinioStorageService service = serviceWithMockClient(mock(MinioClient.class), properties());

        assertEquals("http://public/profile/u_001.jpg", service.buildProfileAvatarUrl("u_001"));
        assertEquals("http://public/article/a_001.jpg", service.buildArticleCoverUrl("a_001"));
        assertEquals("http://public/banner/b_001.jpg", service.buildBannerImageUrl("b_001"));
        assertEquals("http://public/video-cover/v_001.jpg", service.buildVideoCoverUrl("v_001"));
        assertEquals("http://public/avatar/d_001.jpg", service.buildDoctorAvatarUrl("d_001"));
        assertEquals("http://public/checkin/food/u_001.jpg", service.buildCheckinImageUrl("/food/u_001.jpg"));
        assertEquals("", service.buildArticleCoverUrl(null));
        assertEquals("", service.buildArticleCoverUrl(" "));
        assertEquals("", service.buildBannerImageUrl(null));
        assertEquals("", service.buildBannerImageUrl(" "));
        assertEquals("", service.buildVideoCoverUrl(null));
        assertEquals("", service.buildVideoCoverUrl(" "));
        assertEquals("", service.buildDoctorAvatarUrl(null));
        assertEquals("", service.buildDoctorAvatarUrl(" "));
        assertEquals("", service.buildCheckinImageUrl(null));
        assertEquals("", service.buildCheckinImageUrl(" "));
    }

    @Test
    @DisplayName("上传头像、封面和打卡图片成功")
    void shouldUploadObjects() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.bucketExists(any())).thenReturn(true);
        MinioStorageService service = serviceWithMockClient(client, properties());
        ByteArrayInputStream stream = new ByteArrayInputStream(new byte[]{1});

        assertEquals("http://public/profile/u_001.jpg",
                service.uploadProfileAvatar("u_001", stream, 1, null));
        assertEquals("http://public/article/a_001.jpg",
                service.uploadArticleCover("a_001", new ByteArrayInputStream(new byte[]{1}), 1, null));
        assertEquals("http://public/profile/u_002.jpg",
                service.uploadProfileAvatar("u_002", new ByteArrayInputStream(new byte[]{1}), 1, "image/png"));
        assertEquals("http://public/article/a_002.jpg",
                service.uploadArticleCover("a_002", new ByteArrayInputStream(new byte[]{1}), 1, "image/png"));
        assertEquals("food/u_001/upload_1.jpg",
                service.uploadCheckinFoodUser("u_001", "upload_1", new ByteArrayInputStream(new byte[]{1}), 1, null).objectKey());
        assertEquals("medical/u_001/upload_2.jpg",
                service.uploadCheckinMedicalUser("u_001", "upload_2", new ByteArrayInputStream(new byte[]{1}), 1, "image/png").objectKey());
        assertEquals("food/preset_1.jpg",
                service.uploadCheckinFoodPreset("preset_1", new ByteArrayInputStream(new byte[]{1}), 1, null).objectKey());
        assertEquals("medical/preset_2.jpg",
                service.uploadCheckinMedicalPreset("preset_2", new ByteArrayInputStream(new byte[]{1}), 1, null).objectKey());
        assertEquals("food/legacy_1.jpg",
                service.uploadCheckinFood("legacy_1", new ByteArrayInputStream(new byte[]{1}), 1, null).objectKey());
        assertEquals("medical/legacy_2.jpg",
                service.uploadCheckinMedical("legacy_2", new ByteArrayInputStream(new byte[]{1}), 1, null).objectKey());

        verify(client, atLeast(10)).putObject(any());
    }

    @Test
    @DisplayName("上传参数无效时抛出业务异常")
    void shouldRejectInvalidUploadArguments() {
        MinioStorageService service = serviceWithMockClient(mock(MinioClient.class), properties());

        assertBusinessException(400, "用户 ID 无效",
                () -> service.uploadProfileAvatar(" ", new ByteArrayInputStream(new byte[0]), 0, null));
        assertBusinessException(400, "用户 ID 无效",
                () -> service.uploadProfileAvatar(null, new ByteArrayInputStream(new byte[0]), 0, null));
        assertBusinessException(400, "资讯 ID 无效",
                () -> service.uploadArticleCover(null, new ByteArrayInputStream(new byte[0]), 0, null));
        assertBusinessException(400, "资讯 ID 无效",
                () -> service.uploadArticleCover(" ", new ByteArrayInputStream(new byte[0]), 0, null));
        assertBusinessException(400, "用户 ID 无效",
                () -> service.uploadCheckinFoodUser(null, "file", new ByteArrayInputStream(new byte[0]), 0, null));
        assertBusinessException(400, "用户 ID 无效",
                () -> service.uploadCheckinFoodUser(" ", "file", new ByteArrayInputStream(new byte[0]), 0, null));
        assertBusinessException(400, "文件名无效",
                () -> service.uploadCheckinMedicalUser("u_001", null, new ByteArrayInputStream(new byte[0]), 0, null));
        assertBusinessException(400, "文件名无效",
                () -> service.uploadCheckinMedicalUser("u_001", " ", new ByteArrayInputStream(new byte[0]), 0, null));
        assertBusinessException(400, "对象名无效",
                () -> service.uploadCheckinFoodPreset(null, new ByteArrayInputStream(new byte[0]), 0, null));
        assertBusinessException(400, "对象名无效",
                () -> service.uploadCheckinFoodPreset(" ", new ByteArrayInputStream(new byte[0]), 0, null));
    }

    @Test
    @DisplayName("MinIO 不可用时上传抛出对应异常")
    void shouldThrowWhenMinioUnavailable() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.bucketExists(any())).thenThrow(new RuntimeException("down"));
        MinioStorageService service = serviceWithMockClient(client, properties());

        assertBusinessException(500, "MinIO 不可用: down",
                () -> service.uploadProfileAvatar("u_001", new ByteArrayInputStream(new byte[0]), 0, null));
        assertBusinessException(500, "MinIO 不可用: down",
                () -> service.uploadArticleCover("a_001", new ByteArrayInputStream(new byte[0]), 0, null));
        assertBusinessException(500, "MinIO 不可用: down",
                () -> service.uploadCheckinFoodPreset("p_001", new ByteArrayInputStream(new byte[0]), 0, null));
    }

    @Test
    @DisplayName("MinIO putObject 失败时抛出上传失败")
    void shouldThrowWhenPutObjectFails() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.bucketExists(any())).thenReturn(true);
        when(client.putObject(any())).thenThrow(new RuntimeException("write failed"));
        MinioStorageService service = serviceWithMockClient(client, properties());

        assertBusinessException(500, "头像上传失败: write failed",
                () -> service.uploadProfileAvatar("u_001", new ByteArrayInputStream(new byte[]{1}), 1, null));
        assertBusinessException(500, "封面图上传失败: write failed",
                () -> service.uploadArticleCover("a_001", new ByteArrayInputStream(new byte[]{1}), 1, null));
        assertBusinessException(500, "打卡图片上传失败: write failed",
                () -> service.uploadCheckinFoodPreset("p_001", new ByteArrayInputStream(new byte[]{1}), 1, null));
    }

    @Test
    @DisplayName("读取对象成功、参数无效和不存在")
    void shouldGetObject() throws Exception {
        MinioClient client = mock(MinioClient.class);
        GetObjectResponse response = mock(GetObjectResponse.class);
        when(client.getObject(any())).thenReturn(response);
        MinioStorageService service = serviceWithMockClient(client, properties());

        assertSame(response, service.getObject("profile", "u_001.jpg"));
        assertBusinessException(400, "对象路径无效", () -> service.getObject(null, "u_001.jpg"));
        assertBusinessException(400, "对象路径无效", () -> service.getObject("", "u_001.jpg"));
        assertBusinessException(400, "对象路径无效", () -> service.getObject("profile", null));
        assertBusinessException(400, "对象路径无效", () -> service.getObject("profile", " "));

        doThrow(new RuntimeException("missing")).when(client).statObject(any());
        assertBusinessException(404, "资源不存在", () -> service.getObject("profile", "missing.jpg"));

        doThrow(new BusinessException(400, "bad object")).when(client).statObject(any());
        assertBusinessException(400, "bad object", () -> service.getObject("profile", "bad.jpg"));
    }

    @Test
    @DisplayName("初始化 bucket 失败时不抛出异常")
    void shouldIgnoreInitBucketFailures() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.bucketExists(any())).thenThrow(new RuntimeException("down"));
        MinioStorageService service = serviceWithMockClient(client, properties());

        assertDoesNotThrow(service::initBuckets);
        verify(client, times(6)).bucketExists(any());
    }

    @Test
    @DisplayName("初始化 bucket 外层异常时继续处理后续 bucket")
    void shouldCatchInitBucketOuterFailures() {
        MinioProperties throwingProperties = mock(MinioProperties.class);
        when(throwingProperties.getProfileBucket()).thenThrow(new RuntimeException("profile down"));
        when(throwingProperties.getCheckinBucket()).thenThrow(new RuntimeException("checkin down"));
        when(throwingProperties.getArticleBucket()).thenThrow(new RuntimeException("article down"));
        when(throwingProperties.getBannerBucket()).thenThrow(new RuntimeException("banner down"));
        when(throwingProperties.getVideoCoverBucket()).thenThrow(new RuntimeException("video down"));
        when(throwingProperties.getAvatarBucket()).thenThrow(new RuntimeException("avatar down"));
        MinioStorageService service = serviceWithMockClient(mock(MinioClient.class), properties());
        ReflectionTestUtils.setField(service, "properties", throwingProperties);

        assertDoesNotThrow(service::initBuckets);
    }

    @Test
    @DisplayName("bucket 不存在时创建 bucket")
    void shouldCreateBucketWhenMissing() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.bucketExists(any())).thenReturn(false);
        MinioStorageService service = serviceWithMockClient(client, properties());

        assertEquals("http://public/profile/u_001.jpg",
                service.uploadProfileAvatar("u_001", new ByteArrayInputStream(new byte[]{1}), 1, null));

        verify(client).makeBucket(any());
    }

    @Test
    @DisplayName("endpoint 为空时使用默认地址")
    void shouldUseDefaultEndpointWhenBlank() {
        MinioProperties nullEndpoint = properties();
        nullEndpoint.setEndpoint(null);
        MinioProperties blankEndpoint = properties();
        blankEndpoint.setEndpoint(" ");

        assertNotNull(new MinioStorageService(nullEndpoint));
        assertNotNull(new MinioStorageService(blankEndpoint));
    }

    @Test
    @DisplayName("生成用户上传文件名")
    void shouldBuildUserUploadFileName() {
        String fileName = MinioStorageService.buildUserUploadFileName();

        assertTrue(fileName.matches("upload_\\d{14}_[0-9a-f]{8}"));
    }

    private static MinioStorageService serviceWithMockClient(MinioClient client, MinioProperties properties) {
        MinioStorageService service = new MinioStorageService(properties);
        ReflectionTestUtils.setField(service, "minioClient", client);
        return service;
    }

    private static MinioProperties properties() {
        MinioProperties properties = new MinioProperties();
        properties.setPublicBaseUrl("http://public/");
        return properties;
    }

    private static void assertBusinessException(int code, String message, ThrowingRunnable runnable) {
        BusinessException ex = assertThrows(BusinessException.class, runnable::run);
        assertEquals(code, ex.getCode());
        assertEquals(message, ex.getMessage());
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
