<template>
  <div class="side-layout">
    <aside class="sidebar">
      <div class="brand">
        <BrandMark :size="24" />
        <span class="text">{{ brandText }}</span>
      </div>

      <nav class="nav" :aria-label="role === 'owner' ? '经营管理导航' : '前台工作导航'">
        <RouterLink
          v-for="m in menus"
          :key="m.path"
          :to="m.path"
          class="nav-item"
          :class="{ active: isActive(m.path) }"
          :aria-current="isActive(m.path) ? 'page' : undefined"
        >{{ m.title }}</RouterLink>
      </nav>

      <div class="sidebar-foot">
        <div class="who">{{ displayName }}</div>
        <div class="who-role">{{ roleLabel }}</div>
      </div>
    </aside>

    <div class="main">
      <header class="topbar">
        <div class="spacer" />
        <ThemeToggle />
        <button type="button" class="user-chip" @click="logout">退出登录</button>
      </header>

      <main class="content k-page">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { clearAuth } from '@/api/request'
import ThemeToggle from '@/components/ThemeToggle.vue'
import BrandMark from '@/components/BrandMark.vue'

const props = defineProps({
  /** 'staff'（前台管理员） | 'owner'（自习室经营者），由 router 的 props 传入 */
  role: { type: String, default: 'staff' }
})

const router = useRouter()

/* ⚠️ 前台端与经营端共用同一套布局，菜单按 role 区分。
   踩坑记录：早期管理端复用了教师端布局却仍去请求 /api/notify/**，
   管理员角色未放行 → 403 → 被前端当成登录失效踢回登录页。
   因此：布局复用没问题，但「布局里发起的请求」必须按角色收敛。 */
const MENUS = {
  staff: [
    { title: '工作台', path: '/staff/workbench' },
    { title: '座位状态管理', path: '/staff/seats' },
    { title: '预约与签到协助', path: '/staff/checkin' },
    { title: '会员业务办理', path: '/staff/members' },
    { title: '违规处理', path: '/staff/violations' },
    { title: '记录查询', path: '/staff/records' },
    { title: '公告发布', path: '/staff/announcements' }
  ],
  owner: [
    { title: '数据看板', path: '/owner/dashboard' },
    { title: '门店与座位', path: '/owner/stores' },
    { title: '套餐配置', path: '/owner/plans' },
    { title: '运营规则配置', path: '/owner/settings' },
    { title: '人员权限管理', path: '/owner/staff' },
    { title: '数据备份', path: '/owner/backup' },
    { title: '操作日志', path: '/owner/logs' }
  ]
}

const ROLE_LABEL = { staff: '前台管理员', owner: '自习室经营者' }

const menus = computed(() => MENUS[props.role] || [])
const brandText = computed(() => (props.role === 'owner' ? '经营管理后台' : '前台工作台'))
const roleLabel = computed(() => ROLE_LABEL[props.role] || '')

const displayName = computed(() => {
  try {
    const info = JSON.parse(localStorage.getItem('userInfo') || '{}')
    return info.nickname || info.realName || info.phone || ''
  } catch (e) {
    return ''
  }
})

function isActive(path) {
  return router.currentRoute.value.path.startsWith(path)
}

function logout() {
  clearAuth()
  router.push('/login')
}
</script>

<style scoped>
.side-layout { min-height: 100vh; display: grid; grid-template-columns: 220px 1fr; }
.sidebar { background: var(--navy); color: #fff; padding: 20px 14px; display: flex; flex-direction: column; gap: 18px; }
.brand { display: flex; align-items: center; gap: 8px; padding: 0 8px; }
.brand .text { font-family: var(--font-title); font-size: var(--fs-h3); }
.nav { display: flex; flex-direction: column; gap: 4px; flex: 1; }
.nav-item { padding: 10px 14px; border-radius: var(--radius-md); font-size: var(--fs-sm); font-weight: 600; color: rgba(255, 255, 255, 0.74); text-decoration: none; transition: background .18s, color .18s; }
.nav-item:hover { background: rgba(255, 255, 255, 0.1); color: #fff; }
.nav-item.active { background: var(--brand); color: #fff; }
.sidebar-foot { padding: 12px 10px 0; border-top: 1px solid rgba(255, 255, 255, 0.12); }
.sidebar-foot .who { font-size: var(--fs-sm); font-weight: 700; }
.sidebar-foot .who-role { font-size: var(--fs-xs); color: rgba(255, 255, 255, 0.6); margin-top: 2px; }
.main { display: flex; flex-direction: column; min-width: 0; }
.topbar { height: 62px; display: flex; align-items: center; gap: 14px; padding: 0 24px; background: var(--card); border-bottom: 1px solid var(--line); }
.spacer { flex: 1; }
.user-chip {
  padding: 6px 14px; border-radius: var(--radius-pill); background: var(--surface-soft);
  font-size: var(--fs-sm); font-weight: 600; cursor: pointer;
  border: 0; font-family: inherit; color: inherit;
}
.user-chip:hover { color: var(--brand-ink); }
.content { flex: 1; }

/* 窄屏：侧边栏改为顶部横向导航条。
   原实现是 .side-layout 收成 64px 窄条 + 给导航项设 font-size: 0 把文字"藏掉"，
   但导航项本身没有图标，结果是 7 个空白方块，移动端完全无法导航。
   横排后每个菜单项文字都保留，超出部分横向滚动。 */
@media (max-width: 900px) {
  .side-layout { grid-template-columns: 1fr; }
  .sidebar {
    flex-direction: row;
    align-items: center;
    gap: 10px;
    padding: 8px 12px;
    overflow-x: auto;
    scrollbar-width: thin;
  }
  .brand { padding: 0; }
  /* 品牌名在窄屏让位给菜单，保留 📚 作为标识 */
  .brand .text { display: none; }
  .nav { flex-direction: row; flex: 1; gap: 6px; }
  .nav-item { white-space: nowrap; padding: 8px 12px; }
  /* 用户名/角色在横条里放不下，退出入口已在右侧 topbar */
  .sidebar-foot { display: none; }
}
</style>
