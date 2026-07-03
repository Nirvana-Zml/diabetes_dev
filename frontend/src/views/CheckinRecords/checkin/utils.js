import dayjs from 'dayjs'

export function calcGrams(amount, unit, mlToG) {
  if (unit === 2) return amount * (mlToG || 1)
  return amount
}

export function calcFoodCalories(item) {
  const grams = calcGrams(item.input_amount, item.input_unit, item.ml_to_g_ratio)
  return Math.round((item.calories_per_gram || 0) * grams)
}

export function calcPresetFoodCalories(food, amount, unit) {
  if (!food) return 0
  const grams = calcGrams(amount, unit, food.ml_to_g_ratio)
  return Math.round(food.calories_per_gram * grams)
}

export function calcExCalories(kpm, mins) {
  return Math.round((kpm || 0) * (mins || 0))
}

export function formatFoodAmount(r) {
  if (r.input_unit === 2) return `${r.input_amount}ml（≈${r.grams}g）`
  return `${r.input_amount}g`
}

export function formatTime(t) {
  if (!t) return ''
  return dayjs(t).format('HH:mm')
}

export function formatRecordTime(t) {
  if (!t) return ''
  const parsed = dayjs(t)
  return parsed.isValid() ? parsed.format('HH:mm') : ''
}

export function inferMealPeriod(date = dayjs()) {
  const hour = date.hour()
  if (hour < 10) return 1
  if (hour < 14) return 2
  if (hour < 17) return 4
  if (hour < 20) return 5
  if (hour < 22) return 3
  return 6
}

export function currentRecordTime() {
  return dayjs().format('HH:mm')
}

export function buildRecordDateTime(dateStr, timeStr) {
  if (!dateStr || !timeStr) return undefined
  return `${dateStr}T${timeStr}:00`
}

export function onImgError(e) {
  e.target.style.display = 'none'
}

export function buildDateDisplay(dateStr) {
  const d = dayjs(dateStr)
  const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return {
    main: d.isSame(dayjs(), 'day') ? '今天' : d.format('M月D日'),
    full: d.format('YYYY年M月D日'),
    weekday: weekdays[d.day()],
  }
}
