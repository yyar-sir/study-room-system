<template>
  <div class="logs">
    <div class="page-head">
      <div>
        <h1>操作日志</h1>
        <p class="sub">
          谁在什么时候改了什么 —— 由后端 AOP 切面自动记录的审计流水。
          只记录写操作（POST / PUT / DELETE），登录口令等敏感字段已脱敏为 ***。
        </p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <section class="k-card">
      <div class="filter-bar">
        <el-select
          v-model="filters.module"
          clearable
          placeholder="全部模块"
          style="width: 200px"
          aria-label="按模块筛选"
        >
          <!-- 选项来自实际返回数据里出现过的 module，不硬编码：后端新增控制器时这里自动跟上 -->
          <el-option v-for="m in moduleOptions" :key="m" :label="m" :value="m" />
        </el-select>
        <el-select
          v-model="filters.success"
          clearable
          placeholder="全部结果"
          style="width: 150px"
          aria-label="按执行结果筛选"
        >
          <el-option label="成功" :value="1" />
          <el-option label="失败" :value="0" />
        </el-select>
        <span class="filter-count">共 {{ list.length }} 条</span>
      </div>

      <el-table :data="list" size="small" :row-class-name="rowClass">
        <!-- 参数 JSON 可能很长，单独用展开行铺开看，避免撑坏正常列宽 -->
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="detail">
              <div class="detail__title">请求参数（已脱敏）</div>
              <pre class="detail__json">{{ prettyParams(row.params) }}</pre>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ (row.createdAt || '').replace('T', ' ').slice(0, 19) }}</template>
        </el-table-column>
        <el-table-column label="操作人" width="130">
          <template #default="{ row }">
            <span :class="{ 'is-anon': !row.username }">{{ row.username || '匿名' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="module" label="模块" width="140" show-overflow-tooltip />
        <el-table-column prop="action" label="操作" width="130" show-overflow-tooltip />
        <el-table-column label="请求" min-width="220" show-overflow-tooltip>
          <template #default="{ row }"><span class="mono">{{ row.method || '—' }}</span></template>
        </el-table-column>
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <!-- 失败不只靠文字区分：粉色标签 + 整行淡红底色，扫一眼就能定位异常 -->
            <span class="k-tag" :class="row.success === 1 ? 'k-tag--green' : 'k-tag--pink'">
              {{ row.success === 1 ? '成功' : '失败' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="90">
          <template #default="{ row }">
            <span class="mono">{{ row.costMs === null || row.costMs === undefined ? '—' : row.costMs + ' ms' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="140" show-overflow-tooltip />
        <template #empty>
          <EmptyState
            size="sm"
            icon="🧾"
            title="暂无操作日志"
            hint="日志由系统自动记录，执行任意写操作后这里就会出现记录"
          />
        </template>
      </el-table>

      <p class="hint">
        说明：默认按时间倒序返回最近 100 条，最多 500 条；
        接口只做查询、不产生日志，所以反复刷新不会自己把自己写进日志里。
      </p>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import EmptyState from '@/components/EmptyState.vue'
import { ownerOperationLogs } from '@/api/admin'

const list = ref([])
const loading = ref(false)
// success 不传 = 全部；0 = 失败，1 = 成功，与后端字段语义一致
const filters = reactive({ module: null, success: null })
// 见过的模块名会累积下来：切换筛选后下拉框不会因为服务端已过滤而丢掉其它模块
const seenModules = ref([])

const moduleOptions = computed(() => [...seenModules.value].sort((a, b) => a.localeCompare(b)))

function rowClass({ row }) {
  return row.success === 1 ? '' : 'row-failed'
}

/** params 是 JSON 字符串（可能为 null / 为空串），格式化成便于阅读的多行文本 */
function prettyParams(raw) {
  if (raw === null || raw === undefined || raw === '') return '（本次请求无参数）'
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch (e) {
    // 不是合法 JSON 时原样展示，总比丢掉内容好
    return String(raw)
  }
}

async function load() {
  loading.value = true
  try {
    // 空值不提交，避免后端把 null 当成有效的 module/success 条件
    const params = {}
    if (filters.module) params.module = filters.module
    if (filters.success === 0 || filters.success === 1) params.success = filters.success
    list.value = await ownerOperationLogs(params)

    const known = new Set(seenModules.value)
    for (const row of list.value) {
      if (row.module) known.add(row.module)
    }
    seenModules.value = [...known]
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.logs { display: flex; flex-direction: column; gap: 16px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; flex-wrap: wrap; }
.page-head h1 { margin: 0 0 6px; font-size: var(--fs-h1); }
.sub { margin: 0; color: var(--ink-2); font-size: var(--fs-sm); max-width: 720px; line-height: 1.7; }

.filter-bar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
  margin-bottom: var(--space-4);
}
.filter-count { color: var(--ink-3); font-size: var(--fs-xs); }

.mono { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: var(--fs-xs); }
.is-anon { color: var(--ink-3); }

/* 失败行整行着色；:deep 是因为 el-table 的行节点由组件内部渲染 */
:deep(.el-table__row.row-failed) { background: var(--danger-bg); }
:deep(.el-table__row.row-failed > td) { border-bottom-color: var(--danger-line); }
:deep(.el-table__row.row-failed > td:first-child) { box-shadow: inset 3px 0 0 var(--danger-fg); }

.detail { padding: var(--space-2) var(--space-5) var(--space-4); }
.detail__title { margin-bottom: var(--space-2); color: var(--ink-3); font-size: var(--fs-xs); }
.detail__json {
  margin: 0;
  padding: var(--space-3) var(--space-4);
  background: var(--surface-soft);
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  color: var(--ink-2);
  font-size: var(--fs-xs);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 320px;
  overflow: auto;
}

.hint { margin: 14px 0 0; color: var(--ink-3); font-size: var(--fs-xs); line-height: 1.8; }
</style>
