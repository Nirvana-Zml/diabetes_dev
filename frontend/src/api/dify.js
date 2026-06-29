import { DIFY_WORKFLOWS, USE_MOCK } from '@/config'
import { delay } from '@/utils/delay'
import { mockSSEStream } from '@/utils/sse'
import { mockQaAnswer } from '@/mock/data'

/**
 * Dify 工作流调用占位层
 * 【待后续开发】真实对接时：POST 到 DIFY_WORKFLOWS.* URL，处理 SSE / blocking 响应
 * 禁止在本阶段实现真实 HTTP 调用
 */

function logPlaceholder(workflowName, url) {
  if (USE_MOCK) {
    console.info(`[Dify占位] ${workflowName} → ${url}`)
  }
}

/** 科普问答工作流（SSE 流式）— 待后续开发 */
export async function difyQaChat(query, { onChunk, onEnd, conversationId } = {}) {
  logPlaceholder('科普问答', DIFY_WORKFLOWS.QA)
  // TODO: 对接 Dify 科普问答工作流 streaming API
  const chunks = mockQaAnswer.match(/[\s\S]{1,12}/g) || [mockQaAnswer]
  await mockSSEStream(chunks, (c) => onChunk?.({ type: 'text', content: c, conversation_id: conversationId || 'conv_mock' }), () => {
    onEnd?.({ type: 'end', conversation_id: conversationId || 'conv_mock', metadata: { sources: ['中国2型糖尿病防治指南'] } })
  })
}

/** 问诊辅助工作流 — 待后续开发 */
export async function difyConsultationSuggest(patientDescription) {
  logPlaceholder('问诊辅助', DIFY_WORKFLOWS.CONSULTATION)
  // TODO: 对接 Dify 问诊工作流 blocking API
  await delay(500)
  return {
    diagnosis_direction: [{ name: '2型糖尿病血糖控制不佳', confidence: '中' }],
    follow_up_questions: ['目前用药剂量？', '近期饮食是否控制？'],
    examinations: ['糖化血红蛋白', '空腹及餐后血糖监测'],
    treatment_reference: '建议评估当前用药方案',
  }
}

/** 风险评估 Mock（仅 USE_MOCK 时由 risk.js 调用） */
export async function difyRiskAssess(inputData) {
  logPlaceholder('风险评估', DIFY_WORKFLOWS.RISK)
  await delay(800)
  const bmi = inputData.weight / ((inputData.height / 100) ** 2)
  let riskScore = 30
  if (bmi >= 24) riskScore += 15
  if (inputData.fasting_glucose >= 6.1) riskScore += 20
  if (inputData.family_history) riskScore += 15
  if (inputData.is_pregnant) riskScore += 10
  const risk_level = riskScore >= 60 ? 'high' : riskScore >= 40 ? 'medium' : 'low'
  return {
    risk_level,
    risk_score: Math.min(riskScore, 100),
    bmi: Math.round(bmi * 10) / 10,
    bmi_level: bmi >= 28 ? 'obese' : bmi >= 24 ? 'overweight' : 'normal',
    glucose_level: inputData.fasting_glucose >= 7 ? 'diabetes' : inputData.fasting_glucose >= 6.1 ? 'prediabetes' : 'normal',
    report_summary: '基于问卷信息与本地规则引擎的模拟评估结果。',
    factors: [
      { name: 'BMI', weight: bmi >= 24 ? 20 : 5, description: `BMI ${Math.round(bmi * 10) / 10}` },
      { name: '血糖', weight: inputData.fasting_glucose >= 6.1 ? 25 : 5, description: `空腹 ${inputData.fasting_glucose} mmol/L` },
    ],
    suggestions: ['建议定期监测血糖', '保持健康饮食和规律运动', '如有妊娠情况请咨询产科医生'],
    confidence: 'medium',
  }
}

/** 方案生成 Mock（仅 USE_MOCK 时由 plan.js 调用） */
export async function difyPlanGenerate({ onStage } = {}) {
  logPlaceholder('方案生成', DIFY_WORKFLOWS.PLAN)
  const { mockHealthPlan } = await import('@/mock/data')
  const stages = [
    { stage: 'calorie', daily_calories: mockHealthPlan.daily_calories },
    { stage: 'diet', content: mockHealthPlan.diet_plan },
    { stage: 'exercise', content: mockHealthPlan.exercise_plan },
    { stage: 'rest', content: mockHealthPlan.rest_plan },
    { stage: 'medication', content: mockHealthPlan.medication_note },
    { stage: 'complete', plan_id: 'plan_mock_' + Date.now() },
  ]
  for (const s of stages) {
    await delay(400)
    onStage?.(s)
  }
}

/** 资讯 AI 初稿 — 待后续开发（管理端前端直调 Dify） */
export async function difyArticleDraft(topic) {
  logPlaceholder('资讯初稿', DIFY_WORKFLOWS.ARTICLE_DRAFT)
  // TODO: 管理端直调 Dify 工作流 URL
  await delay(600)
  return {
    title: `${topic}：糖尿病患者必读指南`,
    content: `## ${topic}\n\n本文介绍${topic}相关的基本知识。`,
    summary: `关于${topic}的科普文章。`,
    tags: ['糖尿病', topic],
  }
}
