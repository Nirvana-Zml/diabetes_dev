package com.diabetes.user.service;

import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.storage.MinioStorageService;
import com.diabetes.user.dto.*;
import com.diabetes.user.entity.User;
import com.diabetes.user.mapper.UserMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Map;

@Service
public class UserProfileService {

    private static final long MAX_AVATAR_BYTES = 2 * 1024 * 1024;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final MinioStorageService minioStorageService;

    public UserProfileService(UserMapper userMapper,
                              PasswordEncoder passwordEncoder,
                              ObjectMapper objectMapper,
                              MinioStorageService minioStorageService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.minioStorageService = minioStorageService;
    }

    public User getUserOrThrow(String userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return user;
    }

    public UserProfileResponse getProfile(String userId) {
        return toProfileResponse(getUserOrThrow(userId));
    }

    public UserOverviewResponse getOverview(String userId) {
        User user = getUserOrThrow(userId);
        return new UserOverviewResponse(
                user.getUserId(),
                user.getUsername(),
                user.getNickname(),
                resolveAvatarDisplayUrl(user),
                user.getPoints(),
                user.getPhone()
        );
    }

    @Transactional
    public UserProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = getUserOrThrow(userId);
        if (request.nickname() != null) {
            user.setNickname(request.nickname());
        }
        if (request.phone() != null) {
            User existing = userMapper.findByPhone(request.phone());
            if (existing != null && !existing.getUserId().equals(userId)) {
                throw new BusinessException(400, "手机号已被其他账号使用");
            }
            user.setPhone(request.phone());
        }
        if (request.email() != null) {
            user.setEmail(request.email());
        }
        if (request.gender() != null) {
            user.setGender(request.gender());
        }
        if (request.birth_date() != null && !request.birth_date().isBlank()) {
            user.setBirthDate(LocalDate.parse(request.birth_date()));
        }
        userMapper.updateProfile(user);
        return getProfile(userId);
    }

    @Transactional
    public AvatarUploadResponse uploadAvatar(String userId, MultipartFile file) {
        getUserOrThrow(userId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择头像图片");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new BusinessException(400, "图片大小不能超过 2MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new BusinessException(400, "仅支持图片格式（JPG、PNG、WebP、GIF 等）");
        }
        try {
            minioStorageService.uploadProfileAvatar(
                    userId, file.getInputStream(), file.getSize(), contentType);
            String avatarId = userId + ".jpg";
            userMapper.updateAvatarId(userId, avatarId);
            String displayUrl = withAvatarCacheBuster(
                    minioStorageService.buildProfileAvatarUrl(userId),
                    java.time.LocalDateTime.now());
            return new AvatarUploadResponse(displayUrl);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "头像上传失败: " + e.getMessage());
        }
    }

    @Transactional
    public void changePassword(String userId, ChangePasswordRequest request) {
        User user = getUserOrThrow(userId);
        if (!passwordEncoder.matches(request.old_password(), user.getPasswordHash())) {
            throw new BusinessException(400, "原密码错误");
        }
        userMapper.updatePassword(userId, passwordEncoder.encode(request.new_password()));
    }

    @Transactional
    public UserProfileResponse updatePrivacy(String userId, PrivacySettingsRequest request) {
        getUserOrThrow(userId);
        if (request.settings() == null || request.settings().isEmpty()) {
            throw new BusinessException(400, "隐私设置不能为空");
        }
        try {
            String json = objectMapper.writeValueAsString(request.settings());
            userMapper.updatePrivacy(userId, json);
        } catch (JsonProcessingException e) {
            throw new BusinessException(400, "隐私设置格式错误");
        }
        return getProfile(userId);
    }

    private UserProfileResponse toProfileResponse(User user) {
        Object privacy = null;
        if (user.getPrivacySettings() != null && !user.getPrivacySettings().isBlank()) {
            try {
                privacy = objectMapper.readValue(user.getPrivacySettings(), new TypeReference<Map<String, Object>>() {});
            } catch (JsonProcessingException ignored) {
                privacy = user.getPrivacySettings();
            }
        }
        return new UserProfileResponse(
                user.getUserId(),
                user.getUsername(),
                user.getPhone(),
                user.getEmail(),
                resolveAvatarDisplayUrl(user),
                user.getNickname(),
                user.getGender(),
                user.getBirthDate() != null ? user.getBirthDate().toString() : null,
                user.getPoints(),
                privacy,
                user.getCreatedAt() != null ? user.getCreatedAt().toString() : null
        );
    }

    /** 将 DB 中的 AVATAR_ID 解析为前端可访问 URL */
    private String resolveAvatarDisplayUrl(User user) {
        if (user.getAvatarId() == null || user.getAvatarId().isBlank()) {
            return "";
        }
        String avatarId = user.getAvatarId().trim();
        if (avatarId.startsWith("http://") || avatarId.startsWith("https://")) {
            return withAvatarCacheBuster(avatarId, user.getUpdatedAt());
        }
        return withAvatarCacheBuster(
                minioStorageService.buildProfileAvatarUrl(user.getUserId()),
                user.getUpdatedAt());
    }

    private String withAvatarCacheBuster(String avatarUrl, java.time.LocalDateTime updatedAt) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return avatarUrl;
        }
        if (avatarUrl.contains("?v=")) {
            return avatarUrl;
        }
        long version = updatedAt != null
                ? updatedAt.atZone(java.time.ZoneId.systemDefault()).toEpochSecond()
                : System.currentTimeMillis() / 1000;
        return avatarUrl + "?v=" + version;
    }
}
