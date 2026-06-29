package com.diabetes.home.knowledge;

/**
 * Milvus 检索命中的知识片段。
 */
public record DocumentChunk(
        String id,
        String content,
        String docTitle,
        String docSource,
        String docType,
        double score
) {
}
