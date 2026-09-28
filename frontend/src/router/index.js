/* ============================================================
   路由骨架：三端分离（/user 自习室用户 · /staff 前台管理员 · /owner 经营者）
   ============================================================ */
import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', name: 'Login', component: () => import('@/views/Login.vue'), meta: { title: '登录' } },
  { path: '/', redirect: '/user/home' },

  // ---------- 自习室用户：顶部导航 ----------
  {
    path: '/user',
    component: () => import('@/views/user/UserLayout.vue'),
    children: [
      { path: 'home', name: 'UserHome', component: () => import('@/views/user/Home.vue'), meta: { title: '首页' } },
      { path: 'reserve', name: 'UserReserve', component: () => import('@/views/user/Reserve.vue'), meta: { title: '在线选座' } },
      { path: 'reservations', name: 'UserReservations', component: () => import('@/views/user/MyReservations.vue'), meta: { title: '我的预约' } },
      { path: 'plans', name: 'UserPlans', component: () => import('@/views/user/Plans.vue'), meta: { title: '会员套餐' } },
      { path: 'ai', name: 'UserAi', component: () => import('@/views/user/AiAssistant.vue'), meta: { title: 'AI 助手' } },
      { path: 'messages', name: 'UserMessages', component: () => import('@/views/user/Notification.vue'), meta: { title: '消息中心' } },
      { path: 'profile', name: 'UserProfile', component: () => import('@/views/user/Profile.vue'), meta: { title: '个人中心' } }
    ]
  },

  // ---------- 前台管理员：侧栏布局 ----------
  {
    path: '/staff',
    component: () => import('@/views/shared/SidebarLayout.vue'),
    props: { role: 'staff' },
    redirect: '/staff/workbench',
    children: [
      { path: 'workbench', name: 'StaffWorkbench', component: () => import('@/views/staff/Workbench.vue'), meta: { title: '工作台' } },
      { path: 'seats', name: 'StaffSeats', component: () => import('@/views/staff/SeatManage.vue'), meta: { title: '座位状态管理' } },
      { path: 'checkin', name: 'StaffCheckin', component: () => import('@/views/staff/CheckinAssist.vue'), meta: { title: '预约与签到协助' } },
      { path: 'violations', name: 'StaffViolations', component: () => import('@/views/staff/Violations.vue'), meta: { title: '违规处理' } },
      { path: 'members', name: 'StaffMembers', component: () => import('@/views/staff/Members.vue'), meta: { title: '会员业务办理' } },
      { path: 'records', name: 'StaffRecords', component: () => import('@/views/staff/Records.vue'), meta: { title: '记录查询' } },
      { path: 'announcements', name: 'StaffAnnouncements', component: () => import('@/views/staff/Announcements.vue'), meta: { title: '公告发布' } }
    ]
  },

  // ---------- 自习室经营者：同一套侧栏布局，靠 props 区分菜单 ----------
  {
    path: '/owner',
    component: () => import('@/views/shared/SidebarLayout.vue'),
    props: { role: 'owner' },
    redirect: '/owner/dashboard',
    children: [
      { path: 'dashboard', name: 'OwnerDashboard', component: () => import('@/views/owner/Dashboard.vue'), meta: { title: '数据看板' } },
      { path: 'settings', name: 'OwnerSettings', component: () => import('@/views/owner/Settings.vue'), meta: { title: '运营规则配置' } },
      { path: 'stores', name: 'OwnerStores', component: () => import('@/views/owner/Stores.vue'), meta: { title: '门店与座位' } },
      { path: 'plans', name: 'OwnerPlans', component: () => import('@/views/owner/Plans.vue'), meta: { title: '套餐配置' } },
      { path: 'staff', name: 'OwnerStaff', component: () => import('@/views/owner/Staff.vue'), meta: { title: '人员权限管理' } },
      { path: 'backup', name: 'OwnerBackup', component: () => import('@/views/owner/Backup.vue'), meta: { title: '数据备份' } },
      { path: 'logs', name: 'OwnerLogs', component: () => import('@/views/owner/Logs.vue'), meta: { title: '操作日志' } }
    ]
  },

  { path: '/:pathMatch(.*)*', redirect: '/login' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/* ============================================================
   登录态与角色守卫
   ------------------------------------------------------------
   为什么必须有这一段：守卫若不拦，未登录访问 /user/home 时
   页面会先挂载，组件各自打接口，多个请求全部 401，
   每个都弹一次「请先登录」→ 登录页堆成一摞提示。

   ⚠️ 这只是前端体验层拦截，真正的权限校验在后端
      AuthInterceptor + @RequireRole，前端守卫不能替代后端。
   ============================================================ */
const ROLE_HOME = { 0: '/owner/dashboard', 1: '/staff/workbench', 2: '/user/home' }
const ROLE_PREFIX = { 0: '/owner', 1: '/staff', 2: '/user' }
const GUARDED_PREFIXES = ['/owner', '/staff', '/user']

/** 当前登录角色；无 token 返回 null，token 在但信息损坏时按用户处理 */
function currentRole() {
  if (!localStorage.getItem('token')) return null
  try {
    // userInfo 存的是后端 LoginVO 的【扁平】对象，role 在顶层
    const role = Number(JSON.parse(localStorage.getItem('userInfo') || '{}').role)
    return Number.isInteger(role) && role >= 0 && role <= 2 ? role : 2
  } catch (e) {
    return 2
  }
}

router.beforeEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 静学共享自习室` : '静学共享自习室'

  const role = currentRole()

  // ① 未登录：只放行登录页，其余转登录页并记录回跳地址
  if (role === null) {
    if (to.path === '/login') return true
    const query = to.fullPath && to.fullPath !== '/' ? { redirect: to.fullPath } : {}
    return { path: '/login', query }
  }

  // ② 已登录还去登录页（含退出后手滑回退）→ 回自己角色的首页
  if (to.path === '/login') {
    return ROLE_HOME[role] || '/user/home'
  }

  // ③ 角色越权（如用户直接敲 /owner/dashboard）→ 送回自己的首页
  const allowed = ROLE_PREFIX[role]
  if (GUARDED_PREFIXES.some((p) => to.path.startsWith(p)) && !to.path.startsWith(allowed)) {
    return ROLE_HOME[role] || '/user/home'
  }

  return true
})

export default router
