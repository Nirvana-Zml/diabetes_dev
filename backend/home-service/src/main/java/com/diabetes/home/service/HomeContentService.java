package com.diabetes.home.service;

import com.diabetes.home.entity.Banner;
import com.diabetes.home.entity.Video;
import com.diabetes.home.mapper.ResourceMapper;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HomeContentService {

    private final ResourceMapper resourceMapper;

    public HomeContentService(ResourceMapper resourceMapper) {
        this.resourceMapper = resourceMapper;
    }

    public Map<String, Object> getHomeContent() {
        List<Banner> banners = resourceMapper.findActiveBanners();
        List<Video> videos = resourceMapper.findActiveVideos();
        return Map.of(
                "banners", banners.stream().map(this::toBannerMap).toList(),
                "videos", videos.stream().map(this::toVideoMap).toList()
        );
    }

    private Map<String, Object> toBannerMap(Banner banner) {
        Map<String, Object> map = new HashMap<>();
        map.put("bannerId", banner.getBannerId());
        map.put("title", banner.getTitle());
        return map;
    }

    private Map<String, Object> toVideoMap(Video video) {
        Map<String, Object> map = new HashMap<>();
        map.put("videoId", video.getVideoId());
        map.put("title", video.getTitle());
        map.put("duration", formatDuration(video.getDuration()));
        return map;
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
}
