import { describe, expect, it } from 'vitest'

describe('mock data exports', () => {
  it('loads shared mock datasets used by frontend modules', async () => {
    const data = await import('../data')

    expect(data.mockUserProfile).toMatchObject({ user_id: expect.any(String) })
    expect(data.mockBanners.length).toBeGreaterThan(0)
    expect(data.mockCategories.length).toBeGreaterThan(0)
    expect(data.mockVideos.length).toBeGreaterThan(0)
    expect(data.mockDoctors.length).toBeGreaterThan(0)
    expect(data.mockArticles.length).toBeGreaterThan(0)
    expect(data.mockAchievements.length).toBeGreaterThan(0)
    expect(data.mockHealthPlan).toMatchObject({ plan_id: expect.any(String) })
    expect(data.mockHealthAlert).toEqual(expect.any(Object))
    expect(data.mockHealthTrendSummary).toEqual(expect.any(String))
    expect(data.mockQaAnswer).toEqual(expect.any(String))
  })
})
