import { afterEach, describe, expect, it, vi } from 'vitest'

describe('config', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
    vi.resetModules()
  })

  it('uses defaults when env vars are empty', async () => {
    vi.stubEnv('VITE_USE_MOCK', '')
    vi.stubEnv('VITE_API_BASE', '')
    vi.stubEnv('VITE_API_V2_BASE', '')
    vi.stubEnv('VITE_USER_PORTAL_URL', '')
    vi.stubEnv('VITE_DIFY_ARTICLE_DRAFT_URL', '')
    const config = await import('../index.js')
    expect(config.USE_MOCK).toBe(true)
    expect(config.APP_NAME).toBe('糖尿病智能助手')
    expect(config.API_BASE).toBe('/api/v1')
    expect(config.API_V2_BASE).toBe('/api/v2')
    expect(config.USER_PORTAL_URL).toBe('http://localhost:5173')
    expect(config.DIFY_ARTICLE_DRAFT_URL).toBe('__PLACEHOLDER_DIFY_ARTICLE_DRAFT_WORKFLOW__')
  })

  it('reads custom env values', async () => {
    vi.stubEnv('VITE_USE_MOCK', 'false')
    vi.stubEnv('VITE_API_BASE', '/custom/v1')
    vi.stubEnv('VITE_API_V2_BASE', '/custom/v2')
    vi.stubEnv('VITE_USER_PORTAL_URL', 'http://portal.test')
    vi.stubEnv('VITE_DIFY_ARTICLE_DRAFT_URL', 'http://dify.test/run')
    const config = await import('../index.js')
    expect(config.USE_MOCK).toBe(false)
    expect(config.API_BASE).toBe('/custom/v1')
    expect(config.API_V2_BASE).toBe('/custom/v2')
    expect(config.USER_PORTAL_URL).toBe('http://portal.test')
    expect(config.DIFY_ARTICLE_DRAFT_URL).toBe('http://dify.test/run')
  })
})
