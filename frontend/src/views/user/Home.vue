<template>
  <div class="home">
    <!-- 欢迎区：根据当前状态给一句有用的话，而不是干巴巴的"欢迎" -->
    <section class="welcome k-card">
      <div class="w-left">
        <h1>你好，{{ displayName }}</h1>
        <p class="sub">{{ welcomeText }}</p>
      </div>
      <div class="w-right">
        <el-button v-if="!usableMembership" type="primary" @click="go('/user/plans')">去购买套餐</el-button>
        <el-button v-else type="primary" @click="go('/user/reserve')">立即选座</el-button>
      </div>
    </section>

    <!-- 关键数据 -->
    <section class="kpis">
      <div class="kpi k-card">
        <div class="kpi-label">可用权益</div>
        <div class="kpi-value">
          <template v-if="usableMembership">
            {{ usableMembership.remainTimes === -1 ? '不限次' : usableMembership.remainTimes }}
            <span class="kpi-unit">{{ usableMembership.remainTimes === -1 ? '' : '次' }}</span>
          </template>
          <template v-else><span class="kpi-none">无</span></template>
        </div>
        <div class="kpi-foot">{{ usableMembership ? usableMembership.planName : '尚未购买套餐' }}</div>
      </div>

      <div class="kpi k-card">
        <div class="kpi-label">累计自习</div>
        <div class="kpi-value">{{ studyHours }}<span class="kpi-unit">小时</span></div>
        <div class="kpi-foot">已完成 {{ finishedCount }} 次</div>
      </div>

      <div class="kpi k-card">
        <div class="kpi-label">待使用预约</div>
        <div class="kpi-value">{{ pendingCount }}<span class="kpi-unit">个</span></div>
        <div class="kpi-foot">{{ inUseReservation ? '当前正在自习中' : '含今日与后续预约' }}</div>
      </div>

      <div class="kpi k-card">
        <div class="kpi-label">套餐到期</div>
        <div class="kpi-value small">
          {{ usableMembership ? (usableMembership.expireTime || '').slice(0, 10) : '—' }}
        </div>
        <div class="kpi-foot">
          {{ usableMembership ? `剩 ${usableMembership.remainDays} 天` : '—' }}
        </div>
      </div>
    </section>

    <!-- 正在进行的自习：最高优先级，给一键签退 -->
    <section v-if="inUseReservation" class="k-card highlight">
      <div class="hl-left">
        <span class="k-tag k-tag--green">正在自习</span>
        <div class="hl-main">
          <b>{{ inUseReservation.seatNo }}</b>
          <span>{{ inUseReservation.storeName }} · {{ fmt(inUseReservation.startTime) }}-{{ fmt(inUseReservation.endTime) }}</span>
        </div>
      </div>
      <el-button type="primary" @click="doCheckout(inUseReservation)">签退</el-button>
    </section>

    <!-- 下一次预约：给签到码与签到入口 -->
    <section v-if="nextReservation" class="k-card">
      <div class="sec-head">
        <h3>下一次预约</h3>
        <RouterLink to="/user/reservations" class="more">全部预约 →</RouterLink>
      </div>
      <div class="next">
        <div class="next-seat">{{ nextReservation.seatNo }}</div>
        <div class="next-info">
          <div class="next-line">
            {{ nextReservation.reserveDate }} · {{ fmt(nextReservation.startTime) }} - {{ fmt(nextReservation.endTime) }}
          </div>
          <div class="next-line sub">
            {{ nextReservation.storeName }} · {{ nextReservation.seatTypeName }}
          </div>
          <div class="next-code">
            签到码 <code>{{ nextReservation.checkinCode }}</code>
            <span class="hint">到店扫前台二维码即可签到，也可把码报给前台</span>
          </div>
        </div>
        <el-button v-if="nextReservation.canCheckin" type="primary" @click="doCheckin(nextReservation)">
          模拟扫码签到
        </el-button>
        <el-button v-else-if="nextReservation.canCancel" @click="go('/user/reservations')">管理</el-button>
      </div>
    </section>

    <!-- 快捷入口：用 RouterLink 而不是带 @click 的 div，键盘可聚焦、语义正确 -->
    <section class="quick">
      <RouterLink v-for="q in quickLinks" :key="q.path" :to="q.path" class="k-card k-card--hover q-item">
        <div class="q-title">{{ q.title }}</div>
        <div class="q-desc">{{ q.desc }}</div>
      </RouterLink>
    </section>

    <div class="cols">
      <!-- 门店公告 -->
      <section class="k-card">
        <h3>门店公告</h3>
        <div v-if="!announcements.length" class="hint">
          <EmptyState
            size="md"
            icon="📢"
            title="暂无门店公告"
            hint="门店发布营业时间调整、临时闭馆等通知时，会显示在这里。"
          />
        </div>
        <div v-else class="ann-list">
          <article v-for="a in announcements.slice(0, 4)" :key="a.id" class="ann-item">
            <div class="ann-title">
              <span class="k-tag k-tag--orange">{{ a.scope === 1 ? '全平台' : '本门店' }}</span>
              <b>{{ a.title }}</b>
              <span class="ann-time">{{ (a.publishTime || '').slice(0, 10) }}</span>
            </div>
            <p class="ann-content">{{ a.content }}</p>
          </article>
        </div>
      </section>

      <!-- 门店信息 -->
      <section class="k-card">
        <h3>门店信息</h3>
        <div v-if="!store" class="hint">
          <EmptyState
            size="md"
            icon="🏢"
            title="暂无门店信息"
            hint="管理员在后台录入门店后，这里会显示地址、电话与营业时间。"
          />
        </div>
        <template v-else>
          <div class="store-name">{{ store.name }}</div>
          <dl class="store-dl">
            <dt>地址</dt><dd>{{ store.address || '—' }}</dd>
            <dt>电话</dt><dd>{{ store.phone || '—' }}</dd>
            <dt>营业</dt><dd>{{ fmt(store.openTime) }} - {{ fmt(store.closeTime) }}（每天）</dd>
            <dt>座位</dt><dd>{{ store.seatTotal }} 个</dd>
          </dl>
          <p v-if="store.description" class="store-desc">{{ store.description }}</p>
        </template>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { me } from '@/api/auth'
import { myReservations, checkin, checkout } from '@/api/reservation'
import { myMemberships } from '@/api/member'
import { listStores } from '@/api/booking'
import { publicAnnouncements } from '@/api/admin'
import EmptyState from '@/components/EmptyState.vue'

const profile = ref({})
const reservations = ref([])
const memberships = ref([])
const announcements = ref([])
const store = ref(null)
const rules = ref({})

const quickLinks = [
  { title: '在线选座', desc: '看实时座位图并预约', path: '/user/reserve' },
  { title: '我的预约', desc: '签到签退、取消预约', path: '/user/reservations' },
  { title: '会员套餐', desc: '购买续费、查看消费流水', path: '/user/plans' },
  { title: 'AI 智能助手', desc: '问套餐、座位与预约规则', path: '/user/ai' }
]

const displayName = computed(() => {
  try {
    const i = JSON.parse(localStorage.getItem('userInfo') || '{}')
    return i.nickname || i.realName || profile.value.nickname || profile.value.phone || '同学'
  } catch (e) {
    return '同学'
  }
})

const usableMembership = computed(() => memberships.value.find((m) => m.usable) || null)
const inUseReservation = computed(() => reservations.value.find((r) => r.status === 1) || null)
const pendingCount = computed(() => reservations.value.filter((r) => r.status === 0).length)
const finishedCount = computed(() => reservations.value.filter((r) => r.status === 2).length)
const studyHours = computed(() => ((profile.value.totalStudyMinutes || 0) / 60).toFixed(1))

/** 下一次待使用预约：按日期+开始时间取最近的一个 */
const nextReservation = computed(() => {
  const pending = reservations.value.filter((r) => r.status === 0)
  if (!pending.length) return null
  return [...pending].sort((a, b) =>
    `${a.reserveDate} ${a.startTime}`.localeCompare(`${b.reserveDate} ${b.startTime}`)
  )[0]
})

/** 欢迎语随状态变化，避免每次都是同一句空话 */
const welcomeText = computed(() => {
  if (inUseReservation.value) {
    return `你正在 ${inUseReservation.value.seatNo} 自习，离店时记得签退。`
  }
  if (nextReservation.value) {
    const r = nextReservation.value
    return `下一次预约：${r.reserveDate} ${fmt(r.startTime)}-${fmt(r.endTime)}（${r.seatNo}）。`
  }
  if (!usableMembership.value) {
    return '你还没有可用的会员套餐，购买后即可在线选座预约。'
  }
  return '当前有可用套餐，去挑一个座位开始今天的自习吧。'
})

const fmt = (t) => (t ? String(t).slice(0, 5) : '')

async function load() {
  try {
    profile.value = await me()
    reservations.value = await myReservations(null)
    memberships.value = await myMemberships()
  } catch (e) {
    // 拦截器已提示
  }
  // 以下为公开数据，失败不影响主体
  try { announcements.value = await publicAnnouncements() } catch (e) { /* 忽略 */ }
  try {
    const stores = await listStores()
    // 优先展示用户最近预约过的那家门店，没有则展示第一家
    const lastStoreId = reservations.value.find((r) => r.storeId)?.storeId
    store.value = stores.find((s) => s.id === lastStoreId) || stores[0] || null
  } catch (e) { /* 忽略 */ }
}

async function doCheckin(r) {
  try {
    await checkin(r.checkinCode)
    ElMessage.success('签到成功，祝你学习愉快！')
    await load()
  } catch (e) {
    // 拦截器已提示
  }
}

async function doCheckout(r) {
  try {
    await checkout(r.id)
    ElMessage.success('签退成功，本次自习时长已累计')
    await load()
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(load)
</script>

<style scoped>
.home { display: flex; flex-direction: column; gap: 18px; }

/* 欢迎 */
.welcome { display: flex; justify-content: space-between; align-items: center; gap: 20px; flex-wrap: wrap; }
.welcome h1 { margin: 0 0 6px; font-size: var(--fs-h1); }
.welcome .sub { margin: 0; color: var(--ink-2); font-size: var(--fs-sm); }

/* KPI */
.kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 14px; }
.kpi { padding: 16px 18px; }
.kpi-label { color: var(--ink-3); font-size: var(--fs-xs); }
.kpi-value { margin-top: 8px; font-size: var(--fs-stat); font-weight: 700; color: var(--brand-deep); }
.kpi-value.small { font-size: var(--fs-lg); }
.kpi-unit { font-size: var(--fs-sm); font-weight: 600; color: var(--ink-2); margin-left: 2px; }
.kpi-none { color: var(--ink-3); font-size: 20px; }
.kpi-foot { margin-top: 4px; color: var(--ink-3); font-size: var(--fs-xs); }

/* 正在自习 */
.highlight { display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; border-left: 3px solid var(--c-green); }
.hl-left { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.hl-main { display: flex; align-items: baseline; gap: 10px; }
.hl-main b { font-size: var(--fs-lg); }
.hl-main span { color: var(--ink-2); font-size: var(--fs-sm); }

/* 下一次预约 */
h3 { margin: 0 0 12px; font-size: var(--fs-h3); }
.sec-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.sec-head h3 { margin: 0; }
.more { font-size: var(--fs-sm); color: var(--ink-3); }
.more:hover { color: var(--brand-ink); }
.next { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.next-seat {
  min-width: 68px; height: 48px; display: flex; align-items: center; justify-content: center;
  background: var(--brand-soft); color: var(--brand-deep);
  border-radius: var(--radius-md); font-weight: 700; font-size: 16px;
}
.next-info { flex: 1; min-width: 220px; }
.next-line { font-weight: 600; }
.next-line.sub { font-weight: 400; color: var(--ink-2); font-size: var(--fs-sm); margin-top: 2px; }
.next-code { margin-top: 6px; font-size: var(--fs-xs); color: var(--ink-3); display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.next-code code { background: var(--surface-soft); padding: 2px 10px; border-radius: var(--radius-chip); color: var(--brand-deep); font-weight: 700; letter-spacing: 1px; font-size: var(--fs-sm); }

/* 快捷入口 */
.quick { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 14px; }
.q-item { padding: 16px 18px; cursor: pointer; display: block; text-decoration: none; color: inherit; }
.q-title { font-weight: 700; color: var(--brand-deep); }
.q-desc { margin-top: 6px; color: var(--ink-3); font-size: var(--fs-xs); }

/* 底部两栏 */
.cols { display: grid; grid-template-columns: 1.2fr 1fr; gap: 18px; }
.hint { margin: 0; color: var(--ink-3); font-size: var(--fs-sm); }
.ann-list { display: flex; flex-direction: column; gap: 12px; }
.ann-item { border-left: 3px solid var(--accent); padding: 2px 0 2px 12px; }
.ann-title { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.ann-title b { font-size: var(--fs-sm); }
.ann-time { margin-left: auto; color: var(--ink-3); font-size: var(--fs-xs); }
.ann-content { margin: 6px 0 0; color: var(--ink-2); font-size: var(--fs-sm); line-height: 1.8; white-space: pre-wrap; }
.store-name { font-weight: 700; margin-bottom: 10px; }
.store-dl { display: grid; grid-template-columns: 56px 1fr; gap: 8px 10px; margin: 0; font-size: var(--fs-sm); }
.store-dl dt { color: var(--ink-3); }
.store-dl dd { margin: 0; }
.store-desc { margin: 12px 0 0; color: var(--ink-3); font-size: var(--fs-xs); line-height: 1.8; }
@media (max-width: 900px) { .cols { grid-template-columns: 1fr; } }
</style>
