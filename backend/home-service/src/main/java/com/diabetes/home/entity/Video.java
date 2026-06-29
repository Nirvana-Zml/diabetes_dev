package com.diabetes.home.entity;

import java.time.LocalTime;

public class Video {

    private String videoId;
    private String title;
    private LocalTime duration;

    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalTime getDuration() { return duration; }
    public void setDuration(LocalTime duration) { this.duration = duration; }
}
