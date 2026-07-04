import { Dish, FirstAidKit, Odometer, TrendCharts } from '@element-plus/icons-vue'

export const CALORIE_TARGET = 1800

/** 用户自定义食物分类 ID，与后端 UserCustomPresetService.CUSTOM_CATEGORY_ID 一致 */
export const CUSTOM_FOOD_CATEGORY_ID = 'cat_custom'

export const MEAL_PERIODS = [
  { value: 1, label: '早餐' },
  { value: 2, label: '午餐' },
  { value: 3, label: '晚餐' },
  { value: 4, label: '上午加餐' },
  { value: 5, label: '下午加餐' },
  { value: 6, label: '晚上加餐' },
]

export const GLUCOSE_STATUS_LABELS = {
  low: '偏低',
  normal: '正常',
  elevated: '偏高',
  high: '过高',
  unknown: '-',
}

export const CHECKIN_TYPES = [
  { key: 'food', label: '食物打卡', shortLabel: '食物', icon: Dish, color: '#10b981', desc: '记录饮食与热量' },
  { key: 'medication', label: '用药打卡', shortLabel: '用药', icon: FirstAidKit, color: '#6366f1', desc: '记录服药情况' },
  { key: 'exercise', label: '运动打卡', shortLabel: '运动', icon: Odometer, color: '#f59e0b', desc: '记录运动消耗' },
  { key: 'glucose', label: '血糖打卡', shortLabel: '血糖', icon: TrendCharts, color: '#ef4444', desc: '记录血糖趋势' },
]

export const CHECKIN_TYPE_MAP = Object.fromEntries(CHECKIN_TYPES.map((t) => [t.key, t]))

/** 与后端 todayCheckins.checkinType 对齐 */
export const CHECKIN_STATUS_TYPE = {
  food: 'diet',
  medication: 'medication',
  exercise: 'exercise',
  glucose: 'glucose',
}
