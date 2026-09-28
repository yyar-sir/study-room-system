import request from '@/api/request'

/** 预约与签到 */

export const createReservation = (data) => request.post('/user/reservations', data)

/** 我的预约；status 为空表示全部（0待使用 1使用中 2已完成 3已取消 4爽约） */
export const myReservations = (status) =>
  request.get('/user/reservations', { params: status === null || status === undefined ? {} : { status } })

export const reservationDetail = (id) => request.get(`/user/reservations/${id}`)

export const cancelReservation = (id, reason) =>
  request.post(`/user/reservations/${id}/cancel`, { reason })

/** 用户自助扫码签到（传二维码里的签到码） */
export const checkin = (checkinCode) => request.post('/user/checkin', { checkinCode })

export const checkout = (reservationId) => request.post(`/user/checkout/${reservationId}`)
