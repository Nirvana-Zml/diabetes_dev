/**
 * 管理端直调 Dify 资讯初稿工作流（SSE streaming）。
 * 契约见 docs/资讯生成工作流数据契约.md
 */

const DIFY_PROXY_PREFIX = import.meta.env.VITE_DIFY_PROXY_PREFIX || '/dify-proxy'

/** 开发环境将通过 Vite 代理访问 Dify，避免浏览器 CORS */
export function resolveDifyWorkflowUrl(workflowUrl) {
  if (!workflowUrl) return workflowUrl
  const useProxy = import.meta.env.VITE_DIFY_USE_PROXY !== 'false'
  if (!useProxy) return workflowUrl
  try {
    const u = new URL(workflowUrl)
    return `${DIFY_PROXY_PREFIX}${u.pathname}${u.search}`
  } catch {
    if (workflowUrl.startsWith('/')) return workflowUrl
    return workflowUrl
  }
}

function emptyDraft() {
  return { title: '', summary: '', content: '', tags: [] }
}

function mergeDraft(base, patch) {
  if (!patch || typeof patch !== 'object') return base
  const next = { ...base }
  if (patch.title) next.title = patch.title
  if (patch.summary) next.summary = patch.summary
  if (patch.content != null) next.content = patch.content
  if (Array.isArray(patch.tags) && patch.tags.length) next.tags = patch.tags
  return next
}

/**
 * 从工作流 payload 中提取 article_draft 或契约 { event, data } 结构。
 */
export function extractArticleDraftPayload(parsed) {
  if (!parsed || typeof parsed !== 'object') return null

  if (parsed.article_draft && typeof parsed.article_draft === 'object') {
    return parsed.article_draft
  }

  if (parsed.event === 'error') {
    const msg = parsed.data?.message || parsed.message || 'AI 生成失败'
    throw new Error(msg)
  }

  if (parsed.event === 'text_chunk' || parsed.event === 'complete') {
    if (parsed.data && typeof parsed.data === 'object') return parsed.data
  }

  if (typeof parsed.sse_data === 'string' && parsed.sse_data.trim()) {
    try {
      return extractArticleDraftPayload(JSON.parse(parsed.sse_data))
    } catch {
      /* ignore */
    }
  }

  const outputs = parsed.data?.outputs || parsed.outputs
  if (outputs?.article_draft) {
    const raw = outputs.article_draft
    if (typeof raw === 'string') {
      try {
        return JSON.parse(raw)
      } catch {
        return null
      }
    }
    return raw
  }

  if (parsed.text && typeof parsed.text === 'string') {
    try {
      return extractArticleDraftPayload(JSON.parse(parsed.text))
    } catch {
      /* ignore */
    }
  }

  if (parsed.title || parsed.content) return parsed

  return null
}

function handleSseDataBlock(dataStr, draft, callbacks) {
  if (!dataStr || dataStr === '[DONE]') return draft
  let parsed
  try {
    parsed = JSON.parse(dataStr)
  } catch {
    return draft
  }

  const patch = extractArticleDraftPayload(parsed)
  if (patch) {
    draft = mergeDraft(draft, patch)
    callbacks.onChunk?.({ ...draft })
  }

  if (parsed.event === 'workflow_finished' || parsed.event === 'complete') {
    callbacks.onComplete?.({ ...draft })
  }

  if (parsed.event === 'error') {
    const msg = parsed.data?.message || parsed.message || 'AI 生成失败'
    throw new Error(msg)
  }

  return draft
}

/**
 * 调用 Dify 资讯初稿工作流（streaming）。
 * @param {{ workflowUrl: string, apiKey: string }} config
 * @param {{ topic: string, keywords?: string, user?: string }} input
 * @param {{ onChunk?: (draft) => void, onComplete?: (draft) => void }} callbacks
 */
export async function streamArticleDraft(config, input, callbacks = {}) {
  const url = resolveDifyWorkflowUrl(config.workflowUrl)
  const apiKey = config.apiKey
  if (!url || !apiKey) {
    throw new Error('AI 初稿工作流未配置，请联系管理员')
  }
  if (!input.topic?.trim()) {
    throw new Error('请输入资讯主题')
  }

  const res = await fetch(url, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${apiKey}`,
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    body: JSON.stringify({
      response_mode: 'streaming',
      user: input.user || 'admin',
      inputs: {
        topic: input.topic.trim(),
        keywords: (input.keywords || '').trim(),
      },
    }),
  })

  if (!res.ok) {
    const text = (await res.text()).trim()
    let message = `Dify 请求失败: HTTP ${res.status}`
    if (text) {
      try {
        const body = JSON.parse(text)
        message = body.message || body.error || text
      } catch {
        message = text.slice(0, 300)
      }
    }
    throw new Error(message)
  }

  let draft = emptyDraft()
  const reader = res.body?.getReader()
  if (!reader) {
    throw new Error('浏览器不支持流式响应')
  }

  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const parts = buffer.split('\n\n')
    buffer = parts.pop() || ''

    for (const block of parts) {
      if (!block.trim()) continue
      let dataStr = ''
      for (const line of block.split('\n')) {
        if (line.startsWith('data:')) dataStr += line.slice(5).trim()
      }
      draft = handleSseDataBlock(dataStr, draft, callbacks)
    }
  }

  if (buffer.trim()) {
    let dataStr = ''
    for (const line of buffer.split('\n')) {
      if (line.startsWith('data:')) dataStr += line.slice(5).trim()
    }
    draft = handleSseDataBlock(dataStr, draft, callbacks)
  }

  if (!draft.title && !draft.content) {
    throw new Error('未收到有效的 AI 初稿内容，请检查 Dify 工作流输出格式')
  }

  callbacks.onComplete?.({ ...draft })
  return draft
}
