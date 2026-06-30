package com.diabetes.home.controller;

import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.storage.MinioProperties;
import com.diabetes.common.storage.MinioStorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.util.Set;

@RestController
@RequestMapping("/api/v2/home/media")
public class HomeMediaController {

    private static final Set<String> ALLOWED_BUCKETS = Set.of("banner", "video-cover", "video", "avatar");

    private final MinioStorageService minioStorageService;
    private final MinioProperties minioProperties;

    public HomeMediaController(MinioStorageService minioStorageService, MinioProperties minioProperties) {
        this.minioStorageService = minioStorageService;
        this.minioProperties = minioProperties;
    }

    @GetMapping("/{bucket}/{filename:.+}")
    public ResponseEntity<InputStreamResource> getMedia(@PathVariable String bucket,
                                                        @PathVariable String filename) {
        if (!ALLOWED_BUCKETS.contains(bucket)) {
            throw new BusinessException(404, "资源不存在");
        }
        String resolvedBucket = resolveBucketName(bucket);
        InputStream stream = minioStorageService.getObject(resolvedBucket, filename);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .contentType(resolveMediaType(filename))
                .body(new InputStreamResource(stream));
    }

    private String resolveBucketName(String bucket) {
        return switch (bucket) {
            case "banner" -> minioProperties.getBannerBucket();
            case "video-cover" -> minioProperties.getVideoCoverBucket();
            case "video" -> minioProperties.getVideoBucket();
            case "avatar" -> minioProperties.getAvatarBucket();
            default -> bucket;
        };
    }

    private MediaType resolveMediaType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".mp4")) {
            return MediaType.parseMediaType("video/mp4");
        }
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }
}
