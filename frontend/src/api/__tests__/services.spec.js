import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  del: vi.fn(),
  getV2: vi.fn(),
  postV2: vi.fn(),
  putV2: vi.fn(),
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
  putV2: mocks.putV2,
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

function invokePost(_url, _data, options = {}) {
  return options.mockFn ? options.mockFn() : {}
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.get.mockImplementation(invokeMock)
  mocks.post.mockImplementation(invokePost)
  mocks.put.mockImplementation(invokePost)
  mocks.del.mockImplementation(invokeMock)
  mocks.getV2.mockImplementation(async (url) => {
    if (url === '/ai-doctors/departments') return [{ id: 'endo', name: '内分泌科' }]
    if (url === '/consultations') return { sessions: [{ sessionId: 's1', doctorName: '李医生' }], total: 1 }
    if (url.endsWith('/messages')) return { messages: [{ messageId: 'm1', senderType: 'doctor', content: '您好' }] }
    if (url.endsWith('/ai-suggestion')) return { suggestions: ['建议监测血糖'] }
    if (url === '/home/content') return { banners: [{ bannerId: 'b1', title: '轮播' }], videos: [{ videoId: 'v1', title: '视频', duration: '00:03:20' }] }
    if (url === '/ai-doctors') return { doctors: [{ doctorId: 'd1', name: '李医生', department: '内分泌科', status: 1 }] }
    return {}
  })
  mocks.postV2.mockImplementation(async (url) => {
    if (url.endsWith('/messages')) {
      return {
        userMessage: { messageId: 'u1', senderType: 'user', content: '问题' },
        aiMessage: { messageId: 'a1', senderType: 'doctor', content: '答复' },
      }
    }
    if (url.endsWith('/close')) return { success: true }
    return { sessionId: 's1' }
  })
  mocks.mockSSEStream.mockImplementation(async (chunks, onChunk, onEnd) => {
    chunks.forEach((chunk) => onChunk(chunk))
    onEnd()
  })
  mocks.fetchBackendSSE.mockImplementation(async (_url, onEvent) => {
    onEvent({ event: 'stage_calorie', data: { daily_calories: 1600 } })
    onEvent({ event: 'stage_diet', data: { content: { breakfast: '燕麦' } } })
    onEvent({ event: 'stage_medication', data: { content: '按医嘱' } })
    onEvent({ event: 'complete', data: { plan_id: 'p1' } })
  })
})

afterEach(() => {
  vi.unstubAllEnvs()
})

describe('remaining frontend api modules', () => {
  it('covers chat and checkin management api modules', async () => {
    const chat = await import('../chat')
    const management = await import('../checkinManagement')
    const chunks = []
    const ended = []

    await chat.chatQA('血糖怎么控制', {
      conversationId: 'c1',
      onChunk: (chunk) => chunks.push(chunk),
      onEnd: (event) => ended.push(event),
    })
    expect(chunks.length).toBeGreaterThan(0)
    expect(ended[0]).toMatchObject({ type: 'end', conversation_id: 'c1' })

    await expect(management.getManagementStats({ range: 'weekly' })).resolves.toMatchObject({
      total_checkins: 45,
      completion_rate: 0.82,
    })
    await expect(management.getManagementTrends({ days: 7 })).resolves.toMatchObject({
      diet: expect.any(Array),
      exercise: expect.any(Array),
      medication: expect.any(Array),
      glucose: expect.any(Array),
    })
    await expect(management.getAiSummary()).resolves.toMatchObject({
      ai_summary: expect.any(String),
      behavior_patterns: expect.any(Array),
      anomalies: expect.any(Array),
      improvements: expect.any(Array),
    })
    await expect(management.exportReport({ format: 'csv' })).resolves.toMatchObject({
      export_url: 'task_mock',
      task_id: 'task_mock',
    })
  })

  it('covers checkin api calls and normalizers', async () => {
    const api = await import('../checkin')
    await expect(api.uploadCheckinImage('food', new File(['x'], 'a.png'))).resolves.toMatchObject({ object_key: expect.stringContaining('food/') })
    await expect(api.getFoodCategories()).resolves.toHaveLength(4)
    await expect(api.getFoodPresets('cat_drink')).resolves.toHaveLength(1)
    await expect(api.getFoodPresets()).resolves.toHaveLength(2)
    await expect(api.createFoodCheckin({ checkin_date: '2026-06-30' })).resolves.toMatchObject({ checkin_id: 'chk_food_mock' })
    await expect(api.getFoodRecords('2026-06-30')).resolves.toEqual([])
    await expect(api.getMedicationPresets()).resolves.toHaveLength(2)
    await expect(api.createMedicationCheckin({ checkin_date: '2026-06-30' })).resolves.toMatchObject({ checkin_id: 'chk_med_mock' })
    await expect(api.getMedicationRecords('2026-06-30')).resolves.toEqual([])
    await expect(api.getExercisePresets()).resolves.toHaveLength(2)
    await expect(api.createExerciseCheckin({ checkin_date: '2026-06-30' })).resolves.toMatchObject({ calories_burned: 135 })
    await expect(api.getExerciseRecords('2026-06-30')).resolves.toEqual([])
    await expect(api.createGlucoseCheckin({ glucose_value: '6.2' })).resolves.toMatchObject({ points_earned: 15 })
    await expect(api.getGlucoseHistory({ start_date: '2026-06-01', end_date: '2026-06-30', days: 30 })).resolves.toMatchObject({ summary: expect.any(Object) })
    await expect(api.getGlucoseRecords('2026-06-30')).resolves.toEqual([])
    await expect(api.getTodayStatus()).resolves.toMatchObject({ today_points: 10 })
    await expect(api.getCheckinStats()).resolves.toMatchObject({ total_checkins: 45 })
    await expect(api.getAchievements()).resolves.toEqual(expect.arrayContaining([expect.objectContaining({ id: 'ach_0' })]))
  })

  it('covers consultation api response mapping', async () => {
    const api = await import('../consultation')
    await expect(api.getDepartments()).resolves.toHaveLength(1)
    await expect(api.listConsultations({ page_size: 5 })).resolves.toMatchObject({ total: 1, page_size: 5 })
    await expect(api.createConsultation({ doctor_id: 'd1' })).resolves.toMatchObject({ session_id: 's1' })
    await expect(api.getConsultMessages('s1')).resolves.toEqual([expect.objectContaining({ message_id: 'm1' })])
    await expect(api.sendConsultMessage('s1', { content: '问题' })).resolves.toMatchObject({
      userMessage: { message_id: 'u1' },
      aiMessage: { message_id: 'a1' },
    })
    await expect(api.getAiSuggest('s1')).resolves.toMatchObject({ suggestions: ['建议监测血糖'] })
    await expect(api.closeConsultation('s1', { rating: 5, feedback: '好' })).resolves.toEqual({ success: true })
  })

  it('covers api fallback response shapes and optional branches', async () => {
    const consultation = await import('../consultation')
    const management = await import('../checkinManagement')
    const home = await import('../home')
    const dify = await import('../dify')

    mocks.getV2.mockResolvedValueOnce({ departments: [] })
    await expect(consultation.getDepartments()).resolves.toEqual([])

    mocks.getV2.mockResolvedValueOnce({
      list: [{
        sessionId: 's2',
        doctorId: 'd2',
        name: 'AI 医生',
        status: 'closed',
        completionRate: 0.8,
      }],
    })
    await expect(consultation.listConsultations({ size: 3 })).resolves.toMatchObject({
      total: 1,
      page_size: 3,
      list: [expect.objectContaining({ session_id: 's2', doctor_name: 'AI 医生' })],
    })

    mocks.postV2.mockResolvedValueOnce({ sessionId: 'camel-session' })
    await expect(consultation.createConsultation({ doctor_id: 'd2' })).resolves.toMatchObject({
      session_id: 'camel-session',
    })

    mocks.getV2.mockResolvedValueOnce({
      messages: [
        null,
        { messageId: 'm2', senderType: 'user', sentAt: '2026-06-30T08:00:00+08:00' },
      ],
    })
    await expect(consultation.getConsultMessages('s2')).resolves.toEqual([
      null,
      expect.objectContaining({ message_id: 'm2', sender_type: 'user' }),
    ])

    mocks.postV2.mockResolvedValueOnce({ messageId: 'direct', senderType: 'user', content: '只有用户消息' })
    await expect(consultation.sendConsultMessage('s2', { content: '问题' })).resolves.toMatchObject({
      userMessage: { message_id: 'direct' },
      aiMessage: null,
    })

    mocks.get.mockResolvedValueOnce({})
    await expect(management.getManagementTrends()).resolves.toEqual({
      diet: [],
      exercise: [],
      medication: [],
      glucose: [],
    })

    mocks.get.mockResolvedValueOnce({
      aiSummary: '后端摘要',
      behaviorPatterns: [{ type: 'diet', pattern: '稳定', completionRate: 0.9 }],
      anomalies: [{ possibleReason: '外出' }],
      source: 'backend',
    })
    await expect(management.getAiSummary()).resolves.toMatchObject({
      ai_summary: '后端摘要',
      behavior_patterns: [expect.objectContaining({ completion_rate: 0.9 })],
      anomalies: [expect.objectContaining({ possible_reason: '外出' })],
      improvements: [],
      source: 'backend',
    })

    mocks.post.mockResolvedValueOnce({ download_url: 'report.pdf', taskId: 't2' })
    await expect(management.exportReport({ start_date: '2026-06-01' })).resolves.toMatchObject({
      export_url: 'report.pdf',
      task_id: 't2',
    })

    expect(home.normalizeDoctor({ doctorId: 'd9', status: 'custom', rating: '4.5' })).toMatchObject({
      doctor_id: 'd9',
      status: 'custom',
      rating: 4.5,
    })
    expect(home.normalizeVideo({})).toMatchObject({ id: '', title: '', duration: '' })

    await expect(dify.difyRiskAssess({
      height: 170,
      weight: 55,
      fasting_glucose: 5.2,
      family_history: false,
      is_pregnant: false,
    })).resolves.toMatchObject({
      risk_level: 'low',
      bmi_level: 'normal',
      glucose_level: 'normal',
    })

    await expect(dify.difyRiskAssess({
      height: 160,
      weight: 76,
      fasting_glucose: 7.2,
      family_history: false,
      is_pregnant: true,
    })).resolves.toMatchObject({
      risk_level: 'high',
      bmi_level: 'obese',
      glucose_level: 'diabetes',
    })
  })

  it('covers home, risk, health record and user api modules', async () => {
    const home = await import('../home')
    const risk = await import('../risk')
    const health = await import('../healthRecord')
    const user = await import('../user')

    expect(home.normalizeDoctor(null)).toBeNull()
    expect(home.normalizeVideo({ id: 'v2', duration: '01:02:03' })).toMatchObject({ duration: '1:02:03' })
    await expect(home.getHomeContent()).resolves.toMatchObject({ banners: expect.any(Array), videos: expect.any(Array) })
    await expect(home.getVideos({ keyword: '视频' })).resolves.toMatchObject({ total: 1 })
    await expect(home.getRecommend()).resolves.toEqual(expect.any(Array))
    await expect(home.getDoctors({ department: '内分泌科', status: 1, keyword: '李' })).resolves.toHaveLength(1)

    await expect(risk.assessRisk({ height: 170, weight: 80, fasting_glucose: 6.5 })).resolves.toMatchObject({ risk_score: expect.any(Number) })
    await expect(risk.getRiskHistory()).resolves.toMatchObject({ list: expect.any(Array) })
    await expect(risk.getRiskDetail('r1')).resolves.toMatchObject({ assessment_id: expect.any(String) })

    await expect(health.getHealthRecord()).resolves.toEqual(expect.any(Object))
    await expect(health.updateHealthRecord({ height: 170 })).resolves.toEqual(expect.any(Object))

    await expect(user.getUserProfile()).resolves.toMatchObject({ user_id: expect.any(String) })
    await expect(user.updateUserProfile({ gender: 'male' })).resolves.toMatchObject({ gender: 'male' })
    await expect(user.uploadUserAvatar(new File(['x'], 'a.png'))).resolves.toMatchObject({ avatar_url: expect.any(String) })
    await expect(user.getUserConsultations()).resolves.toMatchObject({ list: expect.any(Array) })
    await expect(user.getHealthAlert()).resolves.toEqual(expect.any(Object))
    await expect(user.getHealthTrendSummary()).resolves.toMatchObject({ summary: expect.any(String) })
    await expect(user.exportUserData({ profile: true })).resolves.toMatchObject({ task_id: 'export_mock' })
    await expect(user.getExportTask('t1')).resolves.toMatchObject({ task_id: 't1' })
    await expect(user.updatePrivacySettings({ share: false })).resolves.toEqual({ share: false })
    await expect(user.changePassword({ old: 'a', password: 'b' })).resolves.toEqual({ success: true })
    await expect(user.bindEmail({ email: 'a@b.com' })).resolves.toMatchObject({ email: 'a@b.com' })
    await expect(user.bindPhone({ phone: '13800000000' })).resolves.toMatchObject({ phone: '13800000000' })
    await expect(user.getUserOverview()).resolves.toMatchObject({ user_id: expect.any(String) })
  })

  it('covers dify and plan api modules', async () => {
    const dify = await import('../dify')
    const plan = await import('../plan')
    const chunks = []
    const stages = []

    await dify.difyQaChat('饮食建议', { conversationId: 'c1', onChunk: (c) => chunks.push(c), onEnd: (e) => stages.push(e) })
    expect(chunks.length).toBeGreaterThan(0)
    await expect(dify.difyConsultationSuggest('血糖偏高')).resolves.toMatchObject({ diagnosis_direction: expect.any(Array) })
    await expect(dify.difyRiskAssess({ height: 170, weight: 80, fasting_glucose: 6.5, family_history: true })).resolves.toMatchObject({ risk_level: expect.any(String) })
    await dify.difyPlanGenerate({ onStage: (stage) => stages.push(stage) })
    await expect(dify.difyArticleDraft('饮食')).resolves.toMatchObject({ title: expect.stringContaining('饮食') })

    await plan.generatePlan({ onStage: (stage) => stages.push(stage) })
    await expect(plan.getLatestPlan()).resolves.toMatchObject({ plan_id: expect.any(String) })
    await expect(plan.getPlanDetail('p1')).resolves.toMatchObject({ plan_id: expect.any(String) })
    await expect(plan.getPlanHistory()).resolves.toMatchObject({ list: expect.any(Array), total: 2 })
    await expect(plan.togglePlanFavorite('p1')).resolves.toEqual({ favorited: true })
    await expect(plan.togglePlanFavorite('p1', true)).resolves.toEqual({ favorited: false })
  })

  it('covers home and plan compatibility branches', async () => {
    const home = await import('../home')
    const plan = await import('../plan')

    expect(home.normalizeDoctor({ doctorId: 'd2', status: 2 })).toMatchObject({ status: 'offline' })
    expect(home.normalizeDoctor({ doctorId: 'd3', status: 3 })).toMatchObject({ status: 'busy' })
    expect(home.normalizeDoctor({ doctorId: 'd4', status: 9 })).toMatchObject({ status: '9' })
    expect(home.normalizeVideo({ videoId: 'v3', duration: '00:01:05' })).toMatchObject({ duration: '1:05' })
    expect(home.normalizeVideo({ videoId: 'v4', duration: '12:30' })).toMatchObject({ duration: '12:30' })

    mocks.getV2.mockImplementationOnce(invokeMock)
    await expect(home.getHomeContent()).resolves.toMatchObject({
      banners: expect.any(Array),
      videos: expect.any(Array),
    })

    mocks.getV2.mockResolvedValueOnce([
      { doctorId: 'd1', name: '王医生', department: '内分泌科', hospital: '市医院', status: 1 },
      { doctorId: 'd2', name: '张医生', department: '营养科', hospital: '社区医院', status: 2 },
    ])
    await expect(home.getDoctors({ keyword: '社区' })).resolves.toEqual([
      expect.objectContaining({ doctor_id: 'd2', status: 'offline' }),
    ])

    mocks.getV2.mockResolvedValueOnce({
      list: [{ doctorId: 'd3', name: '陈医生', department: '内分泌科', status: 'busy' }],
    })
    await expect(home.getDoctors()).resolves.toEqual([
      expect.objectContaining({ doctor_id: 'd3', status: 'busy' }),
    ])

    mocks.post.mockResolvedValueOnce({ data: { isFavorite: '1' } })
    await expect(plan.togglePlanFavorite('p-data')).resolves.toEqual({ favorited: true })

    mocks.post.mockResolvedValueOnce({ is_favorite: 0 })
    await expect(plan.togglePlanFavorite('p-false')).resolves.toEqual({ favorited: false })

    mocks.post.mockResolvedValueOnce('收藏成功')
    await expect(plan.togglePlanFavorite('p-text', true)).resolves.toEqual({ favorited: false })

    mocks.post.mockResolvedValueOnce({ message: '收藏成功' })
    await expect(plan.togglePlanFavorite('p-null-object', false)).resolves.toEqual({ favorited: true })
  })

  it('covers home recommend request when mock mode is disabled', async () => {
    vi.stubEnv('VITE_USE_MOCK', 'false')
    vi.resetModules()
    const home = await import('../home')

    await expect(home.getRecommend()).resolves.toEqual(expect.any(Array))
  })
})
