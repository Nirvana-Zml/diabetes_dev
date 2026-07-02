package com.diabetes.article.service;

import com.diabetes.article.config.RecommendProperties;
import com.diabetes.article.entity.ArticleCandidate;
import com.diabetes.article.entity.ScoredArticle;
import com.diabetes.article.mapper.RecommendMapper;
import com.diabetes.common.client.HealthServiceClient;
import com.diabetes.common.dify.DifyClient;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.storage.MinioStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ArticleRecommendServiceReflectionTest {

    private final RecommendMapper recommendMapper = mock(RecommendMapper.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final MinioStorageService minioStorageService = mock(MinioStorageService.class);
    private final HealthServiceClient healthServiceClient = mock(HealthServiceClient.class);
    private final DifyClient difyClient = mock(DifyClient.class);
    private final RecommendProperties properties = new RecommendProperties();
    private final MilvusArticleSearchService milvusArticleSearchService = mock(MilvusArticleSearchService.class);

    private ArticleRecommendService service;
    private Class<?> ctxClass;

    @BeforeEach
    void setUp() throws Exception {
        properties.setPhase4DifyEnabled(true);
        service = new ArticleRecommendService(
                recommendMapper, redis, objectMapper, minioStorageService,
                healthServiceClient, difyClient, properties, milvusArticleSearchService,
                "http://dify.local", "dify-key", "inputs", "object", "blocking", "internal-key");
        when(redis.opsForValue()).thenReturn(valueOps);
        when(minioStorageService.buildArticleCoverUrl(anyString())).thenReturn("http://minio/cover.jpg");
        ctxClass = Class.forName("com.diabetes.article.service.ArticleRecommendService$RecommendContext");
    }

    @Test
    void applyPhase3_skipsBlankFingerprintAndBlankInterestText() throws Exception {
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);

        Object ctxBlankInterest = newContext("u1", "", Set.of());
        ArticleCandidate blankFp = new ArticleCandidate();
        blankFp.setArticleId("art_blank");
        blankFp.setTextFingerprint("");
        List<ScoredArticle> scoredBlank = new ArrayList<>(List.of(new ScoredArticle(blankFp)));
        assertEquals(1, invokePhase3(scoredBlank, ctxBlankInterest));

        Object ctxWithInterest = newContext("u1", "糖尿病 饮食 管理 控制", Set.of());
        ArticleCandidate withFp = candidate("art_match", "糖尿病 饮食 管理 血糖");
        withFp.setTextFingerprint("糖尿病 饮食 管理 血糖");
        List<ScoredArticle> scored = new ArrayList<>(List.of(new ScoredArticle(blankFp), new ScoredArticle(withFp)));
        assertEquals(3, invokePhase3(scored, ctxWithInterest));
    }

    @Test
    void applyPhase3_milvusEmptyHitsFallsBackToJaccard() throws Exception {
        when(milvusArticleSearchService.isAvailable()).thenReturn(true);
        when(milvusArticleSearchService.searchSimilar(anyString(), anyList(), anyInt())).thenReturn(Map.of());

        Object ctx = newContext("u1", "糖尿病 饮食", Set.of());
        ArticleCandidate c = candidate("art_1", "糖尿病 饮食 控制");
        c.setTextFingerprint("糖尿病 饮食 控制");
        List<ScoredArticle> scored = new ArrayList<>(List.of(new ScoredArticle(c)));
        assertEquals(3, invokePhase3(scored, ctx));
    }

    @Test
    void applyPhase4_restArticlesAppendedWhenDifyReturnsSubset() throws Exception {
        ArticleCandidate c1 = candidate("art_1", "摘要1");
        ArticleCandidate c2 = candidate("art_2", "摘要2");
        ArticleCandidate c3 = candidate("art_3", "摘要3");
        List<ScoredArticle> scored = new ArrayList<>(List.of(
                new ScoredArticle(c1), new ScoredArticle(c2), new ScoredArticle(c3)));
        Object ctx = newContext("u1", "兴趣", Set.of("饮食"));
        JsonNode response = objectMapper.readTree("""
                {"data":{"outputs":{"recommendations":[{"article_id":"art_1","reason":"top"}]}}}
                """);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(response);

        assertEquals(4, invokePhase4("u1", scored, ctx));
        assertEquals(3, scored.size());
        assertEquals("art_1", scored.get(0).getCandidate().getArticleId());
    }

    @Test
    void applyPhase3_nullInterestTextReturnsEarly() throws Exception {
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);
        Object ctx = newContext("u1", null, Set.of());
        ArticleCandidate c = candidate("art_1", "摘要");
        c.setTextFingerprint(null);
        assertEquals(1, invokePhase3(new ArrayList<>(List.of(new ScoredArticle(c))), ctx));
    }

    @Test
    void applyPhase3_nullFingerprintSkipped() throws Exception {
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);
        Object ctx = newContext("u1", "糖尿病 饮食", Set.of());
        ArticleCandidate nullFp = candidate("art_null", "x");
        nullFp.setTextFingerprint(null);
        ArticleCandidate match = candidate("art_match", "糖尿病 饮食");
        match.setTextFingerprint("糖尿病 饮食 控制");
        assertEquals(3, invokePhase3(new ArrayList<>(List.of(
                new ScoredArticle(nullFp), new ScoredArticle(match))), ctx));
    }

    @Test
    void buildDifyWorkflowInputs_flatMode() throws Exception {
        ArticleRecommendService flatService = new ArticleRecommendService(
                recommendMapper, redis, objectMapper, minioStorageService,
                healthServiceClient, difyClient, properties, milvusArticleSearchService,
                "http://dify.local", "key", "flat", "object", "blocking", "internal-key");
        Method m = ArticleRecommendService.class.getDeclaredMethod("buildDifyWorkflowInputs", Map.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> inputs = (Map<String, Object>) m.invoke(flatService, Map.of("user_id", "u1"));
        assertNotNull(inputs);
    }

    @Test
    void buildFingerprint_nullAndNonNullTags() throws Exception {
        Method m = ArticleRecommendService.class.getDeclaredMethod("buildFingerprint", ArticleCandidate.class);
        m.setAccessible(true);

        ArticleCandidate nullTags = candidate("art_1", "摘要");
        setCandidateTagsField(nullTags, null);
        assertFalse(((String) m.invoke(service, nullTags)).contains("饮食"));

        ArticleCandidate withTags = candidate("art_2", "摘要");
        withTags.setTags(List.of("饮食", "运动"));
        assertTrue(((String) m.invoke(service, withTags)).contains("饮食"));
    }

    @Test
    void buildInterestTags_allBranchCombinations() throws Exception {
        Object ctx = newContext("u1", "", Set.of());
        setField(ctx, "favoriteIds", Set.of("missing_fav", "fav_null_tags", "fav_with_tags"));
        setField(ctx, "recentReadIds", Set.of("missing_read", "read_null_tags", "read_with_tags"));

        ArticleCandidate favNullTags = candidate("fav_null_tags", "t");
        setCandidateTagsField(favNullTags, null);
        ArticleCandidate favWithTags = candidate("fav_with_tags", "t");
        favWithTags.setTags(List.of("饮食"));
        ArticleCandidate readNullTags = candidate("read_null_tags", "t");
        setCandidateTagsField(readNullTags, null);
        ArticleCandidate readWithTags = candidate("read_with_tags", "t");
        readWithTags.setTags(List.of("运动"));

        Method m = ArticleRecommendService.class.getDeclaredMethod("buildInterestTags", ctxClass, List.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> tags = (Set<String>) m.invoke(service, ctx,
                List.of(favNullTags, favWithTags, readNullTags, readWithTags));
        assertEquals(Set.of("饮食", "运动"), tags);
    }

    private void setCandidateTagsField(ArticleCandidate candidate, List<String> tags) throws Exception {
        Field tagsField = ArticleCandidate.class.getDeclaredField("tags");
        tagsField.setAccessible(true);
        tagsField.set(candidate, tags);
    }

    @Test
    void buildFingerprint_withTagsJoinsTagText() throws Exception {
        ArticleCandidate c = candidate("art_1", "摘要");
        c.setTags(List.of("饮食", "运动"));
        Method m = ArticleRecommendService.class.getDeclaredMethod("buildFingerprint", ArticleCandidate.class);
        m.setAccessible(true);
        assertTrue(((String) m.invoke(service, c)).contains("饮食"));
    }

    @Test
    void buildInterestText_duplicateCandidateIdsMerge() throws Exception {
        Object ctx = newContext("u1", "", Set.of());
        setField(ctx, "favoriteIds", Set.of("art_1"));
        ArticleCandidate dup1 = candidate("art_1", "a");
        ArticleCandidate dup2 = candidate("art_1", "b");
        Method m = ArticleRecommendService.class.getDeclaredMethod("buildInterestText", ctxClass, List.class);
        m.setAccessible(true);
        assertNotNull(m.invoke(service, ctx, List.of(dup1, dup2)));
    }

    @Test
    void toRecommendCard_includesRecReasonForPhase4() throws Exception {
        ArticleCandidate c = candidate("art_1", "s");
        ScoredArticle sa = new ScoredArticle(c);
        sa.setPhase(4);
        sa.setReason("精选理由");
        Method m = ArticleRecommendService.class.getDeclaredMethod("toRecommendCard", ScoredArticle.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> card = (Map<String, Object>) m.invoke(service, sa);
        assertEquals("精选理由", card.get("recReason"));
    }

    @Test
    void parseDifyRecommendations_skipsNullArticleId() throws Exception {
        Method m = ArticleRecommendService.class.getDeclaredMethod("parseDifyRecommendations", JsonNode.class);
        m.setAccessible(true);
        JsonNode node = objectMapper.readTree("""
                {"data":{"outputs":{"recommendations":[{"reason":"无ID"}]}}}
                """);
        @SuppressWarnings("unchecked")
        Map<String, String> map = (Map<String, String>) m.invoke(service, node);
        assertTrue(map.isEmpty());
    }

    @Test
    void resolveCoverImage_httpsAndMinioFallback() throws Exception {
        Method m = ArticleRecommendService.class.getDeclaredMethod("resolveCoverImage", ArticleCandidate.class);
        m.setAccessible(true);
        ArticleCandidate https = candidate("art_1", "s");
        https.setCoverImageId("https://cdn/c.jpg");
        assertEquals("https://cdn/c.jpg", m.invoke(service, https));

        ArticleCandidate relative = candidate("art_2", "s");
        relative.setCoverImageId("cover-id");
        assertEquals("http://minio/cover.jpg", m.invoke(service, relative));

        ArticleCandidate noCover = candidate("art_3", "s");
        assertEquals("http://minio/cover.jpg", m.invoke(service, noCover));
    }

    @Test
    void jaccardSimilarity_oneSideEmpty() throws Exception {
        Method m = ArticleRecommendService.class.getDeclaredMethod("jaccardSimilarity", Set.class, Set.class);
        m.setAccessible(true);
        assertEquals(0.0, (double) m.invoke(service, Set.of("a"), Set.of()));
    }

    @Test
    void blankToDefault_returnsTrimmedValue() throws Exception {
        Method m = ArticleRecommendService.class.getDeclaredMethod("blankToDefault", String.class, String.class);
        m.setAccessible(true);
        assertEquals("detail", m.invoke(service, "  detail  ", "list"));
    }

    @Test
    void recordReadRejectsBlankUserIdString() {
        assertEquals(401, assertThrows(BusinessException.class,
                () -> service.recordRead("", "art_1", 1, "detail")).getCode());
    }

    @Test
    void applyPhase4_handlesDuplicateIdsUnknownDifyIdAndNullReason() throws Exception {
        ArticleCandidate c1 = candidate("art_1", "摘要1");
        ArticleCandidate c2 = candidate("art_2", "摘要2");
        ScoredArticle sa1 = new ScoredArticle(c1);
        ScoredArticle sa1dup = new ScoredArticle(c1);
        ScoredArticle sa2 = new ScoredArticle(c2);
        List<ScoredArticle> scored = new ArrayList<>(List.of(sa1, sa1dup, sa2));

        Object ctx = newContext("u1", "兴趣", Set.of("饮食"));
        JsonNode response = objectMapper.readTree("""
                {"data":{"outputs":{"recommendations":[
                  {"article_id":"art_unknown"},
                  {"article_id":"art_1"},
                  {"article_id":"art_2","reason":null}
                ]}}}
                """);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(response);

        int phase = invokePhase4("u1", scored, ctx);
        assertEquals(4, phase);
        assertEquals(2, scored.size());
        assertEquals("art_1", scored.get(0).getCandidate().getArticleId());
        assertEquals("AI 为您推荐", scored.get(1).getReason());
    }

    @Test
    void applyPhase4_wrapWorkflowInputsWhenNotFlat() throws Exception {
        ArticleCandidate c1 = candidate("art_1", "摘要");
        List<ScoredArticle> scored = new ArrayList<>(List.of(new ScoredArticle(c1)));
        Object ctx = newContext("u1", "兴趣", Set.of());
        JsonNode response = objectMapper.readTree("""
                {"data":{"outputs":{"recommendations":[{"article_id":"art_1","reason":"ok"}]}}}
                """);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(response);
        assertEquals(4, invokePhase4("u1", scored, ctx));
    }

    @Test
    void buildInterestTags_includesNullTagLists() throws Exception {
        Object ctx = newContext("u1", "", Set.of());
        setField(ctx, "favoriteIds", Set.of("art_1"));
        setField(ctx, "recentReadIds", Set.of("art_2"));
        ArticleCandidate favNullTags = candidate("art_1", "t");
        setCandidateTagsField(favNullTags, null);
        ArticleCandidate readNullTags = candidate("art_2", "t");
        setCandidateTagsField(readNullTags, null);
        Method m = ArticleRecommendService.class.getDeclaredMethod("buildInterestTags", ctxClass, List.class);
        m.setAccessible(true);
        assertTrue(((Set<?>) m.invoke(service, ctx, List.of(favNullTags, readNullTags))).isEmpty());
    }

    @Test
    void extractRecommendationsList_nullNode() throws Exception {
        Method m = ArticleRecommendService.class.getDeclaredMethod("extractRecommendationsList", JsonNode.class);
        m.setAccessible(true);
        assertTrue(((JsonNode) m.invoke(service, new Object[]{null})).isMissingNode());
    }

    @Test
    void applyPhase3_blankInterestTextReturnsEarly() throws Exception {
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);
        Object ctx = newContext("u1", "   ", Set.of());
        ArticleCandidate c = candidate("art_1", "摘要");
        c.setTextFingerprint("糖尿病 饮食");
        assertEquals(1, invokePhase3(new ArrayList<>(List.of(new ScoredArticle(c))), ctx));
    }

    @Test
    void applyPhase3_blankFingerprintSkipped() throws Exception {
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);
        Object ctx = newContext("u1", "糖尿病 饮食 管理", Set.of());
        ArticleCandidate blankFp = candidate("art_blank", "x");
        blankFp.setTextFingerprint("   ");
        ArticleCandidate match = candidate("art_match", "糖尿病 饮食 管理 血糖");
        match.setTextFingerprint("糖尿病 饮食 管理 血糖");
        assertEquals(3, invokePhase3(new ArrayList<>(List.of(
                new ScoredArticle(blankFp), new ScoredArticle(match))), ctx));
    }

    @Test
    void buildDifyWorkflowInputs_nullVarNameUsesFlat() throws Exception {
        Field field = ArticleRecommendService.class.getDeclaredField("difyInputVarName");
        field.setAccessible(true);
        field.set(service, null);
        Method m = ArticleRecommendService.class.getDeclaredMethod("buildDifyWorkflowInputs", Map.class);
        m.setAccessible(true);
        assertNotNull(m.invoke(service, Map.of("user_id", "u1")));
    }

    @Test
    void jaccardSimilarity_firstSetEmpty() throws Exception {
        Method m = ArticleRecommendService.class.getDeclaredMethod("jaccardSimilarity", Set.class, Set.class);
        m.setAccessible(true);
        assertEquals(0.0, (double) m.invoke(service, Set.of(), Set.of("x")));
    }

    @Test
    void blankToDefault_nullUsesDefault() throws Exception {
        Method m = ArticleRecommendService.class.getDeclaredMethod("blankToDefault", String.class, String.class);
        m.setAccessible(true);
        assertEquals("detail", m.invoke(service, null, "detail"));
    }

    @Test
    void buildInterestTags_mergeDuplicateCandidateIds() throws Exception {
        Object ctx = newContext("u1", "", Set.of());
        setField(ctx, "favoriteIds", Set.of("art_1"));
        ArticleCandidate first = candidate("art_1", "a");
        first.setTags(List.of("饮食"));
        ArticleCandidate duplicate = candidate("art_1", "b");
        duplicate.setTags(List.of("运动"));
        Method m = ArticleRecommendService.class.getDeclaredMethod("buildInterestTags", ctxClass, List.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> tags = (Set<String>) m.invoke(service, ctx, List.of(first, duplicate));
        assertEquals(Set.of("饮食"), tags);
    }

    @Test
    void toRecommendCard_phase4NullReasonExcluded() throws Exception {
        ArticleCandidate c = candidate("art_1", "s");
        ScoredArticle sa = new ScoredArticle(c);
        sa.setPhase(4);
        sa.setReason(null);
        Method m = ArticleRecommendService.class.getDeclaredMethod("toRecommendCard", ScoredArticle.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> card = (Map<String, Object>) m.invoke(service, sa);
        assertFalse(card.containsKey("recReason"));
    }

    @Test
    void invalidateUserRecommendCache_nullUserIdIsNoOp() {
        assertDoesNotThrow(() -> service.invalidateUserRecommendCache(null));
        verify(redis, never()).keys(anyString());
    }

    @Test
    void buildInterestTags_recentReadWithTags() throws Exception {
        Object ctx = newContext("u1", "", Set.of());
        setField(ctx, "recentReadIds", Set.of("art_read"));
        ArticleCandidate read = candidate("art_read", "t");
        read.setTags(List.of("运动"));
        Method m = ArticleRecommendService.class.getDeclaredMethod("buildInterestTags", ctxClass, List.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> tags = (Set<String>) m.invoke(service, ctx, List.of(read));
        assertEquals(Set.of("运动"), tags);
    }

    @Test
    void resolveCoverImage_blankCoverIdUsesMinio() throws Exception {
        ArticleCandidate blank = candidate("art_1", "s");
        blank.setCoverImageId("   ");
        Method m = ArticleRecommendService.class.getDeclaredMethod("resolveCoverImage", ArticleCandidate.class);
        m.setAccessible(true);
        assertEquals("http://minio/cover.jpg", m.invoke(service, blank));
    }

    @Test
    void buildDifyWorkflowInputs_blankVarNameUsesFlat() throws Exception {
        Field field = ArticleRecommendService.class.getDeclaredField("difyInputVarName");
        field.setAccessible(true);
        field.set(service, "   ");
        Method m = ArticleRecommendService.class.getDeclaredMethod("buildDifyWorkflowInputs", Map.class);
        m.setAccessible(true);
        assertNotNull(m.invoke(service, Map.of("user_id", "u1")));
    }

    @Test
    void blankToDefault_blankStringUsesDefault() throws Exception {
        Method m = ArticleRecommendService.class.getDeclaredMethod("blankToDefault", String.class, String.class);
        m.setAccessible(true);
        assertEquals("detail", m.invoke(service, "   ", "detail"));
    }

    @Test
    void recordReadUsesBlankToDefaultForNullSource() {
        service.recordRead("u1", "art_1", 10, null);
        verify(recommendMapper).upsertUserRead(anyString(), eq("u1"), eq("art_1"), eq(10), eq("detail"));
    }

    @Test
    void extractRecommendationsList_missingNode() throws Exception {
        Method m = ArticleRecommendService.class.getDeclaredMethod("extractRecommendationsList", JsonNode.class);
        m.setAccessible(true);
        assertTrue(((JsonNode) m.invoke(service, objectMapper.missingNode())).isMissingNode());
    }

    @Test
    void buildInterestTags_skipsMissingCandidatesAndNullTags() throws Exception {
        Object ctx = newContext("u1", "", Set.of());
        setField(ctx, "favoriteIds", Set.of("missing_fav", "art_1"));
        setField(ctx, "recentReadIds", Set.of("missing_read", "art_2"));

        ArticleCandidate withTags = candidate("art_1", "t");
        withTags.setTags(List.of("饮食"));
        ArticleCandidate nullTags = candidate("art_2", "t");
        setCandidateTagsField(nullTags, null);

        Method m = ArticleRecommendService.class.getDeclaredMethod("buildInterestTags", ctxClass, List.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> tags = (Set<String>) m.invoke(service, ctx, List.of(withTags, nullTags));
        assertEquals(Set.of("饮食"), tags);
    }

    @Test
    void profileCategoryWeights_highRiskAndHttpCover() throws Exception {
        Method profile = ArticleRecommendService.class.getDeclaredMethod(
                "profileCategoryWeights", Map.class, Map.class);
        profile.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Integer, Double> high = (Map<Integer, Double>) profile.invoke(service,
                Map.of("diabetesType", 2, "bmi", 22),
                Map.of("riskLevel", "高风险"));
        assertTrue(high.getOrDefault(5, 0.0) > 0);

        ArticleCandidate httpCover = candidate("art_1", "s");
        httpCover.setCoverImageId("http://cdn/c.jpg");
        Method resolve = ArticleRecommendService.class.getDeclaredMethod("resolveCoverImage", ArticleCandidate.class);
        resolve.setAccessible(true);
        assertEquals("http://cdn/c.jpg", resolve.invoke(service, httpCover));

        ScoredArticle phase4BlankReason = new ScoredArticle(httpCover);
        phase4BlankReason.setPhase(4);
        phase4BlankReason.setReason("  ");
        Method toCard = ArticleRecommendService.class.getDeclaredMethod("toRecommendCard", ScoredArticle.class);
        toCard.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> card = (Map<String, Object>) toCard.invoke(service, phase4BlankReason);
        assertFalse(card.containsKey("recReason"));
    }

    @Test
    void tagOverlapScore_allNullAndEmptyCombinations() throws Exception {
        Method m = ArticleRecommendService.class.getDeclaredMethod("tagOverlapScore", Set.class, List.class);
        m.setAccessible(true);
        assertEquals(0.0, (double) m.invoke(service, null, List.of("a")));
        assertEquals(0.0, (double) m.invoke(service, Set.of(), List.of("a")));
        assertEquals(0.0, (double) m.invoke(service, Set.of("a"), null));
        assertEquals(0.0, (double) m.invoke(service, Set.of("a"), List.of()));
        assertEquals(1.0, (double) m.invoke(service, Set.of("a"), List.of("a")));
    }

    @Test
    void recordReadRejectsBlankUserAndInvalidateHandlesNullKeys() {
        assertEquals(401, assertThrows(BusinessException.class,
                () -> service.recordRead(null, "art_1", 1, "detail")).getCode());

        when(redis.keys(anyString())).thenReturn(null);
        assertDoesNotThrow(() -> service.invalidateUserRecommendCache("u1"));

        when(redis.keys(anyString())).thenReturn(Set.of());
        assertDoesNotThrow(() -> service.invalidateUserRecommendCache("u1"));
        assertDoesNotThrow(() -> service.invalidateUserRecommendCache("  "));
    }

    @Test
    void parseDifyRecommendations_idWithoutReasonUsesDefault() throws Exception {
        Method m = ArticleRecommendService.class.getDeclaredMethod("parseDifyRecommendations", JsonNode.class);
        m.setAccessible(true);
        JsonNode node = objectMapper.readTree("""
                {"data":{"outputs":{"recommendations":[{"article_id":"art_1"}]}}}
                """);
        @SuppressWarnings("unchecked")
        Map<String, String> map = (Map<String, String>) m.invoke(service, node);
        assertEquals("AI 为您推荐", map.get("art_1"));
    }

    @Test
    void constructorUsesBlankInputVarDefault() {
        assertDoesNotThrow(() -> new ArticleRecommendService(
                recommendMapper, redis, objectMapper, minioStorageService,
                healthServiceClient, difyClient, properties, milvusArticleSearchService,
                "http://dify.local", "key", "  ", "object", "blocking", "internal-key"));
    }

    private Object newContext(String userId, String interestText, Set<String> interestTags) throws Exception {
        Constructor<?> ctor = ctxClass.getDeclaredConstructor(String.class);
        ctor.setAccessible(true);
        Object ctx = ctor.newInstance(userId);
        setField(ctx, "interestText", interestText);
        setField(ctx, "interestTags", interestTags);
        setField(ctx, "recentReadIds", Set.of());
        setField(ctx, "favoriteIds", Set.of());
        setField(ctx, "categoryWeights", Map.of());
        setField(ctx, "profileCategoryWeights", Map.of());
        return ctx;
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = ctxClass.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private int invokePhase3(List<ScoredArticle> scored, Object ctx) throws Exception {
        Method m = ArticleRecommendService.class.getDeclaredMethod("applyPhase3Semantic", List.class, ctxClass);
        m.setAccessible(true);
        return (int) m.invoke(service, scored, ctx);
    }

    private int invokePhase4(String userId, List<ScoredArticle> scored, Object ctx) throws Exception {
        Method m = ArticleRecommendService.class.getDeclaredMethod(
                "applyPhase4DifyRerank", String.class, List.class, ctxClass);
        m.setAccessible(true);
        return (int) m.invoke(service, userId, scored, ctx);
    }

    private static ArticleCandidate candidate(String id, String summary) {
        ArticleCandidate c = new ArticleCandidate();
        c.setArticleId(id);
        c.setTitle("标题-" + id);
        c.setSummary(summary);
        c.setCategory(2);
        c.setViewCount(10);
        c.setPublishedAt(LocalDateTime.now());
        return c;
    }
}
