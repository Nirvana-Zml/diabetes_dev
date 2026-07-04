package com.diabetes.article.milvus;

import com.diabetes.article.config.MilvusProperties;
import com.diabetes.article.service.ArticleVectorSyncService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class MilvusStartupSyncListenerTest {

    @Test
    void testOnReadyWhenMilvusDisabled() {
        MilvusProperties properties = mock(MilvusProperties.class);
        when(properties.isEnabled()).thenReturn(false);
        
        MilvusArticleClient milvusClient = mock(MilvusArticleClient.class);
        ArticleVectorSyncService vectorSyncService = mock(ArticleVectorSyncService.class);
        
        MilvusStartupSyncListener listener = new MilvusStartupSyncListener(properties, milvusClient, vectorSyncService);
        listener.onReady();
        
        verify(vectorSyncService, never()).syncAllPublished();
    }

    @Test
    void testOnReadyWhenSyncOnStartupDisabled() {
        MilvusProperties properties = mock(MilvusProperties.class);
        when(properties.isEnabled()).thenReturn(true);
        when(properties.isSyncOnStartup()).thenReturn(false);
        
        MilvusArticleClient milvusClient = mock(MilvusArticleClient.class);
        ArticleVectorSyncService vectorSyncService = mock(ArticleVectorSyncService.class);
        
        MilvusStartupSyncListener listener = new MilvusStartupSyncListener(properties, milvusClient, vectorSyncService);
        listener.onReady();
        
        verify(vectorSyncService, never()).syncAllPublished();
    }

    @Test
    void testOnReadyWhenMilvusNotReady() {
        MilvusProperties properties = mock(MilvusProperties.class);
        when(properties.isEnabled()).thenReturn(true);
        when(properties.isSyncOnStartup()).thenReturn(true);
        
        MilvusArticleClient milvusClient = mock(MilvusArticleClient.class);
        when(milvusClient.isReady()).thenReturn(false);
        
        ArticleVectorSyncService vectorSyncService = mock(ArticleVectorSyncService.class);
        
        MilvusStartupSyncListener listener = new MilvusStartupSyncListener(properties, milvusClient, vectorSyncService);
        listener.onReady();
        
        verify(vectorSyncService, never()).syncAllPublished();
    }

    @Test
    void testOnReadyWhenAllConditionsMet() {
        MilvusProperties properties = mock(MilvusProperties.class);
        when(properties.isEnabled()).thenReturn(true);
        when(properties.isSyncOnStartup()).thenReturn(true);
        
        MilvusArticleClient milvusClient = mock(MilvusArticleClient.class);
        when(milvusClient.isReady()).thenReturn(true);
        
        ArticleVectorSyncService vectorSyncService = mock(ArticleVectorSyncService.class);
        
        MilvusStartupSyncListener listener = new MilvusStartupSyncListener(properties, milvusClient, vectorSyncService);
        listener.onReady();
        
        verify(vectorSyncService, times(1)).syncAllPublished();
    }
}
