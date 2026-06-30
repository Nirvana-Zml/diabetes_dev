<template>
  <SiteLayout title="科普视频" show-back>
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
          placeholder="搜索视频标题..."
          @keyup.enter="loadVideos"
        />
        <button v-if="keyword" type="button" class="search-box__clear" aria-label="清除" @click="clearSearch">×</button>
        <button type="button" class="search-box__btn" @click="loadVideos">搜索</button>
      </div>
    </section>

    <p v-if="keyword" class="result-bar">
      搜索「{{ keyword }}」共 <strong>{{ total }}</strong> 个视频
    </p>

    <div v-loading="loading" class="video-page">
      <div v-if="videos.length" class="video-grid">
        <article
          v-for="v in videos"
          :key="v.id"
          class="video-card card-lift"
          @click="openVideo(v)"
        >
          <div class="video-cover">
            <img :src="v.cover" :alt="v.title" loading="lazy" />
            <span class="video-cover__shade" />
            <span v-if="v.duration" class="duration">{{ v.duration }}</span>
            <span class="play-overlay">
              <span class="play-btn">
                <svg viewBox="0 0 20 20" fill="currentColor">
                  <path d="M6.3 2.841A1.5 1.5 0 004 4.11V15.89a1.5 1.5 0 002.3 1.269l9.344-5.89a1.5 1.5 0 000-2.538L6.3 2.84z" />
                </svg>
              </span>
            </span>
          </div>
          <h3 class="video-title">{{ v.title }}</h3>
        </article>
      </div>
      <div v-else-if="!loading" class="empty-state">
        <p>{{ keyword ? '未找到相关视频' : '暂无科普视频' }}</p>
      </div>
    </div>

    <VideoPlayerDialog v-model="playerVisible" :video="playingVideo" />
  </SiteLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import SiteLayout from '@/components/layout/SiteLayout.vue'
import VideoPlayerDialog from '@/components/VideoPlayerDialog.vue'
import { getVideos } from '@/api/home'

const keyword = ref('')
const videos = ref([])
const total = ref(0)
const loading = ref(false)
const playerVisible = ref(false)
const playingVideo = ref(null)

async function loadVideos() {
  loading.value = true
  try {
    const data = await getVideos({ keyword: keyword.value })
    videos.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function clearSearch() {
  keyword.value = ''
  loadVideos()
}

function openVideo(video) {
  playingVideo.value = video
  playerVisible.value = true
}

onMounted(loadVideos)
</script>

<style scoped>
.search-wrap {
  margin-bottom: 24px;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 0;
  background: #fff;
  border: 1px solid var(--warm-100);
  border-radius: 999px;
  padding: 4px 4px 4px 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.search-box__icon-wrap {
  display: flex;
  color: var(--warm-400);
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
  padding: 10px 12px;
  background: transparent;
  color: var(--warm-800);
}

.search-box__clear {
  border: none;
  background: none;
  font-size: 22px;
  line-height: 1;
  color: var(--warm-300);
  cursor: pointer;
  padding: 0 8px;
}

.search-box__btn {
  border: none;
  border-radius: 999px;
  padding: 10px 20px;
  background: var(--health-500);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.result-bar {
  margin: 0 0 20px;
  font-size: 14px;
  color: var(--warm-500);
}

.video-page {
  min-height: 240px;
}

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

.empty-state {
  text-align: center;
  padding: 64px 0;
  color: var(--warm-400);
}

@media (max-width: 1024px) {
  .video-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .video-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 480px) {
  .video-grid {
    grid-template-columns: 1fr;
  }
}
</style>
