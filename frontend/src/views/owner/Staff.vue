<template>
  <div class="staff">
    <div class="page-head">
      <div>
        <h1>人员权限管理</h1>
        <p class="sub">
          创建前台账号、分配所属门店（即数据访问范围）与岗位权限。前台账号只能在这里创建。
        </p>
      </div>
      <el-button type="primary" @click="openCreate">新建前台账号</el-button>
    </div>

    <section class="k-card">
      <el-table :data="list" size="small">
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="realName" label="姓名" width="100" />
        <el-table-column prop="nickname" label="昵称" width="110" />
        <el-table-column label="所属门店" width="160">
          <template #default="{ row }">
            {{ storeName(row.storeId) }}
            <el-button link type="primary" size="small" @click="openStore(row)">调整</el-button>
          </template>
        </el-table-column>
        <el-table-column label="岗位权限" min-width="240">
          <template #default="{ row }">
            <span v-if="!row.permissions || !row.permissions.length" class="muted">未分配</span>
            <span
              v-for="p in row.permissions"
              :key="p"
              class="k-tag perm"
            >{{ permLabel(p) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="k-tag" :class="row.status === 1 ? 'k-tag--green' : 'k-tag--pink'">
              {{ row.status === 1 ? '启用' : '已停用' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button size="small" @click="openPerm(row)">权限</el-button>
            <el-button size="small" @click="toggleStatus(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState
            size="sm"
            icon="📋"
            title="还没有员工账号"
            hint="点击右上角「新建前台账号」为前台创建登录账号"
          />
        </template>
      </el-table>
      <p class="hint">
        停用后该账号立即无法登录，其已签发的 token 也会失效（拦截器会校验账号状态）。
      </p>
    </section>

    <!-- 新建 -->
    <el-dialog v-model="createDialog" title="新建前台账号" width="480px">
      <div class="field"><label>手机号（登录账号）</label><el-input v-model="form.phone" maxlength="11" /></div>
      <div class="field"><label>初始密码（至少 6 位）</label><el-input v-model="form.password" show-password /></div>
      <div class="two">
        <div class="field"><label>姓名</label><el-input v-model="form.realName" maxlength="16" /></div>
        <div class="field"><label>昵称</label><el-input v-model="form.nickname" maxlength="16" /></div>
      </div>
      <div class="field">
        <label>所属门店（数据访问范围）</label>
        <el-select v-model="form.storeId" style="width:100%" placeholder="请选择门店">
          <el-option v-for="s in stores" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
      </div>
      <div class="field">
        <label>岗位权限</label>
        <el-checkbox-group v-model="form.permissions">
          <el-checkbox v-for="p in catalog" :key="p.key" :value="p.key">{{ p.label }}</el-checkbox>
        </el-checkbox-group>
      </div>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="create">创建</el-button>
      </template>
    </el-dialog>

    <!-- 权限 -->
    <el-dialog v-model="permDialog" title="设置岗位权限" width="420px">
      <p class="hint">提交时会整体覆盖该账号的权限集合。</p>
      <el-checkbox-group v-model="permForm">
        <el-checkbox v-for="p in catalog" :key="p.key" :value="p.key">{{ p.label }}</el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="permDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="savePerm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import EmptyState from '@/components/EmptyState.vue'
import {
  ownerStaffList,
  ownerCreateStaff,
  ownerSetStaffPermissions,
  ownerSetStaffStatus,
  ownerSetStaffStore,
  permissionCatalog,
  ownerStores
} from '@/api/admin'

const list = ref([])
const stores = ref([])
const catalog = ref([])
const saving = ref(false)

const createDialog = ref(false)
const permDialog = ref(false)
const permForm = ref([])
const currentId = ref(null)

const form = reactive({
  phone: '', password: '', realName: '', nickname: '', storeId: null, permissions: []
})

const storeName = (id) => stores.value.find((s) => s.id === id)?.name || '未分配'
const permLabel = (key) => catalog.value.find((p) => p.key === key)?.label || key

async function load() {
  try {
    list.value = await ownerStaffList()
  } catch (e) {
    // 拦截器已提示
  }
}

function openCreate() {
  Object.assign(form, { phone: '', password: '', realName: '', nickname: '', storeId: null, permissions: [] })
  createDialog.value = true
}

async function create() {
  if (!/^1[3-9]\d{9}$/.test(form.phone.trim())) {
    ElMessage.warning('手机号格式不正确')
    return
  }
  if (!form.password || form.password.length < 6) {
    ElMessage.warning('初始密码至少 6 位')
    return
  }
  if (!form.storeId) {
    ElMessage.warning('请选择所属门店')
    return
  }
  saving.value = true
  try {
    await ownerCreateStaff({ ...form, phone: form.phone.trim() })
    ElMessage.success('前台账号已创建')
    createDialog.value = false
    await load()
  } catch (e) {
    // 拦截器已提示（手机号已注册等）
  } finally {
    saving.value = false
  }
}

function openPerm(row) {
  currentId.value = row.id
  permForm.value = [...(row.permissions || [])]
  permDialog.value = true
}

async function savePerm() {
  saving.value = true
  try {
    await ownerSetStaffPermissions(currentId.value, permForm.value)
    ElMessage.success('权限已更新')
    permDialog.value = false
    await load()
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row) {
  const next = row.status === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(
      `确认${next === 1 ? '启用' : '停用'}账号 ${row.phone}？`,
      '状态变更',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await ownerSetStaffStatus(row.id, next)
    ElMessage.success('已更新')
    await load()
  } catch (e) {
    // 拦截器已提示
  }
}

async function openStore(row) {
  try {
    const { value } = await ElMessageBox.prompt(
      '输入新的门店 ID（数据访问范围）',
      `调整 ${row.phone} 的所属门店`,
      { inputValue: String(row.storeId || ''), inputPattern: /^\d+$/, inputErrorMessage: '请输入数字 ID' }
    )
    await ownerSetStaffStore(row.id, Number(value))
    ElMessage.success('已调整')
    await load()
  } catch (e) {
    // 用户取消或拦截器已提示
  }
}

onMounted(async () => {
  try {
    catalog.value = await permissionCatalog()
    stores.value = await ownerStores()
    await load()
  } catch (e) {
    // 拦截器已提示
  }
})
</script>

<style scoped>
.staff { display: flex; flex-direction: column; gap: 16px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; flex-wrap: wrap; }
.page-head h1 { margin: 0 0 6px; font-size: var(--fs-h1); }
.sub { margin: 0; color: var(--ink-2); font-size: var(--fs-sm); max-width: 760px; line-height: 1.7; }
.hint { margin: 14px 0 0; color: var(--ink-3); font-size: var(--fs-xs); line-height: 1.8; }
.perm { margin-right: 6px; }
.muted { color: var(--ink-3); }
.field { display: flex; flex-direction: column; gap: 6px; margin-bottom: 14px; }
.field label { font-size: var(--fs-xs); color: var(--ink-3); font-weight: 600; }
.two { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
</style>
