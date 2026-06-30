package com.diabetes.home.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.home.service.VideoService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/videos")
public class AdminVideoController {

    private final VideoService videoService;

    public AdminVideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(videoService.adminList(keyword, page, size));
    }

    @GetMapping("/{videoId}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String videoId) {
        return ApiResponse.ok(videoService.adminDetail(videoId));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(videoService.create(body));
    }

    @PutMapping("/{videoId}")
    public ApiResponse<Map<String, Object>> update(@PathVariable String videoId,
                                                   @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(videoService.update(videoId, body));
    }

    @DeleteMapping("/{videoId}")
    public ApiResponse<String> delete(@PathVariable String videoId) {
        videoService.delete(videoId);
        return ApiResponse.ok("删除成功", "删除成功");
    }

    /** 上传封面图（MinIO video-cover bucket，对象名 {videoId}.jpg） */
    @PostMapping(value = "/{videoId}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> uploadCover(@PathVariable String videoId,
                                                        @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(videoService.uploadCover(videoId, file));
    }

    /** 上传视频本体（MinIO video bucket，对象名 {videoId}.mp4），并自动解析时长 */
    @PostMapping(value = "/{videoId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> uploadFile(@PathVariable String videoId,
                                                       @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(videoService.uploadVideoFile(videoId, file));
    }
}
