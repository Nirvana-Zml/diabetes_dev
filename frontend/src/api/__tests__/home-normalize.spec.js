import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  toSnakeCase: vi.fn(),
}))

vi.mock('@/config', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, USE_MOCK: false }
})

vi.mock('@/utils/normalize', async (importActual) => {
  const actual = await importActual()
  return {
    ...actual,
    toSnakeCase: mocks.toSnakeCase,
  }
})

vi.mock('@/utils/request', () => ({
  getV2: vi.fn(),
}))

beforeEach(async () => {
  vi.clearAllMocks()
  vi.resetModules()
  const actual = await vi.importActual('@/utils/normalize')
  mocks.toSnakeCase.mockImplementation(actual.toSnakeCase)
})

describe('home normalization branch coverage', () => {
  it('covers doctor_id and avatar fallback chains', async () => {
    mocks.toSnakeCase.mockReturnValueOnce({ doctor_id: 'from-d' })
    const home = await import('../home')
    expect(home.normalizeDoctor({ doctorId: 'ignored' })).toMatchObject({ doctor_id: 'from-d' })

    mocks.toSnakeCase.mockReturnValueOnce({})
    expect(home.normalizeDoctor({ doctorId: 'camel-id' })).toMatchObject({ doctor_id: 'camel-id' })

    mocks.toSnakeCase.mockReturnValueOnce({})
    expect(home.normalizeDoctor({ doctor_id: 'item-id' })).toMatchObject({ doctor_id: 'item-id' })

    mocks.toSnakeCase.mockReturnValueOnce({ avatar: 'avatar-only.png' })
    expect(home.normalizeDoctor({ doctor_id: 'a1' })).toMatchObject({ avatar_url: 'avatar-only.png' })

    mocks.toSnakeCase.mockReturnValueOnce({})
    expect(home.normalizeDoctor({ doctor_id: 'a2' })).toMatchObject({
      avatar_url: expect.any(String),
    })
  })

  it('covers banner id fallback and doctors response shape', async () => {
    const { getV2 } = await import('@/utils/request')
    const home = await import('../home')

    getV2.mockResolvedValueOnce({
      banners: [{ bannerId: 'banner-camel', title: 'T' }],
      videos: [],
    })
    await expect(home.getHomeContent()).resolves.toMatchObject({
      banners: [expect.objectContaining({ id: 'banner-camel' })],
    })

    mocks.toSnakeCase.mockReturnValueOnce({})
    expect(home.normalizeDoctor({ doctor_id: 'item-only-id' })).toMatchObject({
      doctor_id: 'item-only-id',
    })

    getV2.mockResolvedValueOnce({
      banners: [{ bannerId: 'banner-camel-only' }],
      videos: [],
    })
    await expect(home.getHomeContent()).resolves.toMatchObject({
      banners: [expect.objectContaining({ id: 'banner-camel-only' })],
    })

    mocks.toSnakeCase.mockReturnValueOnce({})
    expect(home.normalizeDoctor({})).toMatchObject({ doctor_id: '' })

    getV2.mockResolvedValueOnce({
      list: [{ doctorId: 'list-shape', name: '列表', department: '内分泌科', status: 1 }],
    })
    await expect(home.getDoctors()).resolves.toEqual([
      expect.objectContaining({ doctor_id: 'list-shape' }),
    ])

    expect(home.normalizeBannerForTest({ id: 'banner-id-only' })).toMatchObject({
      id: 'banner-id-only',
    })
  })
})
