import request from '@/api/request'

/** 认证与账号 */
export const login = (data) => request.post('/auth/login', data)
export const register = (data) => request.post('/auth/register', data)
export const me = () => request.get('/user/me')
export const changePassword = (data) => request.post('/user/change-password', data)

/** 个人资料维护（手机号/角色不允许自行修改） */
export const updateProfile = (data) => request.put('/user/profile', data)

/**
 * 忘记密码。
 * 注意 sendResetCode 在演示环境下会把验证码直接返回，方便演示；
 * 生产环境应改为短信下发且不回传。
 */
export const sendResetCode = (phone) =>
  request.post('/auth/forgot-password/send-code', { phone })

export const resetPassword = (data) => request.post('/auth/forgot-password/reset', data)
