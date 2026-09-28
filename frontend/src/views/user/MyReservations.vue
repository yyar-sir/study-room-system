<template>
  <div class="mine">
    <div class="page-head">
      <div>
        <h1>我的预约</h1>
        <p class="sub">到店后扫前台或座位上的二维码即可自动签到；也可把签到码报给前台协助签到。</p>
      </div>
      <el-button @click="codeDialog = true">输入签到码</el-button>
    </div>

    <el-tabs v-model="activeTab" @tab-change="load">
      <el-tab-pane label="全部" :name="'all'" />
      <el-tab-pane label="待使用" :name="'0'" />
      <el-tab-pane label="使用中" :name="'1'" />
      <el-tab-pane label="已完成" :name="'2'" />
      <el-tab-pane label="已取消" :name="'3'" />
      <el-tab-pane label="爽约" :name="'4'" />
    </el-tabs>

    <div v-if="loading" class="empty">加载中…</div>
    <div v-else-if="!list.length" class="empty">
      <EmptyState
        size="md"
        icon="📅"
        title="还没有预约记录"
        hint="到「在线选座」挑一个座位就能预约：预约不扣次数，签到成功才扣 1 次权益。"
      >
        <template #action>
          <el-button type="primary" @click="router.push('/user/reserve')">去在线选座</el-button>
        </template>
      </EmptyState>
    </div>

    <div v-else class="cards">
      <article v-for="r in list" :key="r.id" class="k-card item">
        <div class="item-head">
          <div class="seat-no">{{ r.seatNo }}</div>
          <div class="meta">
            <div class="store">{{ r.storeName }}</div>
            <div class="time">
              {{ r.reserveDate }} · {{ fmt(r.startTime) }} - {{ fmt(r.endTime) }}
              <span v-if="r.seatTypeName"> · {{ r.seatTypeName }}</span>
            </div>
          </div>
          <span class="k-tag" :class="statusClass(r.status)">{{ r.statusText }}</span>
        </div>

        <!--
          取消原因：预约被取消（尤其被前台代取消）时必须让用户看到原因，
          否则「预约怎么没了」无从得知。此前该字段只存在数据库里，没有任何接口返回。
        -->
        <div v-if="r.cancelReason" class="cancel-reason">
          <span class="label">取消原因</span>
          <span>{{ r.cancelReason }}</span>
        </div>

        <div class="item-foot">
          <div class="code-line">
            <span class="label">签到码</span>
            <code>{{ r.checkinCode }}</code>
            <span class="hint">（报给前台，或用手机扫前台二维码）</span>
          </div>
          <div class="actions">
            <el-button v-if="r.canCheckin" type="primary" size="small" @click="doCheckin(r.checkinCode)">
              模拟扫码签到
            </el-button>
            <el-button v-if="r.canCheckout" size="small" @click="doCheckout(r)">
              签退
            </el-button>
            <el-button v-if="r.canCancel" size="small" @click="doCancel(r)">
              取消预约
            </el-button>
          </div>
        </div>
      </article>
    </div>

    <el-dialog v-model="codeDialog" title="输入签到码" width="360px">
      <el-input v-model="manualCode" placeholder="请输入二维码上的 8 位签到码" maxlength="8" />
      <template #footer>
        <el-button @click="codeDialog = false">取消</el-button>
        <el-button type="primary" @click="submitManualCode">签到</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { myReservations, cancelReservation, checkin, checkout } from '@/api/reservation'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()
const router = useRouter()

const list = ref([])
const loading = ref(false)
const activeTab = ref('all')
const codeDialog = ref(false)
const manualCode = ref('')

function fmt(t) {
  // 后端返回 HH:mm:ss，展示到分钟即可
  return t ? String(t).slice(0, 5) : ''
}

function statusClass(status) {
  return {
    'k-tag--green': status === 1,
    'k-tag--orange': status === 0,
    'k-tag--pink': status === 4,
    'k-tag--yellow': status === 3
  }
}

async function load() {
  loading.value = true
  try {
    list.value = await myReservations(activeTab.value === 'all' ? null : Number(activeTab.value))
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function doCheckin(code) {
  try {
    await checkin(code)
    ElMessage.success('签到成功，祝你学习愉快！')
    await load()
  } catch (e) {
    // 拦截器已提示（超时/重复签到等）
  }
}

async function submitManualCode() {
  const c = manualCode.value.trim().toUpperCase()
  if (c.length !== 8) {
    ElMessage.warning('签到码为 8 位')
    return
  }
  codeDialog.value = false
  manualCode.value = ''
  await doCheckin(c)
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

async function doCancel(r) {
  try {
    await ElMessageBox.confirm(
      `确定取消 ${r.reserveDate} ${fmt(r.startTime)} 的预约吗？`,
      '取消预约',
      { type: 'warning' }
    )
  } catch {
    return // 用户点了取消
  }
  try {
    await cancelReservation(r.id, '用户主动取消')
    ElMessage.success('已取消')
    await load()
  } catch (e) {
    // 拦截器已提示（如距开始不足 30 分钟不可取消）
  }
}

onMounted(async () => {
  // 扫码进入：二维码内容形如 /user/reservations?code=ABCD2345
  // 打开页面即自动尝试签到，省去用户再手动输码
  const code = route.query.code
  if (code) {
    await doCheckin(String(code).toUpperCase())
    // 清掉 query，避免刷新页面时重复签到
    router.replace({ path: '/user/reservations' })
  }
  await load()
})
</script>

<style scoped>
.mine { display: flex; flex-direction: column; gap: 14px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; flex-wrap: wrap; }
.page-head h1 { margin: 0 0 6px; font-size: var(--fs-h1); }
.sub { margin: 0; color: var(--ink-2); font-size: var(--fs-sm); }
.cards { display: flex; flex-direction: column; gap: 12px; }
.item { padding: 16px 18px; }
.item-head { display: flex; align-items: center; gap: 14px; }
.seat-no {
  min-width: 64px; height: 44px; display: flex; align-items: center; justify-content: center;
  background: var(--brand-soft); color: var(--brand-deep);
  border-radius: var(--radius-md); font-weight: 700; font-size: var(--fs-body);
}
.meta { flex: 1; min-width: 0; }
.store { font-weight: 600; }
.time { font-size: var(--fs-sm); color: var(--ink-2); margin-top: 2px; }
.item-foot {
  display: flex; justify-content: space-between; align-items: center; gap: 14px;
  margin-top: 14px; padding-top: 12px; border-top: 1px dashed var(--line); flex-wrap: wrap;
}
.code-line { font-size: var(--fs-xs); color: var(--ink-3); display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }.code-line code {
  background: var(--surface-soft); padding: 2px 10px; border-radius: var(--radius-chip);
  color: var(--brand-deep); font-weight: 700; letter-spacing: 1px; font-size: var(--fs-sm);
}
.actions { display: flex; gap: 8px; }
/* 取消原因用警示色：它是「这条预约为什么没生效」的唯一解释，不该淹没在正文里 */
.cancel-reason {
  display: flex; gap: 8px; align-items: baseline; flex-wrap: wrap;
  margin-top: 10px; padding: 8px 12px;
  border-radius: var(--radius-chip);
  background: var(--danger-bg);
  color: var(--danger-fg);
  font-size: var(--fs-xs);
  line-height: 1.6;
}
.cancel-reason .label { font-weight: 700; flex-shrink: 0; opacity: 0.85; }
.empty { color: var(--ink-3); font-size: var(--fs-sm); padding: 40px 0; text-align: center; }
</style>
