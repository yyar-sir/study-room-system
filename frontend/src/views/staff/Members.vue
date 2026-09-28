<template>
  <div class="members">
    <div class="page-head">
      <div>
        <h1>会员业务办理</h1>
        <p class="sub">为用户办理套餐开通、续费与变更。办理后会落订单、落权益并记账本流水，同时通知用户。</p>
      </div>
    </div>

    <section class="k-card">
      <h3>① 查询用户</h3>
      <div class="row">
        <el-input
          v-model="phone"
          maxlength="11"
          placeholder="输入用户手机号，如 13900000002"
          @keyup.enter="query"
        />
        <el-button type="primary" :loading="querying" @click="query">查询</el-button>
      </div>
      <p v-if="queried && !member" class="hint">未查询到该用户</p>
    </section>

    <template v-if="member">
      <section class="k-card">
        <h3>② 用户信息</h3>
        <dl>
          <dt>手机号</dt><dd>{{ member.phone }}</dd>
          <dt>姓名</dt><dd>{{ member.realName || '—' }}</dd>
          <dt>昵称</dt><dd>{{ member.nickname || '—' }}</dd>
          <dt>累计自习</dt><dd>{{ member.totalStudyMinutes || 0 }} 分钟</dd>
        </dl>

        <h4>已有权益</h4>
        <el-table :data="member.memberships || []" size="small">
          <el-table-column prop="planName" label="套餐" min-width="140" />
          <el-table-column label="剩余次数" width="110">
            <template #default="{ row }">
              {{ row.remainTimes === -1 ? '不限次' : row.remainTimes }}
            </template>
          </el-table-column>
          <el-table-column label="到期" width="130">
            <template #default="{ row }">{{ (row.expireTime || '').slice(0, 10) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <span class="k-tag" :class="row.usable ? 'k-tag--green' : ''">{{ row.statusText }}</span>
            </template>
          </el-table-column>
          <template #empty>
            <EmptyState
              size="sm"
              icon="💳"
              title="该用户还没有任何套餐权益"
              hint="在下方「办理套餐」里选中一个套餐，点「确认为该用户办理」即可为其开通"
            />
          </template>
        </el-table>
      </section>

      <section class="k-card">
        <h3>③ 办理套餐</h3>
        <p class="hint">同一套餐未过期时，办理会顺延有效期并累加次数（即续费），不会产生重复权益。</p>
        <div class="plan-pick">
          <div
            v-for="p in plans"
            :key="p.id"
            class="plan-card"
            :class="{ active: selectedPlanId === p.id, off: p.status !== 1 }"
            @click="p.status === 1 && (selectedPlanId = p.id)"
          >
            <div class="pc-head">
              <b>{{ p.name }}</b>
              <span class="k-tag" :class="p.status === 1 ? 'k-tag--orange' : ''">
                {{ p.status === 1 ? typeText(p.planType) : '已下架' }}
              </span>
            </div>
            <div class="pc-price">¥{{ p.price }}</div>
            <div class="pc-sub">
              {{ p.durationDays }} 天 · {{ p.totalTimes === -1 ? '不限次' : p.totalTimes + ' 次' }}
              · 每日上限 {{ p.dailyLimit }}
            </div>
          </div>
        </div>

        <div class="pay-row">
          <span class="label">收款方式</span>
          <el-radio-group v-model="payType">
            <el-radio :value="1">前台代收（记为模拟支付）</el-radio>
          </el-radio-group>
        </div>

        <el-button
          type="primary"
          class="submit"
          :loading="granting"
          :disabled="!selectedPlanId"
          @click="grant"
        >确认为该用户办理</el-button>
      </section>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { memberByPhone, grantMembership } from '@/api/admin'
import { listPlans } from '@/api/booking'
import EmptyState from '@/components/EmptyState.vue'

const phone = ref('')
const querying = ref(false)
const queried = ref(false)
const member = ref(null)

const plans = ref([])
const selectedPlanId = ref(null)
const payType = ref(1)
const granting = ref(false)

const PLAN_TYPE = { 1: '次卡', 2: '月卡', 3: '季卡', 4: '年卡' }
const typeText = (t) => PLAN_TYPE[t] || '套餐'

async function query() {
  if (!/^1[3-9]\d{9}$/.test(phone.value.trim())) {
    ElMessage.warning('请输入正确的 11 位手机号')
    return
  }
  querying.value = true
  member.value = null
  selectedPlanId.value = null
  try {
    member.value = await memberByPhone(phone.value.trim())
  } catch (e) {
    // 拦截器已提示（手机号不存在会返回 1002）
  } finally {
    queried.value = true
    querying.value = false
  }
}

async function grant() {
  const plan = plans.value.find((p) => p.id === selectedPlanId.value)
  if (!plan) return
  try {
    await ElMessageBox.confirm(
      `确认为 ${member.value.phone} 办理「${plan.name}」（¥${plan.price}）？`,
      '办理确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  granting.value = true
  try {
    const vo = await grantMembership({
      phone: member.value.phone,
      planId: plan.id,
      payType: payType.value
    })
    ElMessage.success(`已办理「${vo.planName}」，到期 ${(vo.expireTime || '').slice(0, 10)}`)
    await query()          // 刷新该用户权益
  } catch (e) {
    // 拦截器已提示
  } finally {
    granting.value = false
  }
}

onMounted(async () => {
  try {
    // 刻意用公开的套餐接口，而不是 /api/owner/plans：
    // 后者是经营者专属（role 0），前台调用会 403。前台办理业务本来也只该看到
    // 【上架中】的套餐 —— 已下架的套餐不应该再卖出去。
    plans.value = await listPlans()
  } catch (e) {
    // 拦截器已提示
  }
})
</script>

<style scoped>
.members { display: flex; flex-direction: column; gap: 18px; }
.page-head h1 { margin: 0 0 6px; font-size: var(--fs-h1); }
.sub { margin: 0; color: var(--ink-2); font-size: var(--fs-sm); max-width: 760px; line-height: 1.7; }
h3 { margin: 0 0 12px; font-size: var(--fs-h3); }
h4 { margin: 18px 0 10px; font-size: var(--fs-sm); color: var(--ink-2); }
.row { display: flex; gap: 10px; }
.hint { margin: 10px 0 0; color: var(--ink-3); font-size: var(--fs-xs); }
dl { display: grid; grid-template-columns: 90px 1fr; gap: 8px 10px; margin: 0; font-size: var(--fs-sm); }
dt { color: var(--ink-3); }
dd { margin: 0; font-weight: 600; }
.plan-pick { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 12px; margin-bottom: 16px; }
.plan-card { border: 1px solid var(--line); border-radius: var(--radius-md); padding: 12px 14px; cursor: pointer; transition: border-color .18s, background .18s; }
.plan-card:hover { border-color: var(--brand); }
.plan-card.active { border-color: var(--brand); background: var(--brand-soft); }
.plan-card.off { opacity: .5; cursor: not-allowed; }
.pc-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.pc-price { font-size: 22px; font-weight: 700; color: var(--brand-ink); margin: 6px 0 4px; }
.pc-sub { color: var(--ink-3); font-size: var(--fs-xs); }
.pay-row { display: flex; align-items: center; gap: 14px; margin-bottom: 16px; }
.pay-row .label { font-size: var(--fs-xs); color: var(--ink-3); font-weight: 600; }
.submit { width: 100%; }
</style>
