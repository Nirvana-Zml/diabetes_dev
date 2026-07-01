import { describe, expect, it, vi } from 'vitest'
import {
  articleCategoryToInt,
  buildDateRange,
  mapRiskRequest,
  normalizeAchievements,
  normalizeArticle,
  normalizeCheckinStats,
  normalizeCheckinToday,
  normalizePlan,
  normalizeRiskResult,
  toCamelCase,
  toSnakeCase,
} from '../normalize'

describe('normalize utils', () => {
  it('converts nested keys between camelCase and snake_case', () => {
    expect(toSnakeCase({ userName: 'tom', records: [{ glucoseValue: 6.2 }] })).toEqual({
      user_name: 'tom',
      records: [{ glucose_value: 6.2 }],
    })
    expect(toCamelCase({ user_name: 'tom', records: [{ glucose_value: 6.2 }] })).toEqual({
      userName: 'tom',
      records: [{ glucoseValue: 6.2 }],
    })
    expect(toSnakeCase(null)).toBeNull()
    expect(toCamelCase('raw')).toBe('raw')
  })

  it('normalizes article category formats', () => {
    expect(articleCategoryToInt()).toBeUndefined()
    expect(articleCategoryToInt(3)).toBe(3)
    expect(articleCategoryToInt('diet')).toBe(2)
    expect(articleCategoryToInt('unknown')).toBe(1)
    expect(normalizeArticle({ articleId: 'a1', category: 2 })).toMatchObject({
      article_id: 'a1',
      category: 'diet',
    })
    expect(normalizeArticle(null)).toBeNull()
  })

  it('normalizes risk factors and summaries', () => {
    expect(normalizeRiskResult({
      reportSummary: 'summary',
      factors: [
        { name: 'bmi', weight: 25 },
        { name: 'sleep', weight: 15 },
        { name: 'diet', weight: 5 },
      ],
    })).toMatchObject({
      report_summary: 'summary',
      factors: [
        { name: 'bmi', level: '高危', description: '', weight: 25 },
        { name: 'sleep', level: '中危', description: '', weight: 15 },
        { name: 'diet', level: '低危', description: '', weight: 5 },
      ],
    })
    expect(normalizeRiskResult({ factors: [{ name: 'custom', level: '已给定', description: 'desc', weight: 1 }] })).toEqual({
      factors: [{ name: 'custom', level: '已给定', description: 'desc', weight: 1 }],
    })
    expect(normalizeRiskResult({ reportSummary: 'legacy' })).toEqual({
      report_summary: 'legacy',
    })
  })

  it('normalizes plan legacy meal, exercise and rest shapes', () => {
    const plan = normalizePlan({
      dietPlan: {
        breakfast: { time: '08:00', foods: ['燕麦'], calories: 100, gi: 'low' },
      },
      exercisePlan: ['快走'],
      restPlan: { glucoseMonitor: ['fasting'] },
      medicationNote: '按医嘱',
    })

    expect(plan.diet_plan.meal_plan.breakfast.foods[0]).toEqual({
      name: '燕麦',
      amount: '',
      calories: 100,
      gi_level: 'low',
    })
    expect(plan.exercise_plan).toEqual({
      weekly_goal: '每周规律运动',
      items: ['快走'],
    })
    expect(plan.rest_plan.glucose_monitor_times).toEqual(['fasting'])
    expect(plan.medication_note).toBe('按医嘱')
    expect(normalizePlan(null)).toBeNull()
  })

  it('normalizes alternate plan shapes', () => {
    expect(normalizePlan({
      dietPlan: { mealPlan: { lunch: { foods: [] } } },
      exercisePlan: { items: [{ exerciseName: '快走' }] },
      restPlan: null,
    })).toMatchObject({
      diet_plan: { meal_plan: { lunch: { foods: [] } } },
      exercise_plan: { items: [{ exercise_name: '快走' }] },
      rest_plan: {},
    })

    expect(normalizePlan({
      dietPlan: { lunch: { foods: [{ foodName: '米饭' }], totalCalories: 200 } },
      exercisePlan: { suggestions: ['快走'], weeklyGoal: '每周三次' },
      restPlan: {},
    }).exercise_plan).toEqual({
      weekly_goal: '每周三次',
      items: [{ type: '快走', duration: '-', frequency: '-', intensity: '中等' }],
    })

    expect(normalizePlan({ dietPlan: 'invalid', exercisePlan: { weeklyGoal: '目标' } })).toMatchObject({
      diet_plan: {},
      exercise_plan: { weekly_goal: '目标', items: [] },
    })

    expect(normalizePlan({
      dietPlan: { nutritionNote: '少油' },
      exercisePlan: null,
      restPlan: 'invalid',
      medicationNote: '饭后',
    })).toMatchObject({
      diet_plan: { nutrition_note: '少油' },
      exercise_plan: { weekly_goal: '', items: [] },
      rest_plan: {},
      medication_note: '饭后',
    })

    const mealDefaults = normalizePlan({
      dietPlan: { dinner: { time: '18:00' } },
      exercisePlan: { weeklyGoal: '' },
    })
    expect(mealDefaults.diet_plan.meal_plan.dinner).toEqual({
      time: '18:00',
      foods: [],
      total_calories: 0,
    })

    expect(normalizePlan({ exercisePlan: null }).diet_plan).toEqual({})

    const foodDefaults = normalizePlan({
      dietPlan: { snack: { foods: ['坚果'] } },
      exercisePlan: { suggestions: ['拉伸'] },
    })
    expect(foodDefaults.diet_plan.meal_plan.snack.foods[0]).toEqual({
      name: '坚果',
      amount: '',
      calories: 0,
      gi_level: 'low',
    })
    expect(foodDefaults.exercise_plan.weekly_goal).toBe('')
  })

  it('normalizes achievements and date ranges', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-06-30T12:00:00.000Z'))

    expect(normalizeAchievements({ achievements: [{ name: '连续打卡', unlocked: true, badgeUrl: 'a.png' }] })).toEqual([
      { id: 'ach_0', name: '连续打卡', desc: '已解锁', unlocked: true, badge_url: 'a.png' },
    ])
    expect(normalizeAchievements([{ name: '未完成', unlocked: false, badge_url: 'b.png' }])).toEqual([
      { id: 'ach_0', name: '未完成', desc: '继续努力', unlocked: false, badge_url: 'b.png' },
    ])
    expect(normalizeAchievements(null)).toEqual([])
    expect(normalizeAchievements([{ name: '无徽章', unlocked: false }])).toEqual([
      { id: 'ach_0', name: '无徽章', desc: '继续努力', unlocked: false, badge_url: '' },
    ])
    expect(normalizeCheckinToday({ todayPoints: 10 })).toEqual({ today_points: 10 })
    expect(normalizeCheckinStats({ totalCheckins: 3 })).toEqual({ total_checkins: 3 })
    expect(buildDateRange({ period: 'custom', date_range: ['2026-06-01', '2026-06-10'] })).toEqual({
      startDate: '2026-06-01',
      endDate: '2026-06-10',
    })
    expect(buildDateRange({ period: 'weekly' })).toEqual({
      startDate: '2026-06-24',
      endDate: '2026-06-30',
    })
    expect(buildDateRange()).toEqual({
      startDate: '2026-06-01',
      endDate: '2026-06-30',
    })

    vi.useRealTimers()
  })

  it('maps risk request form values to backend payload', () => {
    const payload = mapRiskRequest({
      height: '170',
      weight: '68',
      fasting_glucose: '6.1',
      postprandial_glucose: '8.2',
      systolic_bp: '121.6',
      diastolic_bp: '79.2',
      diabetes_type: 'type2',
      diagnosed_date: '',
      is_pregnant: 0,
      family_history: 1,
      is_insulin_taken: true,
      smoking: 'former',
      alcohol: 'regular',
      exercise_freq: 'high',
      medical_histories: [{ disease_code: 'HBP', disease_name: '高血压', status: 2 }],
      medications: [{ drug_name: '二甲双胍', is_insulin: false }],
      family_histories: [{ relation: 'father', disease_name: '糖尿病', is_alive: false, is_diabetes: true }],
    })

    expect(payload).toMatchObject({
      height: 170,
      weight: 68,
      fastingGlucose: 6.1,
      postprandialGlucose: 8.2,
      systolicBp: 122,
      diastolicBp: 79,
      diabetesType: 3,
      familyHistory: true,
      smoking: 1,
      alcohol: 2,
      exerciseFreq: 3,
      dietType: 'balanced',
      testSource: 1,
    })
    expect(payload.medicalHistories).toHaveLength(1)
    expect(payload.medications[0].drugName).toBe('二甲双胍')
    expect(payload.familyHistories[0].isAlive).toBe(false)
  })

  it('maps numeric and optional risk request values', () => {
    const payload = mapRiskRequest({
      height: 170,
      weight: 70,
      fasting_glucose: 5.5,
      random_glucose: 6.8,
      hba1c: 6.1,
      systolic_bp: 120,
      diastolic_bp: 80,
      diabetes_type: 2,
      diagnosed_date: '2026-01-01',
      is_pregnant: true,
      family_history: false,
      is_insulin_taken: false,
      smoking: 2,
      alcohol: 1,
      exercise_freq: 0,
      diet_type: 'low_carb',
      test_source: 3,
      medical_histories: [{ disease_name: '   ' }, { disease_name: '脂肪肝', diagnosed_date: '2020-01-01' }],
      medications: [{ drug_name: '' }, { drug_name: '胰岛素', status: 0 }],
      family_histories: [{ disease_name: '' }, { disease_name: '高血压', member_age: 60, diagnosed_age: 50 }],
    })

    expect(payload).toMatchObject({
      randomGlucose: 6.8,
      hba1c: 6.1,
      diabetesType: 2,
      diagnosedDate: '2026-01-01',
      isPregnant: true,
      familyHistory: false,
      smoking: 2,
      alcohol: 1,
      exerciseFreq: 0,
      dietType: 'low_carb',
      testSource: 3,
    })
    expect(payload.medicalHistories).toHaveLength(1)
    expect(payload.medications).toEqual([expect.objectContaining({ drugName: '胰岛素', status: 0 })])
    expect(payload.familyHistories).toEqual([expect.objectContaining({ memberAge: 60, diagnosedAge: 50, isAlive: true })])
  })

  it('maps risk request fallback values for unknown options and empty lists', () => {
    const payload = mapRiskRequest({
      height: 160,
      weight: 55,
      fasting_glucose: 5.2,
      systolic_bp: 118,
      diastolic_bp: 76,
      diabetes_type: 'other',
      smoking: 'other',
      alcohol: 'other',
      exercise_freq: 'other',
    })

    expect(payload).toMatchObject({
      postprandialGlucose: undefined,
      randomGlucose: undefined,
      hba1c: undefined,
      diagnosedDate: undefined,
      diabetesType: 9,
      smoking: 0,
      alcohol: 0,
      exerciseFreq: 1,
      medicalHistories: [],
      medications: [],
      familyHistories: [],
    })
  })
})
