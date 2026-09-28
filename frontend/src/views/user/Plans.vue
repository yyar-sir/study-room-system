<template>
  <div class="plans">
    <div class="page-head">
      <div>
        <h1>会员套餐</h1>
        <p class="sub">购买后可预约座位；签到成功才扣减次数，取消预约不扣次数。</p>
      </div>
    </div>

    <!-- 我的权益 -->
    <section class="k-card">
      <h3>我的权益</h3>
      <EmptyState
        v-if="!memberships.length"
        icon="🎫"
        title="还没有购买过套餐"
        hint="从下面的套餐列表挑一张买下，剩余次数与到期时间就会显示在这里。"
      />
      <div v-else class="mine-list">
        <div v-for="m in memberships" :key="m.id" class="mine-item">
          <div class="mi-head">
            <b>{{ m.planName }}</b>
            <span class="k-tag" :class="m.usable ? 'k-tag--green' : ''">{{ m.statusText }}</span>
          </div>
          <div class="mi-body">
            <span>剩余次数：<b>{{ m.remainTimes === -1 ? '不限次' : m.remainTimes }}</b></span>
            <span>到期：{{ (m.expireTime || '').slice(0, 10) }}</span>
            <span v-if="m.usable">剩余 {{ m.remainDays }} 天</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 套餐列表 -->
    <section v-if="plans.length" class="plan-grid">
      <article v-for="p in plans" :key="p.id" class="k-card k-card--hover plan">
        <div class="plan-head">
          <h4>{{ p.name }}</h4>
          <span class="k-tag k-tag--orange">{{ typeText(p.planType) }}</span>
        </div>
        <div class="price">
          <span class="now">¥{{ p.price }}</span>
          <span v-if="p.originalPrice" class="was">¥{{ p.originalPrice }}</span>
        </div>
        <p class="desc">{{ p.description }}</p>
        <ul class="benefits">
          <li v-for="(b, i) in splitBenefits(p.benefits)" :key="i">{{ b }}</li>
        </ul>
        <el-button type="primary" class="buy" :loading="buyingId === p.id" @click="buy(p)">
          立即购买
        </el-button>
      </article>
    </section>
    <section v-else class="k-card">
      <EmptyState
        icon="🏷️"
        title="暂无可购买的套餐"
        hint="管理员还没有上架套餐，稍后再来看看。"
      />
    </section>

    <!-- 消费流水 -->
    <section class="k-card">
      <h3>消费与权益流水</h3>
      <p class="hint">权益余额以流水为准，扣费可逐笔追溯。</p>
      <el-table :data="records" size="small">
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <span class="k-tag">{{ bizText(row.bizType) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="说明" min-width="200" />
        <el-table-column label="次数变动" width="100">
          <template #default="{ row }">
            <span :class="row.timesDelta < 0 ? 'delta-minus' : 'delta-zero'">
              {{ row.timesDelta > 0 ? '+' + row.timesDelta : (row.timesDelta === 0 ? '—' : row.timesDelta) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="剩余" width="90">
          <template #default="{ row }">{{ row.balanceTimes === -1 ? '不限' : row.balanceTimes }}</template>
        </el-table-column>
        <template #empty>
          <EmptyState
            size="sm"
            icon="📋"
            title="还没有消费流水"
            hint="购买套餐后的扣次、取消返还与退款记录，都会逐笔显示在这里。"
          />
        </template>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listPlans } from '@/api/booking'
import { myMemberships, purchase, consumptionRecords } from '@/api/member'
import EmptyState from '@/components/EmptyState.vue'

const plans = ref([])
const memberships = ref([])
const records = ref([])
const buyingId = ref(null)

const PLAN_TYPE = { 1: '次卡', 2: '月卡', 3: '季卡', 4: '年卡' }
const BIZ_TYPE = { 1: '购买', 2: '预约扣次', 3: '取消返还', 4: '退款', 5: '爽约扣减' }

const typeText = (t) => PLAN_TYPE[t] || '套餐'
const bizText = (t) => BIZ_TYPE[t] || '其它'

function splitBenefits(s) {
  if (!s) return []
  return s.split(/[;；\n]/).map((x) => x.trim()).filter(Boolean)
}

async function loadAll() {
  try {
    plans.value = await listPlans()
    memberships.value = await myMemberships()
    records.value = await consumptionRecords()
  } catch (e) {
    // 拦截器已提示
  }
}

async function buy(p) {
  buyingId.value = p.id
  try {
    await purchase(p.id, 1)
    ElMessage.success(`已开通「${p.name}」`)
    await loadAll()
  } catch (e) {
    // 拦截器已提示
  } finally {
    buyingId.value = null
  }
}

onMounted(loadAll)
</script>

<style scoped>
.plans { display: flex; flex-direction: column; gap: 18px; }
.page-head h1 { margin: 0 0 6px; font-size: var(--fs-h1); }
.sub { margin: 0; color: var(--ink-2); font-size: var(--fs-sm); }
h3 { margin: 0 0 12px; font-size: var(--fs-h3); }
.hint { margin: 0 0 12px; color: var(--ink-3); font-size: var(--fs-xs); }
.mine-list { display: flex; flex-direction: column; gap: 10px; }
.mine-item { border: 1px solid var(--line); border-radius: var(--radius-md); padding: 12px 14px; }
.mi-head { display: flex; justify-content: space-between; align-items: center; }
.mi-body { display: flex; gap: 18px; flex-wrap: wrap; margin-top: 6px; font-size: var(--fs-sm); color: var(--ink-2); }
.plan-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 16px; }
.plan { display: flex; flex-direction: column; }
.plan-head { display: flex; justify-content: space-between; align-items: center; }
.plan-head h4 { margin: 0; font-size: var(--fs-h3); }
.price { margin: 10px 0 6px; display: flex; align-items: baseline; gap: 10px; }
.price .now { font-size: 28px; font-weight: 700; color: var(--brand-ink); }
.price .was { color: var(--ink-3); text-decoration: line-through; font-size: var(--fs-sm); }
.desc { margin: 0 0 10px; color: var(--ink-2); font-size: var(--fs-sm); min-height: 38px; }
.benefits { margin: 0 0 16px; padding-left: 18px; color: var(--ink-2); font-size: var(--fs-sm); line-height: 1.9; flex: 1; }
.buy { width: 100%; }
.delta-minus { color: var(--danger-fg); font-weight: 700; }
.delta-zero { color: var(--ink-3); }
</style>
