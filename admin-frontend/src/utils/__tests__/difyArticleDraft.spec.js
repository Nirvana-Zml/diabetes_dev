import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  extractArticleDraftPayload,
  resolveDifyWorkflowUrl,
  streamArticleDraft,
} from '../difyArticleDraft'

describe('difyArticleDraft utils', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
    vi.restoreAllMocks()
  })

  it('resolves workflow url through proxy prefix', () => {
    vi.stubEnv('VITE_DIFY_USE_PROXY', 'true')
    expect(resolveDifyWorkflowUrl('http://localhost:58080/v1/workflows/run')).toBe(
      '/dify-proxy/v1/workflows/run',
    )
    expect(resolveDifyWorkflowUrl('/local/path')).toBe('/local/path')
    expect(resolveDifyWorkflowUrl('')).toBe('')
    expect(resolveDifyWorkflowUrl('not-a-url')).toBe('not-a-url')
  })

  it('returns original url when proxy disabled', () => {
    vi.stubEnv('VITE_DIFY_USE_PROXY', 'false')
    const url = 'http://localhost:58080/v1/workflows/run'
    expect(resolveDifyWorkflowUrl(url)).toBe(url)
  })

  it('extracts article draft from multiple payload shapes', () => {
    expect(extractArticleDraftPayload({ article_draft: { title: '标题' } })).toEqual({ title: '标题' })
    expect(extractArticleDraftPayload({ event: 'text_chunk', data: { title: '块' } })).toEqual({ title: '块' })
    expect(extractArticleDraftPayload({ event: 'complete', data: { content: '正文' } })).toEqual({ content: '正文' })
    expect(extractArticleDraftPayload({ title: '直接', content: '内容' })).toEqual({ title: '直接', content: '内容' })
    expect(extractArticleDraftPayload({ outputs: { article_draft: { summary: '摘要' } } })).toEqual({ summary: '摘要' })
    expect(extractArticleDraftPayload({ data: { outputs: { article_draft: '{"title":"json"}' } } })).toEqual({ title: 'json' })
    expect(extractArticleDraftPayload({ data: { outputs: { article_draft: 'bad-json' } } })).toBeNull()
    expect(extractArticleDraftPayload({ sse_data: JSON.stringify({ title: '嵌套' }) })).toEqual({ title: '嵌套' })
    expect(extractArticleDraftPayload({ sse_data: 'bad' })).toBeNull()
    expect(extractArticleDraftPayload({ text: JSON.stringify({ content: 'text字段' }) })).toEqual({ content: 'text字段' })
    expect(extractArticleDraftPayload({ text: 'invalid' })).toBeNull()
    expect(extractArticleDraftPayload(null)).toBeNull()
    expect(extractArticleDraftPayload('raw')).toBeNull()
  })

  it('throws on error events during extraction', () => {
    expect(() => extractArticleDraftPayload({ event: 'error', data: { message: '失败' } })).toThrow('失败')
    expect(() => extractArticleDraftPayload({ event: 'error' })).toThrow('AI 生成失败')
  })

  it('streams article draft from sse response', async () => {
    const chunks = []
    const completed = []
    const encoder = new TextEncoder()
    let readCount = 0
    const sseBody = [
      'data: {"event":"text_chunk","data":{"title":"流式标题"}}\n\n',
      'data: {"event":"complete","data":{"content":"流式正文"}}\n\n',
    ].join('')

    global.fetch = vi.fn(async () => ({
      ok: true,
      body: {
        getReader() {
          return {
            async read() {
              if (readCount === 0) {
                readCount += 1
                return { done: false, value: encoder.encode(sseBody) }
              }
              return { done: true, value: undefined }
            },
          }
        },
      },
    }))

    const draft = await streamArticleDraft(
      { workflowUrl: 'http://localhost/v1/workflows/run', apiKey: 'app-key' },
      { topic: '  控糖饮食  ', keywords: '  低GI  ' },
      {
        onChunk: (value) => chunks.push(value),
        onComplete: (value) => completed.push(value),
      },
    )

    expect(draft).toMatchObject({ title: '流式标题', content: '流式正文' })
    expect(chunks.length).toBeGreaterThan(0)
    expect(completed.length).toBeGreaterThan(0)
    expect(global.fetch).toHaveBeenCalledWith(
      '/dify-proxy/v1/workflows/run',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({ Authorization: 'Bearer app-key' }),
      }),
    )
  })

  it('validates config, topic and response errors', async () => {
    await expect(streamArticleDraft({}, { topic: '主题' })).rejects.toThrow('AI 初稿工作流未配置')
    await expect(streamArticleDraft({ workflowUrl: '/run', apiKey: 'k' }, { topic: '   ' })).rejects.toThrow('请输入资讯主题')

    global.fetch = vi.fn(async () => ({
      ok: false,
      status: 500,
      text: async () => JSON.stringify({ message: '服务不可用' }),
    }))
    await expect(streamArticleDraft({ workflowUrl: '/run', apiKey: 'k' }, { topic: '主题' })).rejects.toThrow('服务不可用')

    global.fetch = vi.fn(async () => ({
      ok: false,
      status: 502,
      text: async () => 'plain error text',
    }))
    await expect(streamArticleDraft({ workflowUrl: '/run', apiKey: 'k' }, { topic: '主题' })).rejects.toThrow('plain error text')

    global.fetch = vi.fn(async () => ({
      ok: true,
      body: { getReader: () => null },
    }))
    await expect(streamArticleDraft({ workflowUrl: '/run', apiKey: 'k' }, { topic: '主题' })).rejects.toThrow('浏览器不支持流式响应')
  })

  it('throws when stream has no valid draft content', async () => {
    global.fetch = vi.fn(async () => ({
      ok: true,
      body: {
        getReader() {
          return {
            async read() {
              return { done: true, value: undefined }
            },
          }
        },
      },
    }))
    await expect(streamArticleDraft({ workflowUrl: '/run', apiKey: 'k' }, { topic: '主题' })).rejects.toThrow('未收到有效的 AI 初稿内容')
  })

  it('handles trailing sse buffer after stream ends', async () => {
    const completed = []
    const encoder = new TextEncoder()
    global.fetch = vi.fn(async () => ({
      ok: true,
      body: {
        getReader() {
          let sent = false
          return {
            async read() {
              if (!sent) {
                sent = true
                return {
                  done: false,
                  value: encoder.encode('data: {"event":"complete","data":{"title":"尾缓冲标题","content":"尾缓冲正文"}}'),
                }
              }
              return { done: true, value: undefined }
            },
          }
        },
      },
    }))

    const draft = await streamArticleDraft(
      { workflowUrl: '/run', apiKey: 'k' },
      { topic: '尾缓冲' },
      { onComplete: (value) => completed.push(value) },
    )

    expect(draft).toMatchObject({ title: '尾缓冲标题', content: '尾缓冲正文' })
    expect(completed.length).toBeGreaterThan(0)
  })

  it('maps http error body fields from json and empty text', async () => {
    global.fetch = vi.fn(async () => ({
      ok: false,
      status: 400,
      text: async () => JSON.stringify({ error: 'bad request' }),
    }))
    await expect(streamArticleDraft({ workflowUrl: '/run', apiKey: 'k' }, { topic: '主题' })).rejects.toThrow('bad request')

    global.fetch = vi.fn(async () => ({
      ok: false,
      status: 400,
      text: async () => '',
    }))
    await expect(streamArticleDraft({ workflowUrl: '/run', apiKey: 'k' }, { topic: '主题' })).rejects.toThrow('Dify 请求失败: HTTP 400')
  })

  it('ignores invalid sse blocks and handles workflow errors', async () => {
    const encoder = new TextEncoder()
    global.fetch = vi.fn(async () => ({
      ok: true,
      body: {
        getReader() {
          let sent = false
          return {
            async read() {
              if (!sent) {
                sent = true
                return {
                  done: false,
                  value: encoder.encode('data: not-json\n\ndata: {"event":"error","message":"生成失败"}\n\n'),
                }
              }
              return { done: true, value: undefined }
            },
          }
        },
      },
    }))
    await expect(streamArticleDraft({ workflowUrl: '/run', apiKey: 'k' }, { topic: '主题' })).rejects.toThrow('生成失败')
  })
})
