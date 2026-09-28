<template>
  <div class="assist">
    <div class="page-head">
      <div>
        <h1>预约与签到协助</h1>
        <p class="sub">用户报出签到码即可代为签到；离店时报手机号即可代查在座预约并签退。</p>
      </div>
      <el-button :loading="loading" @click="loadToday">刷新</el-button>
    </div>

    <div class="tools">
      <!-- 代签到 -->
      <section class="k-card tool">
        <h3>① 协助签到</h3>
        <p class="hint">输入用户二维码上的 8 位签到码</p>
        <div class="row">
          <el-input v-model="checkinCode" maxlength="8" placeholder="例如 A3K7M2QP" @keyup.enter="doCheckin" />
          <el-button type="primary" :loading="checkinLoading" @click="doCheckin">签到</el-button>
        </div>
        <div v-if="lastCheckin" class="result ok">
          已为 <b>{{ lastCheckin.seatNo }}</b>（{{ lastCheckin.reserveDate }}
          {{ fmt(lastCheckin.startTime) }}-{{ fmt(lastCheckin.endTime) }}）完成签到
        </div>
      </section>

      <!-- 代签退 -->
      <section class="k-card tool">
        <h3>② 协助签退</h3>
        <p class="hint">输入用户手机号，查询其在座预约</p>
        <div class="row">
          <el-input v-model="phone" maxlength="11" placeholder="例如 13900000002" @keyup.enter="queryActive" />
          <el-button :loading="queryLoading" @click="queryActive">查询</el-button>
        </div>
        <div v-if="activeList.length" class="active-list">
          <div v-for="r in activeList" :key="r.id" class="active-item">
            <span>{{ r.seatNo }} · {{ fmt(r.startTime) }}-{{ fmt(r.endTime) }}</span>
            <el-button size="small" type="primary" @click="doCheckout(r)">签退</el-button>
          </div>
        </div>
        <p v-else-if="queried" class="hint">该用户当前没有在座预约</p>
      </section>
    </div>

    <!-- 今日到店情况 -->
    <section class="k-card">
      <h3>今日预约（{{ today }}）</h3>
      <el-table :data="todayList" size="small">
        <el-table-column prop="seatNo" label="座位" width="90" />
        <el-table-column label="时段" width="130">
          <template #default="{ row }">{{ fmt(row.startTime) }} - {{ fmt(row.endTime) }}</template>
        </el-table-column>
        <el-table-column prop="seatTypeName" label="类型" width="100" />
        <el-table-column prop="checkinCode" label="签到码" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="k-tag" :class="statusClass(row.status)">{{ row.statusText }}</span>
          </template>
        </el-table-column>
        <!-- min-width 调到 170：待使用行会同时出现「签到」与「代取消」两个按钮，避免挤成两行 -->
        <el-table-column label="操作" min-width="170">
          <template #default="{ row }">
            <el-button v-if="row.canCheckin" size="small" @click="doCheckinByCode(row.checkinCode)">签到</el-button>
            <el-button v-if="row.canCheckout" size="small" @click="doCheckout(row)">签退</el-button>
            <!--
              代取消只对「待使用」(status === 0) 开放：已签到的必须走签退，其余状态后端会拒。
              这里刻意不用 row.canCancel —— 那个字段还叠加了「距开始 N 分钟」的用户自助限制，
              而前台代取消正是为突破该限制而存在，用它会把该能取消的异常单藏起来。
            -->
            <el-button
              v-if="row.status === 0"
              class="btn-cancel"
              size="small"
              type="danger"
              plain
              :loading="cancelLoadingId === row.id"
              @click="doStaffCancel(row)"
            >
              代取消
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState
            size="sm"
            icon="🗓️"
            title="今天还没有用户预约"
            hint="用户在小程序选好座位后，这里会列出座位、签到码与状态；也可以直接用上方「协助签到」输入 8 位签到码"
          />
        </template>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  staffCheckin,
  staffCheckout,
  staffActiveByPhone,
  staffTodayReservations,
  staffCancelReservation
} from '@/api/admin'
import EmptyState from '@/components/EmptyState.vue'

const loading = ref(false)
const todayList = ref([])
// 记录正在代取消的行 id：同一时间只允许一行在请求中，避免重复提交
const cancelLoadingId = ref(null)

const checkinCode = ref('')
const checkinLoading = ref(false)
const lastCheckin = ref(null)

const phone = ref('')
const queryLoading = ref(false)
const activeList = ref([])
const queried = ref(false)

const today = new Date().toISOString().slice(0, 10)

const fmt = (t) => (t ? String(t).slice(0, 5) : '')

function statusClass(status) {
  return {
    'k-tag--green': status === 1,
    'k-tag--orange': status === 0,
    'k-tag--pink': status === 4,
    'k-tag--yellow': status === 3
  }
}

async function loadToday() {
  loading.value = true
  try {
    todayList.value = await staffTodayReservations()
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function doCheckinByCode(code) {
  checkinCode.value = code
  await doCheckin()
}

async function doCheckin() {
  const code = checkinCode.value.trim().toUpperCase()
  if (code.length !== 8) {
    ElMessage.warning('签到码为 8 位')
    return
  }
  checkinLoading.value = true
  lastCheckin.value = null
  try {
    const r = await staffCheckin(code)
    lastCheckin.value = r
    checkinCode.value = ''
    ElMessage.success('签到成功')
    await loadToday()
  } catch (e) {
    // 拦截器已提示（码无效 / 重复签到 / 不在签到时间窗）
  } finally {
    checkinLoading.value = false
  }
}

async function queryActive() {
  if (!/^1[3-9]\d{9}$/.test(phone.value.trim())) {
    ElMessage.warning('请输入正确的 11 位手机号')
    return
  }
  queryLoading.value = true
  activeList.value = []
  try {
    activeList.value = await staffActiveByPhone(phone.value.trim())
  } catch (e) {
    // 拦截器已提示
  } finally {
    queried.value = true
    queryLoading.value = false
  }
}

async function doCheckout(r) {
  try {
    await staffCheckout(r.id)
    ElMessage.success('签退成功')
    activeList.value = activeList.value.filter((x) => x.id !== r.id)
    await loadToday()
  } catch (e) {
    // 拦截器已提示
  }
}

/**
 * 前台代用户取消预约（处理异常订单：约错时段、座位故障换位、来电说来不了）。
 *
 * 与用户自助取消的区别是这个入口不受「距开始 N 分钟」限制，
 * 所以这里不按时间做任何禁用；能否取消完全由状态（待使用）决定，
 * 跨门店等越权情况交给后端拦（403），前端不重复实现门店范围判断。
 */
async function doStaffCancel(row) {
  let reason = ''
  try {
    // prompt 的「取消 / 关闭」会 reject，这里只负责取值，异常一律在下面对照区分
    const { value } = await ElMessageBox.prompt(
      `代取消后座位会立即释放，且会通知用户。确定取消「${row.seatNo} ${fmt(row.startTime)}-${fmt(row.endTime)}」这条预约吗？`,
      '代取消预约',
      {
        confirmButtonText: '确认代取消',
        cancelButtonText: '再想想',
        // 破坏性操作，确认按钮用危险色，避免前台顺手点错
        confirmButtonClass: 'el-button--danger',
        inputPlaceholder: '如：用户来电告知临时有事 / 座位设备故障需换位',
        inputValidator: (v) => (v && v.trim() ? true : '请填写代取消的原因'),
        inputErrorMessage: '请填写代取消的原因',
        type: 'warning'
      }
    )
    reason = (value || '').trim()
  } catch {
    // 点「再想想」或关闭弹窗属于用户主动放弃，不是错误，静默返回（不能弹错误提示）；
    // 校验不通过时 ElMessageBox 同样以 'cancel' reject，但错误文案已显示在输入框下方，
    // 所以这里统一不提示：既把「用户取消」与「调用失败」分开，也不制造多余的报错弹窗
    return
  }

  // inputValidator 已保证非空，这里再兜一次，绝不让空原因打到后端换回 1002
  if (!reason) {
    ElMessage.warning('请填写代取消的原因')
    return
  }

  cancelLoadingId.value = row.id
  try {
    await staffCancelReservation(row.id, reason)
    ElMessage.success('已代取消该预约，并已通知用户')
    await loadToday()
  } catch (e) {
    // 真正的失败（非「待使用」/ 跨门店 403 等）由请求拦截器统一提示
  } finally {
    cancelLoadingId.value = null
  }
}

onMounted(loadToday)
</script>

<style scoped>
.assist { display: flex; flex-direction: column; gap: 18px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; flex-wrap: wrap; }
.page-head h1 { margin: 0 0 6px; font-size: var(--fs-h1); }
.sub { margin: 0; color: var(--ink-2); font-size: var(--fs-sm); }
h3 { margin: 0 0 8px; font-size: var(--fs-h3); }
.tools { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 16px; }
.tool { padding: 16px 18px; }
.hint { margin: 0 0 10px; color: var(--ink-3); font-size: var(--fs-xs); }
.row { display: flex; gap: 10px; }
.result { margin-top: 12px; padding: 10px 12px; border-radius: var(--radius-md); font-size: var(--fs-sm); }
.result.ok { background: var(--ok-bg); color: var(--ok-fg); }
.active-list { margin-top: 12px; display: flex; flex-direction: column; gap: 8px; }
.active-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 12px; border: 1px solid var(--line); border-radius: var(--radius-md); font-size: var(--fs-sm);
}
/*
 * 代取消是破坏性操作：用 plain 描边样式与主操作「签到」区分，不抢视觉焦点。
 * 颜色交给 Element Plus 的 type="danger"（已映射到项目的语义色变量），
 * 这里只补列内间距，避免写死颜色而丢深色主题适配。
 */
.btn-cancel { margin-left: var(--space-2); }
</style>
