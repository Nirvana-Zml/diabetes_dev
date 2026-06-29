import axios from 'axios'
import { API_BASE, API_V2_BASE, USE_MOCK } from '@/config'
import { delay } from '@/utils/delay'
import { clearTokens } from '@/api/auth'
import router from '@/router'

function createHttpClient(baseURL) {
  const client = axios.create({
    baseURL,
    timeout: 30000,
  })

  client.interceptors.request.use((config) => {
    const token = localStorage.getItem('access_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    if (!(config.data instanceof FormData)) {
      config.headers['Content-Type'] = 'application/json'
    }
    return config
  })

  client.interceptors.response.use(
    (res) => {
      const body = res.data
      if (body && typeof body.code === 'number' && body.code !== 200) {
        return Promise.reject(new Error(body.message || '请求失败'))
      }
      return body?.data !== undefined ? body : { code: 200, data: body }
    },
    (err) => {
      if (err.response?.status === 401) {
        clearTokens()
        const current = router.currentRoute.value.fullPath
        if (current !== '/login') {
          router.push({ path: '/login', query: { redirect: current } })
        }
      }
      const msg = err.response?.data?.message || err.message || '网络错误'
      return Promise.reject(new Error(msg))
    },
  )

  return client
}

const http = createHttpClient(API_BASE)
const httpV2 = createHttpClient(API_V2_BASE)

async function invoke(client, method, url, { data, params, mockFn, headers, timeout } = {}) {
  if (USE_MOCK && mockFn) {
    await delay()
    return mockFn()
  }
  const config = { method, url, data, params, headers: headers || {} }
  if (timeout != null) {
    config.timeout = timeout
  }
  const res = await client.request(config)
  return res.data !== undefined ? res.data : res
}

/**
 * 统一请求入口（/api/v1 现有服务）
 */
export async function request(method, url, options = {}) {
  return invoke(http, method, url, options)
}

/**
 * 新服务请求入口（/api/v2）
 */
export async function requestV2(method, url, options = {}) {
  return invoke(httpV2, method, url, options)
}

export function get(url, options) {
  return request('GET', url, options)
}

export function post(url, data, options = {}) {
  return request('POST', url, { ...options, data })
}

export function put(url, data, options = {}) {
  return request('PUT', url, { ...options, data })
}

export function del(url, options = {}) {
  return request('DELETE', url, options)
}

export function getV2(url, options) {
  return requestV2('GET', url, options)
}

export function postV2(url, data, options = {}) {
  return requestV2('POST', url, { ...options, data })
}

export function putV2(url, data, options = {}) {
  return requestV2('PUT', url, { ...options, data })
}

export function delV2(url, options = {}) {
  return requestV2('DELETE', url, options)
}

export default http
export { httpV2 }
