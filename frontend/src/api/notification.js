import request from '@/api/request'

/** 站内消息 */
export const listNotifications = (type) =>
  request.get('/notifications', { params: type ? { type } : {} })

/** 未读数（顶栏红点轮询用） */
export const unreadCount = () => request.get('/notifications/unread-count')

export const markRead = (id) => request.post(`/notifications/${id}/read`)

export const markAllRead = () => request.post('/notifications/read-all')
