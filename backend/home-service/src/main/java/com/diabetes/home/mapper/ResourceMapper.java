package com.diabetes.home.mapper;

import com.diabetes.home.entity.Banner;
import com.diabetes.home.entity.Video;

import java.util.List;

public interface ResourceMapper {

    List<Banner> findActiveBanners();

    List<Video> findActiveVideos();
}
