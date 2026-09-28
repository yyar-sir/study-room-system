import request from '@/api/request'

/**
 * 经营端与前台端接口。
 *
 * 权限边界（后端强制，前端只是配合）：
 *   /api/owner/**  仅经营者 role=0
 *   /api/staff/**  前台 role=1 与经营者 role=0
 * 前台的查询会被后端按 storeId 自动限定为本门店，前端不需要也不应该自己拼 storeId。
 */

// ---------- 经营统计（ECharts 数据源） ----------
export const statsOverview = (storeId) =>
  request.get('/owner/stats/overview', { params: storeId ? { storeId } : {} })

export const hourlyTraffic = (storeId, date) =>
  request.get('/owner/stats/hourly-traffic', { params: { ...(storeId ? { storeId } : {}), ...(date ? { date } : {}) } })

export const reservationTrend = (storeId, days = 7) =>
  request.get('/owner/stats/reservation-trend', { params: { ...(storeId ? { storeId } : {}), days } })

export const revenueTrend = (storeId, days = 7) =>
  request.get('/owner/stats/revenue-trend', { params: { ...(storeId ? { storeId } : {}), days } })

export const memberGrowth = (storeId, days = 7) =>
  request.get('/owner/stats/member-growth', { params: { ...(storeId ? { storeId } : {}), days } })

export const planDistribution = (storeId) =>
  request.get('/owner/stats/plan-distribution', { params: storeId ? { storeId } : {} })

export const seatTypeUsage = (storeId) =>
  request.get('/owner/stats/seat-type-usage', { params: storeId ? { storeId } : {} })

// ---------- 门店 ----------
export const ownerStores = () => request.get('/owner/stores')

// ---------- 前台日常操作 ----------
export const staffSeats = (storeId) =>
  request.get('/staff/seats', { params: storeId ? { storeId } : {} })

export const updateSeatStatus = (id, status, remark) =>
  request.post(`/staff/seats/${id}/status`, { status, remark })

export const staffViolations = (status) =>
  request.get('/staff/violations', { params: status === null || status === undefined ? {} : { status } })

export const handleViolation = (id, handleRemark, penalty) =>
  request.post(`/staff/violations/${id}/handle`, { handleRemark, penalty })

/** 今日到店情况（本门店当天全部预约） */
export const staffTodayReservations = (storeId) =>
  request.get('/staff/reservations/today', { params: storeId ? { storeId } : {} })

/** 按手机号查该用户「使用中」的预约，用于协助签退 */
export const staffActiveByPhone = (phone) =>
  request.get('/staff/reservations/active', { params: { phone } })

/** 前台协助签到（代到店用户操作） */
export const staffCheckin = (checkinCode) => request.post('/staff/checkin', { checkinCode })

export const staffCheckout = (reservationId) => request.post(`/staff/checkout/${reservationId}`)

/**
 * 前台代用户取消预约（处理异常订单）。
 *
 * 与用户自助取消的差异：不受「距开始 N 分钟」限制（前台处理的正是
 * 用户在最后时刻来不了这类异常），但仍受门店数据范围约束，且必须填原因。
 */
export const staffCancelReservation = (reservationId, reason) =>
  request.post(`/staff/reservations/${reservationId}/cancel`, { reason })

// ---------- 经营者运维 ----------
export const aiConfig = () => request.get('/owner/ai/config')
export const runNoShowCheck = () => request.post('/owner/ops/run-noshow-check')

// ---------- 运营规则与系统配置 ----------
export const ownerConfigs = (storeId, group) =>
  request.get('/owner/configs', {
    params: { ...(storeId ? { storeId } : {}), ...(group ? { group } : {}) }
  })

export const ownerSaveConfig = (data) => request.post('/owner/configs', data)

// ---------- 经营者·基础资源（门店 / 座位 / 套餐） ----------
export const ownerCreateStore = (data) => request.post('/owner/stores', data)
export const ownerUpdateStore = (data) => request.put('/owner/stores', data)

export const ownerSeatTypes = () => request.get('/owner/seat-types')
export const ownerSaveSeatType = (data) => request.post('/owner/seat-types', data)

export const ownerSeats = (storeId) => request.get('/owner/seats', { params: { storeId } })
export const ownerCreateSeat = (data) => request.post('/owner/seats', data)
export const ownerUpdateSeat = (data) => request.put('/owner/seats', data)

/** 全部套餐（含已下架） */
export const ownerPlansAll = () => request.get('/owner/plans')
export const ownerSavePlan = (data) => request.post('/owner/plans', data)

// ---------- 前台·会员业务办理 ----------
export const memberByPhone = (phone) =>
  request.get('/staff/members/by-phone', { params: { phone } })

export const grantMembership = (data) => request.post('/staff/members/grant', data)

// ---------- 公告 ----------
export const publicAnnouncements = (storeId) =>
  request.get('/public/announcements', { params: storeId ? { storeId } : {} })

export const staffAnnouncements = (includeOffline = true) =>
  request.get('/staff/announcements', { params: { includeOffline } })

export const saveAnnouncement = (data) => request.post('/staff/announcements', data)
export const offlineAnnouncement = (id) => request.post(`/staff/announcements/${id}/offline`)

// ---------- 前台·记录查询 ----------
export const recordReservations = (date) =>
  request.get('/staff/records/reservations', { params: date ? { date } : {} })

export const recordCheckins = (date) =>
  request.get('/staff/records/checkins', { params: date ? { date } : {} })

export const recordConsumptions = (date) =>
  request.get('/staff/records/consumptions', { params: date ? { date } : {} })

// ---------- 经营者·人员权限管理 ----------
export const permissionCatalog = () => request.get('/owner/staff/permission-catalog')
export const ownerStaffList = () => request.get('/owner/staff')
export const ownerCreateStaff = (data) => request.post('/owner/staff', data)
export const ownerSetStaffPermissions = (id, permissions) =>
  request.post(`/owner/staff/${id}/permissions`, { permissions })
export const ownerSetStaffStatus = (id, status) =>
  request.post(`/owner/staff/${id}/status`, { status })
export const ownerSetStaffStore = (id, storeId) =>
  request.post(`/owner/staff/${id}/store`, { storeId })

// ---------- 经营者·数据备份 ----------
export const runBackup = () => request.post('/owner/backup')
export const backupRecords = () => request.get('/owner/backup')

// ---------- 经营者·营业时段 ----------
// 门店营业时间（store.openTime/closeTime）是「一天从几点开到几点」的总边界；
// 这里是边界内部的分段，可把午休之类的区间标成 bookable=0（不可预约）。
// 未配置任何时段时后端直接回落到总边界，所以本配置是可选的精细化项。
export const ownerBusinessHours = (storeId, includeDisabled = true) =>
  request.get('/owner/business-hours', { params: { storeId, includeDisabled } })
export const ownerSaveBusinessHour = (data) => request.post('/owner/business-hours', data)
export const ownerRemoveBusinessHour = (id) => request.post(`/owner/business-hours/${id}/remove`)

// ---------- 经营者·操作日志 ----------
// 由后端 AOP 切面自动写入（只记写操作，口令类字段已脱敏）
export const ownerOperationLogs = (params = {}) =>
  request.get('/owner/operation-logs', { params })
