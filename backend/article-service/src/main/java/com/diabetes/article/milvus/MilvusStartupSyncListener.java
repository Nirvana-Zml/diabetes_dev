package com.diabetes.article.milvus;

import com.diabetes.article.config.MilvusProperties;
import com.diabetes.article.service.ArticleVectorSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class MilvusStartupSyncListener {

    private static final Logger log = LoggerFactory.getLogger(MilvusStartupSyncListener.class);

    private final MilvusProperties properties;
    private final MilvusArticleClient milvusClient;
    private final ArticleVectorSyncService vectorSyncService;

    public MilvusStartupSyncListener(MilvusProperties properties,
                                     MilvusArticleClient milvusClient,
                                     ArticleVectorSyncService vectorSyncService) {
        this.properties = properties;
        this.milvusClient = milvusClient;
        this.vectorSyncService = vectorSyncService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!properties.isEnabled() || !properties.isSyncOnStartup() || !milvusClient.isReady()) {
            return;
        }
        log.info("Milvus 启动同步已发布资讯向量...");
        vectorSyncService.syncAllPublished();
    }
}
