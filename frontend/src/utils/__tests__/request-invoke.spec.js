import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  clients: [],
  clearTokens: vi.fn(),
  routerPush: vi.fn(),
}))

vi.mock('axios', () => ({
  default: {
    create: vi.fn((options) => {
      const client = {
        options,
        requestHandler: null,
        responseSuccess: null,
        responseError: null,
        nextResponse: { data: undefined },
        nextError: null,
        interceptors: {
          request: { use(fn) { client.requestHandler = fn } },
          response: {
            use(success, error) {
              client.responseSuccess = success
              client.responseError = error
            },
          },
        },
        request: vi.fn(async (config) => {
          const finalConfig = client.requestHandler({ headers: {}, ...config })
          if (client.nextError) throw await client.responseError(client.nextError)
          return client.responseSuccess(client.nextResponse, finalConfig)
        }),
      }
      mocks.clients.push(client)
      return client
    }),
  },
}))

vi.mock('@/config', () => ({
  API_BASE: '/api/v1',
  API_V2_BASE: '/api/v2',
  USE_MOCK: false,
}))

vi.mock('@/api/auth', () => ({ clearTokens: mocks.clearTokens }))
vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    createRouter: actual.createRouter,
    createWebHistory: actual.createWebHistory,
  }
})
vi.mock('@/router', () => ({
  default: {
    currentRoute: { value: { fullPath: '/private', path: '/private', meta: {} } },
    push: mocks.routerPush,
  },
}))
vi.mock('@/utils/delay', () => ({ delay: vi.fn(async () => {}) }))
vi.mock('@/utils/auth', () => ({ isPublicRoute: vi.fn(() => false) }))

beforeEach(async () => {
  vi.resetModules()
  mocks.clients.length = 0
})

describe('request invoke live branch', () => {
  it('returns wrapped body when response data is undefined', async () => {
    const request = await import('../request')
    const client = mocks.clients[0]
    client.nextResponse = { data: undefined }
    await expect(request.get('/empty-body')).resolves.toEqual({ code: 200, data: undefined })
  })

  it('unwraps plain response objects through invoke', async () => {
    vi.resetModules()
    mocks.clients.length = 0
    const request = await import('../request')
    const client = mocks.clients[0]
    client.nextResponse = { data: { plain: true } }
    await expect(request.get('/plain')).resolves.toEqual({ plain: true })
  })
})
