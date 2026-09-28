import request from '@/api/request'

/** 会员套餐、权益与消费流水 */

export const myMemberships = () => request.get('/user/memberships')

/** 购买 / 续费；同一套餐未过期时会自动顺延有效期并累加次数 */
export const purchase = (planId, payType = 1) =>
  request.post('/user/memberships/purchase', { planId, payType })

/** 我的消费与权益流水（账本明细，扣费可追溯） */
export const consumptionRecords = () => request.get('/user/consumption-records')
