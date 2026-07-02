package com.diabetes.article.milvus;

import com.diabetes.article.config.MilvusProperties;
import com.diabetes.article.service.ArticleVectorSyncService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class MilvusStartupSyncListenerTest {

    @Test
    void onReady_skipsWhenDisabledOrNotReady() {
        MilvusProperties properties = new MilvusProperties();
        MilvusArticleClient client = mock(MilvusArticleClient.class);
        ArticleVectorSyncService syncService = mock(ArticleVectorSyncService.class);
        MilvusStartupSyncListener listener = new MilvusStartupSyncListener(properties, client, syncService);

        listener.onReady();
        verifyNoInteractions(syncService);

        properties.setEnabled(true);
        properties.setSyncOnStartup(false);
        listener.onReady();
        verifyNoInteractions(syncService);

        properties.setSyncOnStartup(true);
        when(client.isReady()).thenReturn(false);
        listener.onReady();
        verifyNoInteractions(syncService);
    }

    @Test
    void onReady_syncsWhenMilvusReady() {
        MilvusProperties properties = new MilvusProperties();
        properties.setEnabled(true);
        properties.setSyncOnStartup(true);
        MilvusArticleClient client = mock(MilvusArticleClient.class);
        ArticleVectorSyncService syncService = mock(ArticleVectorSyncService.class);
        when(client.isReady()).thenReturn(true);

        new MilvusStartupSyncListener(properties, client, syncService).onReady();

        verify(syncService).syncAllPublished();
    }
}
