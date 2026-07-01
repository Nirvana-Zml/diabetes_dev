import { describe, expect, it } from 'vitest'
import {
  buildMealEntries,
  buildTodayProgress,
  buildTodayTasksSummary,
  calcExCalories,
  calcFoodCalories,
  calcGrams,
  consultBtnText,
  formatFoodAmount,
  formatViewCount,
  giLabel,
  giTagType,
  glucoseStatusLabel,
  intensityType,
  isFavoriteFlag,
  probText,
  statusText,
  statusType,
  syncFavoriteInHistory,
  truncateTitle,
} from '../pageLogic'

describe('page logic test interfaces', () => {
  it('covers living plan labels, favorites and meal adapters', () => {
    expect(isFavoriteFlag(true)).toBe(true)
    expect(isFavoriteFlag(1)).toBe(true)
    expect(isFavoriteFlag('1')).toBe(true)
    expect(isFavoriteFlag('0')).toBe(false)
    expect(syncFavoriteInHistory([{ plan_id: 'p1', is_favorite: 0 }], 'p1', true)).toEqual([
      { plan_id: 'p1', is_favorite: 1 },
    ])

    expect(giLabel('low')).toBe('低')
    expect(giLabel('medium')).toBe('中')
    expect(giLabel('custom')).toBe('custom')
    expect(giLabel()).toBe('—')
    expect(giTagType('high')).toBe('danger')
    expect(giTagType('missing')).toBe('info')
    expect(intensityType()).toBe('info')
    expect(intensityType('高强度')).toBe('danger')
    expect(intensityType('中等')).toBe('warning')
    expect(intensityType('低')).toBe('success')

    expect(buildMealEntries({
      breakfast: { time: '08:00', foods: [{ name: '燕麦' }], total_calories: 180 },
      supper: { calories: 120 },
    })).toEqual([
      expect.objectContaining({ key: 'breakfast', label: '早餐', totalCalories: 180 }),
      expect.objectContaining({ key: 'supper', label: 'supper', totalCalories: 120 }),
    ])
  })

  it('covers consultation and article display helpers', () => {
    expect(consultBtnText('busy')).toBe('排队中')
    expect(consultBtnText('missing')).toBe('立即咨询')
    expect(statusType('online')).toBe('success')
    expect(statusType('missing')).toBe('info')
    expect(statusText('offline')).toBe('离线')
    expect(statusText()).toBe('离线')
    expect(probText('high')).toBe('较高')
    expect(probText('custom')).toBe('custom')

    expect(formatViewCount(0)).toBe('0')
    expect(formatViewCount(9999)).toBe('9,999')
    expect(formatViewCount(12000)).toBe('1.2万')
    expect(formatViewCount(10000)).toBe('1万')
    expect(truncateTitle()).toBe('')
    expect(truncateTitle('很长的标题内容', 4)).toBe('很长的标...')
    expect(truncateTitle('短标题', 20)).toBe('短标题')
  })

  it('covers checkin calculation and status helpers', () => {
    expect(calcGrams(200, 2, 1.1)).toBeCloseTo(220)
    expect(calcGrams(100, 1, 2)).toBe(100)
    expect(calcFoodCalories({ input_amount: 100, input_unit: 1, calories_per_gram: 1.2 })).toBe(120)
    expect(calcFoodCalories({ input_amount: 200, input_unit: 2, ml_to_g_ratio: 1, calories_per_gram: 0.5 })).toBe(100)
    expect(calcExCalories(5, 30)).toBe(150)
    expect(calcExCalories(null, 30)).toBe(0)
    expect(formatFoodAmount({ input_unit: 2, input_amount: 200, grams: 210 })).toContain('210g')
    expect(formatFoodAmount({ input_unit: 1, input_amount: 80 })).toBe('80g')
    expect(glucoseStatusLabel('elevated')).toBe('偏高')
    expect(glucoseStatusLabel('missing')).toBe('-')

    const tasks = [{ completed: true }, { completed: false }]
    expect(buildTodayProgress(tasks)).toBe(25)
    expect(buildTodayTasksSummary(tasks)).toEqual({ done: 1, total: 4 })
    expect(buildTodayTasksSummary([{}, {}, {}, {}, {}])).toEqual({ done: 0, total: 5 })
  })
})
