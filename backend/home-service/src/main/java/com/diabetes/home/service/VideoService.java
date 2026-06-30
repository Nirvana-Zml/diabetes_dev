package com.diabetes.home.service;

import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.storage.MinioStorageService;
import com.diabetes.home.entity.Video;
import com.diabetes.home.mapper.VideoMapper;
import com.diabetes.home.util.VideoDurationParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class VideoService {

    private static final long MAX_COVER_BYTES = 5L * 1024 * 1024;
    private static final long MAX_VIDEO_BYTES = 500L * 1024 * 1024;
    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile("^video_(\\d+)$");

    private final VideoMapper videoMapper;
    private final MinioStorageService minioStorageService;

    public VideoService(VideoMapper videoMapper, MinioStorageService minioStorageService) {
        this.videoMapper = videoMapper;
        this.minioStorageService = minioStorageService;
    }

    public Map<String, Object> adminList(String keyword, int page, int size) {
        int offset = (page - 1) * size;
        List<Video> videos = videoMapper.findAdminList(blankToNull(keyword), offset, size);
        return Map.of(
                "videos", videos.stream().map(this::toAdminCard).toList(),
                "total", videoMapper.countAdminList(blankToNull(keyword))
        );
    }

    public Map<String, Object> adminDetail(String videoId) {
        Video video = requireVideo(videoId);
        return toAdminDetail(video);
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        String title = stringVal(body, "title");
        if (title.isBlank()) {
            throw new BusinessException(400, "标题不能为空");
        }
        if (title.length() > 100) {
            throw new BusinessException(400, "标题不能超过 100 字");
        }
        String videoId = nextVideoId();
        videoMapper.insert(videoId, title, null);
        return Map.of("videoId", videoId, "title", title);
    }

    @Transactional
    public Map<String, Object> update(String videoId, Map<String, Object> body) {
        requireVideo(videoId);
        String title = stringVal(body, "title");
        if (title.isBlank()) {
            throw new BusinessException(400, "标题不能为空");
        }
        if (title.length() > 100) {
            throw new BusinessException(400, "标题不能超过 100 字");
        }
        videoMapper.update(videoId, title);
        return Map.of("videoId", videoId, "title", title);
    }

    @Transactional
    public void delete(String videoId) {
        requireVideo(videoId);
        videoMapper.softDelete(videoId);
    }

    @Transactional
    public Map<String, Object> uploadCover(String videoId, MultipartFile file) {
        requireVideo(videoId);
        validateImageFile(file, MAX_COVER_BYTES, "封面");
        try {
            minioStorageService.uploadVideoCover(
                    videoId, file.getInputStream(), file.getSize(), file.getContentType());
            String coverUrl = minioStorageService.buildVideoCoverUrl(videoId);
            return Map.of("coverUrl", coverUrl + "?v=" + System.currentTimeMillis());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "封面上传失败: " + e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> uploadVideoFile(String videoId, MultipartFile file) {
        requireVideo(videoId);
        validateVideoFile(file);
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("video-upload-", ".mp4");
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            LocalTime duration = VideoDurationParser.parseMp4Duration(tempFile);
            try (InputStream uploadStream = Files.newInputStream(tempFile)) {
                minioStorageService.uploadVideo(
                        videoId, uploadStream, Files.size(tempFile), file.getContentType());
            }
            videoMapper.updateDuration(videoId, duration);
            String videoUrl = minioStorageService.buildVideoUrl(videoId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("videoUrl", videoUrl + "?v=" + System.currentTimeMillis());
            result.put("duration", formatDuration(duration));
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "视频上传失败: " + e.getMessage());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private Video requireVideo(String videoId) {
        Video video = videoMapper.findById(videoId);
        if (video == null) {
            throw new BusinessException(404, "视频不存在");
        }
        return video;
    }

    private String nextVideoId() {
        String latestId = videoMapper.findLatestVideoId();
        int next = 1;
        if (latestId != null) {
            Matcher matcher = VIDEO_ID_PATTERN.matcher(latestId);
            if (matcher.matches()) {
                next = Integer.parseInt(matcher.group(1)) + 1;
            }
        }
        return String.format("video_%03d", next);
    }

    private void validateImageFile(MultipartFile file, long maxBytes, String label) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择" + label + "图片");
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessException(400, label + "图片大小不能超过 " + (maxBytes / 1024 / 1024) + "MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new BusinessException(400, "仅支持图片格式（JPG、PNG、WebP、GIF 等）");
        }
    }

    private void validateVideoFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择视频文件");
        }
        if (file.getSize() > MAX_VIDEO_BYTES) {
            throw new BusinessException(400, "视频大小不能超过 500MB");
        }
        String originalName = file.getOriginalFilename();
        String contentType = file.getContentType();
        boolean mp4ByName = originalName != null && originalName.toLowerCase().endsWith(".mp4");
        boolean mp4ByType = contentType != null && contentType.toLowerCase().contains("mp4");
        if (!mp4ByName && !mp4ByType) {
            throw new BusinessException(400, "仅支持 MP4 格式视频");
        }
    }

    private Map<String, Object> toAdminCard(Video video) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("videoId", video.getVideoId());
        map.put("title", video.getTitle());
        map.put("duration", formatDuration(video.getDuration()));
        map.put("coverUrl", minioStorageService.buildVideoCoverUrl(video.getVideoId()));
        map.put("videoUrl", minioStorageService.buildVideoUrl(video.getVideoId()));
        map.put("createdAt", video.getCreatedAt());
        map.put("updatedAt", video.getUpdatedAt());
        return map;
    }

    private Map<String, Object> toAdminDetail(Video video) {
        return new HashMap<>(toAdminCard(video));
    }

    private String formatDuration(LocalTime duration) {
        if (duration == null) {
            return "";
        }
        if (duration.getHour() > 0) {
            return String.format("%d:%02d:%02d", duration.getHour(), duration.getMinute(), duration.getSecond());
        }
        return String.format("%02d:%02d", duration.getMinute(), duration.getSecond());
    }

    private static String stringVal(Map<String, Object> body, String key) {
        Object val = body.get(key);
        return val == null ? "" : val.toString().trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
