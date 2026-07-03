import { describe, expect, it, vi } from 'vitest'
import {
  buildBindEmailPayload,
  buildBindPhonePayload,
  buildChangePasswordPayload,
  buildResetPasswordPayload,
  buildVerifyCodePayload,
  closeChat,
  createChatState,
  resetVerifyForm,
  sendChatMessage,
  shouldSendPhoneCode,
  validateConfirmPassword,
  validateEmail,
  validateResetAccount,
} from '../formLogic'

describe('form logic test interfaces', () => {
  it('validates reset password account and password confirmation inputs', () => {
    expect(validateResetAccount('email', '')).toBeInstanceOf(Error)
    expect(validateResetAccount('email', 'bad-email')).toBeInstanceOf(Error)
    expect(validateResetAccount('email', 'ok@example.com')).toBeUndefined()
    expect(validateResetAccount('phone', '123')).toBeInstanceOf(Error)
    expect(validateResetAccount('phone', '13800000000')).toBeUndefined()

    expect(validateConfirmPassword('abcdef', '')).toBeInstanceOf(Error)
    expect(validateConfirmPassword('abcdef', '123456')).toBeInstanceOf(Error)
    expect(validateConfirmPassword('abcdef', 'abcdef')).toBeUndefined()
    expect(validateEmail('bad')).toBeInstanceOf(Error)
    expect(validateEmail('')).toBeInstanceOf(Error)
    expect(validateEmail('new@example.com')).toBeUndefined()
  })

  it('builds auth and security payloads without touching Vue instances', () => {
    const form = { account: ' ok@example.com ', code: '123456', newPassword: 'abcdef' }
    expect(buildVerifyCodePayload('ok@example.com', 'email', 'reset_password')).toEqual({
      account: 'ok@example.com',
      type: 'email',
      purpose: 'reset_password',
    })
    expect(buildResetPasswordPayload(form)).toEqual({
      account: 'ok@example.com',
      code: '123456',
      new_password: 'abcdef',
    })

    resetVerifyForm(form)
    expect(form.account).toBe('')
    expect(form.code).toBe('')

    expect(buildChangePasswordPayload({ old_password: 'old', new_password: 'new' })).toEqual({
      old_password: 'old',
      new_password: 'new',
    })
    expect(buildBindPhonePayload({ phone: '13800000000', code: '111111' })).toEqual({
      phone: '13800000000',
      verify_code: '111111',
    })
    expect(buildBindEmailPayload({ email: 'new@example.com', code: '222222' })).toEqual({
      email: 'new@example.com',
      verify_code: '222222',
    })
    expect(shouldSendPhoneCode('123')).toBe(false)
    expect(shouldSendPhoneCode('13800000000')).toBe(true)
    expect(shouldSendPhoneCode('')).toBe(false)
  })

  it('drives AI chat state through the exported test interface', async () => {
    const chatQA = vi.fn(async (_question, options) => {
      options.onChunk({ content: '回答' })
      options.onEnd({ conversation_id: 'c1' })
    })
    const state = createChatState()

    await expect(sendChatMessage(state, chatQA)).resolves.toBe(false)
    state.query = '血糖怎么控制'
    await expect(sendChatMessage(state, chatQA)).resolves.toBe(true)

    state.streaming = true
    state.query = '重复发送'
    await expect(sendChatMessage(state, chatQA)).resolves.toBe(false)
    state.streaming = false

    const stringChunkChat = vi.fn(async (_question, options) => {
      options.onChunk('直接文本')
      options.onEnd({})
    })
    state.query = '字符串分片'
    await expect(sendChatMessage(state, stringChunkChat)).resolves.toBe(true)
    expect(state.conversationId).toBe('c1')

    const objectChunkChat = vi.fn(async (_question, options) => {
      options.onChunk({})
      options.onEnd({})
    })
    state.query = '对象分片'
    await expect(sendChatMessage(state, objectChunkChat)).resolves.toBe(true)
    expect(state.messages.at(-1).content).toBe('')

    expect(chatQA).toHaveBeenCalled()
    expect(state.messages).toEqual(expect.arrayContaining([
      { role: 'user', content: '血糖怎么控制' },
      { role: 'assistant', content: '回答' },
    ]))
    expect(state.conversationId).toBe('c1')
    expect(state.streaming).toBe(false)

    state.query = '临时内容'
    closeChat(state)
    expect(state.query).toBe('')
  })
})
