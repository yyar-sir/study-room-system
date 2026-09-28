<template>
  <div class="user-layout">
    <header class="topbar">
      <div class="topbar-inner">
        <RouterLink to="/user/home" class="logo" aria-label="返回首页">
          <BrandMark :size="26" />
          <span class="logo-text">静学共享自习室</span>
        </RouterLink>

        <nav class="menus" aria-label="主导航">
          <RouterLink
            v-for="m in menus"
            :key="m.path"
            :to="m.path"
            class="menu-item"
            :class="{ active: isActive(m.path) }"
            :aria-current="isActive(m.path) ? 'page' : undefined"
          >{{ m.title }}</RouterLink>
        </nav>

        <div class="right-area">
          <span class="user-name">{{ displayName }}</span>
          <ThemeToggle />
          <button type="button" class="user-chip" @click="logout">退出</button>
        </div>
      </div>
    </header>

    <main class="content k-page">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { clearAuth } from '@/api/request'
import ThemeToggle from '@/components/ThemeToggle.vue'
import BrandMark from '@/components/BrandMark.vue'

const router = useRouter()

/* 用户端用顶部导航：横向菜单、面向到店用户更直观。
   新增页面时在这里加一条，并同步 router/index.js */
const menus = [
  { title: '首页', path: '/user/home' },
  { title: '在线选座', path: '/user/reserve' },
  { title: '我的预约', path: '/user/reservations' },
  { title: '会员套餐', path: '/user/plans' },
  { title: 'AI 助手', path: '/user/ai' },
  { title: '消息中心', path: '/user/messages' },
  { title: '个人中心', path: '/user/profile' }
]

const displayName = computed(() => {
  try {
    const info = JSON.parse(localStorage.getItem('userInfo') || '{}')
    return info.nickname || info.realName || info.phone || ''
  } catch (e) {
    return ''
  }
})

function isActive(path) {
  const cur = router.currentRoute.value.path
  return path === '/user/home' ? cur === path : cur.startsWith(path)
}

function logout() {
  clearAuth()
  router.push('/login')
}
</script>

<style scoped>
.user-layout { min-height: 100vh; display: flex; flex-direction: column; }
.topbar { position: sticky; top: 0; z-index: 100; background: var(--topbar-bg); backdrop-filter: blur(12px); border-bottom: 1px solid var(--line); }
.topbar-inner { max-width: 1240px; margin: 0 auto; padding: 0 20px; height: 66px; display: flex; align-items: center; gap: 12px; }
.logo { display: flex; align-items: center; gap: 8px; cursor: pointer; flex-shrink: 0; text-decoration: none; }
.logo-text { font-family: var(--font-title); font-size: 19px; color: var(--brand-deep); }
.menus { display: flex; gap: 4px; flex: 1; justify-content: center; flex-wrap: wrap; }
.menu-item { padding: 8px 12px; border-radius: var(--radius-md); font-size: var(--fs-sm); font-weight: 600; white-space: nowrap; color: var(--ink-2); text-decoration: none; transition: color .18s, background .18s; }
.menu-item:hover { color: var(--brand-ink); background: var(--brand-soft); }
.menu-item.active { color: #fff; background: var(--brand); }
.right-area { flex-shrink: 0; display: flex; align-items: center; gap: 10px; }
.user-name { font-size: var(--fs-sm); color: var(--ink-2); font-weight: 600; }
.user-chip {
  padding: 6px 14px; border-radius: var(--radius-pill); background: var(--surface-soft);
  font-size: var(--fs-sm); font-weight: 600; cursor: pointer;
  border: 0; font-family: inherit; color: inherit;
}
.user-chip:hover { color: var(--brand-ink); }
.content { flex: 1; }

/* 窄屏：topbar 由固定 66px 单行改为可换行的两行——
   第一行 品牌 + 退出，第二行菜单横向滚动。
   原实现下 7 个菜单项在 66px 固定高度里 flex-wrap 换行，会溢出被裁掉。 */
@media (max-width: 900px) {
  .topbar-inner { height: auto; flex-wrap: wrap; padding: 8px 12px; row-gap: 8px; }
  .logo-text { display: none; }
  .menus {
    order: 3;
    flex-basis: 100%;
    justify-content: flex-start;
    flex-wrap: nowrap;
    overflow-x: auto;
    scrollbar-width: thin;
  }
  .user-name { display: none; }
}
</style>
