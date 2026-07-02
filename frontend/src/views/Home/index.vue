<template>
  <AppLayout :show-nav="isMobile" full-width>
    <div class="home-root" :class="{ 'home-root--mobile-nav': isMobile }">
      <SiteHeader />

      <main class="home-main">
        <!-- Banner 轮播 -->
        <section v-if="banners.length" class="banner-section">
          <el-carousel
            :height="bannerHeight"
            :interval="3000"
            class="home-carousel"
            arrow="always"
          >
            <el-carousel-item v-for="b in banners" :key="b.id">
              <div class="banner-slide">
                <img class="banner-img" :src="b.image" :alt="b.title" loading="lazy" />
                <div class="glass-overlay">
                  <h2 class="banner-title">{{ b.title }}</h2>
                </div>
              </div>
            </el-carousel-item>
          </el-carousel>
        </section>

        <div class="home-container">
          <!-- 快捷入口 -->
          <section class="home-section">
            <div class="section-heading">
              <span class="heading-bar heading-bar--health" />
              <h2 class="heading-title">快捷服务</h2>
            </div>
            <div class="quick-grid">
              <button
                v-for="entry in quickEntries"
                :key="entry.key"
                type="button"
                class="quick-card card-lift"
                :class="`quick-card--${entry.theme}`"
                @click="navigateTo(entry.path, entry.requireAuth)"
              >
                <span class="quick-card__glow" />
                <span class="quick-card__icon">
                  <el-icon :size="28"><component :is="entry.icon" /></el-icon>
                </span>
                <h3 class="quick-card__title">{{ entry.label }}</h3>
                <p class="quick-card__desc">{{ entry.desc }}</p>
                <span class="quick-card__action">
                  {{ entry.action }}
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M8.25 4.5l7.5 7.5-7.5 7.5" />
                  </svg>
                </span>
              </button>
            </div>
          </section>

          <!-- 视频科普 -->
          <section v-if="videos.length" class="home-section">
            <div class="section-heading section-heading--between">
              <div class="section-heading__left">
                <span class="heading-bar heading-bar--health" />
                <h2 class="heading-title">热门科普视频</h2>
              </div>
              <button type="button" class="link-more" @click="$router.push('/science-videos')">
                查看全部
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M8.25 4.5l7.5 7.5-7.5 7.5" />
                </svg>
              </button>
            </div>
            <div class="video-grid">
              <div v-for="v in videos" :key="v.id" class="video-card" @click="openVideo(v)">
                <div class="video-cover">
                  <img :src="v.cover" :alt="v.title" loading="lazy" />
                  <span class="video-cover__shade" />
                  <span class="duration">{{ v.duration }}</span>
                  <span class="play-overlay">
                    <span class="play-btn">
                      <svg viewBox="0 0 20 20" fill="currentColor">
                        <path d="M6.3 2.841A1.5 1.5 0 004 4.11V15.89a1.5 1.5 0 002.3 1.269l9.344-5.89a1.5 1.5 0 000-2.538L6.3 2.84z" />
                      </svg>
                    </span>
                  </span>
                </div>
                <h3 class="video-title">{{ v.title }}</h3>
              </div>
            </div>
          </section>

          <!-- 医师团队 -->
          <section v-if="doctors.length" class="home-section">
            <div class="section-heading section-heading--between">
              <div class="section-heading__left">
                <span class="heading-bar heading-bar--trust" />
                <h2 class="heading-title">专业医师团队</h2>
              </div>
              <button type="button" class="link-more" @click="navigateTo('/consultation', true)">
                查看全部
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M8.25 4.5l7.5 7.5-7.5 7.5" />
                </svg>
              </button>
            </div>
            <div
              class="auto-scroll-strip auto-scroll-strip--doctors"
              :class="{ 'auto-scroll-strip--paused': doctorScrollPaused }"
              :style="{ '--scroll-duration': `${Math.max(doctors.length * 6, 24)}s` }"
              @mouseenter="doctorScrollPaused = true"
              @mouseleave="doctorScrollPaused = false"
              @touchstart.passive="doctorScrollPaused = true"
              @touchend.passive="doctorScrollPaused = false"
            >
              <div class="auto-scroll-strip__track">
                <div
                  v-for="(doc, index) in loopedDoctors"
                  :key="`${doc.doctor_id}-${index}`"
                  class="doctor-card card-lift"
                  :class="{ 'doctor-card--online': doc.status === 'online' }"
                  @click="startConsult(doc)"
                >
                  <div class="doctor-avatar-wrap">
                    <el-avatar :size="80" :src="doc.avatar_url" :class="{ 'avatar-offline': doc.status === 'offline' }" />
                    <span class="status-dot" :class="`status-dot--${doc.status}`">
                      <span v-if="doc.status === 'online'" class="pulse-ring" />
                    </span>
                  </div>
                  <h3 class="doc-name">{{ doc.name }}</h3>
                  <p class="doc-title">{{ doc.title }} · {{ doc.department }}</p>
                  <div class="doc-rating">
                    <svg viewBox="0 0 20 20" fill="currentColor">
                      <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                    </svg>
                    <span class="rating-value">{{ doc.rating }}</span>
                    <span class="rating-count">· {{ doc.consultation_count }}咨询</span>
                  </div>
                  <button
                    type="button"
                    class="consult-btn"
                    :class="consultBtnClass(doc.status)"
                    @click.stop="startConsult(doc)"
                  >
                    {{ consultBtnText(doc.status) }}
                  </button>
                </div>
              </div>
            </div>
          </section>

          <!-- 知识科普 -->
          <section v-if="articles.length" class="home-section">
            <div class="section-heading section-heading--between">
              <div class="section-heading__left">
                <span class="heading-bar heading-bar--health" />
                <h2 class="heading-title">知识科普</h2>
              </div>
              <button type="button" class="link-more" @click="$router.push('/health-info')">
                更多
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M8.25 4.5l7.5 7.5-7.5 7.5" />
                </svg>
              </button>
            </div>
            <div
              class="auto-scroll-strip auto-scroll-strip--articles"
              :class="{ 'auto-scroll-strip--paused': articleScrollPaused }"
              :style="{ '--scroll-duration': `${Math.max(articles.length * 7, 28)}s` }"
              @mouseenter="articleScrollPaused = true"
              @mouseleave="articleScrollPaused = false"
              @touchstart.passive="articleScrollPaused = true"
              @touchend.passive="articleScrollPaused = false"
            >
              <div class="auto-scroll-strip__track">
                <article
                  v-for="(art, index) in loopedArticles"
                  :key="`${art.article_id}-${index}`"
                  class="article-card card-lift"
                  @click="openArticle(art)"
                >
                  <div class="article-card__inner">
                    <div class="article-thumb">
                      <img :src="art.cover_image" :alt="art.title" />
                    </div>
                    <div class="article-body">
                      <div>
                        <h3 class="article-title">{{ art.title }}</h3>
                        <p class="article-summary">{{ art.summary }}</p>
                      </div>
                      <div class="article-meta">
                        <span>
                          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                            <path stroke-linecap="round" stroke-linejoin="round" d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z" />
                            <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                          </svg>
                          {{ art.view_count.toLocaleString() }}
                        </span>
                        <span v-if="art.published_at">
                          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                            <path stroke-linecap="round" stroke-linejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" />
                          </svg>
                          {{ formatRelative(art.published_at) }}
                        </span>
                      </div>
                    </div>
                  </div>
                </article>
              </div>
            </div>
          </section>

          <DisclaimerBar class="home-disclaimer" />
        </div>
      </main>

      <SiteFooter />
    </div>

    <VideoPlayerDialog v-model="playerVisible" :video="playingVideo" />
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'
import { TrendCharts, Document, DataAnalysis, Clock } from '@element-plus/icons-vue'
import AppLayout from '@/components/layout/AppLayout.vue'
import SiteHeader from '@/components/layout/SiteHeader.vue'
import SiteFooter from '@/components/layout/SiteFooter.vue'
import DisclaimerBar from '@/components/DisclaimerBar.vue'
import VideoPlayerDialog from '@/components/VideoPlayerDialog.vue'
import { useIsMobile } from '@/composables/useBreakpoints'
import { getHomeContent, getHomeArticles, getDoctors } from '@/api/home'
import { isLoggedIn } from '@/utils/auth'
import { requireLogin } from '@/utils/loginGate'
import { useUserStore } from '@/stores/user'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

const router = useRouter()
const userStore = useUserStore()
const isMobile = useIsMobile()
const banners = ref([])
const videos = ref([])
const doctors = ref([])
const articles = ref([])
const bannerHeight = ref('220px')
const playerVisible = ref(false)
const playingVideo = ref(null)
const doctorScrollPaused = ref(false)
const articleScrollPaused = ref(false)

const loopedDoctors = computed(() => {
  const list = doctors.value
  return list.length ? [...list, ...list] : []
})

const loopedArticles = computed(() => {
  const list = articles.value
  return list.length ? [...list, ...list] : []
})

const quickEntries = computed(() => {
  const ps = userStore.profile?.privacy_settings || {}
  const checkinNotify = ps.checkin_notify ?? true
  const reminderPath = checkinNotify
    ? '/checkin-reminder-settings'
    : { path: '/user-center', query: { section: 'checkin-notify' } }

  return [
  {
    key: 'health-evaluation',
    path: '/health-evaluation',
    label: '风险评估',
    desc: '3分钟测一测您的患病风险',
    action: '开始评估',
    icon: TrendCharts,
    theme: 'health',
    requireAuth: true,
  },
  {
    key: 'living-plans',
    path: '/living-plans',
    label: '健康方案',
    desc: '定制您的专属控糖计划',
    action: '制定方案',
    icon: Document,
    theme: 'trust',
    requireAuth: true,
  },
  {
    key: 'checkin-records',
    path: '/checkin-records',
    label: '生活打卡',
    desc: '记录饮食、运动、用药与血糖',
    action: '去打卡',
    icon: DataAnalysis,
    theme: 'accent',
    requireAuth: true,
  },
  {
    key: 'checkin-reminder',
    path: reminderPath,
    label: '打卡提醒',
    desc: '设定时段，提醒您完成饮食/运动/用药/血糖打卡',
    action: checkinNotify ? '管理时段' : '设置提醒',
    icon: Clock,
    theme: 'health-deep',
    requireAuth: true,
  },
]
})

function updateBannerHeight() {
  const edgePad = Math.min(Math.max(window.innerWidth * 0.04, 24), 64) * 2
  const maxW = Math.min(window.innerWidth - edgePad, 1360)
  bannerHeight.value = `${Math.round(maxW / 21 * 8)}px`
}

onMounted(async () => {
  updateBannerHeight()
  window.addEventListener('resize', updateBannerHeight)

  if (isLoggedIn()) {
    userStore.fetchProfile()
  }

  const [contentResult, articlesResult, doctorsResult] = await Promise.allSettled([
    getHomeContent(),
    getHomeArticles(4),
    getDoctors(),
  ])

  if (contentResult.status === 'fulfilled') {
    banners.value = contentResult.value.banners
    videos.value = contentResult.value.videos
  }
  if (articlesResult.status === 'fulfilled') {
    articles.value = articlesResult.value
  }
  if (doctorsResult.status === 'fulfilled') {
    doctors.value = doctorsResult.value
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', updateBannerHeight)
})

function navigateTo(to, requireAuth = false) {
  if (requireAuth && !requireLogin(to)) return
  router.push(to)
}

function openArticle(art) {
  const path = `/health-info/${art.article_id}`
  if (!requireLogin(path)) return
  router.push(path)
}

function consultBtnText(status) {
  return { online: '立即咨询', offline: '离线留言', busy: '排队中' }[status] || '立即咨询'
}

function consultBtnClass(status) {
  return { online: 'consult-btn--primary', offline: 'consult-btn--muted', busy: 'consult-btn--busy' }[status] || 'consult-btn--primary'
}

function formatRelative(iso) {
  return dayjs(iso).fromNow()
}

function startConsult(doc) {
  const target = { path: '/consultation/chat', query: { doctor_id: doc.doctor_id } }
  if (!requireLogin(target)) return
  router.push(target)
}

function openVideo(video) {
  if (!requireLogin('/home')) return
  playingVideo.value = video
  playerVisible.value = true
}
</script>

<style scoped>
.home-root {
  width: 100%;
  min-height: 100vh;
  background: var(--warm-50);
  box-sizing: border-box;
}

/* Main layout */
.home-main {
  width: 100%;
  max-width: var(--content-max);
  margin: 0 auto;
  padding: 32px var(--edge-padding) 0;
  box-sizing: border-box;
}

.home-container {
  display: flex;
  flex-direction: column;
  gap: 48px;
  padding-top: 48px;
}

.home-section {
  margin: 0;
}

/* Banner */
.banner-section {
  margin: 0;
}

.home-carousel {
  overflow: visible;
}

.home-carousel :deep(.el-carousel__container) {
  border-radius: 24px;
  overflow: hidden;
  box-shadow: 0 12px 48px rgba(0, 0, 0, 0.12);
}

.banner-slide {
  position: relative;
  height: 100%;
  overflow: hidden;
}

.banner-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: top center;
}

.glass-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: 32px 40px;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0) 0%, rgba(0, 0, 0, 0.45) 100%);
}

.banner-title {
  margin: 0;
  color: #fff;
  font-size: clamp(20px, 2.5vw, 30px);
  font-weight: 700;
  line-height: 1.25;
  text-shadow: 0 2px 12px rgba(0, 0, 0, 0.35);
  max-width: 640px;
}

.home-carousel :deep(.el-carousel__arrow) {
  width: 44px;
  height: 44px;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(4px);
  color: var(--warm-600);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
}

.home-carousel :deep(.el-carousel__arrow:hover) {
  background: #fff;
  color: var(--health-600);
}

.home-carousel :deep(.el-carousel__indicators) {
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
}

.home-carousel :deep(.el-carousel__indicator) {
  padding: 0 5px;
}

.home-carousel :deep(.el-carousel__button) {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.55);
  opacity: 1;
  transition: all 0.3s ease;
}

.home-carousel :deep(.el-carousel__indicator.is-active .el-carousel__button) {
  width: 32px;
  background: var(--health-500);
}

/* Section headings */
.section-heading {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}

.section-heading--between {
  justify-content: space-between;
}

.section-heading__left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.heading-bar {
  width: 6px;
  height: 20px;
  border-radius: 999px;
  flex-shrink: 0;
}

.heading-bar--health {
  background: linear-gradient(180deg, var(--health-400), var(--health-600));
}

.heading-bar--trust {
  background: linear-gradient(180deg, var(--trust-400), var(--trust-600));
}

.heading-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--warm-800);
}

.link-more {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  border: none;
  background: none;
  padding: 0;
  font-size: 14px;
  color: var(--warm-400);
  cursor: pointer;
  transition: color 0.2s ease;
}

.link-more svg {
  width: 16px;
  height: 16px;
}

.link-more:hover {
  color: var(--health-600);
}

/* Card lift */
.card-lift {
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1), box-shadow 0.3s ease;
}

.card-lift:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.12);
}

.card-lift:active {
  transform: scale(0.97);
}

/* Quick access — 4 columns */
.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 20px;
}

.quick-card {
  position: relative;
  display: block;
  width: 100%;
  text-align: left;
  padding: 24px;
  border: 1px solid rgba(231, 229, 228, 0.8);
  border-radius: 20px;
  background: #fff;
  box-shadow: 0 2px 16px rgba(0, 0, 0, 0.04);
  overflow: hidden;
  cursor: pointer;
}

.quick-card__glow {
  position: absolute;
  top: 0;
  right: 0;
  width: 112px;
  height: 112px;
  border-bottom-left-radius: 100%;
  pointer-events: none;
}

.quick-card--health .quick-card__glow {
  background: linear-gradient(to bottom left, rgba(204, 251, 241, 0.5), transparent);
}

.quick-card--trust .quick-card__glow {
  background: linear-gradient(to bottom left, rgba(219, 234, 254, 0.5), transparent);
}

.quick-card--accent .quick-card__glow {
  background: linear-gradient(to bottom left, rgba(254, 243, 199, 0.5), transparent);
}

.quick-card--health-deep .quick-card__glow {
  background: linear-gradient(to bottom left, rgba(204, 251, 241, 0.5), transparent);
}

.quick-card__icon {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 14px;
  margin-bottom: 16px;
  color: #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.quick-card--health .quick-card__icon {
  background: linear-gradient(135deg, var(--health-400), var(--health-600));
}

.quick-card--trust .quick-card__icon {
  background: linear-gradient(135deg, var(--trust-400), var(--trust-600));
}

.quick-card--accent .quick-card__icon {
  background: linear-gradient(135deg, var(--accent-400), var(--accent-600));
}

.quick-card--health-deep .quick-card__icon {
  background: linear-gradient(135deg, var(--health-500), var(--health-700));
}

.quick-card__title {
  position: relative;
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 700;
  color: var(--warm-800);
}

.quick-card__desc {
  position: relative;
  margin: 0;
  font-size: 14px;
  line-height: 1.5;
  color: var(--warm-400);
}

.quick-card__action {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  font-size: 14px;
  font-weight: 600;
}

.quick-card--health .quick-card__action { color: var(--health-600); }
.quick-card--trust .quick-card__action { color: var(--trust-600); }
.quick-card--accent .quick-card__action { color: var(--accent-600); }
.quick-card--health-deep .quick-card__action { color: var(--health-700); }

.quick-card__action svg {
  width: 16px;
  height: 16px;
  transition: transform 0.2s ease;
}

.quick-card:hover .quick-card__action svg {
  transform: translateX(4px);
}

/* Videos — 4 columns */
.video-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 20px;
}

.video-card {
  cursor: pointer;
}

.video-cover {
  position: relative;
  aspect-ratio: 16 / 10;
  border-radius: 16px;
  overflow: hidden;
  margin-bottom: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

.video-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: top center;
  transition: transform 0.5s ease;
}

.video-card:hover .video-cover img {
  transform: scale(1.05);
}

.video-cover__shade {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.1);
  transition: background 0.3s ease;
}

.video-card:hover .video-cover__shade {
  background: rgba(0, 0, 0, 0.05);
}

.duration {
  position: absolute;
  bottom: 12px;
  right: 12px;
  padding: 2px 8px;
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.7);
  color: #fff;
  font-size: 11px;
  font-weight: 500;
  backdrop-filter: blur(4px);
}

.play-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s ease;
}

.video-card:hover .play-overlay {
  opacity: 1;
}

.play-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

.play-btn svg {
  width: 24px;
  height: 24px;
  margin-left: 3px;
  color: var(--warm-800);
}

.video-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--warm-800);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  transition: color 0.2s ease;
}

.video-card:hover .video-title {
  color: var(--health-600);
}

/* Auto scroll strips — 医师团队 / 知识科普 */
.auto-scroll-strip {
  --strip-gap: 20px;
  --scroll-duration: 32s;
  overflow: hidden;
  margin-left: calc(-1 * var(--edge-padding));
  margin-right: calc(-1 * var(--edge-padding));
  padding-left: var(--edge-padding);
  padding-right: var(--edge-padding);
  mask-image: linear-gradient(
    to right,
    transparent,
    #000 12px,
    #000 calc(100% - 12px),
    transparent
  );
}

.auto-scroll-strip__track {
  display: flex;
  flex-wrap: nowrap;
  align-items: stretch;
  gap: var(--strip-gap);
  width: max-content;
  animation: auto-scroll-x var(--scroll-duration) linear infinite;
  will-change: transform;
}

.auto-scroll-strip--paused .auto-scroll-strip__track {
  animation-play-state: paused;
}

@keyframes auto-scroll-x {
  from {
    transform: translateX(0);
  }
  to {
    transform: translateX(calc(-50% - var(--strip-gap) / 2));
  }
}

@media (prefers-reduced-motion: reduce) {
  .auto-scroll-strip {
    overflow-x: auto;
    mask-image: none;
    scrollbar-width: none;
  }

  .auto-scroll-strip::-webkit-scrollbar {
    display: none;
  }

  .auto-scroll-strip__track {
    animation: none;
  }
}

.auto-scroll-strip--doctors .doctor-card {
  flex: 0 0 280px;
  width: 280px;
}

.auto-scroll-strip--articles .article-card {
  flex: 0 0 480px;
  width: 480px;
}

.doctor-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px;
  border-radius: 20px;
  border: 1px solid rgba(231, 229, 228, 0.8);
  background: #fff;
  box-shadow: 0 2px 16px rgba(0, 0, 0, 0.04);
  text-align: center;
  cursor: pointer;
}

.doctor-card--online {
  border-color: rgba(20, 184, 166, 0.3);
}

.doctor-card--online:hover {
  border-color: rgba(20, 184, 166, 0.6);
}

.doctor-avatar-wrap {
  position: relative;
  display: inline-block;
  margin-bottom: 12px;
}

.doctor-avatar-wrap :deep(.el-avatar) {
  border: 2px solid var(--warm-200);
}

.doctor-card--online :deep(.el-avatar) {
  border-color: #99f6e4;
}

.avatar-offline {
  filter: grayscale(1);
}

.status-dot {
  position: absolute;
  right: 2px;
  bottom: 2px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.status-dot::after {
  content: '';
  width: 12px;
  height: 12px;
  border-radius: 50%;
}

.status-dot--online::after { background: var(--health-500); }
.status-dot--busy::after { background: var(--accent-500); }
.status-dot--offline::after { background: var(--warm-300); }

.pulse-ring {
  position: absolute;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  color: var(--health-500);
}

.pulse-ring::before {
  content: '';
  position: absolute;
  inset: -3px;
  border-radius: 50%;
  border: 2px solid currentColor;
  animation: pulse-ring 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}

@keyframes pulse-ring {
  0% { transform: scale(0.8); opacity: 1; }
  100% { transform: scale(2.2); opacity: 0; }
}

.doc-name {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--warm-800);
}

.doc-title {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--warm-400);
  line-height: 1.45;
  min-height: calc(1.45em * 2);
}

.doc-rating {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin-top: 8px;
}

.doc-rating svg {
  width: 16px;
  height: 16px;
  color: var(--accent-400);
}

.rating-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--warm-600);
}

.rating-count {
  font-size: 12px;
  color: var(--warm-300);
}

.consult-btn {
  width: 100%;
  margin-top: auto;
  padding: 10px 0;
  border: none;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: box-shadow 0.2s ease, background 0.2s ease;
  box-sizing: border-box;
}

.consult-btn--primary {
  color: #fff;
  background: linear-gradient(90deg, var(--health-500), var(--health-600));
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.consult-btn--primary:hover {
  box-shadow: 0 4px 12px rgba(13, 148, 136, 0.25);
}

.consult-btn--busy,
.consult-btn--muted {
  color: var(--warm-500);
  background: var(--warm-100);
}

.consult-btn--muted {
  color: var(--warm-400);
}

.consult-btn--busy:hover,
.consult-btn--muted:hover {
  background: var(--warm-200);
}

.article-card {
  border-radius: 20px;
  border: 1px solid rgba(231, 229, 228, 0.8);
  background: #fff;
  box-shadow: 0 2px 16px rgba(0, 0, 0, 0.04);
  overflow: hidden;
  cursor: pointer;
}

.article-card__inner {
  display: flex;
  gap: 20px;
  padding: 20px;
}

.article-thumb {
  flex-shrink: 0;
  width: 140px;
  height: 100px;
  border-radius: 14px;
  overflow: hidden;
}

.article-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: top center;
  transition: transform 0.5s ease;
}

.article-card:hover .article-thumb img {
  transform: scale(1.08);
}

.article-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 4px 0;
}

.article-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--warm-800);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  transition: color 0.2s ease;
}

.article-card:hover .article-title {
  color: var(--health-600);
}

.article-summary {
  margin: 8px 0 0;
  font-size: 14px;
  line-height: 1.5;
  color: var(--warm-400);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.article-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 12px;
  font-size: 13px;
  color: var(--warm-300);
}

.article-meta span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.article-meta svg {
  width: 16px;
  height: 16px;
}

/* Responsive */
@media (max-width: 1024px) {
  .quick-grid,
  .video-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .home-main {
    padding: 16px var(--edge-padding) 0;
  }

  .home-container {
    gap: 32px;
    padding-top: 32px;
  }

  .home-root--mobile-nav .site-footer {
    margin-bottom: env(safe-area-inset-bottom);
  }

  .glass-overlay {
    padding: 20px 16px;
  }

  .home-carousel :deep(.el-carousel__arrow) {
    width: 36px;
    height: 36px;
    opacity: 0;
    pointer-events: none;
    transition: opacity 0.2s ease;
  }

  .banner-section:hover .home-carousel :deep(.el-carousel__arrow) {
    opacity: 1;
    pointer-events: auto;
  }

  .home-carousel :deep(.el-carousel__indicators) {
    bottom: 12px;
  }

  .home-carousel :deep(.el-carousel__indicator) {
    padding: 0 3px;
  }

  .home-carousel :deep(.el-carousel__button) {
    width: 5px;
    height: 5px;
    background: rgba(120, 120, 120, 0.55);
  }

  .home-carousel :deep(.el-carousel__indicator.is-active .el-carousel__button) {
    width: 14px;
    background: rgba(100, 100, 100, 0.85);
  }

  .section-heading--between {
    flex-wrap: wrap;
    gap: 8px;
  }

  .home-disclaimer {
    display: none;
  }

  /* 横向滚动卡片条 */
  .quick-grid,
  .video-grid {
    display: flex;
    flex-wrap: nowrap;
    gap: 12px;
    overflow-x: auto;
    overscroll-behavior-x: contain;
    scroll-snap-type: x proximity;
    -webkit-overflow-scrolling: touch;
    scrollbar-width: none;
    margin-left: calc(-1 * var(--edge-padding));
    margin-right: calc(-1 * var(--edge-padding));
    padding-left: var(--edge-padding);
    padding-right: var(--edge-padding);
    padding-bottom: 4px;
  }

  .quick-grid::-webkit-scrollbar,
  .video-grid::-webkit-scrollbar {
    display: none;
  }

  .auto-scroll-strip {
    --strip-gap: 12px;
  }

  .auto-scroll-strip--doctors .doctor-card {
    flex: 0 0 132px;
    width: 132px;
    padding: 14px 10px;
    border-radius: 16px;
  }

  /* 快捷服务 */
  .quick-card {
    flex: 0 0 128px;
    width: 128px;
    padding: 14px 12px;
    border-radius: 16px;
    scroll-snap-align: start;
  }

  .quick-card__glow {
    width: 72px;
    height: 72px;
  }

  .quick-card__icon {
    width: 40px;
    height: 40px;
    border-radius: 12px;
    margin-bottom: 10px;
  }

  .quick-card__icon :deep(.el-icon) {
    font-size: 22px !important;
  }

  .quick-card__title {
    font-size: 14px;
    line-height: 1.35;
  }

  .quick-card__desc,
  .quick-card__action {
    display: none;
  }

  /* 科普视频 */
  .video-card {
    flex: 0 0 136px;
    width: 136px;
    scroll-snap-align: start;
  }

  .video-cover {
    border-radius: 12px;
    margin-bottom: 8px;
  }

  .video-title {
    font-size: 13px;
    -webkit-line-clamp: 2;
  }

  .duration {
    bottom: 8px;
    right: 8px;
    font-size: 10px;
  }

  .play-btn {
    width: 40px;
    height: 40px;
  }

  .play-btn svg {
    width: 18px;
    height: 18px;
  }

  /* 医师团队 */
  .doctor-avatar-wrap {
    margin-bottom: 8px;
  }

  .doctor-avatar-wrap :deep(.el-avatar) {
    width: 52px !important;
    height: 52px !important;
  }

  .status-dot {
    width: 16px;
    height: 16px;
  }

  .status-dot::after {
    width: 10px;
    height: 10px;
  }

  .doc-name {
    font-size: 14px;
  }

  .doc-title {
    font-size: 11px;
    line-height: 1.4;
    min-height: calc(1.4em * 2);
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .doc-rating {
    margin-top: 6px;
  }

  .doc-rating svg {
    width: 14px;
    height: 14px;
  }

  .rating-value {
    font-size: 12px;
  }

  .rating-count {
    display: none;
  }

  .consult-btn {
    padding: 8px 0;
    font-size: 12px;
    border-radius: 10px;
    min-height: 36px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  /* 知识科普 */
  .auto-scroll-strip--articles .article-card {
    flex: 0 0 248px;
    width: 248px;
    border-radius: 16px;
  }

  .article-card__inner {
    flex-direction: row;
    align-items: stretch;
    gap: 10px;
    padding: 12px;
  }

  .article-thumb {
    width: 72px;
    height: 72px;
    border-radius: 10px;
  }

  .article-body {
    padding: 0;
    justify-content: center;
  }

  .article-title {
    font-size: 14px;
    -webkit-line-clamp: 2;
  }

  .article-summary {
    display: none;
  }

  .article-meta {
    gap: 8px;
    margin-top: 6px;
    font-size: 11px;
  }

  .article-meta svg {
    width: 14px;
    height: 14px;
  }
}

@media (max-width: 480px) {
  .quick-card {
    flex: 0 0 120px;
    width: 120px;
  }

  .video-card {
    flex: 0 0 128px;
    width: 128px;
  }

  .auto-scroll-strip--doctors .doctor-card {
    flex: 0 0 124px;
    width: 124px;
  }

  .auto-scroll-strip--articles .article-card {
    flex: 0 0 232px;
    width: 232px;
  }

  .heading-title {
    font-size: 18px;
  }
}
</style>
