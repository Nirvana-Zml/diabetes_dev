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

vi.mock('@/api/auth', () => ({
  clearTokens: mocks.clearTokens,
}))

vi.mock('@/router', () => ({
  default: {
    currentRoute: { value: { fullPath: '/private' } },
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

    await expect(request.get('/profile')).resolves.toEqual({ ok: true })
    expect(mocks.clients[0].request).toHaveBeenLastCalledWith(expect.objectContaining({
      method: 'GET',
      url: '/profile',
    }))

    await expect(request.post('/profile', { nickname: '张三' })).resolves.toEqual({ ok: true })
    await expect(request.put('/profile', { nickname: '李四' })).resolves.toEqual({ ok: true })
    await expect(request.del('/profile')).resolves.toEqual({ ok: true })
    await expect(request.getV2('/home')).resolves.toEqual({ ok: true })
    await expect(request.postV2('/home', { a: 1 })).resolves.toEqual({ ok: true })
    await expect(request.putV2('/home', { a: 1 })).resolves.toEqual({ ok: true })
    await expect(request.delV2('/home')).resolves.toEqual({ ok: true })
    await expect(request.get('/slow', { timeout: 1234 })).resolves.toEqual({ ok: true })
    expect(mocks.clients[0].request).toHaveBeenLastCalledWith(expect.objectContaining({
      timeout: 1234,
    }))

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
    expect(mocks.routerPush).toHaveBeenCalledWith({ path: '/login', query: { redirect: '/private' } })
  })
})
