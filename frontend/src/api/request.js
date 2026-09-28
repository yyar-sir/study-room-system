/* ============================================================
   Axios 统一实例：baseURL /api，自动携带 JWT，统一错误提示
   后端约定：{ code, msg, data }，code=0 成功
   ============================================================ */
import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

/* ------------------------------------------------------------
   登录失效提示去重
   一次页面渲染可能并发多个受保护请求，全部 401 时会各弹一条
   「请先登录」，在屏幕上堆成一摞。这里做 3 秒窗口去重：
   同一波失效只提示一次，用户看到的是干净的一条。
   ------------------------------------------------------------ */
let authNoticeAt = 0
function notifyAuthRequired(msg) {
  const now = Date.now()
  if (now - authNoticeAt < 3000) return
  authNoticeAt = now
  ElMessage.warning(msg)
}

/** 清除登录态（token + 用户信息），退出登录与 401 都走这里 */
export function clearAuth() {
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
}

/** 跳登录页；已经在登录页就不再重复跳转 */
function toLogin() {
  if (router.currentRoute.value.path !== '/login') {
    router.push('/login')
  }
}

// 请求拦截：注入 token
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：统一处理业务码
request.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body.code !== 'undefined') {
      if (body.code === 0) return body.data
      if (body.code === 401) {
        notifyAuthRequired(body.msg || '登录已过期，请重新登录')
        clearAuth()
        toLogin()
        return Promise.reject(new Error(body.msg))
      }
      if (body.code === 403) {
        // 已登录但无权限：仅提示，不清除登录态（避免被误踢出登录）
        ElMessage.error(body.msg || '无权限访问')
        return Promise.reject(new Error(body.msg))
      }
      ElMessage.error(body.msg || '请求失败')
      return Promise.reject(new Error(body.msg))
    }
    return body
  },
  (err) => {
    const status = err.response && err.response.status
    // 后端在 401/403/404/405 时同样返回统一的 { code, msg } 结构，
    // 优先用它给出的具体文案，避免全部被笼统的「网络异常」盖掉。
    const body = err.response && err.response.data
    const serverMsg = body && body.msg

    if (status === 401) {
      notifyAuthRequired(serverMsg || '请先登录')
      clearAuth()
      toLogin()
    } else if (status === 403) {
      // 已登录但无权限：仅提示，不清除登录态（避免把已登录用户误踢出登录）
      ElMessage.error(serverMsg || '无权限访问')
    } else if (status === 404) {
      ElMessage.error(serverMsg || '接口不存在')
    } else if (status === 405) {
      ElMessage.error(serverMsg || '请求方法不支持')
    } else {
      ElMessage.error(serverMsg || '网络异常，请稍后再试')
    }
    return Promise.reject(err)
  }
)

export default request
