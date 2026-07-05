import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  delay: vi.fn(async () => {}),
}))

vi.mock('@/utils/request', () => ({
  get: mocks.get,
  post: mocks.post,
}))

vi.mock('@/utils/delay', () => ({
  delay: mocks.delay,
}))

function invokeMock(_url, options = {}) {
  return options.mockFn ? options.mockFn() : {}
}

function invokeWrite(_url, _data, options = {}) {
  return options.mockFn ? options.mockFn() : {}
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.get.mockImplementation(invokeMock)
  mocks.post.mockImplementation(invokeWrite)
})

describe('api coverage gaps', () => {
  it('covers voiceToText mock branch', async () => {
    const { getArticleAudio } = await import('../article')
    await expect(getArticleAudio('a-audio')).resolves.toEqual({
      article_id: 'a-audio',
      audio_url: '',
      source: 'mock',
    })
    expect(mocks.get).toHaveBeenCalledWith('/articles/a-audio/audio', expect.objectContaining({
      timeout: 300000,
    }))
  })

  it('covers checkin food preset custom category and recognition apis', async () => {
    const checkin = await import('../checkin')

    await expect(checkin.getFoodPresets('cat_custom')).resolves.toEqual([
      expect.objectContaining({
        food_id: 'uf_mock_001',
        category_id: 'cat_custom',
        is_user_custom: true,
      }),
    ])

    await expect(checkin.recognizeFoodImage({
      image_object_key: 'food/img.jpg',
      meal_period: 1,
    })).resolves.toMatchObject({
      food_name: '糙米饭',
      category_id: 'cat_grain',
      has_error: false,
    })

    await expect(checkin.getFoodRecognitionWorkflowSpec()).resolves.toEqual({
      output_key: 'food_recognition',
    })
  })
})
