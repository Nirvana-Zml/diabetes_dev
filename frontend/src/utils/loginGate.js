import { ElMessage } from 'element-plus'
import router from '@/router'
import { isLoggedIn, redirectToLogin, LOGIN_REQUIRED_MESSAGE } from '@/utils/auth'

/**
 * 未登录时提示并可选跳转登录页；已登录返回 true。
 * @param {string|import('vue-router').RouteLocationRaw} [redirectPath]
 */
export function requireLogin(redirectPath, { navigate = true } = {}) {
  if (isLoggedIn()) return true
  ElMessage.warning(LOGIN_REQUIRED_MESSAGE)
  if (navigate) {
    const path = typeof redirectPath === 'string'
      ? redirectPath
      : redirectPath
        ? router.resolve(redirectPath).fullPath
        : undefined
    router.push(redirectToLogin(path))
  }
  return false
}
