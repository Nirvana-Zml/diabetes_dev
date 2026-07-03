<template>
  <SiteLayout title="成就墙" show-back>
    <div class="achievement-wall page-container">
      <!-- 成就总进度 -->
      <section class="summary-card">
        <div class="summary-card__glow summary-card__glow--tr" />
        <div class="summary-card__glow summary-card__glow--bl" />
        <div class="summary-card__inner">
          <div class="summary-head">
            <div>
              <h2 class="summary-title">我的成就</h2>
              <p class="summary-sub">坚持打卡，解锁更多精彩</p>
            </div>
            <div class="summary-count">
              <span class="summary-count__num">{{ wall.unlockedCount }}<span class="summary-count__total">/{{ wall.total }}</span></span>
              <span class="summary-count__label">已解锁</span>
            </div>
          </div>

          <div class="summary-progress">
            <div class="summary-progress__meta">
              <span>完成进度</span>
              <span class="summary-progress__pct">{{ wall.progressPercent }}%</span>
            </div>
            <div class="summary-progress__track">
              <div
                class="summary-progress__fill"
                :style="{ width: `${wall.progressPercent}%` }"
              />
            </div>
          </div>

          <div v-if="wall.recentUnlock" class="recent-unlock">
            <span class="recent-unlock__emoji">🎉</span>
            <span class="recent-unlock__text">
              最近解锁：<strong>{{ wall.recentUnlock.name }}</strong>
              · {{ wall.recentUnlock.relative }}
            </span>
          </div>
        </div>
      </section>

      <!-- 筛选 -->
      <section class="filter-bar">
        <button
          v-for="opt in ACHIEVEMENT_FILTERS"
          :key="opt.value"
          type="button"
          class="filter-btn"
          :class="{ active: filter === opt.value }"
          @click="filter = opt.value"
        >{{ opt.label }}</button>
      </section>

      <!-- 成就网格 -->
      <section v-loading="loading" class="achievement-grid">
        <article
          v-for="(item, idx) in filteredList"
          :key="item.id"
          class="achievement-item"
          :class="{
            unlocked: item.unlocked,
            locked: !item.unlocked,
            [`stagger-${(idx % 6) + 1}`]: true,
          }"
          :style="item.unlocked ? cardStyle(item.theme) : undefined"
        >
          <div v-if="item.unlocked" class="achievement-item__shimmer" />
          <div v-else class="achievement-item__lock" aria-hidden="true">
            <svg viewBox="0 0 20 20" fill="currentColor">
              <path fill-rule="evenodd" d="M5 9V7a5 5 0 0110 0v2a2 2 0 012 2v5a2 2 0 01-2 2H5a2 2 0 01-2-2v-5a2 2 0 012-2zm8-2v2H7V7a3 3 0 016 0z" clip-rule="evenodd" />
            </svg>
          </div>

          <div class="achievement-item__body">
            <div
              class="achievement-item__icon"
              :class="{ 'achievement-item__icon--locked': !item.unlocked }"
              :style="item.unlocked ? iconStyle(item.theme) : undefined"
            >
              <span>{{ item.emoji }}</span>
            </div>
            <h3 class="achievement-item__name">{{ item.name }}</h3>
            <p class="achievement-item__desc">{{ item.desc }}</p>
            <span
              class="achievement-item__badge"
              :class="{ 'achievement-item__badge--locked': !item.unlocked }"
              :style="item.unlocked ? badgeStyle(item.theme) : undefined"
            >{{ item.unlock_label }}</span>
          </div>
        </article>

        <p v-if="!loading && !filteredList.length" class="empty-tip">暂无符合条件的成就</p>
      </section>
    </div>
  </SiteLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import SiteLayout from '@/components/layout/SiteLayout.vue'
import { getAchievementWall } from '@/api/checkin'
import { ACHIEVEMENT_FILTERS, ACHIEVEMENT_THEMES } from './achievements/constants'
import { filterAchievements } from './achievements/mergeAchievements'

const loading = ref(true)
const filter = ref('all')
const wall = ref({
  achievements: [],
  total: 0,
  unlockedCount: 0,
  progressPercent: 0,
  recentUnlock: null,
})

const filteredList = computed(() => filterAchievements(wall.value.achievements, filter.value))

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    wall.value = await getAchievementWall()
  } finally {
    loading.value = false
  }
}

function themeOf(key) {
  return ACHIEVEMENT_THEMES[key] || ACHIEVEMENT_THEMES.slate
}

function cardStyle(themeKey) {
  const t = themeOf(themeKey)
  return {
    background: t.cardBg,
    borderColor: t.cardBorder,
    boxShadow: `0 2px 16px ${t.cardShadow}`,
  }
}

function iconStyle(themeKey) {
  const t = themeOf(themeKey)
  return {
    background: t.iconBg,
    boxShadow: `0 4px 16px ${t.iconShadow}`,
  }
}

function badgeStyle(themeKey) {
  const t = themeOf(themeKey)
  return {
    background: t.badgeBg,
    color: t.badgeColor,
  }
}
</script>

<style scoped>
.achievement-wall {
  --aw-primary: #10b981;
  --aw-primary-dark: #059669;
  --aw-gold: #f59e0b;
  --aw-text: #1e293b;
  --aw-text-muted: #64748b;
  --aw-text-soft: #94a3b8;
  --aw-surface: #f1f5f9;
  --aw-card: #ffffff;
  padding-bottom: 32px;
}

.summary-card {
  position: relative;
  overflow: hidden;
  border-radius: 20px;
  padding: 24px;
  margin-bottom: 16px;
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.05) 0%, rgba(16, 185, 129, 0.1) 50%, rgba(245, 158, 11, 0.05) 100%);
  border: 1px solid rgba(16, 185, 129, 0.12);
}

.summary-card__glow {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}

.summary-card__glow--tr {
  top: -40px;
  right: -40px;
  width: 128px;
  height: 128px;
  background: rgba(16, 185, 129, 0.1);
  filter: blur(40px);
}

.summary-card__glow--bl {
  bottom: -32px;
  left: -32px;
  width: 96px;
  height: 96px;
  background: rgba(245, 158, 11, 0.1);
  filter: blur(32px);
}

.summary-card__inner {
  position: relative;
  z-index: 1;
}

.summary-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.summary-title {
  margin: 0;
  font-size: 22px;
  font-weight: 800;
  color: var(--aw-text);
  letter-spacing: -0.02em;
}

.summary-sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--aw-text-muted);
}

.summary-count {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  flex-shrink: 0;
}

.summary-count__num {
  font-size: 32px;
  font-weight: 900;
  line-height: 1;
  color: var(--aw-primary);
}

.summary-count__total {
  font-size: 18px;
  font-weight: 600;
  color: var(--aw-text-soft);
}

.summary-count__label {
  margin-top: 4px;
  font-size: 12px;
  color: var(--aw-text-soft);
}

.summary-progress__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--aw-text-muted);
}

.summary-progress__pct {
  font-weight: 700;
  color: var(--aw-primary);
}

.summary-progress__track {
  height: 12px;
  border-radius: 999px;
  background: var(--aw-surface);
  overflow: hidden;
}

.summary-progress__fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--aw-primary), var(--aw-gold));
  transition: width 1.2s ease-out;
}

.recent-unlock {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  padding: 8px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.65);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.85);
}

.recent-unlock__emoji {
  font-size: 16px;
  line-height: 1;
}

.recent-unlock__text {
  font-size: 12px;
  color: var(--aw-text-muted);
}

.recent-unlock__text strong {
  color: var(--aw-text);
  font-weight: 600;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.filter-btn {
  padding: 8px 16px;
  border: none;
  border-radius: 12px;
  background: var(--aw-surface);
  color: var(--aw-text-muted);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s, color 0.2s, transform 0.15s;
}

.filter-btn:hover {
  background: #d1fae5;
  color: var(--aw-primary);
}

.filter-btn.active {
  background: var(--aw-primary);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 2px 12px rgba(16, 185, 129, 0.3);
}

.filter-btn:active {
  transform: scale(0.97);
}

.achievement-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  min-height: 120px;
}

.achievement-item {
  position: relative;
  overflow: hidden;
  border-radius: 16px;
  padding: 16px;
  border: 1px solid rgba(203, 213, 225, 0.8);
  background: #f8fafc;
  animation: card-enter 0.5s ease-out both;
}

.achievement-item.locked .achievement-item__name {
  color: #64748b;
}

.achievement-item.locked .achievement-item__desc {
  color: #94a3b8;
}

.achievement-item__shimmer {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(90deg, transparent 0%, rgba(255, 255, 255, 0.3) 50%, transparent 100%);
  background-size: 200% 100%;
  animation: shimmer 3s ease-in-out infinite;
}

.achievement-item__lock {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
}

.achievement-item__lock svg {
  width: 14px;
  height: 14px;
}

.achievement-item__body {
  position: relative;
  z-index: 1;
}

.achievement-item__icon {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
  font-size: 24px;
  line-height: 1;
}

.achievement-item__icon--locked {
  background: #e2e8f0;
}

.achievement-item__icon--locked span {
  opacity: 0.4;
  filter: grayscale(1);
}

.achievement-item.unlocked .achievement-item__icon span {
  animation: glow-pulse 2s ease-in-out infinite;
}

.achievement-item__name {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--aw-text);
  line-height: 1.3;
}

.achievement-item__desc {
  margin: 4px 0 0;
  font-size: 11px;
  color: var(--aw-text-soft);
  line-height: 1.45;
}

.achievement-item__badge {
  display: inline-block;
  margin-top: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 500;
}

.achievement-item__badge--locked {
  background: #e2e8f0;
  color: #64748b;
}

.empty-tip {
  grid-column: 1 / -1;
  text-align: center;
  padding: 32px 16px;
  color: var(--aw-text-muted);
  font-size: 14px;
}

.stagger-1 { animation-delay: 0.05s; }
.stagger-2 { animation-delay: 0.1s; }
.stagger-3 { animation-delay: 0.15s; }
.stagger-4 { animation-delay: 0.2s; }
.stagger-5 { animation-delay: 0.25s; }
.stagger-6 { animation-delay: 0.3s; }

@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}

@keyframes glow-pulse {
  0%, 100% { filter: drop-shadow(0 0 4px rgba(245, 158, 11, 0.4)); }
  50% { filter: drop-shadow(0 0 12px rgba(245, 158, 11, 0.7)); }
}

@keyframes card-enter {
  from { opacity: 0; transform: translateY(16px) scale(0.96); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

@media (min-width: 768px) {
  .achievement-grid {
    grid-template-columns: repeat(3, 1fr);
    gap: 16px;
  }
}

@media (min-width: 1024px) {
  .achievement-grid {
    grid-template-columns: repeat(4, 1fr);
  }
}
</style>
