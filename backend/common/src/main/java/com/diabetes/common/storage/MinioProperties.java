package com.diabetes.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    /** MinIO API 地址，如 http://localhost:9000 */
    private String endpoint = "http://localhost:9000";

    private String accessKey = "minio";

    private String secretKey = "minio123456";

    /** 用户头像 bucket */
    private String profileBucket = "profile";

    /** 健康打卡图片 bucket */
    private String checkinBucket = "checkin";

    /** 资讯封面 bucket */
    private String articleBucket = "article";

    /** 首页轮播图 bucket */
    private String bannerBucket = "banner";

    /** 科普视频封面 bucket */
    private String videoCoverBucket = "video-cover";

    /** AI 医生头像 bucket */
    private String avatarBucket = "avatar";

    /** 浏览器可访问的基础 URL，如 http://localhost:9000 */
    private String publicBaseUrl = "http://localhost:9000";

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getProfileBucket() { return profileBucket; }
    public void setProfileBucket(String profileBucket) { this.profileBucket = profileBucket; }
    public String getCheckinBucket() { return checkinBucket; }
    public void setCheckinBucket(String checkinBucket) { this.checkinBucket = checkinBucket; }
    public String getArticleBucket() { return articleBucket; }
    public void setArticleBucket(String articleBucket) { this.articleBucket = articleBucket; }
    public String getBannerBucket() { return bannerBucket; }
    public void setBannerBucket(String bannerBucket) { this.bannerBucket = bannerBucket; }
    public String getVideoCoverBucket() { return videoCoverBucket; }
    public void setVideoCoverBucket(String videoCoverBucket) { this.videoCoverBucket = videoCoverBucket; }
    public String getAvatarBucket() { return avatarBucket; }
    public void setAvatarBucket(String avatarBucket) { this.avatarBucket = avatarBucket; }
    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }
}
