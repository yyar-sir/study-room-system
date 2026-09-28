import request from '@/api/request'

/**
 * 门店与座位。
 *
 * 备注：门店列表走公开接口（未登录也能看），座位实时占用需要登录 ——
 * 座位占用属于经营数据，不对匿名者开放。
 */
export const listStores = () => request.get('/public/stores')

/** 座位图。date/startTime/endTime 三者齐给时才会计算时段占用 */
export const seatMap = (params) => request.get('/seats', { params })

/** 套餐列表（公开） */
export const listPlans = () => request.get('/public/plans')

/**
 * 预约规则（公开）：最多提前几天、单次最长几小时、开始前多久可取消等。
 * 这些值由经营者在后台配置，前端只做展示与预校验，不硬编码 ——
 * 后端仍会二次校验，前端的预校验只是为了少一次无效请求。
 */
export const publicRules = (storeId) =>
  request.get('/public/rules', { params: storeId ? { storeId } : {} })
