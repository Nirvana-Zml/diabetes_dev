/** 将 camelCase 键转为 snake_case（浅层对象/数组） */
export function toSnakeCase(data) {
  if (data == null || typeof data !== 'object') return data
  if (Array.isArray(data)) return data.map(toSnakeCase)
  return Object.fromEntries(
    Object.entries(data).map(([k, v]) => [k.replace(/([A-Z])/g, '_$1').toLowerCase(), toSnakeCase(v)]),
  )
}

/** 将 snake_case 键转为 camelCase（浅层，用于请求体） */
export function toCamelCase(data) {
  if (data == null || typeof data !== 'object') return data
  if (Array.isArray(data)) return data.map(toCamelCase)
  return Object.fromEntries(
    Object.entries(data).map(([k, v]) => [
      k.replace(/_([a-z])/g, (_, c) => c.toUpperCase()),
      toCamelCase(v),
    ]),
  )
}

const ARTICLE_CATEGORY_TO_INT = {
  diabetes_basics: 1,
  diet: 2,
  exercise: 3,
  medication: 4,
  complications: 5,
}

const ARTICLE_CATEGORY_TO_STR = Object.fromEntries(
  Object.entries(ARTICLE_CATEGORY_TO_INT).map(([k, v]) => [String(v), k]),
)

export function articleCategoryToInt(category) {
  if (!category) return undefined
  if (typeof category === 'number') return category
  return ARTICLE_CATEGORY_TO_INT[category] ?? 1
}

export function normalizeArticle(item) {
  if (!item) return item
  const a = toSnakeCase(item)
  if (typeof a.category === 'number') {
    a.category = ARTICLE_CATEGORY_TO_STR[a.category] || String(a.category)
  }
  return a
}

export function normalizeRiskResult(data) {
  const r = toSnakeCase(data)
  if (r.factors) {
    r.factors = r.factors.map((f) => ({
      name: f.name,
      level: f.level || (Number(f.weight) >= 25 ? '高危' : Number(f.weight) >= 15 ? '中危' : '低危'),
      description: f.description || '',
      weight: f.weight,
    }))
  }
  return r
}

export function normalizePlan(data) {
  if (!data) return null
  const p = toSnakeCase(data)
  p.diet_plan = normalizeDietPlan(p.diet_plan || {})
  p.exercise_plan = normalizeExercisePlan(p.exercise_plan)
  p.rest_plan = normalizeRestPlan(p.rest_plan || {})
  return p
}

function normalizeDietPlan(diet) {
  if (!diet || typeof diet !== 'object') return {}
  if (diet.meal_plan) return diet
  const meals = ['breakfast', 'lunch', 'dinner', 'snack']
  const hasLegacyMeals = meals.some((k) => diet[k])
  if (hasLegacyMeals) {
    const mealPlan = {}
    for (const key of meals) {
      if (!diet[key]) continue
      const meal = diet[key]
      mealPlan[key] = {
        time: meal.time || '',
        foods: (meal.foods || []).map((f) =>
          typeof f === 'string'
            ? { name: f, amount: '', calories: meal.calories || 0, gi_level: meal.gi || 'low' }
            : toSnakeCase(f),
        ),
        total_calories: meal.total_calories ?? meal.calories ?? 0,
      }
    }
    return { ...diet, meal_plan: mealPlan }
  }
  return diet
}

function normalizeExercisePlan(exercise) {
  if (!exercise) return { weekly_goal: '', items: [] }
  if (Array.isArray(exercise)) {
    return { weekly_goal: '每周规律运动', items: exercise.map((x) => toSnakeCase(x)) }
  }
  const e = toSnakeCase(exercise)
  if (Array.isArray(e.items)) return e
  if (Array.isArray(e.suggestions)) {
    return {
      weekly_goal: e.weekly_goal || e.weeklyGoal || '',
      items: e.suggestions.map((s) => ({ type: s, duration: '-', frequency: '-', intensity: '中等' })),
    }
  }
  return { weekly_goal: e.weekly_goal || '', items: [] }
}

function normalizeRestPlan(rest) {
  if (!rest || typeof rest !== 'object') return {}
  const r = toSnakeCase(rest)
  if (r.glucose_monitor && !r.glucose_monitor_times) {
    r.glucose_monitor_times = r.glucose_monitor
  }
  return r
}

export function normalizeCheckinToday(data) {
  return toSnakeCase(data)
}

export function normalizeCheckinStats(data) {
  return toSnakeCase(data)
}

export function normalizeAchievements(data) {
  const list = data?.achievements || data || []
  return list.map((a, i) => ({
    id: `ach_${i}`,
    name: a.name,
    desc: a.unlocked ? '已解锁' : '继续努力',
    unlocked: !!a.unlocked,
    badge_url: a.badge_url || a.badgeUrl || '',
  }))
}

export function buildDateRange({ period, date_range: dateRange } = {}) {
  const today = new Date()
  const fmt = (d) => d.toISOString().slice(0, 10)
  if (period === 'custom' && dateRange?.length === 2) {
    return { startDate: dateRange[0], endDate: dateRange[1] }
  }
  const start = new Date(today)
  start.setDate(start.getDate() - (period === 'weekly' ? 6 : 29))
  return { startDate: fmt(start), endDate: fmt(today) }
}

export function mapRiskRequest(form) {
  const smokingMap = { never: 0, former: 1, current: 2 }
  const alcoholMap = { never: 0, occasional: 1, regular: 2 }
  const exerciseMap = { none: 0, low: 1, medium: 2, high: 3 }
  const diabetesTypeMap = { none: 0, prediabetes: 1, type1: 2, type2: 3, gestational: 4, unknown: 9 }

  const smoking = typeof form.smoking === 'number' ? form.smoking : (smokingMap[form.smoking] ?? 0)
  const alcohol = typeof form.alcohol === 'number' ? form.alcohol : (alcoholMap[form.alcohol] ?? 0)
  const exerciseFreq = typeof form.exercise_freq === 'number' ? form.exercise_freq : (exerciseMap[form.exercise_freq] ?? 1)
  const diabetesType = typeof form.diabetes_type === 'number'
    ? form.diabetes_type
    : (diabetesTypeMap[form.diabetes_type] ?? 9)

  return {
    height: Number(form.height),
    weight: Number(form.weight),
    fastingGlucose: Number(form.fasting_glucose),
    postprandialGlucose: form.postprandial_glucose != null ? Number(form.postprandial_glucose) : undefined,
    randomGlucose: form.random_glucose != null ? Number(form.random_glucose) : undefined,
    hba1c: form.hba1c != null ? Number(form.hba1c) : undefined,
    systolicBp: Math.round(Number(form.systolic_bp)),
    diastolicBp: Math.round(Number(form.diastolic_bp)),
    diabetesType,
    diagnosedDate: form.diagnosed_date || undefined,
    isPregnant: !!form.is_pregnant,
    familyHistory: !!form.family_history,
    isInsulinTaken: !!form.is_insulin_taken,
    smoking,
    alcohol,
    exerciseFreq,
    dietType: form.diet_type || 'balanced',
    testSource: form.test_source ?? 1,
    medicalHistories: (form.medical_histories || [])
      .filter((h) => h.disease_name?.trim())
      .map((h) => ({
        diseaseCode: h.disease_code,
        diseaseName: h.disease_name,
        diagnosedDate: h.diagnosed_date || undefined,
        status: h.status ?? 1,
        note: h.note,
      })),
    medications: (form.medications || [])
      .filter((m) => m.drug_name?.trim())
      .map((m) => ({
        drugName: m.drug_name,
        genericName: m.generic_name,
        dosage: m.dosage,
        frequencyDesc: m.frequency_desc,
        isInsulin: !!m.is_insulin,
        status: m.status ?? 1,
      })),
    familyHistories: (form.family_histories || [])
      .filter((f) => f.disease_name?.trim())
      .map((f) => ({
        relation: f.relation,
        memberAge: f.member_age ?? undefined,
        isAlive: f.is_alive !== false,
        diseaseName: f.disease_name,
        diagnosedAge: f.diagnosed_age ?? undefined,
        isDiabetes: !!f.is_diabetes,
        note: f.note,
      })),
  }
}
