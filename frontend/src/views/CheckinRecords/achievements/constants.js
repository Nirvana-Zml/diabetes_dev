/** 成就墙完整目录（与需求稿一致） */
export const ACHIEVEMENT_CATALOG = [
  {
    id: 'first_checkin',
    name: '初来乍到',
    desc: '完成首次打卡',
    emoji: '👋',
    theme: 'amber',
    apiNames: ['首次打卡'],
    evaluate: (s) => (s.total_checkins || 0) > 0,
  },
  {
    id: 'streak_7',
    name: '坚持不懈',
    desc: '连续打卡7天',
    emoji: '🔥',
    theme: 'emerald',
    apiNames: ['连续打卡达人'],
    evaluate: (s) => (s.streak_days || 0) >= 7,
    progress: (s) => ({ unit: '天', remaining: Math.max(0, 7 - (s.streak_days || 0)) }),
  },
  {
    id: 'exercise_master',
    name: '运动达人',
    desc: '累计运动打卡30次',
    emoji: '🏃',
    theme: 'sky',
    evaluate: (s) => (s.exercise_count || 0) >= 30,
    progress: (s) => ({ unit: '次', remaining: Math.max(0, 30 - (s.exercise_count || 0)) }),
  },
  {
    id: 'food_expert',
    name: '饮食专家',
    desc: '记录食物打卡100次',
    emoji: '🥗',
    theme: 'rose',
    evaluate: (s) => (s.food_count || 0) >= 100,
    progress: (s) => ({ unit: '次', remaining: Math.max(0, 100 - (s.food_count || 0)) }),
  },
  {
    id: 'glucose_guard',
    name: '血糖卫士',
    desc: '连续记录血糖7天',
    emoji: '🛡️',
    theme: 'violet',
    evaluate: (s) => (s.glucose_streak || 0) >= 7,
    progress: (s) => ({ unit: '天', remaining: Math.max(0, 7 - (s.glucose_streak || 0)) }),
  },
  {
    id: 'early_bird',
    name: '早起鸟',
    desc: '连续7天8点前打卡',
    emoji: '🌅',
    theme: 'yellow',
    evaluate: (s) => (s.early_bird_streak || 0) >= 7,
    progress: (s) => ({ unit: '天', remaining: Math.max(0, 7 - (s.early_bird_streak || 0)) }),
  },
  {
    id: 'century',
    name: '百日坚持',
    desc: '连续打卡100天',
    emoji: '💯',
    theme: 'slate',
    apiNames: ['打卡王者'],
    evaluate: (s) => (s.streak_days || 0) >= 100,
    progress: (s) => ({ unit: '天', remaining: Math.max(0, 100 - (s.streak_days || 0)) }),
  },
  {
    id: 'medication_streak',
    name: '药不能停',
    desc: '连续用药打卡30天',
    emoji: '💊',
    theme: 'slate',
    evaluate: (s) => (s.medication_streak || 0) >= 30,
    progress: (s) => ({ unit: '天', remaining: Math.max(0, 30 - (s.medication_streak || 0)) }),
  },
  {
    id: 'calorie_master',
    name: '热量掌控者',
    desc: '连续7天热量达标',
    emoji: '⚖️',
    theme: 'slate',
    evaluate: (s) => (s.calorie_streak || 0) >= 7,
    progress: (s) => ({ unit: '天', remaining: Math.max(0, 7 - (s.calorie_streak || 0)) }),
  },
  {
    id: 'all_rounder',
    name: '全能健将',
    desc: '四项打卡均达50次',
    emoji: '🏆',
    theme: 'slate',
    evaluate: (s) => {
      const counts = [s.food_count, s.exercise_count, s.medication_count, s.glucose_count]
      return counts.every((n) => (n || 0) >= 50)
    },
    progress: (s) => {
      const counts = [s.food_count || 0, s.exercise_count || 0, s.medication_count || 0, s.glucose_count || 0]
      const min = Math.min(...counts)
      return { unit: '次', remaining: Math.max(0, 50 - min) }
    },
  },
  {
    id: 'night_diner',
    name: '深夜食堂',
    desc: '记录晚餐打卡20次',
    emoji: '🌙',
    theme: 'slate',
    evaluate: (s) => (s.dinner_count || 0) >= 20,
    progress: (s) => ({ unit: '次', remaining: Math.max(0, 20 - (s.dinner_count || 0)) }),
  },
  {
    id: 'perfect_day',
    name: '完美一周',
    desc: '单日完成全部打卡',
    emoji: '⭐',
    theme: 'slate',
    evaluate: (s) => (s.perfect_days || 0) >= 1,
    progress: () => ({ unit: '', remaining: null }),
  },
]

export const ACHIEVEMENT_FILTERS = [
  { value: 'all', label: '全部' },
  { value: 'unlocked', label: '已解锁' },
  { value: 'locked', label: '未解锁' },
]

/** 后端成就名 -> 目录 id */
export const API_ACHIEVEMENT_UNLOCKS = {
  首次打卡: ['first_checkin'],
  连续打卡达人: ['streak_7'],
}

export const ACHIEVEMENT_THEMES = {
  amber: {
    cardBg: 'linear-gradient(135deg, #fffbeb 0%, #fff7ed 50%, #fefce8 100%)',
    cardBorder: 'rgba(251, 191, 36, 0.45)',
    cardShadow: 'rgba(245, 158, 11, 0.12)',
    iconBg: 'linear-gradient(135deg, #fbbf24, #f97316)',
    iconShadow: 'rgba(245, 158, 11, 0.3)',
    badgeBg: '#fef3c7',
    badgeColor: '#b45309',
  },
  emerald: {
    cardBg: 'linear-gradient(135deg, #ecfdf5 0%, #f0fdfa 50%, #f0fdf4 100%)',
    cardBorder: 'rgba(52, 211, 153, 0.45)',
    cardShadow: 'rgba(16, 185, 129, 0.12)',
    iconBg: 'linear-gradient(135deg, #34d399, #14b8a6)',
    iconShadow: 'rgba(16, 185, 129, 0.3)',
    badgeBg: '#d1fae5',
    badgeColor: '#047857',
  },
  sky: {
    cardBg: 'linear-gradient(135deg, #f0f9ff 0%, #eff6ff 50%, #ecfeff 100%)',
    cardBorder: 'rgba(56, 189, 248, 0.45)',
    cardShadow: 'rgba(14, 165, 233, 0.12)',
    iconBg: 'linear-gradient(135deg, #38bdf8, #3b82f6)',
    iconShadow: 'rgba(14, 165, 233, 0.3)',
    badgeBg: '#e0f2fe',
    badgeColor: '#0369a1',
  },
  rose: {
    cardBg: 'linear-gradient(135deg, #fff1f2 0%, #fdf2f8 50%, #fef2f2 100%)',
    cardBorder: 'rgba(251, 113, 133, 0.45)',
    cardShadow: 'rgba(244, 63, 94, 0.12)',
    iconBg: 'linear-gradient(135deg, #fb7185, #ec4899)',
    iconShadow: 'rgba(244, 63, 94, 0.3)',
    badgeBg: '#ffe4e6',
    badgeColor: '#be123c',
  },
  violet: {
    cardBg: 'linear-gradient(135deg, #f5f3ff 0%, #faf5ff 50%, #fdf4ff 100%)',
    cardBorder: 'rgba(167, 139, 250, 0.45)',
    cardShadow: 'rgba(139, 92, 246, 0.12)',
    iconBg: 'linear-gradient(135deg, #a78bfa, #a855f7)',
    iconShadow: 'rgba(139, 92, 246, 0.3)',
    badgeBg: '#ede9fe',
    badgeColor: '#6d28d9',
  },
  yellow: {
    cardBg: 'linear-gradient(135deg, #fefce8 0%, #fffbeb 50%, #fff7ed 100%)',
    cardBorder: 'rgba(250, 204, 21, 0.45)',
    cardShadow: 'rgba(234, 179, 8, 0.12)',
    iconBg: 'linear-gradient(135deg, #facc15, #f59e0b)',
    iconShadow: 'rgba(234, 179, 8, 0.3)',
    badgeBg: '#fef9c3',
    badgeColor: '#a16207',
  },
  slate: {
    cardBg: '#f8fafc',
    cardBorder: 'rgba(203, 213, 225, 0.8)',
    cardShadow: 'transparent',
    iconBg: '#e2e8f0',
    iconShadow: 'transparent',
    badgeBg: '#e2e8f0',
    badgeColor: '#64748b',
  },
}
