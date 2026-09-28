<template>
  <div class="seat-manage">
    <div class="page-head">
      <div>
        <h1>座位状态管理</h1>
        <p class="sub">点击座位后可调整其物理状态。维修中/已停用的座位，用户端将无法预约。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="main">
      <section class="k-card">
        <div class="legend">
          <span><i class="dot dot-free" />可用</span>
          <span><i class="dot dot-fix" />维修中</span>
          <span><i class="dot dot-off" />已停用</span>
          <span><i class="dot dot-pick" />已选中</span>
        </div>
        <div v-if="loading" class="empty">加载中…</div>
        <template v-else-if="!seats.length">
          <EmptyState
            size="md"
            icon="🪑"
            title="本门店还没有配置座位"
            hint="座位由经营者在「门店与座位布局」中添加，配置好后回到这里就能调整每个座位的状态"
          />
        </template>
        <template v-else>
          <div class="grid" :style="{ gridTemplateColumns: `repeat(${maxCol + 1}, 46px)` }">
            <button
              v-for="s in seats"
              :key="s.id"
              class="seat"
              :class="seatClass(s)"
              :style="{ gridRow: s.rowIndex + 2, gridColumn: s.colIndex + 1 }"
              :title="`${s.seatNo} · ${statusText(s.status)}${s.remark ? ' · ' + s.remark : ''}`"
              @click="selected = s"
            >{{ s.seatNo }}</button>
          </div>
        </template>
      </section>

      <aside class="k-card panel">
        <h3>座位操作</h3>
        <div v-if="!selected" class="empty">请先在左侧点击一个座位</div>
        <template v-else>
          <dl>
            <dt>座位号</dt><dd>{{ selected.seatNo }}</dd>
            <dt>当前状态</dt><dd>{{ statusText(selected.status) }}</dd>
            <dt>备注</dt><dd>{{ selected.remark || '—' }}</dd>
          </dl>

          <div class="field">
            <label>调整为</label>
            <el-radio-group v-model="form.status">
              <el-radio :value="0">可用</el-radio>
              <el-radio :value="1">维修中</el-radio>
              <el-radio :value="2">已停用</el-radio>
            </el-radio-group>
          </div>

          <div class="field">
            <label>备注（如故障原因）</label>
            <el-input v-model="form.remark" maxlength="100" placeholder="选填" />
          </div>

          <el-button type="primary" class="submit" :loading="saving" @click="save">提交变更</el-button>
        </template>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { staffSeats, updateSeatStatus } from '@/api/admin'
import EmptyState from '@/components/EmptyState.vue'

const seats = ref([])
const selected = ref(null)
const loading = ref(false)
const saving = ref(false)

const form = reactive({ status: 0, remark: '' })

const maxCol = computed(() => seats.value.reduce((m, s) => Math.max(m, s.colIndex || 0), 0))

const STATUS_TEXT = { 0: '可用', 1: '维修中', 2: '已停用' }
const statusText = (s) => STATUS_TEXT[s] ?? '未知'

function seatClass(s) {
  return {
    picked: selected.value && selected.value.id === s.id,
    fix: s.status === 1,
    off: s.status === 2
  }
}

// 选中座位后，把表单同步为该座位的当前值，避免误提交上一个座位的状态
watch(selected, (s) => {
  if (s) {
    form.status = s.status ?? 0
    form.remark = s.remark || ''
  }
})

async function load() {
  loading.value = true
  try {
    seats.value = await staffSeats()
    if (selected.value) {
      selected.value = seats.value.find((x) => x.id === selected.value.id) || null
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!selected.value) return
  saving.value = true
  try {
    await updateSeatStatus(selected.value.id, form.status, form.remark)
    ElMessage.success(`座位 ${selected.value.seatNo} 已更新为「${statusText(form.status)}」`)
    await load()
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.seat-manage { display: flex; flex-direction: column; gap: 18px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; flex-wrap: wrap; }
.page-head h1 { margin: 0 0 6px; font-size: var(--fs-h1); }
.sub { margin: 0; color: var(--ink-2); font-size: var(--fs-sm); }
.main { display: grid; grid-template-columns: 1fr 300px; gap: 18px; align-items: start; }
.legend { display: flex; gap: 18px; flex-wrap: wrap; margin-bottom: 16px; font-size: var(--fs-xs); color: var(--ink-2); }
.legend span { display: inline-flex; align-items: center; gap: 6px; }
.dot { width: 12px; height: 12px; border-radius: var(--radius-xs); display: inline-block; }
.dot-free { background: var(--surface-soft); border: 1px solid var(--line); }
.dot-fix { background: var(--warn-bg); }
.dot-off { background: var(--muted-bg); }
.dot-pick { background: var(--brand); }
.grid { display: grid; gap: 8px; justify-content: start; }
.seat {
  width: 46px; height: 40px; border-radius: var(--radius-sm);
  border: 1px solid var(--line); background: var(--surface-soft);
  color: var(--ink-2); font-size: var(--fs-2xs); font-weight: 600; cursor: pointer;
  transition: transform .15s, background .15s, color .15s;
}
.seat:hover { transform: translateY(-2px); border-color: var(--brand); color: var(--brand-ink); }
.seat.fix { background: var(--warn-bg); color: var(--warn-fg); border-color: var(--warn-line); }
.seat.off { background: var(--muted-bg); color: var(--muted-fg); border-color: var(--muted-line); }
.seat.picked { background: var(--brand); color: #fff; border-color: var(--brand); }
.panel h3 { margin: 0 0 14px; font-size: var(--fs-h3); }
.panel dl { display: grid; grid-template-columns: 70px 1fr; gap: 8px 10px; margin: 0 0 16px; font-size: var(--fs-sm); }
.panel dt { color: var(--ink-3); }
.panel dd { margin: 0; font-weight: 600; }
.field { display: flex; flex-direction: column; gap: 6px; margin-bottom: 14px; }
.field label { font-size: var(--fs-xs); color: var(--ink-3); font-weight: 600; }
.submit { width: 100%; }
.empty { color: var(--ink-3); font-size: var(--fs-sm); padding: 24px 0; text-align: center; }
@media (max-width: 900px) { .main { grid-template-columns: 1fr; } }
</style>
