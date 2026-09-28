<template>
  <div class="reserve">
    <div class="page-head">
      <div>
        <h1>在线预约选座</h1>
        <p class="sub">选择日期与时段，点击灰色座位即可预约；红色为已被预约，橙色为维修中。</p>
      </div>
      <span class="k-tag" :class="hasMembership ? 'k-tag--green' : 'k-tag--orange'">
        {{ hasMembership ? '套餐可用' : '暂无可用套餐' }}
      </span>
    </div>

    <!-- 筛选条件 -->
    <section class="k-card filter">
      <div class="field">
        <label>门店</label>
        <el-select v-model="query.storeId" placeholder="请选择门店" style="width: 100%">
          <el-option v-for="s in stores" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
      </div>
      <div class="field">
        <label>日期</label>
        <el-date-picker
          v-model="query.date"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择日期"
          :disabled-date="disabledDate"
          style="width: 100%"
        />
      </div>
      <div class="field">
        <label>开始时间</label>
        <el-time-select
          v-model="query.startTime"
          start="08:00"
          step="00:30"
          end="21:30"
          placeholder="开始"
          style="width: 100%"
        />
      </div>
      <div class="field">
        <label>结束时间</label>
        <el-time-select
          v-model="query.endTime"
          :start="query.startTime || '08:30'"
          step="00:30"
          end="22:00"
          placeholder="结束"
          style="width: 100%"
        />
      </div>
      <el-button type="primary" :loading="loading" @click="load">查询座位</el-button>
    </section>

    <div class="main">
      <!-- 座位图 -->
      <section class="k-card seat-area">
        <div class="legend">
          <span><i class="dot dot-free" />可预约</span>
          <span><i class="dot dot-busy" />已被预约</span>
          <span><i class="dot dot-fix" />维修中</span>
          <span><i class="dot dot-pick" />已选中</span>
        </div>

        <div v-if="loading" class="empty">正在加载座位…</div>
        <!--
          必须区分「还没查过」和「查了确实没有」：
          本页 onMounted 不会自动查座位（要用户先选好日期时段再点查询），
          若把两者合并成一个空状态，首次进入就会断言「该门店没有可预约的座位」——
          而门店其实有 30 个空座位，这句话是不成立的。
        -->
        <div v-else-if="!queried" class="empty">
          <EmptyState
            size="md"
            icon="🗓️"
            title="选好日期与时段后，点「查询座位」"
            hint="预约不扣次数，签到成功才扣 1 次权益；开始前 30 分钟以上可免费取消。"
          />
        </div>
        <div v-else-if="!seats.length" class="empty">
          <EmptyState
            size="md"
            icon="🪑"
            title="这个时段没有可预约的座位"
            hint="换一家门店，或调整日期与时段后重新点「查询座位」。"
          />
        </div>
        <!--
          没有可用套餐时必须单独成一支，且必须排在座位网格之前。
          因为此时后端仍会返回全部座位，只是把 bookable 全标成 false
          （reason「暂无可用套餐」），于是 seats.length > 0，
          「没有座位」那一支永远不会命中 —— 用户看到的会是一整片点不动的
          灰色座位，却不知道原因。重置演示数据后演示账号正是这个状态。
        -->
        <div v-else-if="!hasMembership" class="empty">
          <EmptyState
            size="md"
            icon="🎫"
            title="你还没有可用的套餐权益"
            hint="预约本身不扣次数，但需要有生效中的套餐才能选座。开通后剩余次数与到期时间会显示在「会员套餐」页。"
          >
            <template #action>
              <el-button type="primary" @click="router.push('/user/plans')">去购买套餐</el-button>
            </template>
          </EmptyState>
        </div>
        <div v-else class="grid" :style="{ gridTemplateColumns: `repeat(${maxCol + 1}, 46px)` }">
          <button
            v-for="seat in seats"
            :key="seat.id"
            class="seat"
            :class="seatClass(seat)"
            :style="{ gridRow: seat.rowIndex + 2, gridColumn: seat.colIndex + 1 }"
            :title="seatTooltip(seat)"
            :disabled="!seat.bookable"
            @click="pick(seat)"
          >
            {{ seat.seatNo }}
          </button>
        </div>
      </section>

      <!-- 确认区 -->
      <aside class="k-card summary">
        <h3>预约信息</h3>
        <dl>
          <dt>门店</dt><dd>{{ currentStoreName || '—' }}</dd>
          <dt>日期</dt><dd>{{ query.date || '—' }}</dd>
          <dt>时段</dt><dd>{{ query.startTime || '—' }} ~ {{ query.endTime || '—' }}</dd>
          <dt>座位</dt><dd>{{ selected ? `${selected.seatNo}（${selected.typeName || '—'}）` : '未选择' }}</dd>
        </dl>

        <el-button
          type="primary"
          class="submit"
          :loading="submitting"
          :disabled="!selected"
          @click="submit"
        >确认预约</el-button>

        <p class="tip">
          预约不扣次数，<b>签到成功才扣减 1 次套餐权益</b>；
          开始前 {{ rules.cancelBeforeMinutes }} 分钟以上可免费取消。
        </p>
        <p class="tip tip-rule">
          当前规则：最多提前 {{ rules.advanceBookDays }} 天预约，单次最长 {{ rules.maxReserveHours }} 小时。
        </p>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listStores, seatMap, publicRules } from '@/api/booking'
import { createReservation } from '@/api/reservation'
import { myMemberships } from '@/api/member'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()

const stores = ref([])
const seats = ref([])
const selected = ref(null)
const loading = ref(false)
const submitting = ref(false)
const hasMembership = ref(false)
/** 是否已经成功查询过一次座位；用来区分「还没查」和「查了没有」 */
const queried = ref(false)

/** 预约规则（来自后端配置，不硬编码） */
const rules = ref({ advanceBookDays: 7, maxReserveHours: 4, cancelBeforeMinutes: 30 })

const query = reactive({
  storeId: null,
  date: '',
  startTime: '',
  endTime: ''
})

const currentStoreName = computed(
  () => stores.value.find((s) => s.id === query.storeId)?.name || ''
)
const maxCol = computed(() => seats.value.reduce((m, s) => Math.max(m, s.colIndex || 0), 0))

/** 可预约日期窗口由后台配置决定（默认 7 天），不在前端写死 */
function disabledDate(d) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const max = new Date(today)
  max.setDate(max.getDate() + (rules.value.advanceBookDays || 7))
  return d < today || d > max
}

/** 'HH:mm' → 当天分钟数，用于算时长 */
function toMinutes(t) {
  if (!t) return 0
  const [h, m] = String(t).split(':')
  return Number(h) * 60 + Number(m)
}

function seatClass(seat) {
  return {
    picked: selected.value && selected.value.id === seat.id,
    busy: seat.occupied,
    fix: seat.status === 1 || seat.status === 2
  }
}

function seatTooltip(seat) {
  if (seat.reason) return `${seat.seatNo} · ${seat.reason}`
  return `${seat.seatNo} · ${seat.typeName || ''}`
}

function pick(seat) {
  if (!seat.bookable) return
  selected.value = selected.value && selected.value.id === seat.id ? null : seat
}

async function load() {
  if (!query.storeId) {
    ElMessage.warning('请先选择门店')
    return
  }
  if (!query.date || !query.startTime || !query.endTime) {
    ElMessage.warning('请选择完整的日期与时段')
    return
  }
  if (query.endTime <= query.startTime) {
    ElMessage.warning('结束时间必须晚于开始时间')
    return
  }
  // 单次时长上限：不挡的话用户能一次约满整个营业时段（08:00-22:00）把座位锁死
  if (toMinutes(query.endTime) - toMinutes(query.startTime) > (rules.value.maxReserveHours || 4) * 60) {
    ElMessage.warning(`单次预约最长 ${rules.value.maxReserveHours} 小时`)
    return
  }
  loading.value = true
  selected.value = null
  try {
    seats.value = await seatMap({
      storeId: query.storeId,
      date: query.date,
      startTime: query.startTime,
      endTime: query.endTime
    })
    // 只有真正拿到结果才置位：请求失败时保持「请先查询」的引导，
    // 而不是让用户以为「这个时段没座位」
    queried.value = true
    // 提示是否有可用套餐：没有套餐时后端会把所有座位标为不可预约
    const mine = await myMemberships()
    hasMembership.value = (mine || []).some((m) => m.usable)
  } catch (e) {
    // 错误提示已由 request 拦截器统一处理
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!selected.value) return
  submitting.value = true
  try {
    await createReservation({
      seatId: selected.value.id,
      reserveDate: query.date,
      startTime: query.startTime,
      endTime: query.endTime
    })
    ElMessage.success('预约成功，请按时到店扫码签到')
    selected.value = null
    await load()
    router.push('/user/reservations')
  } catch (e) {
    // 冲突/无套餐等业务错误由拦截器提示；这里刷新一次座位图，避免用户对着过期状态重复点
    await load()
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  try {
    // 先取规则：可预约天数窗口与单次时长上限都由后端配置决定
    rules.value = await publicRules()
    stores.value = await listStores()
    if (stores.value.length) {
      query.storeId = stores.value[0].id
    }
    const d = new Date()
    query.date = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    query.startTime = '09:00'
    query.endTime = '11:00'
    const mine = await myMemberships()
    hasMembership.value = (mine || []).some((m) => m.usable)
  } catch (e) {
    // 静默：未登录时路由守卫已拦截
  }
})
</script>

<style scoped>
.reserve { display: flex; flex-direction: column; gap: 18px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; flex-wrap: wrap; }
.page-head h1 { margin: 0 0 6px; font-size: var(--fs-h1); }
.sub { margin: 0; color: var(--ink-2); }
.filter { display: flex; gap: 14px; align-items: flex-end; flex-wrap: wrap; }
.field { display: flex; flex-direction: column; gap: 6px; min-width: 150px; }
.field label { font-size: var(--fs-xs); color: var(--ink-3); font-weight: 600; }
.main { display: grid; grid-template-columns: 1fr 300px; gap: 18px; align-items: start; }
.seat-area { min-height: 320px; }
.legend { display: flex; gap: 18px; flex-wrap: wrap; margin-bottom: 16px; font-size: var(--fs-xs); color: var(--ink-2); }
.legend span { display: inline-flex; align-items: center; gap: 6px; }
.dot { width: 12px; height: 12px; border-radius: var(--radius-xs); display: inline-block; }
.dot-free { background: var(--surface-soft); border: 1px solid var(--line); }
.dot-busy { background: var(--danger-bg); }
.dot-fix { background: var(--warn-bg); }
.dot-pick { background: var(--brand); }
.grid { display: grid; gap: 8px; justify-content: start; }
.seat {
  width: 46px; height: 40px; border-radius: var(--radius-sm);
  border: 1px solid var(--line); background: var(--surface-soft);
  color: var(--ink-2); font-size: var(--fs-2xs); font-weight: 600; cursor: pointer;
  transition: transform .15s, background .15s, color .15s;
}
.seat:hover:not(:disabled) { transform: translateY(-2px); border-color: var(--brand); color: var(--brand-ink); }
.seat:disabled { cursor: not-allowed; }
.seat.busy { background: var(--danger-bg); color: var(--danger-fg); border-color: var(--danger-line); }
.seat.fix { background: var(--warn-bg); color: var(--warn-fg); border-color: var(--warn-line); }
.seat.picked { background: var(--brand); color: #fff; border-color: var(--brand); }
.empty { color: var(--ink-3); font-size: var(--fs-sm); padding: 40px 0; text-align: center; }
.summary h3 { margin: 0 0 14px; font-size: var(--fs-h3); }
.summary dl { display: grid; grid-template-columns: 60px 1fr; gap: 8px 10px; margin: 0 0 18px; font-size: var(--fs-sm); }
.summary dt { color: var(--ink-3); }
.summary dd { margin: 0; color: var(--ink); font-weight: 600; }
.submit { width: 100%; }
.tip { margin: 14px 0 0; font-size: var(--fs-xs); color: var(--ink-3); line-height: 1.8; }
.tip-rule { margin-top: 6px; padding-top: 6px; border-top: 1px dashed var(--line); color: var(--brand-deep); }
@media (max-width: 900px) { .main { grid-template-columns: 1fr; } }
</style>
