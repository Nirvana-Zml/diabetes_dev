package com.diabetes.common.redis;

/**
 * 业务 Redis Key 前缀，与 Dify 等外部组件隔离。
 */
public final class RedisKeys {

    public static final String PREFIX = "diabetes:";

    private RedisKeys() {
    }

    public static String checkinToday(String userId, String date) {
        return PREFIX + "checkin:today:" + userId + ":" + date;
    }

    public static String checkinStats(String userId, String range) {
        return PREFIX + "checkin:stats:" + userId + ":" + range;
    }

    public static String articleRecommend(int page, int size) {
        return PREFIX + "article:recommend:" + page + ":" + size;
    }

    public static String articleRecPopular(int page, int size) {
        return PREFIX + "article:rec:popular:" + page + ":" + size;
    }

    public static String articleRecPersonalized(String userId, int page, int size) {
        return PREFIX + "article:rec:user:" + userId + ":" + page + ":" + size;
    }

    public static String articleRecPersonalizedPattern(String userId) {
        return PREFIX + "article:rec:user:" + userId + ":*";
    }

    public static String articleRecPopularPattern() {
        return PREFIX + "article:rec:popular:*";
    }

    public static String articleList(Integer category, int page, int size) {
        return PREFIX + "article:list:" + (category == null ? "all" : category) + ":" + page + ":" + size;
    }

    public static String articleRecommendPattern() {
        return PREFIX + "article:recommend:*";
    }

    public static String articleListPattern() {
        return PREFIX + "article:list:*";
    }
}
