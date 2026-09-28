<template>
  <div class="ann">
    <div class="page-head">
      <div>
        <h1>公告发布</h1>
        <p class="sub">
          发布门店临时通知、节假日运营安排。前台只能发本门店公告，全平台公告由经营者发布。
        </p>
      </div>
      <el-button type="primary" @click="open(null)">发布公告</el-button>
    </div>

    <section class="k-card">
      <el-table :data="list" size="small">
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
        <el-table-column label="范围" width="100">
          <template #default="{ row }">
            <span class="k-tag" :class="row.scope === 1 ? 'k-tag--orange' : ''">
              {{ row.scope === 1 ? '全平台' : '本门店' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="k-tag" :class="row.status === 1 ? 'k-tag--green' : ''">
              {{ row.status === 1 ? '已发布' : '已下架' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="发布时间" width="170">
          <template #default="{ row }">{{ (row.publishTime || '').replace('T', ' ').slice(0, 16) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" @click="open(row)">编辑</el-button>
            <el-button v-if="row.status === 1" size="small" @click="offline(row)">下架</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState
            size="sm"
            icon="📢"
            title="本门店还没有发布过公告"
            hint="点击右上角「发布公告」填写标题与内容，保存后用户端立即可见"
          />
        </template>
      </el-table>
    </section>

    <el-dialog v-model="dialog" :title="form.id ? '编辑公告' : '发布公告'" width="520px">
      <div class="field"><label>标题</label><el-input v-model="form.title" maxlength="128" /></div>
      <div class="field">
        <label>内容</label>
        <el-input v-model="form.content" type="textarea" :rows="5" maxlength="1000" show-word-limit />
      </div>
      <div class="field">
        <label>发布范围</label>
        <el-radio-group v-model="form.scope">
          <el-radio :value="2">本门店</el-radio>
          <el-radio :value="1" :disabled="!isOwner">全平台（仅经营者）</el-radio>
        </el-radio-group>
      </div>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存并发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { staffAnnouncements, saveAnnouncement, offlineAnnouncement } from '@/api/admin'
import EmptyState from '@/components/EmptyState.vue'

const list = ref([])
const dialog = ref(false)
const saving = ref(false)

const form = reactive({ id: null, title: '', content: '', scope: 2 })

/** 前台不能发全平台公告（后端也会强制改回本门店，这里只是不让点） */
const isOwner = computed(() => {
  try {
    return Number(JSON.parse(localStorage.getItem('userInfo') || '{}').role) === 0
  } catch (e) {
    return false
  }
})

async function load() {
  try {
    list.value = await staffAnnouncements(true)
  } catch (e) {
    // 拦截器已提示
  }
}

function open(row) {
  Object.assign(form, row
    ? { id: row.id, title: row.title, content: row.content, scope: row.scope }
    : { id: null, title: '', content: '', scope: 2 })
  dialog.value = true
}

async function save() {
  if (!form.title.trim() || !form.content.trim()) {
    ElMessage.warning('标题与内容都不能为空')
    return
  }
  saving.value = true
  try {
    await saveAnnouncement({ ...form })
    ElMessage.success('已发布')
    dialog.value = false
    await load()
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function offline(row) {
  try {
    await ElMessageBox.confirm(`确认下架「${row.title}」？用户端将不再展示。`, '下架公告', { type: 'warning' })
  } catch {
    return
  }
  try {
    await offlineAnnouncement(row.id)
    ElMessage.success('已下架')
    await load()
  } catch (e) {
    // 拦截器已提示（跨门店会 403）
  }
}

onMounted(load)
</script>

<style scoped>
.ann { display: flex; flex-direction: column; gap: 16px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; flex-wrap: wrap; }
.page-head h1 { margin: 0 0 6px; font-size: var(--fs-h1); }
.sub { margin: 0; color: var(--ink-2); font-size: var(--fs-sm); max-width: 720px; line-height: 1.7; }
.field { display: flex; flex-direction: column; gap: 6px; margin-bottom: 14px; }
.field label { font-size: var(--fs-xs); color: var(--ink-3); font-weight: 600; }
</style>
