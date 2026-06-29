package com.diabetes.common.client;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 科普首页模块占位客户端：供其他微服务调用，返回空数据结构，不阻断业务流程。
 */
@Component
public class HomeServiceClient {

    public HomeContentPlaceholder getHomeContent() {
        return new HomeContentPlaceholder(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    public RecommendPlaceholder getRecommend(int page, int size) {
        return new RecommendPlaceholder(Collections.emptyList(), 0);
    }

    public record HomeContentPlaceholder(List<Object> banners, List<Object> categories, List<Object> videos) {}

    public record RecommendPlaceholder(List<Object> articles, int total) {}
}
