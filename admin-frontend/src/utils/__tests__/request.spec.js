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
        nextResponse: { data: { code: 200, data: { ok: true } } },
        nextError: null,
        interceptors: {
          request: {
            use(fn) {
              client.requestHandler = fn
            },
          },
          response: {
            use(success, error) {
              client.responseSuccess = success
              client.responseError = error
            },
          },
        },
        request: vi.fn(async (config) => {
          const finalConfig = client.requestHandler({ headers: {}, ...config })
          if (client.nextError) {
            throw await client.responseError(client.nextError)
          }
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
  USE_MOCK: true,
}))

vi.mock('@/api/auth', () => ({
  clearTokens: mocks.clearTokens,
}))

vi.mock('@/router', () => ({
  default: {
    currentRoute: { value: { fullPath: '/articles', path: '/articles', meta: {} } },
    push: mocks.routerPush,
  },
}))

vi.mock('@/utils/delay', () => ({
  delay: vi.fn(async () => {}),
}))

beforeEach(() => {
  localStorage.clear()
  mocks.clearTokens.mockClear()
  mocks.routerPush.mockClear()
  for (const client of mocks.clients) {
    client.nextResponse = { data: { code: 200, data: { ok: true } } }
    client.nextError = null
    client.request.mockClear()
  }
})

describe('request utility', () => {
  it('creates v1 and v2 axios clients and sends common methods', async () => {
    const request = await import('../request')
    localStorage.setItem('access_token', 'token')

    await expect(request.get('/admin/stats/overview')).resolves.toEqual({ ok: true })
    expect(mocks.clients[0].request).toHaveBeenLastCalledWith(expect.objectContaining({
      method: 'GET',
      url: '/admin/stats/overview',
    }))

    await expect(request.post('/admin/articles', { title: '标题' })).resolves.toEqual({ ok: true })
    await expect(request.put('/admin/articles/a1', { title: '更新' })).resolves.toEqual({ ok: true })
    await expect(request.del('/admin/articles/a1')).resolves.toEqual({ ok: true })
    await expect(request.getV2('/home')).resolves.toEqual({ ok: true })
    await expect(request.postV2('/home', { a: 1 })).resolves.toEqual({ ok: true })
    await expect(request.putV2('/home', { a: 1 })).resolves.toEqual({ ok: true })
    await expect(request.delV2('/home')).resolves.toEqual({ ok: true })
    await expect(request.get('/slow', { timeout: 1234, onUploadProgress: vi.fn() })).resolves.toEqual({ ok: true })
    expect(mocks.clients[0].request).toHaveBeenLastCalledWith(expect.objectContaining({
      timeout: 1234,
    }))

    expect(mocks.clients[0].requestHandler({ headers: {}, url: '/path///' }).url).toBe('/path')
    expect(mocks.clients[0].requestHandler({ headers: {}, url: '///' }).url).toBe('/')
    expect(mocks.clients[0].requestHandler({ headers: {} }).headers.Authorization).toBe('Bearer token')
    expect(mocks.clients[0].requestHandler({ headers: {}, data: new FormData() }).headers['Content-Type']).toBeUndefined()
  })

  it('uses mock functions and maps business errors', async () => {
    const request = await import('../request')

    await expect(request.request('GET', '/mock', {
      mockFn: async () => ({ mocked: true }),
    })).resolves.toEqual({ mocked: true })

    mocks.clients[0].nextResponse = { data: { code: 500, message: '业务失败' } }
    await expect(request.get('/bad')).rejects.toThrow('业务失败')

    mocks.clients[0].nextResponse = { data: { code: 500 } }
    await expect(request.get('/bad-default')).rejects.toThrow('请求失败')

    mocks.clients[0].nextResponse = { data: { raw: true } }
    await expect(request.get('/raw')).resolves.toEqual({ raw: true })
  })

  it('clears tokens and redirects on unauthorized responses', async () => {
    const request = await import('../request')
    mocks.clients[0].nextError = {
      response: {
        status: 401,
        data: { message: '未登录' },
      },
    }

    await expect(request.get('/private')).rejects.toThrow('未登录')
    expect(mocks.clearTokens).toHaveBeenCalled()
    expect(mocks.routerPush).toHaveBeenCalledWith({ path: '/login', query: { redirect: '/articles' } })
  })

  it('skips login redirect when already on login page', async () => {
    const router = (await import('@/router')).default
    router.currentRoute.value.fullPath = '/login'
    const request = await import('../request')
    mocks.clients[0].nextError = {
      response: { status: 401, data: { message: '未登录' } },
    }

    await expect(request.get('/private')).rejects.toThrow('未登录')
    expect(mocks.routerPush).not.toHaveBeenCalled()
    router.currentRoute.value.fullPath = '/articles'
  })

  it('returns wrapped response when nested data is absent', async () => {
    const request = await import('../request')
    mocks.clients[0].nextResponse = { data: undefined }
    await expect(request.get('/empty-body')).resolves.toEqual({ code: 200, data: undefined })
  })

  it('maps generic network errors', async () => {
    const request = await import('../request')
    mocks.clients[0].nextError = { message: 'timeout' }
    await expect(request.get('/timeout')).rejects.toThrow('timeout')
  })

  it('falls back to default network error message', async () => {
    const request = await import('../request')
    mocks.clients[0].nextError = { response: { status: 503, data: {} } }
    await expect(request.get('/offline')).rejects.toThrow('网络错误')
  })
})
