import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'
import {
  ACHIEVEMENT_CATALOG,
  API_ACHIEVEMENT_UNLOCKS,
} from './constants'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

function normalizeStats(raw = {}) {
  return {
    total_checkins: raw.total_checkins ?? raw.totalCheckins ?? 0,
    streak_days: raw.streak_days ?? raw.streakDays ?? 0,
    food_count: raw.food_count ?? raw.foodCount ?? 0,
    exercise_count: raw.exercise_count ?? raw.exerciseCount ?? 0,
    medication_count: raw.medication_count ?? raw.medicationCount ?? 0,
    glucose_count: raw.glucose_count ?? raw.glucoseCount ?? 0,
    medication_streak: raw.medication_streak ?? raw.medicationStreak ?? 0,
    glucose_streak: raw.glucose_streak ?? raw.glucoseStreak ?? 0,
    early_bird_streak: raw.early_bird_streak ?? raw.earlyBirdStreak ?? 0,
    calorie_streak: raw.calorie_streak ?? raw.calorieStreak ?? 0,
    dinner_count: raw.dinner_count ?? raw.dinnerCount ?? 0,
    perfect_days: raw.perfect_days ?? raw.perfectDays ?? 0,
  }
}

function buildApiUnlockMap(apiAchievements = []) {
  const unlockedIds = new Set()
  const unlockDates = {}

  for (const item of apiAchievements) {
    const name = item.name
    const unlocked = !!item.unlocked
    if (!unlocked) continue

    const ids = API_ACHIEVEMENT_UNLOCKS[name] || []
    for (const id of ids) {
      unlockedIds.add(id)
      const at = item.unlocked_at || item.unlockedAt
      if (at && (!unlockDates[id] || dayjs(at).isAfter(unlockDates[id]))) {
        unlockDates[id] = at
      }
    }

    // 按成就名直接匹配目录项
    const matched = ACHIEVEMENT_CATALOG.find((c) => c.name === name || c.apiNames?.includes(name))
    if (matched) {
      unlockedIds.add(matched.id)
      const at = item.unlocked_at || item.unlockedAt
      if (at) unlockDates[matched.id] = at
    }
  }

  return { unlockedIds, unlockDates }
}

function formatUnlockDate(value) {
  if (!value) return ''
  return dayjs(value).format('YYYY.MM.DD')
}

function formatProgressHint(catalogItem, stats) {
  if (!catalogItem.progress) return '待完成'
  const { unit, remaining } = catalogItem.progress(stats)
  if (remaining == null) return '待完成'
  if (remaining === 0) return '即将解锁'
  return unit ? `还差 ${remaining} ${unit}` : '待完成'
}

/**
 * 合并成就目录、接口解锁状态与打卡统计
 * @param {Array} apiAchievements 后端 /checkin/achievements 原始列表
 * @param {Object} stats getCheckinStats 返回值
 * @param {Object} [overrides] mock 模式下的额外解锁日期等
 */
export function buildAchievementWall(apiAchievements = [], statsRaw = {}, overrides = {}) {
  const stats = normalizeStats(statsRaw)
  const { unlockedIds, unlockDates } = buildApiUnlockMap(apiAchievements)
  const overrideDates = overrides.unlockDates || {}

  const achievements = ACHIEVEMENT_CATALOG.map((item) => {
    const ruleUnlocked = item.evaluate ? item.evaluate(stats) : false
    const apiUnlocked = unlockedIds.has(item.id)
    const unlocked = ruleUnlocked || apiUnlocked

    const unlockedAt = overrideDates[item.id]
      || unlockDates[item.id]
      || (unlocked ? overrides.defaultUnlockDate : null)

    return {
      id: item.id,
      name: item.name,
      desc: item.desc,
      emoji: item.emoji,
      theme: unlocked ? item.theme : 'slate',
      unlocked,
      unlocked_at: unlockedAt,
      unlock_label: unlocked
        ? (unlockedAt ? `${formatUnlockDate(unlockedAt)} 解锁` : '已解锁')
        : formatProgressHint(item, stats),
    }
  })

  const unlockedList = achievements.filter((a) => a.unlocked)
  const total = achievements.length
  const unlockedCount = unlockedList.length
  const progressPercent = total ? Math.round((unlockedCount / total) * 100) : 0

  const recent = [...unlockedList]
    .sort((a, b) => {
      const ta = a.unlocked_at ? dayjs(a.unlocked_at).valueOf() : 0
      const tb = b.unlocked_at ? dayjs(b.unlocked_at).valueOf() : 0
      return tb - ta
    })[0]

  const recentUnlock = recent
    ? {
        name: recent.name,
        relative: recent.unlocked_at
          ? dayjs(recent.unlocked_at).fromNow()
          : '刚刚',
      }
    : null

  return {
    achievements,
    total,
    unlockedCount,
    progressPercent,
    recentUnlock,
  }
}

export function filterAchievements(list, filter) {
  if (filter === 'unlocked') return list.filter((a) => a.unlocked)
  if (filter === 'locked') return list.filter((a) => !a.unlocked)
  return list
}

/** @internal 供单元测试覆盖格式化分支 */
export const achievementWallTestUtils = {
  formatUnlockDate,
  formatProgressHint,
}
