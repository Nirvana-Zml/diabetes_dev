<template>
  <SiteLayout :show-back="isMobile && isDetail" class="health-info-page" :class="{ 'health-info-page--detail-mobile': isMobile && isDetail }">

    <!-- 列表页 -->
    <div v-if="!isDetail" class="info-list-page">
      <section class="search-wrap">
        <div class="search-box">
          <div class="search-box__icon-wrap">
            <svg class="search-box__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
          </div>
          <input
            v-model="keyword"
            type="search"
            class="search-box__input"
            placeholder="搜索资讯标题或摘要..."
            @keyup.enter="handleSearch"
          />
          <button v-if="keyword" type="button" class="search-box__clear" aria-label="清除" @click="handleSearchClear">×</button>
          <button type="button" class="search-box__btn" @click="handleSearch">搜索</button>
        </div>
      </section>

      <template v-if="!searchMode">
        <nav class="tab-nav" aria-label="资讯分类">
          <button
            v-for="tab in mainTabs"
            :key="tab.name"
            type="button"
            class="tab-nav__item"
            :class="{ 'tab-nav__item--active': listTab === tab.name }"
            @click="switchTab(tab.name)"
          >
            {{ tab.label }}
          </button>
        </nav>

        <section v-if="listTab === 'category'" class="category-pills">
          <button
            type="button"
            class="category-pill"
            :class="{ 'category-pill--active': !category }"
            @click="switchCategory('')"
          >
            全部
          </button>
          <button
            v-for="(label, key) in categoryMap"
            :key="key"
            type="button"
            class="category-pill"
            :class="{ 'category-pill--active': category === key }"
            @click="switchCategory(key)"
          >
            {{ label }}
          </button>
        </section>
      </template>

      <div v-else class="search-result-bar">
        <span>搜索「{{ keyword }}」共 <strong>{{ total }}</strong> 条结果</span>
        <button type="button" class="search-result-bar__clear" @click="handleSearchClear">清除搜索</button>
      </div>

      <div v-if="listTab === 'favorites' && !isLoggedIn()" class="login-panel">
        <div class="login-panel__icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path stroke-linecap="round" stroke-linejoin="round" d="M17.593 3.322c1.1.128 1.907 1.077 1.907 2.185V21L12 17.25 4.5 21V5.507c0-1.108.806-2.057 1.907-2.185a48.507 48.507 0 0111.186 0z" />
          </svg>
        </div>
        <p>登录后可查看收藏的资讯</p>
        <el-button type="primary" round @click="goLogin">去登录</el-button>
      </div>

      <template v-else>
        <p
          v-if="!searchMode && listTab === 'recommend' && recommendStrategy === 'personalized'"
          class="rec-banner"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09z" />
          </svg>
          已根据您的阅读偏好与健康档案为您推荐
        </p>

        <div v-loading="loading" class="article-grid stagger-children">
          <article
            v-for="art in articles"
            :key="art.article_id"
            class="article-card card-hover animate-fade-in"
            @click="goDetail(art.article_id)"
          >
            <div class="article-card__cover">
              <img :src="art.cover_image || defaultCover" :alt="art.title" loading="lazy" />
              <span
                class="article-card__badge"
                :style="categoryBadgeStyle(art.category)"
              >
                {{ categoryMap[art.category] || '健康资讯' }}
              </span>
            </div>
            <div class="article-card__body">
              <h3 class="article-card__title">{{ art.title }}</h3>
              <p class="article-card__summary">{{ art.summary }}</p>
              <div class="article-card__footer">
                <div class="article-card__meta">
                  <span>{{ formatDate(art.published_at) }}</span>
                  <span class="article-card__dot" aria-hidden="true" />
                  <span>{{ formatViewCount(art.view_count) }} 阅读</span>
                </div>
                <div class="article-card__actions">
                  <button
                    type="button"
                    class="action-btn"
                    :class="{ 'action-btn--active': isFavorited(art.article_id) }"
                    :title="isFavorited(art.article_id) ? '取消收藏' : '收藏'"
                    @click.stop="toggleFavOnList(art.article_id)"
                  >
                    <svg viewBox="0 0 24 24" :fill="isFavorited(art.article_id) ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z" />
                    </svg>
                  </button>
                  <span class="action-arrow" aria-hidden="true">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M9 5l7 7-7 7" />
                    </svg>
                  </span>
                </div>
              </div>
            </div>
          </article>
        </div>

        <div v-if="!loading && !articles.length" class="empty-panel">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m0 12.75h7.5m-7.5 3H12M10.5 2.25H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" />
          </svg>
          <p>暂无资讯</p>
        </div>
      </template>
    </div>

    <!-- 详情页 -->
    <div v-else class="info-detail-page">
      <nav v-if="detail && !isMobile" class="detail-breadcrumb" aria-label="面包屑">
        <button type="button" class="detail-breadcrumb__link" @click="goHome">首页</button>
        <span class="detail-breadcrumb__sep">&gt;</span>
        <button type="button" class="detail-breadcrumb__link" @click="goList">健康资讯</button>
        <span class="detail-breadcrumb__sep">&gt;</span>
        <span class="detail-breadcrumb__current">{{ truncateTitle(detail.title) }}</span>
      </nav>

      <div v-if="detail" class="detail-article">
        <div class="detail-hero">
          <img
            :src="detail.cover_image || defaultCover"
            class="detail-hero__cover"
            :alt="detail.title"
          />
        </div>

        <div v-if="detailTags.length" class="detail-tags">
          <span
            v-for="tag in detailTags"
            :key="tag.label"
            class="detail-tag"
            :style="tagStyle(tag.type)"
          >
            {{ tag.label }}
          </span>
        </div>

        <h1 class="detail-article__title">{{ detail.title }}</h1>

        <div class="detail-meta">
          <span class="detail-meta__item">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z" />
              <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
            {{ formatViewCount(detail.view_count) }} 阅读
          </span>
          <span class="detail-meta__dot">·</span>
          <span class="detail-meta__item">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" d="M6.75 3v2.25M17.25 3v2.25M3 18.75V7.5a2.25 2.25 0 012.25-2.25h13.5A2.25 2.25 0 0121 7.5v11.25m-18 0A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75m-18 0v-7.5A2.25 2.25 0 015.25 9h13.5A2.25 2.25 0 0121 11.25v7.5" />
            </svg>
            {{ formatDetailDate(detail.published_at) }}
          </span>
          <span class="detail-meta__dot">·</span>
          <span class="detail-meta__item">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            阅读约 {{ readMinutes }} 分钟
          </span>
        </div>

        <div class="detail-article__content">
          <MarkdownContent :content="detail.content" />
        </div>

        <div class="detail-article__actions">
          <button
            type="button"
            class="detail-action-btn"
            :class="{ 'detail-action-btn--favorited': favorited }"
            @click="toggleFav"
          >
            <svg viewBox="0 0 24 24" :fill="favorited ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="1.8">
              <path stroke-linecap="round" stroke-linejoin="round" d="M17.593 3.322c1.1.128 1.907 1.077 1.907 2.185V21L12 17.25 4.5 21V5.507c0-1.108.806-2.057 1.907-2.185a48.507 48.507 0 0111.186 0z" />
            </svg>
            {{ favorited ? '已收藏' : '收藏' }}
          </button>
          <button type="button" class="detail-action-btn detail-action-btn--ghost" @click="handleShare">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <path stroke-linecap="round" stroke-linejoin="round" d="M7.217 10.907a2.25 2.25 0 100 2.186m0-2.186c.18.324.283.696.283 1.093s-.103.77-.283 1.093m0-2.186l9.566-5.314m-9.566 7.5l9.566 5.314m0 0a2.25 2.25 0 103.935 2.186 2.25 2.25 0 00-3.935-2.186zm0-12.814a2.25 2.25 0 103.933-2.185 2.25 2.25 0 00-3.933 2.185z" />
            </svg>
            分享
          </button>
        </div>

        <DisclaimerBar class="detail-disclaimer" />
      </div>

      <section v-if="related.length" class="related-section">
        <h2 class="related-section__title">
          <span class="related-section__bar" aria-hidden="true" />
          相关推荐
        </h2>
        <div class="related-list">
          <article
            v-for="art in related"
            :key="art.article_id"
            class="related-card"
            @click="goDetail(art.article_id)"
          >
            <div class="related-card__body">
              <span
                class="related-card__tag"
                :style="tagStyle(art.category)"
              >
                {{ categoryMap[art.category] || '健康资讯' }}
              </span>
              <h3 class="related-card__title">{{ art.title }}</h3>
              <p class="related-card__summary">{{ art.summary }}</p>
              <div class="related-card__meta">
                {{ formatViewCount(art.view_count) }} 阅读
                <span class="related-card__dot">·</span>
                {{ formatDetailDate(art.published_at) }}
              </div>
            </div>
            <div class="related-card__thumb">
              <img :src="art.cover_image || defaultCover" :alt="art.title" loading="lazy" />
            </div>
          </article>
        </div>
      </section>
    </div>
  </SiteLayout>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import SiteLayout from '@/components/layout/SiteLayout.vue'
import { useIsMobile } from '@/composables/useBreakpoints'
import MarkdownContent from '@/components/MarkdownContent.vue'
import DisclaimerBar from '@/components/DisclaimerBar.vue'
import {
  getArticles,
  getRecommendArticles,
  getRelatedArticles,
  getArticleDetail,
  getArticleFavorites,
  searchArticles,
  toggleArticleFavorite,
  reportArticleReadEvent,
  categoryMap,
} from '@/api/article'
import { isLoggedIn, redirectToLogin } from '@/utils/auth'

const defaultCover = 'https://images.unsplash.com/photo-1571019614242-c5c5dee9f50b?w=800&h=480&fit=crop'

const categoryTagStyle = {
  diabetes_basics: { background: '#EFF6FF', color: '#2563EB' },
  diet: { background: '#FFF7ED', color: '#EA580C' },
  exercise: { background: '#F0FDFA', color: '#0D9488' },
  medication: { background: '#F5F3FF', color: '#7C3AED' },
  complications: { background: '#FEF2F2', color: '#DC2626' },
  topic: { background: '#F5F5F4', color: '#78716C' },
}

const categoryBadgeStyleMap = {
  diabetes_basics: { background: '#0ea5e9' },
  diet: { background: '#14b8a6' },
  exercise: { background: '#10b981' },
  medication: { background: '#f59e0b' },
  complications: { background: '#f43f5e' },
  topic: { background: '#78716c' },
}

const mainTabs = [
  { name: 'recommend', label: '推荐' },
  { name: 'category', label: '分类' },
  { name: 'favorites', label: '我的收藏' },
]

const route = useRoute()
const router = useRouter()
const isMobile = useIsMobile()
const isDetail = computed(() => !!route.params.id)
const articles = ref([])
const detail = ref(null)
const related = ref([])
const keyword = ref('')
const listTab = ref('recommend')
const category = ref(route.query.category || '')
const favorited = ref(false)
const favoriteIds = ref(new Set())
const searchMode = ref(false)
const loading = ref(false)
const total = ref(0)
const recommendStrategy = ref('popular')
const readStartedAt = ref(0)

const detailTags = computed(() => {
  if (!detail.value) return []
  const tags = []
  const categoryLabel = categoryMap[detail.value.category]
  if (categoryLabel) {
    tags.push({ label: categoryLabel, type: detail.value.category })
  }
  const extras = (detail.value.tags || []).filter((t) => t && t !== categoryLabel)
  if (extras.length) {
    tags.push({ label: extras[0], type: 'topic' })
  }
  return tags
})

const readMinutes = computed(() => {
  if (!detail.value?.content) return 1
  const chars = detail.value.content.replace(/\s/g, '').length
  return Math.max(1, Math.round(chars / 500))
})

onMounted(async () => {
  if (isDetail.value) loadDetail()
  else {
    await loadFavoriteIds()
    loadArticles()
  }
})

onUnmounted(() => {
  flushReadEvent()
})

watch(() => route.params.id, (id, prev) => {
  if (prev && prev !== id) {
    flushReadEvent(prev)
  }
  if (id) loadDetail()
  else loadArticles()
})

async function loadFavoriteIds() {
  if (!isLoggedIn()) {
    favoriteIds.value = new Set()
    return
  }
  try {
    const data = await getArticleFavorites({ size: 200 })
    favoriteIds.value = new Set(data.list.map((a) => a.article_id))
  } catch {
    favoriteIds.value = new Set()
  }
}

function isFavorited(articleId) {
  return favoriteIds.value.has(articleId)
}

function switchTab(name) {
  listTab.value = name
  loadArticles()
}

function switchCategory(key) {
  category.value = key
  loadArticles()
}

async function loadArticles() {
  if (searchMode.value && keyword.value.trim()) {
    await runSearch()
    return
  }

  loading.value = true
  try {
    if (listTab.value === 'recommend') {
      const data = await getRecommendArticles()
      articles.value = data.list
      total.value = data.total
      recommendStrategy.value = data.strategy || 'popular'
    } else if (listTab.value === 'favorites') {
      if (!isLoggedIn()) {
        articles.value = []
        total.value = 0
        return
      }
      const data = await getArticleFavorites()
      articles.value = data.list
      total.value = data.total
      favoriteIds.value = new Set(data.list.map((a) => a.article_id))
    } else {
      const data = await getArticles({ category: category.value || undefined })
      articles.value = data.list
      total.value = data.total
    }
  } finally {
    loading.value = false
  }
}

async function runSearch() {
  const q = keyword.value.trim()
  if (!q) {
    handleSearchClear()
    return
  }
  searchMode.value = true
  loading.value = true
  try {
    const data = await searchArticles(q)
    articles.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  runSearch()
}

function handleSearchClear() {
  searchMode.value = false
  keyword.value = ''
  loadArticles()
}

async function loadDetail() {
  flushReadEvent()
  detail.value = await getArticleDetail(route.params.id)
  favorited.value = !!detail.value.favorited
  if (favorited.value) {
    favoriteIds.value = new Set([...favoriteIds.value, route.params.id])
  }
  readStartedAt.value = Date.now()
  if (isLoggedIn()) {
    reportArticleReadEvent(route.params.id, { duration_sec: 0, source: 'detail' }).catch(() => {})
  }
  const data = await getRelatedArticles(route.params.id, { size: 4 })
  related.value = data.list
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function flushReadEvent(articleId) {
  const id = articleId || route.params.id
  if (!id || !readStartedAt.value || !isLoggedIn()) return
  const durationSec = Math.round((Date.now() - readStartedAt.value) / 1000)
  readStartedAt.value = 0
  if (durationSec > 0) {
    reportArticleReadEvent(id, { duration_sec: durationSec, source: 'detail' }).catch(() => {})
  }
}

function goDetail(id) {
  router.push(`/health-info/${id}`)
}

function goHome() {
  router.push('/')
}

function goList() {
  router.push('/health-info')
}

function goLogin() {
  router.push(redirectToLogin('/health-info'))
}

function formatDate(d) {
  return d ? dayjs(d).format('YYYY.MM.DD') : ''
}

function formatDetailDate(d) {
  return d ? dayjs(d).format('YYYY-MM-DD') : ''
}

function truncateTitle(title, max = 20) {
  if (!title) return ''
  return title.length > max ? `${title.slice(0, max)}...` : title
}

function tagStyle(type) {
  return categoryTagStyle[type] || categoryTagStyle.topic
}

function categoryBadgeStyle(type) {
  return categoryBadgeStyleMap[type] || categoryBadgeStyleMap.topic
}

function formatViewCount(count) {
  const n = Number(count) || 0
  if (n >= 10000) return `${(n / 10000).toFixed(1).replace(/\.0$/, '')}万`
  return n.toLocaleString('zh-CN')
}

async function toggleFavOnList(articleId) {
  if (!isLoggedIn()) {
    router.push(redirectToLogin('/health-info'))
    return
  }
  try {
    const res = await toggleArticleFavorite(articleId)
    const next = new Set(favoriteIds.value)
    if (res.favorited) next.add(articleId)
    else next.delete(articleId)
    favoriteIds.value = next
    ElMessage.success(res.favorited ? '已收藏' : '已取消收藏')
    if (listTab.value === 'favorites' && !res.favorited) {
      articles.value = articles.value.filter((a) => a.article_id !== articleId)
      total.value = Math.max(0, total.value - 1)
    }
  } catch (err) {
    ElMessage.error(err.message || '操作失败')
  }
}

async function toggleFav() {
  if (!isLoggedIn()) {
    router.push(redirectToLogin(route.fullPath))
    return
  }
  try {
    const res = await toggleArticleFavorite(route.params.id)
    favorited.value = res.favorited
    const next = new Set(favoriteIds.value)
    if (res.favorited) next.add(route.params.id)
    else next.delete(route.params.id)
    favoriteIds.value = next
    ElMessage.success(favorited.value ? '已收藏' : '已取消收藏')
  } catch (err) {
    ElMessage.error(err.message || '操作失败')
  }
}

function handleShare() {
  ElMessage.info('分享功能开发中')
}
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Noto+Serif+SC:wght@400;500;600;700&display=swap');

.health-info-page :deep(.site-page) {
  background: var(--warm-50);
}

.health-info-page :deep(.site-main) {
  padding-top: 40px;
}

/* ── 列表页 ── */
.info-list-page {
  padding-bottom: 48px;
}

.search-wrap {
  max-width: 720px;
  margin: 0 auto 40px;
}

.search-box {
  display: flex;
  align-items: stretch;
  background: #fff;
  border-radius: 16px;
  border: 1px solid var(--warm-200);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  overflow: hidden;
  transition: box-shadow 0.3s ease, border-color 0.3s ease;
}

.search-box:focus-within {
  box-shadow: 0 0 0 3px rgba(20, 184, 166, 0.15);
  border-color: var(--health-500);
}

.search-box__icon-wrap {
  display: flex;
  align-items: center;
  padding-left: 20px;
  padding-right: 12px;
  color: var(--warm-400);
  flex-shrink: 0;
}

.search-box__icon {
  width: 20px;
  height: 20px;
}

.search-box__input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 15px;
  color: var(--warm-800);
  background: transparent;
  min-width: 0;
  padding: 16px 16px 16px 0;
}

.search-box__input::placeholder {
  color: var(--warm-400);
}

.search-box__clear {
  align-self: center;
  border: none;
  background: var(--warm-100);
  color: var(--warm-400);
  width: 24px;
  height: 24px;
  border-radius: 50%;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  flex-shrink: 0;
  margin-right: 8px;
}

.search-box__btn {
  border: none;
  padding: 16px 28px;
  background: var(--health-500);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  flex-shrink: 0;
  transition: background-color 0.2s ease;
}

.search-box__btn:hover {
  background: var(--health-600);
}

.tab-nav {
  display: flex;
  align-items: stretch;
  justify-content: center;
  border-bottom: 1px solid var(--warm-200);
  margin-bottom: 32px;
  overflow-x: auto;
}

.tab-nav__item {
  flex: none;
  min-width: 88px;
  padding: 14px 32px;
  border: none;
  background: transparent;
  font-size: 15px;
  font-weight: 500;
  color: var(--warm-500);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition: color 0.2s ease, border-color 0.2s ease;
  white-space: nowrap;
}

.tab-nav__item:hover {
  color: var(--warm-600);
  border-bottom-color: var(--warm-200);
}

.tab-nav__item--active {
  color: var(--health-600);
  border-bottom-color: var(--health-600);
  font-weight: 600;
}

.category-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 40px;
}

.category-pill {
  padding: 8px 20px;
  border: 1px solid var(--warm-200);
  border-radius: 999px;
  background: #fff;
  font-size: 14px;
  font-weight: 500;
  color: var(--warm-600);
  cursor: pointer;
  transition: all 0.2s ease;
}

.category-pill:hover {
  background: var(--warm-100);
  border-color: var(--warm-300);
}

.category-pill--active {
  background: var(--health-600);
  border-color: var(--health-600);
  color: #fff;
  box-shadow: 0 1px 3px rgba(13, 148, 136, 0.2);
}

.search-result-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 32px;
  padding: 14px 20px;
  background: #fff;
  border-radius: 14px;
  font-size: 14px;
  color: var(--warm-500);
  border: 1px solid var(--warm-200);
}

.search-result-bar strong {
  color: var(--health-600);
}

.search-result-bar__clear {
  border: none;
  background: none;
  color: var(--health-600);
  font-size: 14px;
  cursor: pointer;
  font-weight: 500;
}

.rec-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 24px;
  padding: 12px 18px;
  background: rgba(13, 148, 136, 0.06);
  border-radius: 12px;
  font-size: 13px;
  color: var(--health-700);
  border: 1px solid rgba(13, 148, 136, 0.12);
}

.rec-banner svg {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  color: var(--health-500);
}

.login-panel,
.empty-panel {
  text-align: center;
  padding: 64px 24px;
  background: #fff;
  border-radius: 16px;
  border: 1px solid var(--warm-100);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.login-panel__icon svg,
.empty-panel svg {
  width: 48px;
  height: 48px;
  color: var(--warm-300);
  margin-bottom: 16px;
}

.login-panel p,
.empty-panel p {
  margin: 0 0 20px;
  color: var(--warm-500);
  font-size: 15px;
}

/* ── 卡片网格 ── */
.article-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
  min-height: 120px;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.animate-fade-in {
  opacity: 0;
  animation: fadeIn 0.5s ease-out forwards;
}

.stagger-children > .article-card:nth-child(1) { animation-delay: 0.05s; }
.stagger-children > .article-card:nth-child(2) { animation-delay: 0.1s; }
.stagger-children > .article-card:nth-child(3) { animation-delay: 0.15s; }
.stagger-children > .article-card:nth-child(4) { animation-delay: 0.2s; }
.stagger-children > .article-card:nth-child(5) { animation-delay: 0.25s; }
.stagger-children > .article-card:nth-child(6) { animation-delay: 0.3s; }

.article-card {
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 16px;
  border: 1px solid var(--warm-100);
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1), box-shadow 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.card-hover:hover {
  transform: translateY(-2px);
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.08);
}

.article-card__cover {
  position: relative;
  width: 100%;
  height: 200px;
  overflow: hidden;
  background: var(--warm-100);
  flex-shrink: 0;
}

.article-card__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: top;
  transition: transform 0.4s ease;
}

.article-card:hover .article-card__cover img {
  transform: scale(1.03);
}

.article-card__badge {
  position: absolute;
  top: 12px;
  left: 12px;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12);
}

.article-card__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 24px;
}

.article-card__title {
  margin: 0 0 12px;
  font-family: 'Noto Serif SC', Georgia, serif;
  font-size: 18px;
  font-weight: 700;
  color: var(--warm-900, #1c1917);
  line-height: 1.375;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.article-card__summary {
  flex: 1;
  margin: 0 0 16px;
  font-size: 14px;
  line-height: 1.625;
  color: var(--warm-500);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.article-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid var(--warm-100);
}

.article-card__meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: var(--warm-400);
}

.article-card__dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--warm-300);
  flex-shrink: 0;
}

.article-card__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 8px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--warm-400);
  cursor: pointer;
  transition: color 0.2s ease, background-color 0.2s ease;
}

.action-btn svg {
  width: 16px;
  height: 16px;
}

.action-btn:hover {
  color: var(--health-500);
  background: rgba(13, 148, 136, 0.08);
}

.action-btn--active {
  color: var(--health-500);
  background: rgba(13, 148, 136, 0.08);
}

.action-arrow {
  display: inline-flex;
  padding: 8px;
  color: var(--warm-400);
  transition: color 0.2s ease, background-color 0.2s ease;
  border-radius: 8px;
}

.action-arrow svg {
  width: 16px;
  height: 16px;
}

.article-card:hover .action-arrow {
  color: var(--health-500);
  background: rgba(13, 148, 136, 0.08);
}

/* ── 详情页 ── */
.info-detail-page {
  max-width: 820px;
  margin: 0 auto;
  padding: 8px 0 56px;
}

.detail-breadcrumb {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 24px;
  font-size: 13px;
  color: var(--warm-400);
}

.detail-breadcrumb__link {
  border: none;
  background: none;
  padding: 0;
  color: var(--warm-400);
  cursor: pointer;
  transition: color 0.2s ease;
}

.detail-breadcrumb__link:hover {
  color: var(--health-600);
}

.detail-breadcrumb__sep {
  color: var(--warm-300);
}

.detail-breadcrumb__current {
  color: var(--warm-500);
}

.detail-article {
  background: #fff;
  border-radius: 16px;
  padding: clamp(24px, 3vw, 32px);
  border: 1px solid var(--warm-100);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  margin-bottom: 32px;
}

.detail-hero__cover {
  width: 100%;
  max-height: 420px;
  object-fit: cover;
  object-position: top;
  border-radius: 12px;
  display: block;
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 20px 0 16px;
}

.detail-tag {
  display: inline-flex;
  align-items: center;
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
}

.detail-article__title {
  margin: 0 0 16px;
  font-family: 'Noto Serif SC', Georgia, serif;
  font-size: clamp(24px, 3.2vw, 32px);
  font-weight: 700;
  color: var(--warm-900, #1c1917);
  line-height: 1.35;
  letter-spacing: -0.01em;
}

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 28px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--warm-100);
  font-size: 13px;
  color: var(--warm-400);
}

.detail-meta__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.detail-meta__item svg {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}

.detail-meta__dot {
  color: var(--warm-300);
}

.detail-article__content {
  font-size: 16px;
  line-height: 1.9;
  color: var(--warm-700, #44403c);
}

.detail-article__content :deep(.markdown-body) {
  font-size: 16px;
  line-height: 1.9;
  color: var(--warm-700, #44403c);
}

.detail-article__content :deep(.markdown-body h2),
.detail-article__content :deep(.markdown-body h3) {
  margin: 28px 0 14px;
  font-family: 'Noto Serif SC', Georgia, serif;
  font-size: 20px;
  font-weight: 700;
  color: var(--warm-900, #1c1917);
  line-height: 1.4;
}

.detail-article__content :deep(.markdown-body p) {
  margin: 0 0 16px;
}

.detail-article__content :deep(.markdown-body strong) {
  color: var(--warm-900, #1c1917);
  font-weight: 700;
}

.detail-article__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid var(--warm-100);
}

.detail-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 11px 24px;
  border: 1px solid var(--warm-200);
  border-radius: 12px;
  background: #fff;
  font-size: 14px;
  font-weight: 600;
  color: var(--warm-600);
  cursor: pointer;
  transition: all 0.2s ease;
}

.detail-action-btn svg {
  width: 18px;
  height: 18px;
}

.detail-action-btn:hover {
  border-color: var(--health-500);
  color: var(--health-600);
  background: rgba(13, 148, 136, 0.06);
}

.detail-action-btn--favorited {
  border-color: var(--health-600);
  background: var(--health-600);
  color: #fff;
}

.detail-action-btn--favorited:hover {
  border-color: var(--health-700);
  background: var(--health-700);
  color: #fff;
}

.detail-action-btn--ghost:hover {
  background: var(--warm-50);
}

.detail-disclaimer {
  margin-top: 24px !important;
}

.related-section {
  background: #fff;
  border-radius: 16px;
  padding: 24px clamp(20px, 3vw, 28px);
  border: 1px solid var(--warm-100);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.related-section__title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 0 20px;
  font-family: 'Noto Serif SC', Georgia, serif;
  font-size: 20px;
  font-weight: 700;
  color: var(--warm-900, #1c1917);
}

.related-section__bar {
  width: 4px;
  height: 20px;
  border-radius: 2px;
  background: var(--health-600);
  flex-shrink: 0;
}

.related-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.related-card {
  display: flex;
  align-items: stretch;
  gap: 20px;
  padding: 18px 20px;
  background: var(--warm-50);
  border-radius: 12px;
  border: 1px solid var(--warm-100);
  cursor: pointer;
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
}

.related-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
  border-color: var(--warm-200);
}

.related-card__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.related-card__tag {
  align-self: flex-start;
  display: inline-flex;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 10px;
}

.related-card__title {
  margin: 0 0 8px;
  font-family: 'Noto Serif SC', Georgia, serif;
  font-size: 17px;
  font-weight: 700;
  color: var(--warm-900, #1c1917);
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.related-card__summary {
  flex: 1;
  margin: 0 0 12px;
  font-size: 14px;
  line-height: 1.65;
  color: var(--warm-500);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.related-card__meta {
  font-size: 12px;
  color: var(--warm-400);
}

.related-card__dot {
  margin: 0 6px;
}

.related-card__thumb {
  flex-shrink: 0;
  width: 112px;
  height: 112px;
  border-radius: 10px;
  overflow: hidden;
  background: var(--warm-100);
}

.related-card__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

@media (max-width: 1280px) {
  .article-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .article-grid {
    grid-template-columns: 1fr;
    gap: 20px;
  }

  .tab-nav__item {
    padding: 12px 20px;
    font-size: 14px;
  }

  .search-box__btn {
    padding: 16px 20px;
  }

  .category-pills {
    gap: 8px;
    margin-bottom: 24px;
  }

  .category-pill {
    padding: 6px 16px;
    font-size: 13px;
  }

  .related-card {
    flex-direction: column-reverse;
    gap: 14px;
  }

  .related-card__thumb {
    width: 100%;
    height: 160px;
  }

  /* 详情页：去掉面包屑与白卡片 */
  .health-info-page--detail-mobile :deep(.site-main) {
    padding-top: 16px;
  }

  .info-detail-page {
    max-width: none;
    padding: 0 0 32px;
  }

  .detail-article {
    background: transparent;
    border: none;
    box-shadow: none;
    border-radius: 0;
    padding: 0;
    margin-bottom: 24px;
  }

  .detail-hero__cover {
    border-radius: 12px;
    max-height: 220px;
  }

  .detail-article__title {
    font-size: 22px;
    margin-top: 4px;
  }

  .detail-meta {
    margin-bottom: 20px;
    padding-bottom: 16px;
  }

  .detail-article__actions {
    margin-top: 8px;
  }

  .related-section {
    padding: 0;
  }
}
</style>
