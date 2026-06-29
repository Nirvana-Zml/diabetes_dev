export function isLoggedIn() {
  return !!localStorage.getItem('access_token')
}

export function getUserRole() {
  return localStorage.getItem('user_role') || ''
}

export function isAdmin() {
  return getUserRole() === 'admin'
}

export function saveUserRole(role) {
  if (role) localStorage.setItem('user_role', role)
  else localStorage.removeItem('user_role')
}

export function redirectToLogin(redirectPath) {
  const query = redirectPath ? { redirect: redirectPath } : undefined
  return { path: '/login', query }
}
