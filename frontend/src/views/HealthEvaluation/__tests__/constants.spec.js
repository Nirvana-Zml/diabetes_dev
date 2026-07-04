import { describe, expect, it } from 'vitest'

describe('health evaluation constants', () => {
  it('exports form options and default structures', async () => {
    const constants = await import('../constants')

    expect(Object.keys(constants).length).toBeGreaterThan(0)
    for (const value of Object.values(constants)) {
      expect(value).toBeDefined()
    }
  })

  it('creates default records and maps helper labels', async () => {
    const constants = await import('../constants')

    expect(constants.emptyFamilyHistory()).toMatchObject({
      relation: '父亲',
      disease_name: '糖尿病',
      is_diabetes: true,
    })
    expect(constants.emptyMedicalHistory()).toMatchObject({
      disease_name: '',
      status: 1,
    })
    expect(constants.emptyMedication()).toMatchObject({
      drug_name: '',
      is_insulin: false,
      status: 1,
    })
    expect(constants.createDefaultForm()).toMatchObject({
      height: 170,
      diabetes_type: 9,
      family_histories: [],
      medical_histories: [],
      medications: [],
    })

    expect(constants.calcAge()).toBeNull()
    expect(constants.calcAge('invalid')).toBeNull()
    expect(constants.calcAge('2000-01-01')).toBeGreaterThan(20)
    const nextMonth = new Date()
    nextMonth.setMonth(nextMonth.getMonth() + 1)
    const nextMonthBirth = `2000-${String(nextMonth.getMonth() + 1).padStart(2, '0')}-01`
    expect(constants.calcAge(nextMonthBirth)).toBeLessThan(constants.calcAge('2000-01-01'))
    const sameMonthFutureDay = new Date()
    sameMonthFutureDay.setDate(sameMonthFutureDay.getDate() + 1)
    const futureDayBirth = `2000-${String(sameMonthFutureDay.getMonth() + 1).padStart(2, '0')}-${String(sameMonthFutureDay.getDate()).padStart(2, '0')}`
    expect(constants.calcAge(futureDayBirth)).toBeLessThan(constants.calcAge('2000-01-01'))

    expect(constants.genderLabel('male')).toBe('男')
    expect(constants.genderLabel('custom')).toBe('custom')
    expect(constants.genderLabel(2)).toBe('女')
    expect(constants.genderLabel(99)).toBe('未知')

    expect(constants.normalizeDietType()).toBe('balanced')
    expect(constants.normalizeDietType('high-sugar')).toBe('high_sugar')
    expect(constants.normalizeDietType('high-fat')).toBe('high_fat')
    expect(constants.normalizeDietType('balanced')).toBe('balanced')
  })
})
