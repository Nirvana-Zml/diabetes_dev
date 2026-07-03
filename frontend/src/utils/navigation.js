import router from '@/router'
import { isAuthPath } from '@/utils/auth'

/** 返回上一页；若上一页是登录/注册等鉴权页，则回到 fallback（默认首页） */
export function safeBack(fallback = '/home') {
  const back = window.history.state?.back
  if (isAuthPath(back)) {
    router.replace(fallback)
    return
  }
  if (window.history.length > 1) {
    router.back()
    return
  }
  router.replace(fallback)
}
