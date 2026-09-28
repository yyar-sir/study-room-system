<template>
  <div class="violations">
    <div class="page-head">
      <div>
        <h1>违规占座处理</h1>
        <p class="sub">爽约由系统自动判定并扣次；前台核实后登记处理意见，用户会收到站内通知。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-tabs v-model="activeTab" @tab-change="load">
      <el-tab-pane label="待处理" name="0" />
      <el-tab-pane label="已处理" name="1" />
      <el-tab-pane label="全部" name="all" />
    </el-tabs>

    <section class="k-card">
      <el-table :data="list" size="small">
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <span class="k-tag k-tag--pink">{{ typeText(row.violationType) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="违规说明" min-width="240" />
        <el-table-column prop="penalty" label="处罚" width="180" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="k-tag" :class="row.status === 1 ? 'k-tag--green' : 'k-tag--orange'">
              {{ statusText(row.status) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="发生时间" width="170">
          <template #default="{ row }">{{ (row.createdAt || '').replace('T', ' ').slice(0, 16) }}</template>
        </el-table-column>
        <el-table-column label="处理意见" min-width="180">
          <template #default="{ row }">
            <span :class="{ muted: !row.handleRemark }">{{ row.handleRemark || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" size="small" type="primary" @click="open(row)">处理</el-button>
            <span v-else class="muted">已处理</span>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState
            size="sm"
            icon="⚖️"
            title="暂无违规记录"
            hint="可用上方「待处理 / 已处理 / 全部」切换查看其它范围；爽约由系统自动判定并扣次后才会生成记录"
          />
        </template>
      </el-table>
    </section>

    <el-dialog v-model="dialog" title="处理违规" width="460px">
      <div v-if="current" class="dialog-body">
        <dl>
          <dt>违规说明</dt><dd>{{ current.description }}</dd>
          <dt>系统处罚</dt><dd>{{ current.penalty || '—' }}</dd>
        </dl>
        <div class="field">
          <label>处理意见</label>
          <el-input
            v-model="form.handleRemark"
            type="textarea"
            :rows="3"
            maxlength="200"
            placeholder="例如：已电话核实，本次仅提醒"
          />
        </div>
        <div class="field">
          <label>处罚说明（会覆盖系统默认处罚文案）</label>
          <el-input v-model="form.penalty" maxlength="100" placeholder="选填，如：警告一次" />
        </div>
      </div>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">确认处理并通知用户</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { staffViolations, handleViolation } from '@/api/admin'
import EmptyState from '@/components/EmptyState.vue'

const list = ref([])
const loading = ref(false)
const activeTab = ref('0')
const dialog = ref(false)
const saving = ref(false)
const current = ref(null)

const form = reactive({ handleRemark: '', penalty: '' })

const TYPE_TEXT = { 1: '超时未签退', 2: '预约未到', 3: '占用他人座位', 4: '其他' }
const STATUS_TEXT = { 0: '待处理', 1: '已处理', 2: '已申诉' }

const typeText = (t) => TYPE_TEXT[t] || '其他'
const statusText = (s) => STATUS_TEXT[s] || '未知'

async function load() {
  loading.value = true
  try {
    list.value = await staffViolations(activeTab.value === 'all' ? null : Number(activeTab.value))
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function open(row) {
  current.value = row
  form.handleRemark = ''
  form.penalty = row.penalty || ''
  dialog.value = true
}

async function submit() {
  if (!form.handleRemark.trim()) {
    ElMessage.warning('请填写处理意见')
    return
  }
  saving.value = true
  try {
    await handleViolation(current.value.id, form.handleRemark.trim(), form.penalty.trim() || null)
    ElMessage.success('已处理，并已通知用户')
    dialog.value = false
    await load()
  } catch (e) {
    // 拦截器已提示（重复处理会返回 1002）
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.violations { display: flex; flex-direction: column; gap: 14px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; flex-wrap: wrap; }
.page-head h1 { margin: 0 0 6px; font-size: var(--fs-h1); }
.sub { margin: 0; color: var(--ink-2); font-size: var(--fs-sm); }
.muted { color: var(--ink-3); }
.dialog-body dl { display: grid; grid-template-columns: 80px 1fr; gap: 8px 10px; margin: 0 0 16px; font-size: var(--fs-sm); }
.dialog-body dt { color: var(--ink-3); }
.dialog-body dd { margin: 0; }
.field { display: flex; flex-direction: column; gap: 6px; margin-bottom: 14px; }
.field label { font-size: var(--fs-xs); color: var(--ink-3); font-weight: 600; }
</style>
