<template>
  <div class="settings">
    <div class="page-head">
      <div>
        <h1>运营规则与系统配置</h1>
        <p class="sub">
          这些规则直接决定预约与签到行为，修改后立即生效（业务代码每次实时读取，无需重启）。
        </p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-tabs v-model="activeGroup" @tab-change="load">
      <el-tab-pane label="运营规则" name="rule" />
      <el-tab-pane label="提醒设置" name="remind" />
      <el-tab-pane label="系统参数" name="system" />
    </el-tabs>

    <section class="k-card">
      <el-table :data="list" size="small">
        <el-table-column prop="configKey" label="配置键" width="220" />
        <el-table-column prop="description" label="说明" min-width="220" />
        <el-table-column label="当前值" width="220">
          <template #default="{ row }">
            <el-input v-model="row.configValue" size="small" @keyup.enter="save(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button size="small" type="primary" :loading="savingKey === row.configKey" @click="save(row)">
              保存
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState
            size="sm"
            icon="📋"
            title="暂无配置项"
            hint="切换上方「运营规则 / 提醒设置 / 系统参数」标签页查看其它分组，或点「刷新」重新加载"
          />
        </template>
      </el-table>
      <p class="hint">
        提示：改动会立刻影响用户端。例如把「可提前预约天数」改成 3 之后，
        用户就无法再预约 3 天以后的时段。
      </p>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import EmptyState from '@/components/EmptyState.vue'
import { ownerConfigs, ownerSaveConfig } from '@/api/admin'

const list = ref([])
const loading = ref(false)
const savingKey = ref(null)
const activeGroup = ref('rule')

async function load() {
  loading.value = true
  try {
    // 不传 storeId = 全局配置（storeId=0）
    list.value = await ownerConfigs(null, activeGroup.value)
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function save(row) {
  if (row.configValue === null || row.configValue === undefined || String(row.configValue).trim() === '') {
    ElMessage.warning('配置值不能为空')
    return
  }
  savingKey.value = row.configKey
  try {
    await ownerSaveConfig({
      storeId: row.storeId,
      configKey: row.configKey,
      configValue: String(row.configValue).trim(),
      configGroup: row.configGroup,
      description: row.description
    })
    ElMessage.success(`「${row.configKey}」已更新`)
    await load()
  } catch (e) {
    // 拦截器已提示
  } finally {
    savingKey.value = null
  }
}

onMounted(load)
</script>

<style scoped>
.settings { display: flex; flex-direction: column; gap: 14px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; flex-wrap: wrap; }
.page-head h1 { margin: 0 0 6px; font-size: var(--fs-h1); }
.sub { margin: 0; color: var(--ink-2); font-size: var(--fs-sm); }
.hint { margin: 14px 0 0; color: var(--ink-3); font-size: var(--fs-xs); line-height: 1.8; }
</style>
