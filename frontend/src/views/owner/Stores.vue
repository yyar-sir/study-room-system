<template>
  <div class="stores">
    <div class="page-head">
      <div>
        <h1>门店与座位布局</h1>
        <p class="sub">维护门店基础信息与座位布局。座位编号在同一门店内不可重复。</p>
      </div>
      <el-button type="primary" @click="openStoreDialog(null)">新建门店</el-button>
    </div>

    <div class="main">
      <!-- 门店列表 -->
      <section class="k-card">
        <h3>门店列表</h3>
        <div class="store-list">
          <template v-if="!stores.length">
            <EmptyState
              icon="🏬"
              title="还没有门店"
              hint="点击右上角「新建门店」录入第一家门店，之后可为它配置座位布局"
            />
          </template>
          <template v-else>
            <div
              v-for="s in stores"
              :key="s.id"
              class="store-item"
              :class="{ active: s.id === currentId }"
              @click="selectStore(s.id)"
            >
              <div class="si-main">
                <b>{{ s.name }}</b>
                <span class="k-tag" :class="s.status === 1 ? 'k-tag--green' : ''">
                  {{ s.status === 1 ? '营业中' : '已停业' }}
                </span>
              </div>
              <div class="si-sub">
                {{ s.address || '未填写地址' }} · 座位 {{ s.seatTotal || 0 }} 个
              </div>
              <div class="si-sub">营业 {{ fmt(s.openTime) }} - {{ fmt(s.closeTime) }}</div>
              <el-button size="small" @click.stop="openStoreDialog(s)">编辑</el-button>
            </div>
          </template>
        </div>
      </section>

      <!-- 座位布局 -->
      <section class="k-card">
        <div class="seat-head">
          <h3>座位布局{{ currentStore ? ' · ' + currentStore.name : '' }}</h3>
          <el-button size="small" type="primary" :disabled="!currentId" @click="openSeatDialog(null)">
            新增座位
          </el-button>
        </div>
        <p class="hint">点击座位可编辑编号、类型与网格位置。灰色为停用，橙色为维修中。</p>

        <div v-if="!currentId" class="empty">请先在左侧选择门店</div>
        <div v-else-if="!seats.length" class="empty">该门店还没有座位，点右上角新增</div>
        <div v-else class="grid" :style="{ gridTemplateColumns: `repeat(${maxCol + 1}, 46px)` }">
          <button
            v-for="s in seats"
            :key="s.id"
            class="seat"
            :class="{ fix: s.status === 1, off: s.status === 2 }"
            :style="{ gridRow: s.rowIndex + 2, gridColumn: s.colIndex + 1 }"
            :title="`${s.seatNo} · ${typeName(s.typeId)} · ${statusText(s.status)}`"
            @click="openSeatDialog(s)"
          >{{ s.seatNo }}</button>
        </div>

        <div class="legend">
          <span><i class="dot dot-free" />可用</span>
          <span><i class="dot dot-fix" />维修中</span>
          <span><i class="dot dot-off" />已停用</span>
        </div>
      </section>
    </div>

    <!-- 营业时段（门店营业时间之内的分段，属于可选的精细化配置） -->
    <section class="k-card">
      <div class="seat-head">
        <h3>营业时段{{ currentStore ? ' · ' + currentStore.name : '' }}</h3>
        <el-button
          size="small"
          type="primary"
          :disabled="!currentId"
          :loading="hourLoading"
          @click="openHourDialog(null)"
        >
          新增时段
        </el-button>
      </div>
      <!-- 这句必须常驻：使用者看到时段表才知道不配也能用，避免误以为必须配满一天 -->
      <p class="hint">
        本面板是可选的精细化配置：一天可以在门店营业时间内切成多段，并把某段标成「不可预约」。
        未配置任何时段时，预约校验会回落到门店营业时间{{
          currentStore ? `（${fmt(currentStore.openTime)}–${fmt(currentStore.closeTime)}）` : ''
        }}；一旦配置，预约必须完整落在所有「可预约」时段的并集之内，落在空隙（例如午休）会被拒绝。
      </p>

      <div v-if="!currentId" class="empty">请先在上方选择门店</div>
      <el-table v-else v-loading="hourLoading" :data="sortedHours" style="width: 100%">
        <el-table-column prop="name" label="时段名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="开始时间" width="110">
          <template #default="{ row }">{{ hms(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="110">
          <template #default="{ row }">{{ hms(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="可否预约" width="110">
          <!-- 与后端 bookable 字段语义一致：1 可预约，0 不可预约（如午休） -->
          <template #default="{ row }">
            <span class="k-tag" :class="row.bookable === 1 ? 'k-tag--green' : 'k-tag--pink'">
              {{ row.bookable === 1 ? '可预约' : '不可预约' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="k-tag" :class="row.status === 1 ? 'k-tag--teal' : ''">
              {{ row.status === 1 ? '启用' : '停用' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openHourDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" plain @click="removeHour(row)">删除</el-button>
          </template>
        </el-table-column>
        <!-- 空状态复用共享组件：表格内嵌必须用 sm，否则会把表格撑得很高 -->
        <template #empty>
          <EmptyState
            size="sm"
            icon="🕘"
            title="还没有配置营业时段"
            hint="未配置时段时按门店营业时间校验预约；如需排除午休等不可约区间，可点右上角「新增时段」"
          />
        </template>
      </el-table>
    </section>

    <!-- 门店编辑 -->
    <el-dialog v-model="storeDialog" :title="storeForm.id ? '编辑门店' : '新建门店'" width="480px">
      <div class="field"><label>门店名称</label><el-input v-model="storeForm.name" maxlength="64" /></div>
      <div class="field"><label>地址</label><el-input v-model="storeForm.address" maxlength="255" /></div>
      <div class="field"><label>联系电话</label><el-input v-model="storeForm.phone" maxlength="20" /></div>
      <div class="two">
        <div class="field">
          <label>开始营业</label>
          <el-time-select v-model="storeForm.openTime" start="06:00" step="00:30" end="12:00" style="width:100%" />
        </div>
        <div class="field">
          <label>结束营业</label>
          <el-time-select v-model="storeForm.closeTime" start="12:00" step="00:30" end="23:30" style="width:100%" />
        </div>
      </div>
      <div class="field"><label>简介</label><el-input v-model="storeForm.description" type="textarea" :rows="2" maxlength="500" /></div>
      <div class="field">
        <label>状态</label>
        <el-radio-group v-model="storeForm.status">
          <el-radio :value="1">营业中</el-radio>
          <el-radio :value="0">已停业</el-radio>
        </el-radio-group>
      </div>
      <template #footer>
        <el-button @click="storeDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveStore">保存</el-button>
      </template>
    </el-dialog>

    <!-- 座位编辑 -->
    <el-dialog v-model="seatDialog" :title="seatForm.id ? '编辑座位' : '新增座位'" width="440px">
      <div class="field"><label>座位编号</label><el-input v-model="seatForm.seatNo" maxlength="16" placeholder="如 A-01" /></div>
      <div class="field">
        <label>座位类型</label>
        <el-select v-model="seatForm.typeId" style="width:100%">
          <el-option v-for="t in seatTypes" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
      </div>
      <div class="two">
        <div class="field"><label>网格行号</label><el-input-number v-model="seatForm.rowIndex" :min="0" :max="50" style="width:100%" /></div>
        <div class="field"><label>网格列号</label><el-input-number v-model="seatForm.colIndex" :min="0" :max="50" style="width:100%" /></div>
      </div>
      <div class="field">
        <label>状态</label>
        <el-radio-group v-model="seatForm.status">
          <el-radio :value="0">可用</el-radio>
          <el-radio :value="1">维修中</el-radio>
          <el-radio :value="2">已停用</el-radio>
        </el-radio-group>
      </div>
      <template #footer>
        <el-button @click="seatDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveSeat">保存</el-button>
      </template>
    </el-dialog>

    <!-- 营业时段编辑 -->
    <el-dialog v-model="hourDialog" :title="hourForm.id ? '编辑时段' : '新增时段'" width="440px">
      <div class="field">
        <label>时段名称</label>
        <el-input v-model="hourForm.name" maxlength="32" placeholder="如 上午场 / 午休 / 下午场" />
      </div>
      <div class="two">
        <div class="field">
          <label>开始时间</label>
          <!-- 沿用门店弹窗的 el-time-select：它直接产出 HH:mm 字符串，提交时补 ":00" 即 HH:mm:ss -->
          <el-time-select
            v-model="hourForm.startTime"
            start="00:00"
            step="00:30"
            end="23:30"
            placeholder="开始"
            style="width:100%"
          />
        </div>
        <div class="field">
          <label>结束时间</label>
          <el-time-select
            v-model="hourForm.endTime"
            :start="hourForm.startTime || '00:30'"
            step="00:30"
            end="23:30"
            placeholder="结束"
            style="width:100%"
          />
        </div>
      </div>
      <div class="field">
        <label>可否预约</label>
        <el-switch
          v-model="hourForm.bookable"
          :active-value="1"
          :inactive-value="0"
          active-text="可预约"
          inactive-text="不可预约（如午休）"
        />
      </div>
      <div class="field">
        <label>状态</label>
        <el-radio-group v-model="hourForm.status">
          <el-radio :value="1">启用</el-radio>
          <el-radio :value="0">停用</el-radio>
        </el-radio-group>
        <span class="field-tip">停用的时段不参与后端「启用中时段不可重叠」的判定。</span>
      </div>
      <div class="field">
        <label>排序</label>
        <el-input-number v-model="hourForm.sort" :min="0" :max="999" style="width:100%" />
        <span class="field-tip">数字越小越靠前，用于控制时段展示与校验顺序。</span>
      </div>
      <template #footer>
        <el-button @click="hourDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveHour">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import EmptyState from '@/components/EmptyState.vue'
import {
  ownerStores,
  ownerCreateStore,
  ownerUpdateStore,
  ownerSeats,
  ownerCreateSeat,
  ownerUpdateSeat,
  ownerSeatTypes,
  ownerBusinessHours,
  ownerSaveBusinessHour,
  ownerRemoveBusinessHour
} from '@/api/admin'

const stores = ref([])
const seats = ref([])
const seatTypes = ref([])
const hours = ref([])
const currentId = ref(null)
const saving = ref(false)
const hourLoading = ref(false)

const storeDialog = ref(false)
const seatDialog = ref(false)
const hourDialog = ref(false)
const storeForm = reactive({
  id: null, name: '', address: '', phone: '',
  openTime: '08:00', closeTime: '22:00', description: '', status: 1
})
const seatForm = reactive({
  id: null, seatNo: '', typeId: null, rowIndex: 0, colIndex: 0, status: 0
})
// 时间在表单里统一用 HH:mm（配合 el-time-select），提交前再补 ":00"
const hourForm = reactive({
  id: null, name: '', startTime: '08:00', endTime: '12:00', bookable: 1, status: 1, sort: 0
})

const currentStore = computed(() => stores.value.find((s) => s.id === currentId.value))
const maxCol = computed(() => seats.value.reduce((m, s) => Math.max(m, s.colIndex || 0), 0))
// 默认按 sort 再按开始时间；末尾用 id 兜底，避免同 sort 同时段时顺序抖动
const sortedHours = computed(() =>
  [...hours.value].sort(
    (a, b) =>
      (a.sort ?? 0) - (b.sort ?? 0) ||
      String(a.startTime || '').localeCompare(String(b.startTime || '')) ||
      (a.id ?? 0) - (b.id ?? 0)
  )
)

const STATUS_TEXT = { 0: '可用', 1: '维修中', 2: '已停用' }
const statusText = (s) => STATUS_TEXT[s] ?? '未知'
const fmt = (t) => (t ? String(t).slice(0, 5) : '—')
const typeName = (id) => seatTypes.value.find((t) => t.id === id)?.name || '—'
// 后端返回 "08:00:00"，展示时只留 HH:mm
const hms = (t) => (t ? String(t).slice(0, 5) : '—')

async function loadStores() {
  stores.value = await ownerStores()
  if (!currentId.value && stores.value.length) {
    currentId.value = stores.value[0].id
  }
}

async function loadSeats() {
  if (!currentId.value) {
    seats.value = []
    return
  }
  seats.value = await ownerSeats(currentId.value)
}

async function loadHours() {
  if (!currentId.value) {
    hours.value = []
    return
  }
  hourLoading.value = true
  try {
    // includeDisabled 走默认 true：停用时段也要列出来，否则用户没法重新启用它
    hours.value = (await ownerBusinessHours(currentId.value)) || []
  } catch (e) {
    hours.value = []
    // 拦截器已提示
  } finally {
    hourLoading.value = false
  }
}

async function selectStore(id) {
  currentId.value = id
  // 座位与时段都挂在门店下，切店必须一起重载，否则会串到上一家门店的数据
  await Promise.all([loadSeats(), loadHours()])
}

function openStoreDialog(s) {
  Object.assign(storeForm, s
    ? {
        id: s.id, name: s.name, address: s.address, phone: s.phone,
        openTime: fmt(s.openTime) === '—' ? '08:00' : fmt(s.openTime),
        closeTime: fmt(s.closeTime) === '—' ? '22:00' : fmt(s.closeTime),
        description: s.description, status: s.status
      }
    : { id: null, name: '', address: '', phone: '', openTime: '08:00', closeTime: '22:00', description: '', status: 1 })
  storeDialog.value = true
}

async function saveStore() {
  if (!storeForm.name.trim()) {
    ElMessage.warning('请填写门店名称')
    return
  }
  saving.value = true
  try {
    const payload = { ...storeForm, openTime: storeForm.openTime + ':00', closeTime: storeForm.closeTime + ':00' }
    if (storeForm.id) {
      await ownerUpdateStore(payload)
    } else {
      const created = await ownerCreateStore(payload)
      currentId.value = created.id
    }
    ElMessage.success('已保存')
    storeDialog.value = false
    await loadStores()
    await loadSeats()
    await loadHours()   // 营业时间可能刚被改小，面板说明里的回落区间要跟着更新
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

function openSeatDialog(s) {
  Object.assign(seatForm, s
    ? { id: s.id, seatNo: s.seatNo, typeId: s.typeId, rowIndex: s.rowIndex, colIndex: s.colIndex, status: s.status }
    : { id: null, seatNo: '', typeId: seatTypes.value[0]?.id ?? null, rowIndex: 0, colIndex: 0, status: 0 })
  seatDialog.value = true
}

async function saveSeat() {
  if (!seatForm.seatNo.trim()) {
    ElMessage.warning('请填写座位编号')
    return
  }
  saving.value = true
  try {
    if (seatForm.id) {
      await ownerUpdateSeat({ ...seatForm })
    } else {
      await ownerCreateSeat({ ...seatForm, storeId: currentId.value })
    }
    ElMessage.success('已保存')
    seatDialog.value = false
    await loadSeats()
    await loadStores()   // 座位总数是冗余字段，新增后需要刷新
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

function openHourDialog(row) {
  Object.assign(hourForm, row
    ? {
        id: row.id,
        name: row.name,
        startTime: hms(row.startTime),
        endTime: hms(row.endTime),
        bookable: row.bookable ?? 1,
        status: row.status ?? 1,
        sort: row.sort ?? 0
      }
    // 新增时默认接在门店开门时刻之后，减少手工输入
    : {
        id: null, name: '',
        startTime: fmt(currentStore.value?.openTime) === '—' ? '08:00' : fmt(currentStore.value.openTime),
        endTime: fmt(currentStore.value?.closeTime) === '—' ? '12:00' : fmt(currentStore.value.closeTime),
        bookable: 1, status: 1, sort: 0
      })
  hourDialog.value = true
}

async function saveHour() {
  if (!currentId.value) {
    ElMessage.warning('请先选择门店')
    return
  }
  if (!hourForm.name.trim()) {
    ElMessage.warning('请填写时段名称')
    return
  }
  if (!hourForm.startTime || !hourForm.endTime) {
    ElMessage.warning('请选择开始与结束时间')
    return
  }
  // 先本地拦一次：后端也会校验，但本地提示更快，也省掉一次往返
  if (hourForm.endTime <= hourForm.startTime) {
    ElMessage.warning('结束时间必须晚于开始时间')
    return
  }
  saving.value = true
  try {
    // 时间统一提交 HH:mm:ss：el-time-select 产出 HH:mm，补上秒；带 id 即修改
    await ownerSaveBusinessHour({
      id: hourForm.id,
      storeId: currentId.value,
      name: hourForm.name.trim(),
      startTime: hourForm.startTime + ':00',
      endTime: hourForm.endTime + ':00',
      bookable: hourForm.bookable,
      status: hourForm.status,
      sort: hourForm.sort ?? 0
    })
    ElMessage.success('已保存')
    hourDialog.value = false
    await loadHours()
  } catch (e) {
    // 与启用中时段重叠时后端返回业务错误码 1002，中文提示由拦截器统一弹出
  } finally {
    saving.value = false
  }
}

async function removeHour(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除时段「${row.name}」（${hms(row.startTime)}–${hms(row.endTime)}）？删除后这段时间将重新归属门店营业时间。`,
      '删除时段',
      { type: 'warning' }
    )
  } catch {
    return   // 用户点了取消
  }
  try {
    await ownerRemoveBusinessHour(row.id)
    ElMessage.success('已删除')
    await loadHours()
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(async () => {
  try {
    seatTypes.value = await ownerSeatTypes()
    await loadStores()
    await Promise.all([loadSeats(), loadHours()])
  } catch (e) {
    // 拦截器已提示
  }
})
</script>

<style scoped>
.stores { display: flex; flex-direction: column; gap: 18px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; flex-wrap: wrap; }
.page-head h1 { margin: 0 0 6px; font-size: var(--fs-h1); }
.sub { margin: 0; color: var(--ink-2); font-size: var(--fs-sm); }
.main { display: grid; grid-template-columns: 340px 1fr; gap: 18px; align-items: start; }
h3 { margin: 0 0 12px; font-size: var(--fs-h3); }
.store-list { display: flex; flex-direction: column; gap: 10px; }
.store-item { border: 1px solid var(--line); border-radius: var(--radius-md); padding: 12px 14px; cursor: pointer; transition: border-color .18s, background .18s; }
.store-item:hover { border-color: var(--brand); }
.store-item.active { border-color: var(--brand); background: var(--brand-soft); }
.si-main { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.si-sub { color: var(--ink-3); font-size: var(--fs-xs); margin-top: 4px; }
/* 选中项的背景是浅蓝 --brand-soft，比白底更「亮」，
   同样的 --ink-3 压上去只有 4.0:1，达不到 4.5:1，因此这一处单独加深一档。 */
.store-item.active .si-sub { color: var(--ink-2); }
.seat-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.seat-head h3 { margin: 0; }
.hint { margin: 0 0 14px; color: var(--ink-3); font-size: var(--fs-xs); }
.grid { display: grid; gap: 8px; justify-content: start; }
.seat { width: 46px; height: 40px; border-radius: var(--radius-sm); border: 1px solid var(--line); background: var(--surface-soft); color: var(--ink-2); font-size: var(--fs-2xs); font-weight: 600; cursor: pointer; transition: transform .15s, border-color .15s; }
.seat:hover { transform: translateY(-2px); border-color: var(--brand); color: var(--brand-ink); }
.seat.fix { background: var(--warn-bg); color: var(--warn-fg); border-color: var(--warn-line); }
.seat.off { background: var(--muted-bg); color: var(--muted-fg); border-color: var(--muted-line); }
.legend { display: flex; gap: 18px; margin-top: 16px; font-size: var(--fs-xs); color: var(--ink-2); }
.legend span { display: inline-flex; align-items: center; gap: 6px; }
.dot { width: 12px; height: 12px; border-radius: var(--radius-xs); display: inline-block; }
.dot-free { background: var(--surface-soft); border: 1px solid var(--line); }
.dot-fix { background: var(--warn-bg); }
.dot-off { background: var(--muted-bg); }
.field { display: flex; flex-direction: column; gap: 6px; margin-bottom: 14px; }
.field label { font-size: var(--fs-xs); color: var(--ink-3); font-weight: 600; }
.field-tip { font-size: var(--fs-2xs); color: var(--ink-3); line-height: 1.5; }
.two { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.empty { color: var(--ink-3); font-size: var(--fs-sm); padding: 24px 0; text-align: center; }
@media (max-width: 900px) { .main { grid-template-columns: 1fr; } }
</style>
