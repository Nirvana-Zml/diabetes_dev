package com.diabetes.checkin.service;

import com.diabetes.checkin.dto.ImageUploadResponse;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.storage.MinioStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class CheckinImageService {

    private static final long MAX_IMAGE_BYTES = 2 * 1024 * 1024;

    private final MinioStorageService minioStorageService;

    public CheckinImageService(MinioStorageService minioStorageService) {
        this.minioStorageService = minioStorageService;
    }

    public ImageUploadResponse upload(String userId, String type, MultipartFile file) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择图片文件");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException(400, "图片大小不能超过 2MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException(400, "仅支持图片格式");
        }

        String fileBaseName = MinioStorageService.buildUserUploadFileName();
        MinioStorageService.CheckinImageUploadResult result;
        try {
            result = switch (type) {
                case "food" -> minioStorageService.uploadCheckinFoodUser(
                        userId, fileBaseName, file.getInputStream(), file.getSize(), contentType);
                case "medical" -> minioStorageService.uploadCheckinMedicalUser(
                        userId, fileBaseName, file.getInputStream(), file.getSize(), contentType);
                default -> throw new BusinessException(400, "type 参数无效，应为 food 或 medical");
            };
        } catch (IOException e) {
            throw new BusinessException(500, "读取上传文件失败");
        }
        return new ImageUploadResponse(fileBaseName, result.objectKey(), result.imageUrl());
    }
}
