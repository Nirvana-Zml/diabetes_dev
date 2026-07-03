import { describe, expect, it, vi } from 'vitest'
import dayjs from 'dayjs'
import {
  buildDateDisplay,
  buildRecordDateTime,
  calcExCalories,
  calcFoodCalories,
  calcGrams,
  calcPresetFoodCalories,
  currentRecordTime,
  formatRecordTime,
  formatTime,
  inferMealPeriod,
  onImgError,
} from '../utils'

describe('checkin utils', () => {
  it('calculates grams and calories', () => {
    expect(calcGrams(200, 2, 1.1)).toBeCloseTo(220)
    expect(calcGrams(80, 1, 1)).toBe(80)
    expect(calcGrams(100, 2)).toBe(100)
    expect(calcFoodCalories({ input_amount: 100, input_unit: 1, calories_per_gram: 1.5 })).toBe(150)
    expect(calcPresetFoodCalories(null, 100, 1)).toBe(0)
    expect(calcPresetFoodCalories({ calories_per_gram: 0.5, ml_to_g_ratio: 1 }, 200, 2)).toBe(100)
    expect(calcExCalories(6, 20)).toBe(120)
  })

  it('formats time and record metadata', () => {
    expect(formatTime()).toBe('')
    expect(formatTime('2026-06-30T08:30:00')).toBe('08:30')
    expect(formatRecordTime('invalid')).toBe('')
    expect(formatRecordTime('')).toBe('')
    expect(formatRecordTime('2026-06-30T09:15:00')).toBe('09:15')
    expect(buildRecordDateTime('2026-06-30', '08:00')).toBe('2026-06-30T08:00:00')
    expect(buildRecordDateTime('', '08:00')).toBeUndefined()
    expect(currentRecordTime()).toMatch(/^\d{2}:\d{2}$/)
  })

  it('infers meal period by hour', () => {
    expect(inferMealPeriod(dayjs('2026-06-30T07:00:00'))).toBe(1)
    expect(inferMealPeriod(dayjs('2026-06-30T11:00:00'))).toBe(2)
    expect(inferMealPeriod(dayjs('2026-06-30T15:00:00'))).toBe(4)
    expect(inferMealPeriod(dayjs('2026-06-30T18:30:00'))).toBe(5)
    expect(inferMealPeriod(dayjs('2026-06-30T21:00:00'))).toBe(3)
    expect(inferMealPeriod(dayjs('2026-06-30T23:00:00'))).toBe(6)
  })

  it('builds date display and hides broken images', () => {
    const today = buildDateDisplay(dayjs().format('YYYY-MM-DD'))
    expect(today.main).toBe('今天')
    expect(today.weekday).toMatch(/周/)

    const other = buildDateDisplay('2026-01-02')
    expect(other.main).toBe('1月2日')
    expect(other.full).toBe('2026年1月2日')

    const target = { style: { display: 'block' } }
    onImgError({ target })
    expect(target.style.display).toBe('none')
  })
})
