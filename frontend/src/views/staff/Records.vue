<template>
  <div class="records">
    <div class="page-head">
      <div>
        <h1>记录查询</h1>
        <p class="sub">查询预约记录、签到日志与会员消费流水，用于核对当日到店与营收。</p>
      </div>
      <div class="head-actions">
        <el-date-picker v-model="date" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
        <el-button :loading="loading" @click="load">查询</el-button>
      </div>
    </div>

    <el-tabs v-model="tab" @tab-change="load">
      <el-tab-pane label="预约记录" name="reservations" />
      <el-tab-pane label="签到日志" name="checkins" />
      <el-tab-pane label="消费流水" name="consumptions" />
    </el-tabs>

    <!-- 预约记录 -->
    <section v-if="tab === 'reservations'" class="k-card">
      <el-table :data="list" size="small">
        <el-table-column prop="reservationNo" label="预约单号" width="190" />
        <el-table-column prop="phone" label="用户手机号" width="130" />
        <el-table-column prop="seatId" label="座位ID" width="80" />
        <el-table-column label="时段" width="130">
          <template #default="{ row }">{{ fmt(row.startTime) }} - {{ fmt(row.endTime) }}</template>
        </el-table-column>
        <el-table-column prop="checkinCode" label="签到码" width="110" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="k-tag" :class="statusClass(row.status)">{{ row.statusText }}</span>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState
            size="sm"
            icon="🗓️"
            title="当日没有预约记录"
            hint="换个日期后点「查询」再看看，或等用户在小程序选座下单后再来核对"
          />
        </template>
      </el-table>
      <p class="hint">合计 {{ list.length }} 条</p>
    </section>

    <!-- 签到日志 -->
    <section v-if="tab === 'checkins'" class="k-card">
      <el-table :data="list" size="small">
        <el-table-column prop="phone" label="用户手机号" width="130" />
        <el-table-column prop="seatId" label="座位ID" width="80" />
        <el-table-column label="签到时间" width="170">
          <template #default="{ row }">{{ (row.checkinTime || '').replace('T', ' ').slice(0, 19) }}</template>
        </el-table-column>
        <el-table-column label="签退时间" width="170">
          <template #default="{ row }">
            {{ row.checkoutTime ? String(row.checkoutTime).replace('T', ' ').slice(0, 19) : '未签退' }}
          </template>
        </el-table-column>
        <el-table-column label="方式" width="110">
          <template #default="{ row }">{{ row.checkinType === 2 ? '前台协助' : '用户扫码' }}</template>
        </el-table-column>
        <el-table-column label="时长(分钟)" width="110">
          <template #default="{ row }">{{ row.durationMinutes }}</template>
        </el-table-column>
        <template #empty>
          <EmptyState
            size="sm"
            icon="✅"
            title="当日没有签到日志"
            hint="用户扫码签到，或前台用「协助签到」代签后，这里会留下签到与签退时间"
          />
        </template>
      </el-table>
      <p class="hint">
        合计 {{ list.length }} 条，其中已完成签退 {{ list.filter((x) => x.checkedOut).length }} 条
      </p>
    </section>

    <!-- 消费流水 -->
    <section v-if="tab === 'consumptions'" class="k-card">
      <el-table :data="list" size="small">
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ (row.createdAt || '').replace('T', ' ').slice(0, 19) }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="用户手机号" width="130" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <span class="k-tag">{{ bizText(row.bizType) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="100">
          <template #default="{ row }">¥{{ row.amount }}</template>
        </el-table-column>
        <el-table-column label="次数变动" width="100">
          <template #default="{ row }">
            <span :class="row.timesDelta < 0 ? 'minus' : ''">
              {{ row.timesDelta > 0 ? '+' + row.timesDelta : (row.timesDelta === 0 ? '—' : row.timesDelta) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="说明" min-width="200" />
        <template #empty>
          <EmptyState
            size="sm"
            icon="🧾"
            title="当日没有消费流水"
            hint="前台办理会员套餐、用户预约扣次后才会产生流水，可换个日期再点「查询」"
          />
        </template>
      </el-table>
      <p class="hint">
        合计 {{ list.length }} 条，当日收款合计 ¥{{
          list.reduce((s, x) => s + Number(x.amount || 0), 0).toFixed(2)
        }}
      </p>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { recordReservations, recordCheckins, recordConsumptions } from '@/api/admin'
import EmptyState from '@/components/EmptyState.vue'

const tab = ref('reservations')
const date = ref(new Date().toISOString().slice(0, 10))
const list = ref([])
const loading = ref(false)

const BIZ_TYPE = { 1: '购买', 2: '预约扣次', 3: '取消返还', 4: '退款', 5: '爽约扣减' }
const bizText = (t) => BIZ_TYPE[t] || '其它'
const fmt = (t) => (t ? String(t).slice(0, 5) : '')

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
    if (tab.value === 'reservations') {
      list.value = await recordReservations(date.value)
    } else if (tab.value === 'checkins') {
      list.value = await recordCheckins(date.value)
    } else {
      list.value = await recordConsumptions(date.value)
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.records { display: flex; flex-direction: column; gap: 14px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; flex-wrap: wrap; }
.page-head h1 { margin: 0 0 6px; font-size: var(--fs-h1); }
.sub { margin: 0; color: var(--ink-2); font-size: var(--fs-sm); }
.head-actions { display: flex; gap: 10px; align-items: center; }
.hint { margin: 14px 0 0; color: var(--ink-3); font-size: var(--fs-xs); }
.minus { color: var(--danger-fg); font-weight: 700; }
</style>
