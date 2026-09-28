<template>
  <div class="backup">
    <div class="page-head">
      <div>
        <h1>数据备份</h1>
        <p class="sub">
          调用 mysqldump 导出数据库，备份文件与备份记录分别保存在磁盘与数据库中。
        </p>
      </div>
      <el-button type="primary" :loading="running" @click="run">立即备份</el-button>
    </div>

    <el-alert
      v-if="lastFailed"
      type="warning"
      :closable="false"
      show-icon
      title="最近一次备份失败"
      :description="lastFailed.remark"
    />

    <section class="k-card">
      <el-table :data="list" size="small">
        <el-table-column prop="fileName" label="备份文件" min-width="240" />
        <el-table-column label="大小" width="120">
          <template #default="{ row }">{{ sizeText(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="方式" width="90">
          <template #default="{ row }">{{ row.backupType === 2 ? '定时' : '手动' }}</template>
        </el-table-column>
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <span class="k-tag" :class="row.status === 1 ? 'k-tag--green' : 'k-tag--pink'">
              {{ row.status === 1 ? '成功' : '失败' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ (row.createdAt || '').replace('T', ' ').slice(0, 19) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
        <template #empty>
          <EmptyState
            size="sm"
            icon="📋"
            title="暂无备份记录"
            hint="点击右上角「立即备份」生成第一份数据库备份"
          />
        </template>
      </el-table>
      <p class="hint">
        说明：备份失败同样会留记录并写明原因，不会静默丢失 ——
        否则"没备份成功"这件事将完全无迹可查。
      </p>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import EmptyState from '@/components/EmptyState.vue'
import { runBackup, backupRecords } from '@/api/admin'

const list = ref([])
const running = ref(false)

const lastFailed = computed(() => list.value.find((x) => x.status === 0) || null)

function sizeText(bytes) {
  const n = Number(bytes || 0)
  if (n <= 0) return '—'
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / 1024 / 1024).toFixed(2) + ' MB'
}

async function load() {
  try {
    list.value = await backupRecords()
  } catch (e) {
    // 拦截器已提示
  }
}

async function run() {
  running.value = true
  try {
    const r = await runBackup()
    if (r.status === 1) {
      ElMessage.success(`备份成功：${r.fileName}（${sizeText(r.fileSize)}）`)
    } else {
      ElMessage.warning('备份失败：' + (r.remark || '未知原因'))
    }
    await load()
  } catch (e) {
    // 拦截器已提示
  } finally {
    running.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.backup { display: flex; flex-direction: column; gap: 16px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; flex-wrap: wrap; }
.page-head h1 { margin: 0 0 6px; font-size: var(--fs-h1); }
.sub { margin: 0; color: var(--ink-2); font-size: var(--fs-sm); max-width: 720px; line-height: 1.7; }
.hint { margin: 14px 0 0; color: var(--ink-3); font-size: var(--fs-xs); line-height: 1.8; }
</style>
