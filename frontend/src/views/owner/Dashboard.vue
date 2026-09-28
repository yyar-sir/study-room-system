<template>
  <div class="dashboard">
    <div class="page-head">
      <div>
        <h1>经营数据看板</h1>
        <p class="sub">座位使用率、时段客流、营收与会员增长的多维统计。</p>
      </div>
      <div class="head-actions">
        <el-select v-model="storeId" placeholder="全部门店" clearable style="width: 200px" @change="loadAll">
          <el-option v-for="s in stores" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <el-button :loading="loading" @click="loadAll">刷新</el-button>
      </div>
    </div>

    <!-- KPI -->
    <section ref="kpiRef" v-loading="loading" class="kpis">
      <div v-for="k in kpis" :key="k.label" class="kpi k-card">
        <div class="kpi-label">{{ k.label }}</div>
        <div class="kpi-value">
          <span v-if="k.prefix" class="kpi-unit">{{ k.prefix }}</span><span
            :data-count="k.num"
            :data-decimals="k.decimals || 0"
          >{{ k.text }}</span><span class="kpi-unit">{{ k.unit }}</span>
        </div>
      </div>
    </section>

    <section class="charts">
      <div class="k-card chart-card">
        <h3>时段客流分布（今日）</h3>
        <div ref="hourlyRef" class="chart" />
      </div>
      <div class="k-card chart-card">
        <h3>套餐销量分布</h3>
        <div ref="planRef" class="chart" />
      </div>
      <div class="k-card chart-card">
        <h3>近 7 天预约量</h3>
        <div ref="reserveRef" class="chart" />
      </div>
      <div class="k-card chart-card">
        <h3>近 7 天营收趋势</h3>
        <div ref="revenueRef" class="chart" />
      </div>
      <div class="k-card chart-card">
        <h3>近 7 天会员增长</h3>
        <div ref="memberRef" class="chart" />
      </div>
      <div class="k-card chart-card wide">
        <h3>座位类型预约占比</h3>
        <div ref="seatTypeRef" class="chart" />
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart, BarChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { listStores } from '@/api/booking'
import { countUpGroup, prefersReducedMotion } from '@/utils/motion'
import { useTheme } from '@/composables/useTheme'
import {
  statsOverview,
  hourlyTraffic,
  planDistribution,
  reservationTrend,
  revenueTrend,
  memberGrowth,
  seatTypeUsage
} from '@/api/admin'

// 按需注册 ECharts 模块，而不是 `import * as echarts from 'echarts'`。
// 全量引入会让产物多出约 900KB，而我们只用到折线/柱状/饼三种图。
echarts.use([
  LineChart,
  BarChart,
  PieChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  CanvasRenderer
])

const stores = ref([])
const storeId = ref(null)
const loading = ref(false)
const overview = ref({})

const hourlyRef = ref(null)
const kpiRef = ref(null)
const planRef = ref(null)
const reserveRef = ref(null)
const revenueRef = ref(null)
const memberRef = ref(null)
const seatTypeRef = ref(null)

/**
 * 元素 → ECharts 实例。
 *
 * ⚠️ 必须用 WeakMap，不能用普通对象：DOM 元素作对象键时会被 toString() 成
 * "[object HTMLDivElement]"，六个图表 div 会得到**同一个键**，于是六张图共用
 * 一个实例。而该实例初始化在第一个 div 上，每次 setOption(option, true) 又整体
 * 覆盖，结果是第一张卡片显示最后一张图的配置、另外五张卡片永远空白（且不报错）。
 */
const instances = new WeakMap()
/** WeakMap 不可遍历，另外存一份用于 resize 与 dispose */
const instanceList = []

const { theme } = useTheme()

/** 最近一次拉取的图表数据，供 nextTick 后统一绘制 */
let pending = {}

const kpis = computed(() => {
  const o = overview.value
  const rate = Number(o.occupancyRate ?? 0)
  const raw = [
    { label: '今日预约', num: Number(o.todayReservations ?? 0), unit: ' 单' },
    { label: '今日签到', num: Number(o.todayCheckins ?? 0), unit: ' 人' },
    { label: '当前在座', num: Number(o.inUseSeats ?? 0), unit: ' 人' },
    { label: '座位使用率', num: rate, unit: ' %', decimals: Number.isInteger(rate) ? 0 : 1 },
    { label: '今日营收', num: Number(o.todayRevenue ?? 0), prefix: '¥' },
    { label: '累计会员', num: Number(o.memberTotal ?? 0), unit: ' 人' }
  ]
  // text 是首帧/降级时的静态文案；滚动动画由 utils/motion.js 的 count-up 接管 textContent
  return raw.map((k) => ({ ...k, text: (k.prefix || '') + k.num.toFixed(k.decimals || 0) }))
})

/**
 * ECharts 的配置是 JS 对象，没法写 var(--x)，所以这里在绘制时从 :root 上
 * 取设计系统的真实色值。好处是改 main.css 一处，图表配色跟着变。
 */
function token(name, fallback) {
  const v = getComputedStyle(document.documentElement).getPropertyValue(name)
  return String(v || '').trim() || fallback
}

/** #rrggbb → rgba(r,g,b,a)，用于 areaStyle 这类需要半透明的场景 */
function alpha(hex, a) {
  const m = /^#?([0-9a-f]{6})$/i.exec(String(hex).trim())
  if (!m) return hex
  const n = parseInt(m[1], 16)
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`
}

/** 每次绘制都重新取色，而不是在模块加载时固化 */
function palette() {
  return {
    line: token('--line', '#e6eaf3'),
    ink2: token('--ink-2', '#4c5a78'),
    ink3: token('--ink-3', '#8896b4'),
    brand: token('--brand', '#1e4fd8'),
    teal: token('--c-teal', '#178a7d'),
    accent: token('--accent', '#ff8a3d'),
    pink: token('--c-pink', '#e05c8a'),
    // 会员增长单独用绿：前四张已占用 brand/teal/accent/pink，沿用任一个会和已有曲线撞色
    green: token('--c-green', '#2e9e6b')
  }
}

const axis = (c) => ({
  type: 'category',
  axisLine: { lineStyle: { color: c.line } },
  axisLabel: { color: c.ink3 }
})
const valueAxis = (c) => ({
  type: 'value',
  splitLine: { lineStyle: { color: c.line } },
  axisLabel: { color: c.ink3 }
})

function render(el, option) {
  if (!el) return
  let inst = instances.get(el)
  if (!inst) {
    inst = echarts.init(el)
    instances.set(el, inst)
    instanceList.push(inst)
  }
  inst.setOption(option, true)
}

async function loadAll() {
  loading.value = true
  try {
    const sid = storeId.value || null
    const [ov, hourly, plans, reserves, revenue, members, seatTypes] = await Promise.all([
      statsOverview(sid),
      hourlyTraffic(sid),
      planDistribution(sid),
      reservationTrend(sid, 7),
      revenueTrend(sid, 7),
      memberGrowth(sid, 7),
      seatTypeUsage(sid)
    ])
    overview.value = ov || {}

    pending = { hourly, plans, reserves, revenue, members, seatTypes }
    await nextTick()
    drawAll()
    animateKpis()
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function drawAll() {
  const { hourly = [], plans = [], reserves = [], revenue = [], members = [], seatTypes = [] } = pending
  const c = palette()

  render(hourlyRef.value, {
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 16, top: 24, bottom: 30 },
    xAxis: { ...axis(c), data: hourly.map((x) => x.hour) },
    yAxis: valueAxis(c),
    series: [{
      type: 'bar',
      data: hourly.map((x) => x.count),
      itemStyle: { color: c.brand, borderRadius: [6, 6, 0, 0] },
      barMaxWidth: 22
    }]
  })

  render(planRef.value, {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { color: c.ink2 } },
    series: [{
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['50%', '44%'],
      data: plans.map((x) => ({ name: x.name, value: x.value })),
      label: { color: c.ink2 }
    }]
  })

  render(reserveRef.value, {
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 16, top: 24, bottom: 30 },
    xAxis: { ...axis(c), data: reserves.map((x) => x.date.slice(5)) },
    yAxis: valueAxis(c),
    series: [{
      type: 'line',
      smooth: true,
      data: reserves.map((x) => x.count),
      itemStyle: { color: c.teal },
      areaStyle: { color: alpha(c.teal, 0.12) }
    }]
  })

  render(revenueRef.value, {
    tooltip: { trigger: 'axis' },
    grid: { left: 56, right: 16, top: 24, bottom: 30 },
    xAxis: { ...axis(c), data: revenue.map((x) => x.date.slice(5)) },
    yAxis: valueAxis(c),
    series: [{
      type: 'line',
      smooth: true,
      data: revenue.map((x) => Number(x.amount || 0)),
      itemStyle: { color: c.accent },
      areaStyle: { color: alpha(c.accent, 0.12) }
    }]
  })

  // 会员增长与预约量同为「每日计数」，接口结构一致，所以除了配色与字段名外
  // 刻意与上面那张折线图保持一致，避免同一看板里出现两种曲线风格
  render(memberRef.value, {
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 16, top: 24, bottom: 30 },
    xAxis: { ...axis(c), data: members.map((x) => x.date.slice(5)) },
    yAxis: valueAxis(c),
    series: [{
      type: 'line',
      smooth: true,
      data: members.map((x) => x.count),
      itemStyle: { color: c.green },
      areaStyle: { color: alpha(c.green, 0.12) }
    }]
  })

  render(seatTypeRef.value, {
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 16, top: 24, bottom: 30 },
    xAxis: { ...axis(c), data: seatTypes.map((x) => x.name) },
    yAxis: valueAxis(c),
    series: [{
      type: 'bar',
      data: seatTypes.map((x) => x.value),
      itemStyle: { color: c.pink, borderRadius: [6, 6, 0, 0] },
      barMaxWidth: 46
    }]
  })
}

/**
 * KPI 数字滚动。必须在 nextTick 之后调用：此时 Vue 已把真实值写进 DOM，
 * count-up 再接管这几个 span 的 textContent 从 0 滚到目标值。
 * countUpTo 内部已判断 prefers-reduced-motion（CSS 侧关不掉 JS 动画）。
 */
function animateKpis() {
  if (!kpiRef.value || prefersReducedMotion()) return
  countUpGroup(kpiRef.value, { duration: 900, stagger: 60 })
}

/**
 * 图表配色是绘制时从 CSS Token 读的，只在挂载那次读。
 * 切主题时必须重绘，否则深色下坐标轴/图例还是浅色主题的深灰，糊在深底上看不见。
 * flush: 'post' 保证重绘发生在 <html> 的 class/data-theme 已切换之后。
 */
watch(theme, () => drawAll(), { flush: 'post' })

function onResize() {
  instanceList.forEach((i) => i.resize())
}

onMounted(async () => {
  try {
    stores.value = await listStores()
  } catch (e) {
    // 忽略：门店下拉拿不到也不影响看板（会退化成全部门店）
  }
  window.addEventListener('resize', onResize)
  await loadAll()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  // 必须 dispose，否则 echarts 实例与其 canvas 不会被回收
  instanceList.forEach((i) => i.dispose())
})
</script>

<style scoped>
.dashboard { display: flex; flex-direction: column; gap: 18px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; flex-wrap: wrap; }
.page-head h1 { margin: 0 0 6px; font-size: var(--fs-h1); }
.sub { margin: 0; color: var(--ink-2); font-size: var(--fs-sm); }
.head-actions { display: flex; gap: 10px; align-items: center; }
.kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 14px; }
.kpi { padding: 16px 18px; }
.kpi-label { color: var(--ink-3); font-size: var(--fs-xs); }
.kpi-value { margin-top: 8px; font-size: var(--fs-stat); font-weight: 700; color: var(--brand-deep); }
.kpi-unit { font-size: var(--fs-sm); font-weight: 600; color: var(--ink-2); margin-left: 2px; }
/* min(340px, 100%) 兜底：窄屏容器不足 340px 时不再强制 340px，避免横向溢出 */
.charts { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(340px, 100%), 1fr)); gap: 16px; }
.chart-card { padding: 16px 18px; }
.chart-card.wide { grid-column: 1 / -1; }
.chart-card h3 { margin: 0 0 10px; font-size: var(--fs-h3); }
.chart { width: 100%; height: 260px; }
</style>
