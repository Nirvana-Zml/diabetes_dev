package com.diabetes.common.storage;

import com.diabetes.common.exception.BusinessException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import io.minio.StatObjectArgs;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

public class MinioStorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioStorageService.class);

    private final MinioClient minioClient;
    private final MinioProperties properties;

    public MinioStorageService(MinioProperties properties) {
        this.properties = properties;
        String endpoint = normalizeEndpoint(properties.getEndpoint());
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    @PostConstruct
    public void initBuckets() {
        try {
            ensureBucket(properties.getProfileBucket(), false);
        } catch (Exception e) {
            log.warn("MinIO profile bucket 启动时初始化失败（首次上传时将重试）: {}", e.getMessage());
        }
        try {
            ensureBucket(properties.getCheckinBucket(), false);
        } catch (Exception e) {
            log.warn("MinIO checkin bucket 启动时初始化失败（首次上传时将重试）: {}", e.getMessage());
        }
        try {
            ensureBucket(properties.getArticleBucket(), false);
        } catch (Exception e) {
            log.warn("MinIO article bucket 启动时初始化失败（首次上传时将重试）: {}", e.getMessage());
        }
        try {
            ensureBucket(properties.getBannerBucket(), false);
        } catch (Exception e) {
            log.warn("MinIO banner bucket 启动时初始化失败（首次上传时将重试）: {}", e.getMessage());
        }
        try {
            ensureBucket(properties.getVideoCoverBucket(), false);
        } catch (Exception e) {
            log.warn("MinIO video-cover bucket 启动时初始化失败（首次上传时将重试）: {}", e.getMessage());
        }
        try {
            ensureBucket(properties.getVideoBucket(), false);
        } catch (Exception e) {
            log.warn("MinIO video bucket 启动时初始化失败（首次上传时将重试）: {}", e.getMessage());
        }
        try {
            ensureBucket(properties.getAvatarBucket(), false);
        } catch (Exception e) {
            log.warn("MinIO avatar bucket 启动时初始化失败（首次上传时将重试）: {}", e.getMessage());
        }
        try {
            ensureBucket(properties.getExportBucket(), false);
        } catch (Exception e) {
            log.warn("MinIO export bucket 启动时初始化失败（首次上传时将重试）: {}", e.getMessage());
        }
        try {
            ensureBucket(properties.getSttBucket(), false);
        } catch (Exception e) {
            log.warn("MinIO stt bucket 启动时初始化失败（首次上传时将重试）: {}", e.getMessage());
        }
    }

    /**
     * 上传用户头像至 profile bucket，对象名固定为 {@code {userId}.jpg}
     */
    public String uploadProfileAvatar(String userId, InputStream inputStream, long size, String contentType) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(400, "用户 ID 无效");
        }
        String objectName = userId + ".jpg";
        String bucket = properties.getProfileBucket();
        try {
            ensureBucket(bucket, true);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(inputStream, size, -1)
                    .contentType(contentType != null ? contentType : "image/jpeg")
                    .build());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "头像上传失败: " + e.getMessage());
        }
        return buildProfileAvatarUrl(userId);
    }

    public String buildProfileAvatarUrl(String userId) {
        return buildBucketObjectUrl(properties.getProfileBucket(), userId + ".jpg");
    }

    /**
     * 上传资讯封面至 article bucket，对象名固定为 {@code {articleId}.jpg}
     */
    public String uploadArticleCover(String articleId, InputStream inputStream, long size, String contentType) {
        if (articleId == null || articleId.isBlank()) {
            throw new BusinessException(400, "资讯 ID 无效");
        }
        String objectName = articleId + ".jpg";
        String bucket = properties.getArticleBucket();
        try {
            ensureBucket(bucket, true);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(inputStream, size, -1)
                    .contentType(contentType != null ? contentType : "image/jpeg")
                    .build());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "封面图上传失败: " + e.getMessage());
        }
        return buildArticleCoverUrl(articleId);
    }

    public String buildArticleCoverUrl(String articleId) {
        if (articleId == null || articleId.isBlank()) {
            return "";
        }
        return buildBucketObjectUrl(properties.getArticleBucket(), articleId + ".jpg");
    }

    /**
     * 上传资讯朗读音频至 article bucket，对象名固定为 {@code {articleId}-audio.wav}
     */
    public String uploadArticleAudio(String articleId, InputStream inputStream, long size) {
        if (articleId == null || articleId.isBlank()) {
            throw new BusinessException(400, "资讯 ID 无效");
        }
        String objectName = articleId + "-audio.wav";
        String bucket = properties.getArticleBucket();
        try {
            ensureBucket(bucket, true);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(inputStream, size, -1)
                    .contentType("audio/wav")
                    .build());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "朗读音频上传失败: " + e.getMessage());
        }
        return buildArticleAudioUrl(articleId);
    }

    public String buildArticleAudioUrl(String articleId) {
        if (articleId == null || articleId.isBlank()) {
            return "";
        }
        return buildBucketObjectUrl(properties.getArticleBucket(), articleId + "-audio.wav");
    }

    public boolean articleAudioExists(String articleId) {
        if (articleId == null || articleId.isBlank()) {
            return false;
        }
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(properties.getArticleBucket())
                    .object(articleId + "-audio.wav")
                    .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void deleteArticleAudio(String articleId) {
        if (articleId == null || articleId.isBlank()) {
            return;
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getArticleBucket())
                    .object(articleId + "-audio.wav")
                    .build());
        } catch (Exception e) {
            log.warn("删除资讯朗读音频失败: articleId={}, err={}", articleId, e.getMessage());
        }
    }

    public String buildBannerImageUrl(String bannerId) {
        if (bannerId == null || bannerId.isBlank()) {
            return "";
        }
        return buildBucketObjectUrl(properties.getBannerBucket(), bannerId + ".jpg");
    }

    public String buildVideoCoverUrl(String videoId) {
        if (videoId == null || videoId.isBlank()) {
            return "";
        }
        return buildBucketObjectUrl(properties.getVideoCoverBucket(), videoId + ".jpg");
    }

    /**
     * 上传科普视频至 video bucket，对象名固定为 {@code {videoId}.mp4}
     */
    public String uploadVideo(String videoId, InputStream inputStream, long size, String contentType) {
        if (videoId == null || videoId.isBlank()) {
            throw new BusinessException(400, "视频 ID 无效");
        }
        String objectName = videoId + ".mp4";
        String bucket = properties.getVideoBucket();
        try {
            ensureBucket(bucket, true);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(inputStream, size, -1)
                    .contentType(contentType != null ? contentType : "video/mp4")
                    .build());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "视频上传失败: " + e.getMessage());
        }
        return buildVideoUrl(videoId);
    }

    /**
     * 上传科普视频封面至 video-cover bucket，对象名固定为 {@code {videoId}.jpg}
     */
    public String uploadVideoCover(String videoId, InputStream inputStream, long size, String contentType) {
        if (videoId == null || videoId.isBlank()) {
            throw new BusinessException(400, "视频 ID 无效");
        }
        String objectName = videoId + ".jpg";
        String bucket = properties.getVideoCoverBucket();
        try {
            ensureBucket(bucket, true);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(inputStream, size, -1)
                    .contentType(contentType != null ? contentType : "image/jpeg")
                    .build());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "视频封面上传失败: " + e.getMessage());
        }
        return buildVideoCoverUrl(videoId);
    }

    public String buildVideoUrl(String videoId) {
        if (videoId == null || videoId.isBlank()) {
            return "";
        }
        return buildBucketObjectUrl(properties.getVideoBucket(), videoId + ".mp4");
    }

    /** AI 医生头像 URL，对象名固定为 {@code {doctorId}.jpg} */
    public String buildDoctorAvatarUrl(String doctorId) {
        if (doctorId == null || doctorId.isBlank()) {
            return "";
        }
        return buildBucketObjectUrl(properties.getAvatarBucket(), doctorId + ".jpg");
    }

    /**
     * 读取 MinIO 对象流（用于 API 代理输出）。
     */
    public InputStream getObject(String bucket, String objectKey) {
        if (bucket == null || bucket.isBlank() || objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(400, "对象路径无效");
        }
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(404, "资源不存在");
        }
    }

    /**
     * 上传用户自定义食物图片，Object Key：{@code food/{userId}/upload_{yyyyMMddHHmmss}_{suffix}.jpg}
     */
    public CheckinImageUploadResult uploadCheckinFoodUser(String userId, String fileBaseName,
                                                            InputStream inputStream, long size, String contentType) {
        return uploadCheckinUserImage("food", userId, fileBaseName, inputStream, size, contentType);
    }

    /**
     * 上传用户自定义用药图片，Object Key：{@code medical/{userId}/upload_{yyyyMMddHHmmss}_{suffix}.jpg}
     */
    public CheckinImageUploadResult uploadCheckinMedicalUser(String userId, String fileBaseName,
                                                             InputStream inputStream, long size, String contentType) {
        return uploadCheckinUserImage("medical", userId, fileBaseName, inputStream, size, contentType);
    }

    /**
     * 上传系统预设食物图片（管理端/种子数据），Object Key：{@code food/{presetId}.jpg}
     */
    public CheckinImageUploadResult uploadCheckinFoodPreset(String presetId, InputStream inputStream,
                                                              long size, String contentType) {
        return uploadCheckinImage("food", presetId, inputStream, size, contentType);
    }

    /**
     * 上传系统预设用药图片，Object Key：{@code medical/{presetId}.jpg}
     */
    public CheckinImageUploadResult uploadCheckinMedicalPreset(String presetId, InputStream inputStream,
                                                               long size, String contentType) {
        return uploadCheckinImage("medical", presetId, inputStream, size, contentType);
    }

    /** @deprecated 使用 {@link #uploadCheckinFoodUser} 或 {@link #uploadCheckinFoodPreset} */
    @Deprecated
    public CheckinImageUploadResult uploadCheckinFood(String imageId, InputStream inputStream, long size, String contentType) {
        return uploadCheckinImage("food", imageId, inputStream, size, contentType);
    }

    /** @deprecated 使用 {@link #uploadCheckinMedicalUser} 或 {@link #uploadCheckinMedicalPreset} */
    @Deprecated
    public CheckinImageUploadResult uploadCheckinMedical(String imageId, InputStream inputStream, long size, String contentType) {
        return uploadCheckinImage("medical", imageId, inputStream, size, contentType);
    }

    public String buildCheckinImageUrl(String objectKey) {
        return buildCheckinImageUrl(objectKey, properties.getPublicBaseUrl());
    }

    /** 使用指定公网基址构建打卡图片 URL（如 ngrok 地址，供 Dify 等外部服务拉取）。 */
    public String buildCheckinImageUrl(String objectKey, String publicBaseUrl) {
        if (objectKey == null || objectKey.isBlank()) {
            return "";
        }
        String base = publicBaseUrl == null || publicBaseUrl.isBlank()
                ? properties.getPublicBaseUrl()
                : publicBaseUrl;
        return buildBucketObjectUrl(base, properties.getCheckinBucket(), objectKey.replaceAll("^/+", ""));
    }

    /**
     * 上传用户导出文件至 export bucket，Object Key：{@code {userId}/{fileName}}
     */
    public String uploadExportFile(String userId, String fileName, InputStream inputStream,
                                   long size, String contentType) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(400, "用户 ID 无效");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new BusinessException(400, "文件名无效");
        }
        String objectKey = userId + "/" + fileName.replaceAll("^/+", "");
        String bucket = properties.getExportBucket();
        try {
            ensureBucket(bucket, true);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(inputStream, size, -1)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .build());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "导出文件上传失败: " + e.getMessage());
        }
        return buildExportFileUrl(userId, fileName);
    }

    public String buildExportFileUrl(String userId, String fileName) {
        return buildBucketObjectUrl(properties.getExportBucket(), userId + "/" + fileName.replaceAll("^/+", ""));
    }

    /**
     * 上传 STT 临时音频（供阿里云 Fun-ASR 通过公网 URL 拉取），Object Key：{@code temp/{uuid}/{filename}}
     */
    public SttAudioUploadResult uploadSttTempAudio(java.io.InputStream inputStream, long size,
                                                    String filename, String contentType,
                                                    String publicBaseUrl) {
        if (filename == null || filename.isBlank()) {
            throw new BusinessException(400, "文件名无效");
        }
        String safeName = filename.replaceAll("^/+", "").replaceAll(".*[/\\\\]", "");
        if (safeName.isBlank()) {
            safeName = "voice.wav";
        }
        String objectKey = "temp/" + java.util.UUID.randomUUID() + "/" + safeName;
        String bucket = properties.getSttBucket();
        try {
            ensureBucket(bucket, true);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(inputStream, size, -1)
                    .contentType(contentType != null ? contentType : "audio/wav")
                    .build());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "音频临时上传失败: " + e.getMessage());
        }
        String base = publicBaseUrl == null || publicBaseUrl.isBlank()
                ? properties.getPublicBaseUrl()
                : publicBaseUrl;
        return new SttAudioUploadResult(objectKey, buildBucketObjectUrl(base, bucket, objectKey));
    }

    public void deleteSttTempAudio(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getSttBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            log.warn("删除 STT 临时音频失败: key={}, err={}", objectKey, e.getMessage());
        }
    }

    private CheckinImageUploadResult uploadCheckinUserImage(String folder, String userId, String fileBaseName,
                                                            InputStream inputStream, long size, String contentType) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(400, "用户 ID 无效");
        }
        if (fileBaseName == null || fileBaseName.isBlank()) {
            throw new BusinessException(400, "文件名无效");
        }
        String objectKey = folder + "/" + userId + "/" + fileBaseName + ".jpg";
        return putCheckinObject(objectKey, inputStream, size, contentType);
    }

    private CheckinImageUploadResult uploadCheckinImage(String folder, String objectName, InputStream inputStream,
                                                          long size, String contentType) {
        if (objectName == null || objectName.isBlank()) {
            throw new BusinessException(400, "对象名无效");
        }
        String objectKey = folder + "/" + objectName + ".jpg";
        return putCheckinObject(objectKey, inputStream, size, contentType);
    }

    private CheckinImageUploadResult putCheckinObject(String objectKey, InputStream inputStream,
                                                        long size, String contentType) {
        String bucket = properties.getCheckinBucket();
        try {
            ensureBucket(bucket, true);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(inputStream, size, -1)
                    .contentType(contentType != null ? contentType : "image/jpeg")
                    .build());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "打卡图片上传失败: " + e.getMessage());
        }
        return new CheckinImageUploadResult(objectKey, buildCheckinImageUrl(objectKey));
    }

    /**
     * 生成用户上传图片文件名（不含扩展名）：upload_{yyyyMMddHHmmss}_{8位随机}
     */
    public static String buildUserUploadFileName() {
        String ts = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String suffix = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "upload_" + ts + "_" + suffix;
    }

    private String buildBucketObjectUrl(String bucket, String objectKey) {
        return buildBucketObjectUrl(properties.getPublicBaseUrl(), bucket, objectKey);
    }

    private static String buildBucketObjectUrl(String publicBaseUrl, String bucket, String objectKey) {
        String base = publicBaseUrl.replaceAll("/+$", "");
        return base + "/" + bucket + "/" + objectKey.replaceAll("^/+", "");
    }

    private void ensureBucket(String bucket, boolean failOnError) {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(bucket)
                    .config(buildPublicReadPolicy(bucket))
                    .build());
        } catch (Exception e) {
            log.warn("MinIO bucket [{}] 初始化失败: {}", bucket, e.getMessage());
            if (failOnError) {
                throw new BusinessException(500, "MinIO 不可用: " + e.getMessage());
            }
        }
    }

    public record CheckinImageUploadResult(String objectKey, String imageUrl) {}

    public record SttAudioUploadResult(String objectKey, String publicUrl) {}

    private String buildPublicReadPolicy(String bucket) {
        return """
                {
                  "Version": "2012-10-17",
                  "Statement": [{
                    "Effect": "Allow",
                    "Principal": {"AWS": ["*"]},
                    "Action": ["s3:GetObject"],
                    "Resource": ["arn:aws:s3:::%s/*"]
                  }]
                }
                """.formatted(bucket);
    }

    private String normalizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "http://localhost:9000";
        }
        return endpoint.replaceAll("/+$", "");
    }
}
