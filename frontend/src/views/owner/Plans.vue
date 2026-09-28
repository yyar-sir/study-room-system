<template>
  <div class="plans">
    <div class="page-head">
      <div>
        <h1>会员套餐配置</h1>
        <p class="sub">
          设置次卡 / 月卡 / 季卡的价格、有效期与每日预约上限。下架用状态位，不删除记录 ——
          历史订单与用户权益都引用了套餐。
        </p>
      </div>
      <el-button type="primary" @click="open(null)">新建套餐</el-button>
    </div>

    <section class="k-card">
      <el-table :data="list" size="small">
        <el-table-column prop="name" label="套餐名称" min-width="150" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <span class="k-tag k-tag--orange">{{ typeText(row.planType) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="售价" width="100">
          <template #default="{ row }">¥{{ row.price }}</template>
        </el-table-column>
        <el-table-column label="原价" width="100">
          <template #default="{ row }">{{ row.originalPrice ? '¥' + row.originalPrice : '—' }}</template>
        </el-table-column>
        <el-table-column label="有效天数" width="100">
          <template #default="{ row }">{{ row.durationDays }}</template>
        </el-table-column>
        <el-table-column label="可用次数" width="100">
          <template #default="{ row }">{{ row.totalTimes === -1 ? '不限次' : row.totalTimes }}</template>
        </el-table-column>
        <el-table-column label="每日上限" width="100">
          <template #default="{ row }">{{ row.dailyLimit }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="k-tag" :class="row.status === 1 ? 'k-tag--green' : ''">
              {{ row.status === 1 ? '已上架' : '已下架' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" @click="open(row)">编辑</el-button>
            <el-button size="small" @click="toggle(row)">
              {{ row.status === 1 ? '下架' : '上架' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState
            size="sm"
            icon="📋"
            title="暂无套餐"
            hint="点击右上角「新建套餐」配置第一张次卡或月卡"
          />
        </template>
      </el-table>
    </section>

    <el-dialog v-model="dialog" :title="form.id ? '编辑套餐' : '新建套餐'" width="520px">
      <div class="field"><label>套餐名称</label><el-input v-model="form.name" maxlength="64" /></div>
      <div class="two">
        <div class="field">
          <label>套餐类型</label>
          <el-select v-model="form.planType" style="width:100%">
            <el-option label="次卡" :value="1" />
            <el-option label="月卡" :value="2" />
            <el-option label="季卡" :value="3" />
            <el-option label="年卡" :value="4" />
          </el-select>
        </div>
        <div class="field"><label>排序（越小越靠前）</label><el-input-number v-model="form.sort" :min="0" :max="999" style="width:100%" /></div>
      </div>
      <div class="two">
        <div class="field"><label>售价（元）</label><el-input-number v-model="form.price" :min="0" :precision="2" style="width:100%" /></div>
        <div class="field"><label>原价（元，可空）</label><el-input-number v-model="form.originalPrice" :min="0" :precision="2" style="width:100%" /></div>
      </div>
      <div class="two">
        <div class="field"><label>有效天数</label><el-input-number v-model="form.durationDays" :min="1" :max="3650" style="width:100%" /></div>
        <div class="field">
          <label>可用次数（-1 为不限次）</label>
          <el-input-number v-model="form.totalTimes" :min="-1" :max="9999" style="width:100%" />
        </div>
      </div>
      <div class="two">
        <div class="field"><label>每日预约上限</label><el-input-number v-model="form.dailyLimit" :min="1" :max="20" style="width:100%" /></div>
        <div class="field">
          <label>状态</label>
          <el-radio-group v-model="form.status">
            <el-radio :value="1">上架</el-radio>
            <el-radio :value="0">下架</el-radio>
          </el-radio-group>
        </div>
      </div>
      <div class="field"><label>权益说明（分号分隔，展示在套餐卡片上）</label><el-input v-model="form.benefits" maxlength="500" /></div>
      <div class="field"><label>简介</label><el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" /></div>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import EmptyState from '@/components/EmptyState.vue'
import { ownerPlansAll, ownerSavePlan } from '@/api/admin'

const list = ref([])
const dialog = ref(false)
const saving = ref(false)

const form = reactive({
  id: null, name: '', planType: 1, price: 0, originalPrice: null,
  durationDays: 30, totalTimes: -1, dailyLimit: 1,
  benefits: '', description: '', status: 1, sort: 0
})

const PLAN_TYPE = { 1: '次卡', 2: '月卡', 3: '季卡', 4: '年卡' }
const typeText = (t) => PLAN_TYPE[t] || '套餐'

async function load() {
  try {
    list.value = await ownerPlansAll()
  } catch (e) {
    // 拦截器已提示
  }
}

function open(row) {
  Object.assign(form, row
    ? { ...row }
    : {
        id: null, name: '', planType: 1, price: 0, originalPrice: null,
        durationDays: 30, totalTimes: -1, dailyLimit: 1,
        benefits: '', description: '', status: 1, sort: 0
      })
  dialog.value = true
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写套餐名称')
    return
  }
  saving.value = true
  try {
    await ownerSavePlan({ ...form })
    ElMessage.success('已保存')
    dialog.value = false
    await load()
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

/** 上下架只改状态位：套餐被订单与用户权益引用，删记录会让历史数据失去依据 */
async function toggle(row) {
  try {
    await ownerSavePlan({ id: row.id, name: row.name, price: row.price, status: row.status === 1 ? 0 : 1 })
    ElMessage.success(row.status === 1 ? '已下架' : '已上架')
    await load()
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(load)
</script>

<style scoped>
.plans { display: flex; flex-direction: column; gap: 18px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; flex-wrap: wrap; }
.page-head h1 { margin: 0 0 6px; font-size: var(--fs-h1); }
.sub { margin: 0; color: var(--ink-2); font-size: var(--fs-sm); max-width: 720px; line-height: 1.7; }
.field { display: flex; flex-direction: column; gap: 6px; margin-bottom: 14px; }
.field label { font-size: var(--fs-xs); color: var(--ink-3); font-weight: 600; }
.two { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
</style>
