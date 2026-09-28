import request from '@/api/request'

/**
 * AI 智能自习助手。
 *
 * 超时刻意设到 60s（普通接口是 30s）：生成类请求本身较慢，
 * 若沿用默认超时，前端会在模型还没返回时就先超时，看起来像"服务挂了"。
 * 后端另有限流，问得太快会返回业务码 6002。
 */
export const ask = (question, sessionId) =>
  request.post('/user/ai/ask', { question, sessionId }, { timeout: 60000 })

/**
 * 拉取当前会话的历史消息。
 *
 * 命名为 aiHistory 而不是 history：后者会与浏览器全局的 window.history
 * 在阅读时混淆，import 时也必须额外起别名。
 * 返回的是消息列表（role = 'user' | 'assistant'），按时间正序。
 */
export const aiHistory = (sessionId) =>
  request.get('/user/ai/history', { params: sessionId ? { sessionId } : {} })
