import { describe, expect, it } from 'vitest'

describe('mock data exports', () => {
  it('loads shared mock datasets used by admin modules', async () => {
    const data = await import('../data')

    expect(data.mockArticles.length).toBeGreaterThan(0)
    expect(data.mockVideos.length).toBeGreaterThan(0)
    expect(data.mockArticleContent).toContain('Markdown')
  })
})
