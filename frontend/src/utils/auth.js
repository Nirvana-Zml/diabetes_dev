/** 无需登录即可访问的路由前缀 */
export const PUBLIC_PATHS = [
  '/',
  '/home',
  '/health-info',
  '/assistant',
  '/login',
  '/register',
  '/forgot-password',
]

export function isLoggedIn() {
  return !!localStorage.getItem('access_token')
}

export function saveUserRole(role) {
  if (role) localStorage.setItem('user_role', role)
  else localStorage.removeItem('user_role')
}

export function isPublicRoute(to) {
  if (to.meta?.public) return true
  return PUBLIC_PATHS.some((p) => to.path === p || to.path.startsWith(`${p}/`))
}

/** 未登录时跳转登录页，并带上登录后回跳地址 */
export function redirectToLogin(redirectPath) {
  const query = redirectPath ? { redirect: redirectPath } : undefined
  return { path: '/login', query }
}

export const LOGIN_REQUIRED_MESSAGE = '请先登录或注册'

const AUTH_PATHS = ['/login', '/register', '/forgot-password']

/** 登录成功后回跳地址（仅允许站内相对路径） */
export function resolvePostLoginRedirect(raw) {
  if (typeof raw !== 'string' || !raw.startsWith('/') || raw.startsWith('//')) {
    return '/home'
  }
  return raw
}

export function isAuthPath(path) {
  if (!path) return false
  const base = String(path).split('?')[0]
  return AUTH_PATHS.includes(base)
}
