/** 无需登录即可访问的路由前缀 */
export const PUBLIC_PATHS = [
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
