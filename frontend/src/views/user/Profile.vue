<template>
  <div class="profile">
    <div class="page-head">
      <div>
        <h1>个人中心</h1>
        <p class="sub">维护个人资料、查看学习与消费数据、修改登录密码。</p>
      </div>
    </div>

    <!-- 学习概览 -->
    <section class="kpis">
      <div class="kpi k-card">
        <div class="kpi-label">累计学习时长</div>
        <div class="kpi-value">{{ studyHours }}<span class="kpi-unit">小时</span></div>
      </div>
      <div class="kpi k-card">
        <div class="kpi-label">累计预约</div>
        <div class="kpi-value">{{ reservations.length }}<span class="kpi-unit">次</span></div>
      </div>
      <div class="kpi k-card">
        <div class="kpi-label">已完成自习</div>
        <div class="kpi-value">{{ finishedCount }}<span class="kpi-unit">次</span></div>
      </div>
      <div class="kpi k-card">
        <div class="kpi-label">当前有效期套餐</div>
        <div class="kpi-value">{{ usableCount }}<span class="kpi-unit">个</span></div>
      </div>
    </section>

    <div class="cols">
      <!-- 资料 -->
      <section class="k-card">
        <h3>基本资料</h3>
        <div class="field">
          <label>手机号（登录账号，不可修改）</label>
          <el-input :model-value="profile.phone" disabled />
        </div>
        <div class="two">
          <div class="field"><label>昵称</label><el-input v-model="form.nickname" maxlength="16" /></div>
          <div class="field"><label>姓名</label><el-input v-model="form.realName" maxlength="16" /></div>
        </div>
        <div class="field">
          <label>性别</label>
          <el-radio-group v-model="form.gender">
            <el-radio :value="0">未设置</el-radio>
            <el-radio :value="1">男</el-radio>
            <el-radio :value="2">女</el-radio>
          </el-radio-group>
        </div>
        <el-button type="primary" :loading="savingProfile" @click="saveProfile">保存资料</el-button>
      </section>

      <!-- 改密 -->
      <section class="k-card">
        <h3>修改密码</h3>
        <p class="hint">
          修改成功后，此前签发的所有登录凭证会立即失效，需要重新登录（这是 JWT 无状态令牌的必要补偿）。
        </p>
        <div class="field"><label>原密码</label><el-input v-model="pwd.oldPassword" show-password /></div>
        <div class="field"><label>新密码（6-20 位）</label><el-input v-model="pwd.newPassword" show-password /></div>
        <el-button type="primary" :loading="savingPwd" @click="savePassword">修改密码</el-button>
      </section>
    </div>

    <!-- 近期消费 -->
    <section class="k-card">
      <div class="sec-head">
        <h3>近期消费与权益流水</h3>
        <RouterLink to="/user/plans" class="more">查看全部 →</RouterLink>
      </div>
      <el-table :data="records.slice(0, 5)" size="small">
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ (row.createdAt || '').replace('T', ' ').slice(0, 16) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }"><span class="k-tag">{{ bizText(row.bizType) }}</span></template>
        </el-table-column>
        <el-table-column prop="remark" label="说明" min-width="200" />
        <el-table-column label="次数" width="90">
          <template #default="{ row }">
            <span :class="row.timesDelta < 0 ? 'minus' : ''">
              {{ row.timesDelta > 0 ? '+' + row.timesDelta : (row.timesDelta === 0 ? '—' : row.timesDelta) }}
            </span>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState
            size="sm"
            icon="🧾"
            title="还没有消费记录"
            hint="在「会员套餐」页购买套餐后，最近 5 条权益流水会显示在这里。"
          />
        </template>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { me, updateProfile, changePassword } from '@/api/auth'
import { myReservations } from '@/api/reservation'
import { myMemberships, consumptionRecords } from '@/api/member'
import EmptyState from '@/components/EmptyState.vue'

const profile = ref({})
const reservations = ref([])
const memberships = ref([])
const records = ref([])

const savingProfile = ref(false)
const savingPwd = ref(false)

const form = reactive({ nickname: '', realName: '', gender: 0 })
const pwd = reactive({ oldPassword: '', newPassword: '' })

const BIZ_TYPE = { 1: '购买', 2: '预约扣次', 3: '取消返还', 4: '退款', 5: '爽约扣减' }
const bizText = (t) => BIZ_TYPE[t] || '其它'

const studyHours = computed(() => ((profile.value.totalStudyMinutes || 0) / 60).toFixed(1))
const finishedCount = computed(() => reservations.value.filter((r) => r.status === 2).length)
const usableCount = computed(() => memberships.value.filter((m) => m.usable).length)

async function load() {
  try {
    profile.value = await me()
    Object.assign(form, {
      nickname: profile.value.nickname || '',
      realName: profile.value.realName || '',
      gender: profile.value.gender ?? 0
    })
    reservations.value = await myReservations(null)
    memberships.value = await myMemberships()
    records.value = await consumptionRecords()
  } catch (e) {
    // 拦截器已提示
  }
}

async function saveProfile() {
  if (!form.nickname.trim()) {
    ElMessage.warning('昵称不能为空')
    return
  }
  savingProfile.value = true
  try {
    profile.value = await updateProfile({ ...form })
    // 同步本地缓存，避免顶栏显示的还是旧昵称
    const info = JSON.parse(localStorage.getItem('userInfo') || '{}')
    localStorage.setItem('userInfo', JSON.stringify({ ...info, ...form }))
    ElMessage.success('资料已保存')
  } catch (e) {
    // 拦截器已提示
  } finally {
    savingProfile.value = false
  }
}

async function savePassword() {
  if (!pwd.oldPassword || !pwd.newPassword) {
    ElMessage.warning('请填写原密码与新密码')
    return
  }
  if (pwd.newPassword.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  savingPwd.value = true
  try {
    await changePassword({ ...pwd })
    ElMessage.success('密码已修改，请重新登录')
    pwd.oldPassword = ''
    pwd.newPassword = ''
    // 改密后旧 token 已失效，直接引导重新登录
    setTimeout(() => {
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      window.location.href = '/login'
    }, 800)
  } catch (e) {
    // 拦截器已提示（原密码不正确等）
  } finally {
    savingPwd.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.profile { display: flex; flex-direction: column; gap: 18px; }
.page-head h1 { margin: 0 0 6px; font-size: var(--fs-h1); }
.sub { margin: 0; color: var(--ink-2); font-size: var(--fs-sm); }
.kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 14px; }
.kpi { padding: 16px 18px; }
.kpi-label { color: var(--ink-3); font-size: var(--fs-xs); }
.kpi-value { margin-top: 8px; font-size: var(--fs-stat); font-weight: 700; color: var(--brand-deep); }
.kpi-unit { font-size: var(--fs-sm); font-weight: 600; color: var(--ink-2); margin-left: 3px; }
.cols { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
h3 { margin: 0 0 12px; font-size: var(--fs-h3); }
.hint { margin: 0 0 12px; color: var(--ink-3); font-size: var(--fs-xs); line-height: 1.8; }
.field { display: flex; flex-direction: column; gap: 6px; margin-bottom: 14px; }
.field label { font-size: var(--fs-xs); color: var(--ink-3); font-weight: 600; }
.two { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.sec-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.sec-head h3 { margin: 0; }
.more { font-size: var(--fs-sm); color: var(--ink-3); }
.more:hover { color: var(--brand-ink); }
.minus { color: var(--danger-fg); font-weight: 700; }
@media (max-width: 900px) { .cols { grid-template-columns: 1fr; } }
</style>
