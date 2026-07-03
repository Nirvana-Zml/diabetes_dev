import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  del: vi.fn(),
  getV2: vi.fn(),
  postV2: vi.fn(),
  fetchBackendSSE: vi.fn(),
  mockSSEStream: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  get: mocks.get,
  post: mocks.post,
  put: mocks.put,
  del: mocks.del,
  getV2: mocks.getV2,
  postV2: mocks.postV2,
}))

vi.mock('@/utils/sse', () => ({
  fetchBackendSSE: mocks.fetchBackendSSE,
  mockSSEStream: mocks.mockSSEStream,
}))

vi.mock('@/utils/delay', () => ({
  delay: vi.fn(async () => {}),
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
  mocks.put.mockImplementation(invokeWrite)
  mocks.del.mockImplementation(invokeMock)
  mocks.getV2.mockImplementation(async () => ({}))
  mocks.postV2.mockImplementation(async () => ({}))
})

afterEach(() => {
  vi.unstubAllEnvs()
})

describe('core business branch coverage', () => {
  it('covers consultation response normalization branches', async () => {
    const api = await import('../consultation')

    mocks.getV2.mockResolvedValueOnce({ departments: [{ id: 'd1' }] })
    await expect(api.getDepartments()).resolves.toEqual([])

    mocks.getV2.mockResolvedValueOnce({
      list: [{
        sessionId: 's-list',
        name: '王医生',
        title: '主任',
        department: '内分泌科',
        status: 'active',
        feedback: '很好',
      }],
      total: 2,
    })
    await expect(api.listConsultations({ page: 2, size: 5 })).resolves.toMatchObject({
      total: 2,
      page: 2,
      page_size: 5,
      list: [expect.objectContaining({
        session_id: 's-list',
        doctor_name: '王医生',
        doctor_title: '主任',
        feedback: '很好',
      })],
    })

    mocks.postV2.mockResolvedValueOnce({ sessionId: 'camel-id' })
    await expect(api.createConsultation({ doctor_id: 'd1' })).resolves.toMatchObject({
      session_id: 'camel-id',
    })

    mocks.getV2.mockResolvedValueOnce({
      messages: [
        null,
        { messageId: 'm-null-content', senderType: 'doctor' },
      ],
    })
    await expect(api.getConsultMessages('s1')).resolves.toEqual([
      null,
      expect.objectContaining({ message_id: 'm-null-content', content: '' }),
    ])

    mocks.postV2.mockResolvedValueOnce({
      userMessage: { messageId: 'u2', senderType: 'user', content: '问题' },
      aiMessage: { messageId: 'a2', senderType: 'doctor', content: '答复' },
    })
    await expect(api.sendConsultMessage('s1', { content: '问题' })).resolves.toMatchObject({
      userMessage: { message_id: 'u2' },
      aiMessage: { message_id: 'a2' },
    })
  })

  it('covers home doctor, banner and video normalization branches', async () => {
    const home = await import('../home')

    expect(home.normalizeDoctor(null)).toBeNull()
    expect(home.normalizeDoctor({
      doctorId: 'd9',
      status: 9,
      avatar: 'avatar.png',
      consultationCount: 12,
    })).toMatchObject({
      doctor_id: 'd9',
      status: '9',
      avatar_url: 'avatar.png',
      consultation_count: 12,
    })

    expect(home.normalizeVideo({
      id: 'v-hour',
      duration: '01:02:03',
      cover_url: 'cover.png',
      video_url: 'video.mp4',
    })).toMatchObject({
      id: 'v-hour',
      duration: '1:02:03',
      cover: 'cover.png',
      url: 'video.mp4',
    })
    expect(home.normalizeVideo({ videoId: 'v-plain', duration: '12:30' })).toMatchObject({
      duration: '12:30',
    })
    expect(home.normalizeVideo({})).toMatchObject({ duration: '' })

    mocks.getV2.mockResolvedValueOnce({
      banners: [{ bannerId: 'b1', title: '轮播', imageUrl: 'banner.png' }],
      videos: [{ videoId: 'v1', title: '科普', duration: '00:03:20' }],
      categories: [{ id: 'diet', name: '饮食' }],
    })
    await expect(home.getHomeContent()).resolves.toMatchObject({
      banners: [expect.objectContaining({ image: 'banner.png' })],
      categories: expect.any(Array),
    })

    mocks.getV2.mockImplementationOnce(async () => {
      const { mockVideos } = await import('@/mock/data')
      return mockVideos
    })
    await expect(home.getVideos({ keyword: '不存在' })).resolves.toMatchObject({ total: 0 })

    mocks.getV2.mockResolvedValueOnce({
      doctors: [
        { doctorId: 'd1', name: '李医生', department: '内分泌科', hospital: '市医院', status: 1 },
        { doctorId: 'd2', name: '王医生', department: '营养科', hospital: '社区医院', status: 2 },
      ],
    })
    await expect(home.getDoctors({ department: '营养科', status: 2, keyword: '社区' })).resolves.toEqual([
      expect.objectContaining({ doctor_id: 'd2', status: 'offline' }),
    ])

    mocks.getV2.mockResolvedValueOnce({
      list: [{ doctorId: 'd3', name: '赵医生', department: '内分泌科', status: 'busy' }],
    })
    await expect(home.getDoctors()).resolves.toEqual([
      expect.objectContaining({ doctor_id: 'd3', status: 'busy' }),
    ])
  })

  it('covers checkin management and reminder normalization branches', async () => {
    const management = await import('../checkinManagement')
    const reminder = await import('../checkinReminder')

    mocks.get.mockResolvedValueOnce({
      aiSummary: '摘要',
      behaviorPatterns: [{ type: 'diet', pattern: '稳定', completionRate: 0.9, description: '好' }],
      anomalies: [{ date: '2026-06-01', type: 'miss', value: 1, description: '缺失', possibleReason: '外出' }],
      improvements: ['改进'],
      source: 'backend',
    })
    await expect(management.getAiSummary()).resolves.toMatchObject({
      ai_summary: '摘要',
      behavior_patterns: [expect.objectContaining({ completion_rate: 0.9, suggestion: '' })],
      anomalies: [expect.objectContaining({ possible_reason: '外出', value: 1 })],
      source: 'backend',
    })

    mocks.post.mockResolvedValueOnce({ task_id: 'task-1' })
    await expect(management.exportReport({ format: 'csv' })).resolves.toMatchObject({
      export_url: 'task-1',
      task_id: 'task-1',
    })

    mocks.put.mockResolvedValueOnce([
      { checkinType: 9, remindTime: '09:00', enabled: false, sortOrder: 2 },
    ])
    await expect(reminder.saveReminderRules([
      { checkin_type: 9, remind_time: '09:00', enabled: false, sort_order: 2 },
    ])).resolves.toEqual([
      expect.objectContaining({ checkin_type: 9, checkin_type_label: '打卡', tab: 'food' }),
    ])

    mocks.get.mockResolvedValueOnce([
      { checkinType: 4, remindTime: '07:30' },
    ])
    await expect(reminder.getReminderDefaults()).resolves.toEqual([
      expect.objectContaining({ checkin_type: 4, tab: 'glucose' }),
    ])
  })

  it('covers checkin api array guards and glucose payload branches', async () => {
    const checkin = await import('../checkin')

    mocks.get.mockResolvedValueOnce('not-array')
    await expect(checkin.getFoodPresets()).resolves.toEqual([])

    mocks.get.mockResolvedValueOnce('not-array')
    await expect(checkin.getFoodRecords('2026-06-30')).resolves.toEqual([])

    mocks.get.mockResolvedValueOnce('not-array')
    await expect(checkin.getMedicationPresets()).resolves.toEqual([])

    mocks.get.mockResolvedValueOnce('not-array')
    await expect(checkin.getMedicationRecords('2026-06-30')).resolves.toEqual([])

    mocks.get.mockResolvedValueOnce('not-array')
    await expect(checkin.getExercisePresets()).resolves.toEqual([])

    mocks.get.mockResolvedValueOnce('not-array')
    await expect(checkin.getExerciseRecords('2026-06-30')).resolves.toEqual([])

    mocks.get.mockResolvedValueOnce('not-array')
    await expect(checkin.getGlucoseRecords('2026-06-30')).resolves.toEqual([])

    mocks.get.mockResolvedValueOnce([{ id: 'ach_raw', name: '原始', unlocked: true, unlockedAt: '2026-06-01' }])
    await expect(checkin.getAchievements()).resolves.toEqual([
      expect.objectContaining({ id: 'ach_raw', unlocked_at: '2026-06-01' }),
    ])

    await expect(checkin.createGlucoseCheckin({
      checkinDate: '2026-06-30',
      glucoseValue: 6.1,
      measureContext: 1,
      unit: 2,
    })).resolves.toMatchObject({ points_earned: 15 })
  })

  it('covers risk history, user gender and health record branches', async () => {
    vi.stubEnv('VITE_USE_MOCK', 'false')
    vi.resetModules()

    const risk = await import('../risk')
    mocks.get.mockResolvedValueOnce({
      list: [{ assessmentId: 'r-live', riskScore: 55 }],
      total: 3,
    })
    await expect(risk.getRiskHistory({ page: 2, page_size: 5 })).resolves.toMatchObject({
      total: 3,
      page: 2,
      page_size: 5,
      list: [expect.objectContaining({ assessment_id: 'r-live' })],
    })

    vi.unstubAllEnvs()
    vi.resetModules()

    const user = await import('../user')
    await expect(user.updateUserProfile({ gender: 'unknown' })).resolves.toMatchObject({ gender: 'unknown' })

    const health = await import('../healthRecord')
    await expect(health.updateHealthRecord({ notes: '仅备注' })).resolves.toMatchObject({
      notes: '仅备注',
      bmi: expect.any(Number),
    })
  })

  it('covers plan favorite parsing and dify qa stream fallback', async () => {
    const plan = await import('../plan')
    const dify = await import('../dify')

    mocks.post.mockResolvedValueOnce({ data: { favorited: false } })
    await expect(plan.togglePlanFavorite('p-nested')).resolves.toEqual({ favorited: false })

    mocks.post.mockResolvedValueOnce({ favorited: '1' })
    await expect(plan.togglePlanFavorite('p-string')).resolves.toEqual({ favorited: true })

    mocks.post.mockResolvedValueOnce(null)
    await expect(plan.togglePlanFavorite('p-null', false)).resolves.toEqual({ favorited: true })

    mocks.post.mockResolvedValueOnce('ok')
    await expect(plan.togglePlanFavorite('p-string-res', true)).resolves.toEqual({ favorited: false })

    mocks.post.mockResolvedValueOnce(1)
    await expect(plan.togglePlanFavorite('p-number-res', false)).resolves.toEqual({ favorited: true })

    mocks.get.mockResolvedValueOnce({ list: [{ planId: 'p-only-list' }], total: undefined })
    await expect(plan.getPlanHistory()).resolves.toMatchObject({
      list: [expect.objectContaining({ plan_id: 'p-only-list' })],
      total: 1,
    })

    mocks.mockSSEStream.mockImplementationOnce(async (_chunks, onChunk, onEnd) => {
      onChunk('片段')
      onEnd?.()
    })
    await dify.difyQaChat('')
    await dify.difyQaChat('问题', { conversationId: 'c1' })
  })

  it('covers remaining consultation, management, reminder and risk branches', async () => {
    const consultation = await import('../consultation')
    const management = await import('../checkinManagement')
    const reminder = await import('../checkinReminder')
    const risk = await import('../risk')
    const home = await import('../home')

    mocks.getV2.mockResolvedValueOnce({
      sessions: [{
        sessionId: 's3',
        doctorName: 'AI 医生',
        title: '副主任',
        department: '内分泌科',
      }],
    })
    await expect(consultation.listConsultations()).resolves.toMatchObject({
      list: [expect.objectContaining({ doctor_name: 'AI 医生', doctor_title: '副主任' })],
    })

    mocks.postV2.mockResolvedValueOnce({ session_id: 'snake-session' })
    await expect(consultation.createConsultation({ doctor_id: 'd1' })).resolves.toMatchObject({
      session_id: 'snake-session',
    })

    mocks.postV2.mockResolvedValueOnce({})
    await expect(consultation.createConsultation({ doctor_id: 'd2' })).resolves.toMatchObject({
      session_id: '',
    })

    mocks.getV2.mockResolvedValueOnce({ messages: [{ messageId: 'm-only-id', senderType: 'doctor' }] })
    await expect(consultation.getConsultMessages('s4')).resolves.toEqual([
      expect.objectContaining({ message_id: 'm-only-id', sender_type: 'doctor' }),
    ])

    mocks.get.mockResolvedValueOnce({
      summary: '纯 summary',
      behavior_patterns: [{ type: 'diet', pattern: '一般' }],
      anomalies: [{ date: '2026-06-02', type: 'miss', description: '缺失' }],
    })
    await expect(management.getAiSummary()).resolves.toMatchObject({
      ai_summary: '纯 summary',
      behavior_patterns: [expect.objectContaining({ completion_rate: null })],
      anomalies: [expect.objectContaining({ possible_reason: '' })],
    })

    mocks.post.mockResolvedValueOnce({ download_url: 'report.pdf', task_id: 'task-1' })
    await expect(management.exportReport({ format: 'pdf' })).resolves.toMatchObject({
      export_url: 'report.pdf',
      task_id: 'task-1',
    })

    mocks.put.mockResolvedValueOnce({ not: 'array' })
    await expect(reminder.saveReminderRules([
      { checkin_type: 1, remind_time: '08:00', enabled: false, sort_order: 0 },
    ])).resolves.toEqual([
      expect.objectContaining({ checkin_type: 1, enabled: false }),
    ])

    mocks.get.mockResolvedValueOnce([{ checkinType: 2, remindTime: '18:00' }])
    await expect(reminder.getReminderDefaults()).resolves.toEqual([
      expect.objectContaining({ checkin_type: 2, tab: 'exercise' }),
    ])

    mocks.get.mockResolvedValueOnce({
      list: [{ assessmentId: 'r-no-total' }],
    })
    await expect(risk.getRiskHistory()).resolves.toMatchObject({
      total: 1,
      list: [expect.objectContaining({ assessment_id: 'r-no-total' })],
    })

    mocks.get.mockResolvedValueOnce({
      list: [{ assessmentId: 'r-list-only' }],
    })
    await expect(risk.getRiskHistory()).resolves.toMatchObject({
      list: [expect.objectContaining({ assessment_id: 'r-list-only' })],
    })

    expect(home.normalizeDoctor({
      doctor_id: 'legacy',
      status: 'online',
    })).toMatchObject({
      doctor_id: 'legacy',
      status: 'online',
      avatar_url: expect.any(String),
    })

    expect(home.normalizeDoctor({
      doctorId: 'camel-id',
      name: '张医生',
      department: '内分泌科',
      hospital: '市医院',
      status: 1,
    })).toMatchObject({
      doctor_id: 'camel-id',
      status: 'online',
    })

    mocks.getV2.mockResolvedValueOnce({
      doctors: [
        { doctorId: 'd5', name: '钱医生', department: '内分泌科', hospital: '市医院', status: 1 },
      ],
    })
    await expect(home.getDoctors({ department: '内分泌科', keyword: '钱' })).resolves.toEqual([
      expect.objectContaining({ doctor_id: 'd5' }),
    ])

    mocks.getV2.mockResolvedValueOnce([
      { doctorId: 'd-array', name: '数组医生', department: '内分泌科', status: 1 },
    ])
    await expect(home.getDoctors({ status: 'online' })).resolves.toEqual([
      expect.objectContaining({ doctor_id: 'd-array', status: 'online' }),
    ])

    mocks.getV2.mockResolvedValueOnce({ banners: [], videos: [] })
    await expect(home.getHomeContent()).resolves.toMatchObject({
      banners: [],
      videos: [],
    })

    mocks.getV2.mockResolvedValueOnce([{ id: 'dept1', name: '内分泌科' }])
    await expect(consultation.getDepartments()).resolves.toEqual([
      { id: 'dept1', name: '内分泌科' },
    ])
  })

  it('covers checkin achievement id fallback branch', async () => {
    const checkin = await import('../checkin')
    mocks.get.mockResolvedValueOnce([
      { name: '无名成就', unlocked: false },
    ])
    await expect(checkin.getAchievements()).resolves.toEqual([
      expect.objectContaining({ id: 'ach_0', name: '无名成就' }),
    ])
  })

  it('covers user numeric gender and health record bmi branch', async () => {
    const user = await import('../user')
    const health = await import('../healthRecord')

    await expect(user.updateUserProfile({ gender: 1, nickname: '数值性别' })).resolves.toMatchObject({
      gender: 1,
    })

    await expect(user.updateUserProfile({ gender: 'invalid', nickname: '无效性别' })).resolves.toMatchObject({
      nickname: '无效性别',
    })

    await expect(health.updateHealthRecord({ height: 170, weight: 65 })).resolves.toMatchObject({
      height: 170,
      weight: 65,
      bmi: expect.any(Number),
    })
  })

  it('covers remaining home, consultation and checkin normalization branches', async () => {
    const home = await import('../home')
    const consultation = await import('../consultation')
    const checkin = await import('../checkin')
    const management = await import('../checkinManagement')
    const reminder = await import('../checkinReminder')

    expect(home.normalizeDoctor({
      status: 'busy',
      doctor_id: 'legacy-id',
    })).toMatchObject({
      doctor_id: 'legacy-id',
      status: 'busy',
      avatar_url: expect.any(String),
    })

    expect(home.normalizeDoctor({
      doctorId: 'camel-only',
      name: '无状态医生',
    })).toMatchObject({
      doctor_id: 'camel-only',
      status: 'offline',
    })

    mocks.getV2.mockResolvedValueOnce({
      banners: [{ banner_id: 'bb1', title: 'Banner' }],
      videos: [{ id: 'vv1', title: 'Video', duration: 'bad' }],
    })
    await expect(home.getHomeContent()).resolves.toMatchObject({
      banners: [expect.objectContaining({ id: 'bb1', image: expect.any(String) })],
      videos: [expect.objectContaining({ id: 'vv1', duration: 'bad' })],
    })

    mocks.getV2.mockResolvedValueOnce([
      { doctorId: 'd-array', name: '数组响应', department: '内分泌科', hospital: '市医院', status: 3 },
    ])
    await expect(home.getDoctors({ keyword: '市医院' })).resolves.toEqual([
      expect.objectContaining({ doctor_id: 'd-array', status: 'busy' }),
    ])

    mocks.getV2.mockResolvedValueOnce({
      doctors: [
        { doctorId: 'd-name', name: '姓名匹配', department: '营养科', status: 1 },
        { doctorId: 'd-dept', name: '赵医生', department: '内分泌科', status: 1 },
      ],
    })
    await expect(home.getDoctors({ keyword: '姓名' })).resolves.toEqual([
      expect.objectContaining({ doctor_id: 'd-name' }),
    ])

    mocks.getV2.mockResolvedValueOnce({
      doctors: [{ doctorId: 'd-status', name: '状态医生', department: '内分泌科', status: 'online' }],
    })
    await expect(home.getDoctors({ status: 'online' })).resolves.toEqual([
      expect.objectContaining({ status: 'online' }),
    ])

    mocks.getV2.mockResolvedValueOnce({ list: [] })
    await expect(consultation.getConsultMessages('empty')).resolves.toEqual([])

    mocks.getV2.mockResolvedValueOnce({
      sessions: [{ sessionId: 's-empty-name', doctorTitle: '主任' }],
    })
    await expect(consultation.listConsultations()).resolves.toMatchObject({
      list: [expect.objectContaining({ doctor_name: 'AI 医生', doctor_title: '主任' })],
    })

    mocks.postV2.mockResolvedValueOnce({
      user_message: { messageId: 'u-snake', senderType: 'user', sentAt: '2026-01-01T00:00:00Z' },
      ai_message: null,
    })
    await expect(consultation.sendConsultMessage('s5', { content: 'hi' })).resolves.toMatchObject({
      userMessage: expect.objectContaining({ message_id: 'u-snake', sent_at: '2026-01-01T00:00:00Z' }),
      aiMessage: null,
    })

    mocks.get.mockResolvedValueOnce([
      { id: 'ach-direct', name: '直出数组', unlocked: true },
    ])
    await expect(checkin.getAchievements()).resolves.toEqual([
      expect.objectContaining({ id: 'ach-direct' }),
    ])

    mocks.get.mockResolvedValueOnce({
      ai_summary: '后端 summary',
      behavior_patterns: [{ type: 'diet', pattern: '好', completionRate: 0.7, suggestion: '建议' }],
      anomalies: [{ date: '2026-06-03', type: 'miss', value: 2, possibleReason: '忙碌' }],
      improvements: [],
      source: 'backend',
    })
    await expect(management.getAiSummary()).resolves.toMatchObject({
      ai_summary: '后端 summary',
      behavior_patterns: [expect.objectContaining({ completion_rate: 0.7, suggestion: '建议' })],
      anomalies: [expect.objectContaining({ value: 2, possible_reason: '忙碌' })],
      source: 'backend',
    })

    mocks.post.mockResolvedValueOnce({ task_id: 'only-task' })
    await expect(management.exportReport({ format: 'csv' })).resolves.toMatchObject({
      export_url: 'only-task',
      task_id: 'only-task',
    })

    mocks.put.mockResolvedValueOnce([
      { checkinType: 1, remindTime: '08:00', enabled: true },
    ])
    await expect(reminder.saveReminderRules([
      { checkin_type: 1, remind_time: '08:00', enabled: true },
    ])).resolves.toEqual([
      expect.objectContaining({ checkin_type: 1, enabled: true, tab: 'food' }),
    ])

    mocks.get.mockResolvedValueOnce([
      { checkinType: 3, remindTime: '09:00' },
    ])
    await expect(reminder.getReminderDefaults()).resolves.toEqual([
      expect.objectContaining({ checkin_type: 3, tab: 'medication' }),
    ])

    vi.stubEnv('VITE_USE_MOCK', 'false')
    vi.resetModules()
    const riskLive = await import('../risk')
    mocks.get.mockResolvedValueOnce({
      records: [{ assessmentId: 'r-records', riskScore: 33 }],
      total: 4,
    })
    await expect(riskLive.getRiskHistory()).resolves.toMatchObject({
      total: 4,
      list: [expect.objectContaining({ assessment_id: 'r-records' })],
    })
    vi.unstubAllEnvs()
    vi.resetModules()
  })

  it('covers final home, consultation, checkin and management branches', async () => {
    const home = await import('../home')
    const consultation = await import('../consultation')
    const checkin = await import('../checkin')
    const management = await import('../checkinManagement')
    const reminder = await import('../checkinReminder')

    expect(home.normalizeDoctor({ doctor_id: 'only-snake-id', name: '李' })).toMatchObject({
      doctor_id: 'only-snake-id',
      avatar_url: expect.any(String),
    })

    mocks.getV2.mockResolvedValueOnce({
      banners: [{ id: 'banner-id-only' }],
      videos: [],
    })
    await expect(home.getHomeContent()).resolves.toMatchObject({
      banners: [expect.objectContaining({ id: 'banner-id-only', title: '', image: expect.any(String) })],
    })

    mocks.getV2.mockResolvedValueOnce({
      list: [{ doctorId: 'list-only', name: '列表医生', department: '内分泌科', status: 'custom' }],
    })
    await expect(home.getDoctors({ status: 'custom' })).resolves.toEqual([
      expect.objectContaining({ doctor_id: 'list-only', status: 'custom' }),
    ])

    mocks.getV2.mockResolvedValueOnce({
      list: [{ sessionId: 'sess-list', doctor_name: '张医生', doctor_title: '主治' }],
    })
    await expect(consultation.listConsultations()).resolves.toMatchObject({
      list: [expect.objectContaining({ session_id: 'sess-list', doctor_name: '张医生' })],
    })

    mocks.getV2.mockResolvedValueOnce({
      messages: [{ message_id: 'm-snake', sender_type: 'doctor', content: '你好' }],
    })
    await expect(consultation.getConsultMessages('s6')).resolves.toEqual([
      expect.objectContaining({ message_id: 'm-snake', sender_type: 'doctor' }),
    ])

    mocks.get.mockResolvedValueOnce({ achievements: [{ id: 'wrapped', name: '包装', unlocked: true }] })
    await expect(checkin.getAchievements()).resolves.toEqual([
      expect.objectContaining({ id: 'wrapped' }),
    ])

    mocks.get.mockResolvedValueOnce({
      summary: 'summary 字段',
      behavior_patterns: [],
      anomalies: [],
    })
    await expect(management.getAiSummary()).resolves.toMatchObject({
      ai_summary: 'summary 字段',
      behavior_patterns: [],
      anomalies: [],
    })

    mocks.post.mockResolvedValueOnce({})
    await expect(management.exportReport({ format: 'pdf' })).resolves.toMatchObject({
      export_url: '__PLACEHOLDER_EXPORT_URL__',
    })

    mocks.get.mockResolvedValueOnce({ rules: [{ checkinType: 2, remindTime: '18:00' }] })
    await expect(reminder.getReminderDefaults()).resolves.toEqual([
      expect.objectContaining({ checkin_type: 2, tab: 'exercise' }),
    ])

    mocks.get.mockResolvedValueOnce(null)
    await expect(checkin.getAchievements()).resolves.toEqual([])

    mocks.get.mockResolvedValueOnce({})
    await expect(management.getAiSummary()).resolves.toMatchObject({
      ai_summary: expect.any(String),
      behavior_patterns: [],
      anomalies: [],
    })
  })

  it('covers risk list fallback and reminder defaults array shape', async () => {
    vi.stubEnv('VITE_USE_MOCK', 'false')
    vi.resetModules()

    const risk = await import('../risk')
    const reminder = await import('../checkinReminder')

    mocks.get.mockResolvedValueOnce({
      records: [{ assessmentId: 'r-records-branch', riskScore: 44 }],
    })
    await expect(risk.getRiskHistory()).resolves.toMatchObject({
      list: [expect.objectContaining({ assessment_id: 'r-records-branch' })],
    })

    mocks.get.mockResolvedValueOnce([
      { checkinType: 4, remindTime: '07:30' },
    ])
    await expect(reminder.getReminderDefaults()).resolves.toEqual([
      expect.objectContaining({ checkin_type: 4, tab: 'glucose' }),
    ])

    vi.unstubAllEnvs()
    vi.resetModules()
  })
})
