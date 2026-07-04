import { get, post } from '@/utils/request'
import { toSnakeCase, toCamelCase } from '@/utils/normalize'
import { USE_MOCK } from '@/config'
import { mockAchievements, mockAchievementStats } from '@/mock/data'
import { buildAchievementWall } from '@/views/CheckinRecords/achievements/mergeAchievements'
import dayjs from 'dayjs'

const mockFoodCategories = [
  { category_id: 'cat_drink', category_name: '饮品' },
  { category_id: 'cat_fruit', category_name: '水果' },
  { category_id: 'cat_grain', category_name: '主食' },
  { category_id: 'cat_protein', category_name: '蛋白' },
  { category_id: 'cat_custom', category_name: '自定义' },
]

const mockFoodPresets = [
  {
    food_id: 'food_milk_001',
    category_id: 'cat_drink',
    food_name: '牛奶',
    calories_per_gram: 0.54,
    is_liquid: true,
    ml_to_g_ratio: 1.03,
    image_object_key: 'food/food_milk_001.jpg',
    image_url: '',
  },
  {
    food_id: 'food_rice_001',
    category_id: 'cat_grain',
    food_name: '糙米饭',
    calories_per_gram: 1.16,
    is_liquid: false,
    ml_to_g_ratio: 1,
    image_object_key: 'food/food_rice_001.jpg',
    image_url: '',
  },
]

const mockMedicationPresets = [
  { drug_id: 'drug_metformin_001', drug_name: '二甲双胍片', image_object_key: 'medical/drug_metformin_001.jpg', image_url: '', is_user_custom: false },
  { drug_id: 'drug_glimepiride_001', drug_name: '格列美脲片', image_object_key: 'medical/drug_glimepiride_001.jpg', image_url: '', is_user_custom: false },
]

const mockExercisePresets = [
  { exercise_id: 'ex_walk_001', exercise_name: '快走', calories_per_minute: 4.5 },
  { exercise_id: 'ex_jog_001', exercise_name: '慢跑', calories_per_minute: 7.0 },
]

/** POST /checkin/upload-image — 存入 food|medical/{userId}/upload_{时间}_{随机}.jpg */
export function uploadCheckinImage(type, file) {
  const form = new FormData()
  form.append('file', file)
  return post(`/checkin/upload-image?type=${type}`, form, {
    mockFn: async () => ({
      imageId: 'upload_20260625120000_mock0001',
      objectKey: `${type}/usr_mock/upload_20260625120000_mock0001.jpg`,
      imageUrl: '',
    }),
  }).then(toSnakeCase)
}

/** GET /checkin/food/categories */
export function getFoodCategories() {
  return get('/checkin/food/categories', {
    mockFn: async () => mockFoodCategories,
  }).then((data) => (Array.isArray(data) ? data.map(toSnakeCase) : []))
}

/** GET /checkin/food/presets */
export function getFoodPresets(categoryId) {
  return get('/checkin/food/presets', {
    params: categoryId ? { categoryId } : undefined,
    mockFn: async () => (categoryId === 'cat_custom'
      ? [{
          food_id: 'uf_mock_001',
          category_id: 'cat_custom',
          original_category_id: 'cat_grain',
          food_name: '我的全麦面包',
          calories_per_gram: 2.65,
          is_liquid: false,
          ml_to_g_ratio: 1,
          image_object_key: 'food/usr_mock/upload_mock.jpg',
          image_url: '',
          is_user_custom: true,
        }]
      : categoryId
        ? mockFoodPresets.filter((f) => f.category_id === categoryId)
        : mockFoodPresets),
  }).then((data) => (Array.isArray(data) ? data.map(toSnakeCase) : []))
}

/** POST /checkin/food */
export function createFoodCheckin(payload) {
  return post('/checkin/food', toCamelCase(payload), {
    mockFn: async () => ({ checkinId: 'chk_food_mock', checkinDate: payload.checkin_date, totalCalories: 120 }),
  }).then(toSnakeCase)
}

/** GET /checkin/food/records */
export function getFoodRecords(date) {
  return get('/checkin/food/records', {
    params: { date },
    mockFn: async () => [],
  }).then((data) => (Array.isArray(data) ? data.map(toSnakeCase) : []))
}

/** POST /checkin/food/recognize — AI 识别自定义食物图片 */
export function recognizeFoodImage(payload) {
  return post('/checkin/food/recognize', toCamelCase(payload), {
    timeout: 120000,
    mockFn: async () => ({
      foodName: '糙米饭',
      categoryId: 'cat_grain',
      categoryName: '主食',
      caloriesPerGram: 1.16,
      isLiquid: false,
      mlToGRatio: 1,
      suggestedInputUnit: 1,
      suggestedInputAmount: 150,
      suggestedGrams: 150,
      suggestedTotalCalories: 174,
      matchedFoodId: 'food_rice_001',
      sourceType: 1,
      confidence: 'high',
      giLevel: 'medium',
      nutritionTip: '糙米饭升糖较白米饭慢，建议控制在一碗以内并搭配蔬菜。',
      recognitionSummary: '识别为一碗糙米饭，估算约 150g。',
      items: [],
      hasError: false,
      errorMessage: '',
    }),
  }).then(toSnakeCase)
}

/** GET /checkin/food/dify-workflow-spec */
export function getFoodRecognitionWorkflowSpec() {
  return get('/checkin/food/dify-workflow-spec', {
    mockFn: async () => ({ outputKey: 'food_recognition' }),
  }).then(toSnakeCase)
}

/** GET /checkin/medication/presets */
export function getMedicationPresets() {
  return get('/checkin/medication/presets', {
    mockFn: async () => mockMedicationPresets,
  }).then((data) => (Array.isArray(data) ? data.map(toSnakeCase) : []))
}

/** POST /checkin/medication */
export function createMedicationCheckin(payload) {
  return post('/checkin/medication', toCamelCase(payload), {
    mockFn: async () => ({ checkinId: 'chk_med_mock', checkinDate: payload.checkin_date }),
  }).then(toSnakeCase)
}

/** GET /checkin/medication/records */
export function getMedicationRecords(date) {
  return get('/checkin/medication/records', {
    params: { date },
    mockFn: async () => [],
  }).then((data) => (Array.isArray(data) ? data.map(toSnakeCase) : []))
}

/** GET /checkin/exercise/presets */
export function getExercisePresets() {
  return get('/checkin/exercise/presets', {
    mockFn: async () => mockExercisePresets,
  }).then((data) => (Array.isArray(data) ? data.map(toSnakeCase) : []))
}

/** POST /checkin/exercise */
export function createExerciseCheckin(payload) {
  return post('/checkin/exercise', toCamelCase(payload), {
    mockFn: async () => ({ checkinId: 'chk_ex_mock', checkinDate: payload.checkin_date, caloriesBurned: 135 }),
  }).then(toSnakeCase)
}

/** GET /checkin/exercise/records */
export function getExerciseRecords(date) {
  return get('/checkin/exercise/records', {
    params: { date },
    mockFn: async () => [],
  }).then((data) => (Array.isArray(data) ? data.map(toSnakeCase) : []))
}

/** POST /checkin/glucose */
export function createGlucoseCheckin(payload) {
  const body = {
    checkinDate: payload.checkin_date ?? payload.checkinDate,
    glucoseValue: Number(payload.glucose_value ?? payload.glucoseValue),
    measureContext: payload.measure_context ?? payload.measureContext ?? 4,
    unit: payload.unit ?? 1,
  }
  return post('/checkin/glucose', body, {
    mockFn: async () => ({ checkinId: 'chk_glu_mock', pointsEarned: 15, streakDays: 5 }),
  }).then(toSnakeCase)
}

/** GET /checkin/glucose/history — 趋势与历史（startDate/endDate 或 days） */
export function getGlucoseHistory(params = {}) {
  const query = {}
  if (params.start_date) query.startDate = params.start_date
  if (params.end_date) query.endDate = params.end_date
  if (params.days) query.days = params.days
  return get('/checkin/glucose/history', {
    params: query,
    mockFn: async () => ({
      start_date: dayjs().subtract(13, 'day').format('YYYY-MM-DD'),
      end_date: dayjs().format('YYYY-MM-DD'),
      records: [
        { checkin_id: 'g1', glucose_value: 5.6, measure_context_label: '空腹', checkin_date: dayjs().format('YYYY-MM-DD'), record_time: dayjs().hour(7).minute(30).format(), status: 'normal' },
        { checkin_id: 'g2', glucose_value: 7.8, measure_context_label: '餐后2h', checkin_date: dayjs().format('YYYY-MM-DD'), record_time: dayjs().hour(10).minute(0).format(), status: 'high' },
      ],
      summary: { count: 2, avg: 6.7, max: 7.8, min: 5.6 },
    }),
  }).then(toSnakeCase)
}

/** GET /checkin/glucose/records */
export function getGlucoseRecords(date) {
  return get('/checkin/glucose/records', {
    params: { date },
    mockFn: async () => [],
  }).then((data) => (Array.isArray(data) ? data.map(toSnakeCase) : []))
}

/** GET /checkin/today — 英雄区今日状态 */
export function getTodayStatus() {
  return get('/checkin/today', {
    mockFn: async () => ({
      todayCheckins: [
        { checkinType: 'diet', completed: true },
        { checkinType: 'exercise', completed: false },
        { checkinType: 'medication', completed: false },
        { checkinType: 'glucose', completed: false },
      ],
      todayPoints: 10,
      streakDays: 5,
      totalPoints: 520,
    }),
  }).then(toSnakeCase)
}

/** GET /checkin/stats */
export function getCheckinStats(params = {}) {
  return get('/checkin/stats', {
    params: { range: params.range || 'monthly' },
    mockFn: async () => ({
      totalCheckins: 45,
      completionRate: 0.85,
      streakDays: 12,
      totalPoints: 520,
    }),
  }).then(toSnakeCase)
}

function normalizeApiAchievements(data) {
  const list = data?.achievements || data || []
  return list.map((a, i) => ({
    id: a.id || `ach_${i}`,
    name: a.name,
    desc: a.desc,
    unlocked: !!a.unlocked,
    unlocked_at: a.unlocked_at || a.unlockedAt,
    badge_url: a.badge_url || a.badgeUrl || '',
  }))
}

/** GET /checkin/achievements */
export function getAchievements() {
  return get('/checkin/achievements', {
    mockFn: async () => ({ achievements: mockAchievements }),
  }).then((data) => normalizeApiAchievements(data))
}

/** 成就墙：合并目录、接口解锁与打卡统计 */
export async function getAchievementWall() {
  const [achRes, stats] = await Promise.all([
    get('/checkin/achievements', {
      mockFn: async () => ({ achievements: mockAchievements }),
    }),
    getCheckinStats(),
  ])

  const apiList = normalizeApiAchievements(achRes)
  const mockOverrides = USE_MOCK
    ? {
        unlockDates: Object.fromEntries(
          mockAchievements
            .filter((a) => a.unlocked && a.unlocked_at)
            .map((a) => [a.id, a.unlocked_at]),
        ),
      }
    : {}

  const mergedStats = USE_MOCK
    ? { ...stats, ...mockAchievementStats }
    : stats

  return buildAchievementWall(apiList, mergedStats, mockOverrides)
}
