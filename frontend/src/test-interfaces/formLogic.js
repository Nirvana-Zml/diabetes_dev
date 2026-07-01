export function validateResetAccount(type, value) {
  if (!value) return new Error('请输入账号')
  if (type === 'email' && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
    return new Error('请输入正确的邮箱')
  }
  if (type === 'phone' && !/^1\d{10}$/.test(value)) {
    return new Error('请输入正确的手机号')
  }
  return undefined
}

export function validateConfirmPassword(password, confirm) {
  if (!confirm) return new Error('请再次输入密码')
  if (password !== confirm) return new Error('两次输入密码不一致')
  return undefined
}

export function resetVerifyForm(form) {
  form.account = ''
  form.code = ''
}

export function buildVerifyCodePayload(account, type, purpose) {
  return {
    account,
    type,
    purpose,
  }
}

export function buildResetPasswordPayload(form) {
  return {
    account: form.account.trim(),
    code: form.code,
    new_password: form.newPassword,
  }
}

export function validateEmail(value) {
  if (!value) return new Error('请输入邮箱')
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) return new Error('请输入正确的邮箱')
  return undefined
}

export function buildChangePasswordPayload(form) {
  return {
    old_password: form.old_password,
    new_password: form.new_password,
  }
}

export function buildBindPhonePayload(form) {
  return {
    phone: form.phone,
    verify_code: form.code,
  }
}

export function buildBindEmailPayload(form) {
  return {
    email: form.email,
    verify_code: form.code,
  }
}

export function shouldSendPhoneCode(phone) {
  return /^1\d{10}$/.test(phone || '')
}

export function createChatState() {
  return {
    query: '',
    messages: [],
    streaming: false,
    conversationId: '',
  }
}

export async function sendChatMessage(state, chatQA) {
  const question = state.query.trim()
  if (!question || state.streaming) return false

  state.messages.push({ role: 'user', content: question })
  state.query = ''
  state.streaming = true
  const assistant = { role: 'assistant', content: '' }
  state.messages.push(assistant)

  try {
    await chatQA(question, {
      conversationId: state.conversationId,
      onChunk: (chunk) => {
        assistant.content += typeof chunk === 'string' ? chunk : chunk.content || ''
      },
      onEnd: (event = {}) => {
        state.conversationId = event.conversation_id || state.conversationId
      },
    })
    return true
  } finally {
    state.streaming = false
  }
}

export function closeChat(state) {
  state.query = ''
}
