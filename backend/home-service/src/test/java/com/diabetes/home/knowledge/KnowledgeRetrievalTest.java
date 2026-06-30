package com.diabetes.home.knowledge;

import com.diabetes.home.config.KnowledgeMilvusProperties;
import com.diabetes.home.dify.DifyQaChatContract;
import com.diabetes.home.milvus.KnowledgeEmbeddingService;
import com.diabetes.home.milvus.MilvusKnowledgeClient;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KnowledgeRetrievalTest {

    @Test
    void semanticSearchHandlesBlankNotReadyFallbackAndConfiguredTopK() {
        KnowledgeMilvusProperties props = new KnowledgeMilvusProperties();
        props.setSearchTopK(7);
        props.setScoreThreshold(0.6);
        MilvusKnowledgeClient milvus = mock(MilvusKnowledgeClient.class);
        KnowledgeEmbeddingService embedding = mock(KnowledgeEmbeddingService.class);
        KnowledgeRetrieval retrieval = new KnowledgeRetrieval(props, milvus, embedding);

        assertTrue(retrieval.semanticSearch(null, 5).isEmpty());
        assertTrue(retrieval.semanticSearch(" ", 5).isEmpty());
        when(milvus.isReady()).thenReturn(false);
        assertTrue(retrieval.semanticSearch("query", 5).isEmpty());

        when(milvus.isReady()).thenReturn(true);
        when(embedding.embed("query")).thenReturn(new float[]{1, 0});
        DocumentChunk chunk = new DocumentChunk("1", "content", "title", "src", "guideline", 0.9);
        when(milvus.search(any(), eq(7), eq(DifyQaChatContract.PHASE1_DOC_TYPE), eq(0.6))).thenReturn(List.of());
        when(milvus.search(any(), eq(7), isNull(), eq(0.6))).thenReturn(List.of(chunk));
        assertEquals(List.of(chunk), retrieval.semanticSearch("query", 0));

        when(milvus.search(any(), eq(2), eq(DifyQaChatContract.PHASE1_DOC_TYPE), eq(0.6))).thenReturn(List.of(chunk));
        assertEquals(List.of(chunk), retrieval.semanticSearch("query", 2));

        when(milvus.search(any(), eq(3), eq(DifyQaChatContract.PHASE1_DOC_TYPE), eq(0.6))).thenReturn(List.of());
        when(milvus.search(any(), eq(3), isNull(), eq(0.6))).thenReturn(List.of());
        assertTrue(retrieval.semanticSearch("query", 3).isEmpty());
    }

    @Test
    void formatsKnowledgeContextSourcesAndTruncate() throws Exception {
        KnowledgeRetrieval retrieval = new KnowledgeRetrieval(new KnowledgeMilvusProperties(),
                mock(MilvusKnowledgeClient.class), mock(KnowledgeEmbeddingService.class));
        assertEquals("", retrieval.buildKnowledgeContext(null));
        assertEquals("", retrieval.buildKnowledgeContext(List.of()));
        assertEquals(List.of(), retrieval.extractSources(null));
        assertEquals(List.of(), retrieval.extractSources(List.of()));

        List<DocumentChunk> chunks = List.of(
                new DocumentChunk("1", " content ", "标题", "src", "type", 0.8),
                new DocumentChunk("2", null, " ", "src", "type", 0.7),
                new DocumentChunk("3", "abc", "标题", "src", "type", 0.6),
                new DocumentChunk("4", "def", null, "src", "type", 0.5)
        );
        String context = retrieval.buildKnowledgeContext(chunks);
        assertTrue(context.contains("【片段1 | 来源: 标题 | 相似度: 0.800】"));
        assertTrue(context.contains("未知来源"));
        assertTrue(context.contains("\n\n"));
        assertEquals(List.of("标题"), retrieval.extractSources(chunks));

        Method truncate = KnowledgeRetrieval.class.getDeclaredMethod("truncate", String.class, int.class);
        truncate.setAccessible(true);
        assertEquals("", truncate.invoke(null, null, 3));
        assertEquals("abc", truncate.invoke(null, "abc", 3));
        assertEquals("abc...", truncate.invoke(null, "abcdef", 3));
    }
}
