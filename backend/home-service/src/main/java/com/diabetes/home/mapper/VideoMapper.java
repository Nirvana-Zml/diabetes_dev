package com.diabetes.home.mapper;

import com.diabetes.home.entity.Video;
import org.apache.ibatis.annotations.Param;

import java.time.LocalTime;
import java.util.List;

public interface VideoMapper {

    List<Video> findAdminList(@Param("keyword") String keyword,
                              @Param("offset") int offset,
                              @Param("size") int size);

    int countAdminList(@Param("keyword") String keyword);

    Video findById(@Param("videoId") String videoId);

    String findLatestVideoId();

    int insert(@Param("videoId") String videoId,
               @Param("title") String title,
               @Param("duration") LocalTime duration);

    int update(@Param("videoId") String videoId, @Param("title") String title);

    int updateDuration(@Param("videoId") String videoId, @Param("duration") LocalTime duration);

    int softDelete(@Param("videoId") String videoId);
}
