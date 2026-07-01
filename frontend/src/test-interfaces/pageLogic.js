export function isFavoriteFlag(value) {
  return value === true || value === 1 || value === '1'
}

export function syncFavoriteInHistory(history, planId, favorited) {
  const flag = favorited ? 1 : 0
  return history.map((item) => (item.plan_id === planId ? { ...item, is_favorite: flag } : item))
}

export function giLabel(level) {
  return { low: '低', medium: '中', high: '高' }[level] || level || '—'
}

export function giTagType(level) {
  return { low: 'success', medium: 'warning', high: 'danger' }[level] || 'info'
}

export function intensityType(value) {
  if (!value) return 'info'
  if (value.includes('高') || value.includes('强')) return 'danger'
  if (value.includes('中')) return 'warning'
  return 'success'
}

export function buildMealEntries(mealPlan = {}) {
  const meta = {
    breakfast: { label: '早餐', color: '#0d9488' },
    lunch: { label: '午餐', color: '#409eff' },
    dinner: { label: '晚餐', color: '#e6a23c' },
    snack: { label: '加餐', color: '#67c23a' },
  }
  return Object.entries(mealPlan).map(([key, meal]) => ({
    key,
    label: meta[key]?.label || key,
    color: meta[key]?.color || '#909399',
    time: meal.time || meta[key]?.label,
    foods: meal.foods || [],
    totalCalories: meal.total_calories ?? meal.calories ?? 0,
  }))
}

export function consultBtnText(status) {
  return { online: '立即咨询', offline: '留言咨询', busy: '排队中' }[status] || '立即咨询'
}

export function statusType(status) {
  return { online: 'success', offline: 'info', busy: 'warning' }[status] || 'info'
}

export function statusText(status) {
  return { online: '在线', offline: '离线', busy: '忙碌' }[status] || status || '离线'
}

export function probText(probability) {
  return { high: '较高', medium: '中等', low: '较低' }[probability] || probability
}

export function formatViewCount(count) {
  const n = Number(count) || 0
  if (n >= 10000) return `${(n / 10000).toFixed(1).replace(/\.0$/, '')}万`
  return n.toLocaleString('zh-CN')
}

export function truncateTitle(title, max = 20) {
  if (!title) return ''
  return title.length > max ? `${title.slice(0, max)}...` : title
}

export function calcGrams(amount, unit, mlToG) {
  if (unit === 2) return amount * (mlToG || 1)
  return amount
}

export function calcFoodCalories(item) {
  const grams = calcGrams(item.input_amount, item.input_unit, item.ml_to_g_ratio)
  return Math.round((item.calories_per_gram || 0) * grams)
}

export function calcExCalories(kpm, mins) {
  return Math.round((kpm || 0) * (mins || 0))
}

export function formatFoodAmount(record) {
  if (record.input_unit === 2) return `${record.input_amount}ml（≈${record.grams}g）`
  return `${record.input_amount}g`
}

export function glucoseStatusLabel(status) {
  const labels = {
    low: '偏低',
    normal: '正常',
    elevated: '偏高',
    high: '过高',
    unknown: '-',
  }
  return labels[status] || labels.unknown
}

export function buildTodayTasksSummary(list = []) {
  const done = list.filter((item) => item.completed).length
  return { done, total: Math.max(list.length, 4) }
}

export function buildTodayProgress(list = []) {
  const done = list.filter((item) => item.completed).length
  return Math.round((done / 4) * 100)
}
