import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  toSnakeCase: vi.fn(),
  getV2: vi.fn(),
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
  getV2: mocks.getV2,
  postV2: vi.fn(),
}))

beforeEach(async () => {
  vi.clearAllMocks()
  vi.resetModules()
  const actual = await vi.importActual('@/utils/normalize')
  mocks.toSnakeCase.mockImplementation(actual.toSnakeCase)
  mocks.getV2.mockResolvedValue({})
})

describe('consultation normalization branch coverage', () => {
  it('covers message field fallbacks via snake_case gaps', async () => {
    mocks.toSnakeCase.mockReturnValueOnce({})
    const consultation = await import('../consultation')
    mocks.getV2.mockResolvedValueOnce({
      messages: [{ messageId: 'm-camel', senderType: 'doctor', sentAt: '2026-01-02T00:00:00Z' }],
    })
    await expect(consultation.getConsultMessages('s1')).resolves.toEqual([
      expect.objectContaining({
        message_id: 'm-camel',
        sender_type: 'doctor',
        sent_at: '2026-01-02T00:00:00Z',
      }),
    ])

    mocks.toSnakeCase.mockReturnValueOnce({ message_id: 'm-snake' })
    mocks.getV2.mockResolvedValueOnce({
      messages: [{ messageId: 'ignored', senderType: 'user' }],
    })
    await expect(consultation.getConsultMessages('s2')).resolves.toEqual([
      expect.objectContaining({ message_id: 'm-snake', sender_type: 'user' }),
    ])
  })

  it('normalizes messages when snake_case fields are missing', async () => {
    mocks.toSnakeCase.mockReturnValue({})
    const consultation = await import('../consultation')
    expect(consultation.normalizeConsultMessageForTest({
      messageId: 'm-direct',
      senderType: 'doctor',
    })).toEqual(expect.objectContaining({
      message_id: 'm-direct',
      sender_type: 'doctor',
      content: '',
    }))

    expect(consultation.normalizeConsultMessageForTest({
      messageId: 'm-sender',
    })).toEqual(expect.objectContaining({
      message_id: 'm-sender',
      sender_type: 'user',
    }))
  })

  it('reads consultation list from sessions and list payloads', async () => {
    const consultation = await import('../consultation')

    mocks.getV2.mockResolvedValueOnce({
      messages: [{ messageId: 'm-item-only', senderType: 'user' }],
    })
    mocks.toSnakeCase.mockReturnValueOnce({})
    await expect(consultation.getConsultMessages('s3')).resolves.toEqual([
      expect.objectContaining({ message_id: 'm-item-only', sender_type: 'user' }),
    ])

    mocks.getV2.mockResolvedValueOnce({
      list: [{ sessionId: 'sess-list-only', doctorName: '列表会话' }],
    })
    await expect(consultation.listConsultations()).resolves.toMatchObject({
      list: [expect.objectContaining({ session_id: 'sess-list-only' })],
    })
  })
})
