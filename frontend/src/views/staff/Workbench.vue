<template>
  <div class="workbench">
    <div class="page-head">
      <div>
        <h1>前台工作台</h1>
        <p class="sub">{{ today }} · {{ storeLabel }}</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <!-- 今日概览 -->
    <section ref="kpiRef" class="kpis">
      <div class="kpi k-card">
        <div class="kpi-label">今日预约</div>
        <div class="kpi-value">
          <span :data-count="todayList.length" data-decimals="0">{{ todayList.length }}</span><span class="kpi-unit">单</span>
        </div>
      </div>
      <div class="kpi k-card">
        <div class="kpi-label">已签到</div>
        <div class="kpi-value">
          <span :data-count="checkedInCount" data-decimals="0">{{ checkedInCount }}</span><span class="kpi-unit">人</span>
        </div>
      </div>
      <div class="kpi k-card">
        <div class="kpi-label">当前在座</div>
        <div class="kpi-value">
          <span :data-count="inUseCount" data-decimals="0">{{ inUseCount }}</span><span class="kpi-unit">人</span>
        </div>
      </div>
      <div class="kpi k-card">
        <div class="kpi-label">待处理违规</div>
        <div class="kpi-value" :class="{ warn: pendingViolations.length > 0 }">
          <span :data-count="pendingViolations.length" data-decimals="0">{{ pendingViolations.length }}</span><span class="kpi-unit">条</span>
        </div>
      </div>
    </section>

    <div class="cols">
      <!-- 待签到：到点没来的，前台要重点盯 -->
      <section class="k-card">
        <div class="sec-head">
          <h3>待签到</h3>
          <RouterLink to="/staff/checkin" class="more">去协助签到 →</RouterLink>
        </div>
        <EmptyState
          v-if="!waitingCheckin.length"
          icon="⏰"
          title="当前没有等待签到的预约"
          hint="今天暂无「待使用」的预约；用户到店后可点上方「去协助签到」输入 8 位签到码代为签到"
        />
        <div v-else class="list">
          <div v-for="r in waitingCheckin" :key="r.id" class="item">
            <span class="seat">{{ r.seatNo }}</span>
            <span class="time">{{ fmt(r.startTime) }} - {{ fmt(r.endTime) }}</span>
            <code class="code">{{ r.checkinCode }}</code>
            <el-button v-if="r.canCheckin" size="small" type="primary" @click="doCheckin(r, false)">
              签到
            </el-button>
            <span v-else class="muted">未到签到时间</span>
          </div>
        </div>
      </section>

      <!-- 在座：可协助签退 -->
      <section class="k-card">
        <div class="sec-head">
          <h3>在座用户</h3>
          <RouterLink to="/staff/checkin" class="more">按手机号签退 →</RouterLink>
        </div>
        <EmptyState
          v-if="!inUseList.length"
          icon="🪑"
          title="当前没有正在自习的用户"
          hint="用户完成签到后这里会显示在座座位，可点「协助签退」或按手机号结束本次自习"
        />
        <div v-else class="list">
          <div v-for="r in inUseList" :key="r.id" class="item">
            <span class="seat">{{ r.seatNo }}</span>
            <span class="time">{{ fmt(r.startTime) }} 开始</span>
            <el-button size="small" @click="doCheckout(r)">协助签退</el-button>
          </div>
        </div>
      </section>
    </div>

    <!-- 待处理违规 -->
    <section class="k-card">
      <div class="sec-head">
        <h3>待处理违规</h3>
        <RouterLink to="/staff/violations" class="more">全部违规 →</RouterLink>
      </div>
      <el-table :data="pendingViolations" size="small">
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <span class="k-tag k-tag--pink">{{ typeText(row.violationType) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="违规说明" min-width="260" />
        <el-table-column prop="penalty" label="系统处罚" width="180" />
        <el-table-column label="发生时间" width="160">
          <template #default="{ row }">{{ (row.createdAt || '').replace('T', ' ').slice(0, 16) }}</template>
        </el-table-column>
        <template #empty>
          <EmptyState
            size="sm"
            icon="⚖️"
            title="暂无待处理的违规"
            hint="爽约、超时未签退等由系统自动判定并扣次后才会生成记录，点「全部违规」可查看历史记录"
          />
        </template>
      </el-table>
    </section>

    <!-- 快捷入口：用 RouterLink 而不是带 @click 的 div，键盘可聚焦、语义正确 -->
    <section class="quick">
      <RouterLink v-for="q in quickLinks" :key="q.path" :to="q.path" class="k-card k-card--hover q-item">
        <div class="q-title">{{ q.title }}</div>
        <div class="q-desc">{{ q.desc }}</div>
      </RouterLink>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { staffTodayReservations, staffViolations, staffCheckin, staffCheckout } from '@/api/admin'
import { listStores } from '@/api/booking'
import EmptyState from '@/components/EmptyState.vue'
import { countUpGroup, prefersReducedMotion } from '@/utils/motion'

const todayList = ref([])
const pendingViolations = ref([])
const loading = ref(false)
const storeName = ref('')
const kpiRef = ref(null)

const today = new Date().toISOString().slice(0, 10)

const quickLinks = [
  { title: '座位状态管理', desc: '查看占用、标记维修/停用', path: '/staff/seats' },
  { title: '预约与签到协助', desc: '代签到、按手机号签退', path: '/staff/checkin' },
  { title: '会员业务办理', desc: '代客开卡与续费', path: '/staff/members' },
  { title: '记录查询', desc: '预约/签到/消费流水', path: '/staff/records' }
]

const fmt = (t) => (t ? String(t).slice(0, 5) : '')
const nz = (v) => (v ? 1 : 0)

const checkedInCount = computed(() =>
  todayList.value.filter((r) => r.status === 1 || r.status === 2).length
)
const inUseList = computed(() => todayList.value.filter((r) => r.status === 1))
const inUseCount = computed(() => inUseList.value.length)
/** 待签到 = 状态仍为「待使用」的预约 */
const waitingCheckin = computed(() => todayList.value.filter((r) => r.status === 0))

const storeLabel = computed(() => storeName.value || '本门店')

const TYPE_TEXT = { 1: '超时未签退', 2: '预约未到', 3: '占用他人座位', 4: '其他' }
const typeText = (t) => TYPE_TEXT[t] || '其他'

async function load() {
  loading.value = true
  try {
    todayList.value = await staffTodayReservations()
  } catch (e) {
    // 拦截器已提示
  }
  try {
    pendingViolations.value = await staffViolations(0)
  } catch (e) {
    // 拦截器已提示
  }
  try {
    // 用【公开】门店接口取门店名。
    // 不能用 /api/owner/stores —— 那是经营者专属（role 0），前台调用会 403，
    // 而 request 拦截器对 403 会弹错误提示，前台一进首页就会看到「无权限访问」。
    const stores = await listStores()
    const info = JSON.parse(localStorage.getItem('userInfo') || '{}')
    storeName.value = stores.find((s) => s.id === info.storeId)?.name || ''
  } catch (e) {
    // 取不到门店名不影响其它内容
  }
  loading.value = false
  animateKpis()
}

/**
 * KPI 数字滚动。
 * 必须 await nextTick：调用点刚改完 ref，DOM 上 data-count 还是旧值，
 * 不等 flush 就会把数字滚到上一次的数据。
 * countUpTo 内部已判断 prefers-reduced-motion（CSS 侧关不掉 JS 动画）。
 */
async function animateKpis() {
  if (!kpiRef.value || prefersReducedMotion()) return
  await nextTick()
  countUpGroup(kpiRef.value, { duration: 900, stagger: 60 })
}

async function doCheckin(r, asStaff = true) {
  try {
    await staffCheckin(r.checkinCode)
    ElMessage.success(`已为 ${r.seatNo} 完成签到`)
    await load()
  } catch (e) {
    // 拦截器已提示
  }
}

async function doCheckout(r) {
  try {
    await staffCheckout(r.id)
    ElMessage.success('签退成功')
    await load()
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(load)
</script>

<style scoped>
.workbench { display: flex; flex-direction: column; gap: 18px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; flex-wrap: wrap; }
.page-head h1 { margin: 0 0 6px; font-size: var(--fs-h1); }
.sub { margin: 0; color: var(--ink-2); font-size: var(--fs-sm); }
.kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 14px; }
.kpi { padding: 16px 18px; }
.kpi-label { color: var(--ink-3); font-size: var(--fs-xs); }
.kpi-value { margin-top: 8px; font-size: var(--fs-stat); font-weight: 700; color: var(--brand-deep); }
.kpi-value.warn { color: var(--accent-deep); }
.kpi-unit { font-size: var(--fs-sm); font-weight: 600; color: var(--ink-2); margin-left: 2px; }
.cols { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
h3 { margin: 0 0 12px; font-size: var(--fs-h3); }
.sec-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.sec-head h3 { margin: 0; }
.more { font-size: var(--fs-sm); color: var(--ink-3); }
.more:hover { color: var(--brand-ink); }
.list { display: flex; flex-direction: column; gap: 8px; }
.item { display: flex; align-items: center; gap: 12px; padding: 8px 12px; border: 1px solid var(--line); border-radius: var(--radius-md); font-size: var(--fs-sm); }
.seat { min-width: 52px; font-weight: 700; color: var(--brand-deep); }
.time { color: var(--ink-2); }
.code { background: var(--surface-soft); padding: 2px 8px; border-radius: var(--radius-chip); color: var(--brand-deep); font-size: var(--fs-xs); letter-spacing: 1px; margin-left: auto; }
.muted { color: var(--ink-3); font-size: var(--fs-xs); margin-left: auto; }
.quick { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 14px; }
.q-item { padding: 16px 18px; cursor: pointer; display: block; text-decoration: none; color: inherit; }
.q-title { font-weight: 700; color: var(--brand-deep); }
.q-desc { margin-top: 6px; color: var(--ink-3); font-size: var(--fs-xs); }
@media (max-width: 900px) { .cols { grid-template-columns: 1fr; } }
</style>
