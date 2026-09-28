<template>
  <div class="notify">
    <div class="page-head">
      <div>
        <h1>消息中心</h1>
        <p class="sub">套餐到期、预约提醒、违规通知、门店公告与系统消息都会出现在这里。</p>
      </div>
      <el-button :disabled="!unread" @click="readAll">全部标为已读</el-button>
    </div>

    <el-tabs v-model="activeType" @tab-change="load">
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane label="套餐到期" name="plan_expire" />
      <el-tab-pane label="预约提醒" name="reserve_remind" />
      <el-tab-pane label="违规通知" name="violation" />
      <el-tab-pane label="公告" name="announce" />
      <!-- 系统消息：前台代客开卡、账号变更等都会走这个类型。
           之前漏了这个 tab，用户只能在「全部」里看到，按分类找不到。 -->
      <el-tab-pane label="系统消息" name="system" />
    </el-tabs>

    <div v-if="loading" class="empty">加载中…</div>
    <div v-else-if="!list.length" class="empty">
      <EmptyState
        size="md"
        icon="🔔"
        title="这个分类下还没有消息"
        hint="套餐到期、预约提醒、违规通知、门店公告与系统消息都由系统自动推送，收到后会出现在这里。"
      />
    </div>

    <div v-else class="list">
      <article
        v-for="n in list"
        :key="n.id"
        class="k-card item"
        :class="{ unread: n.isRead === 0 }"
        @click="open(n)"
      >
        <div class="item-head">
          <span class="k-tag" :class="tagClass(n.type)">{{ typeText(n.type) }}</span>
          <b>{{ n.title }}</b>
          <span v-if="n.isRead === 0" class="dot-unread" />
          <span class="time">{{ (n.createdAt || '').replace('T', ' ').slice(0, 16) }}</span>
        </div>
        <p class="content">{{ n.content }}</p>
      </article>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listNotifications, unreadCount, markRead, markAllRead } from '@/api/notification'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()

const list = ref([])
const loading = ref(false)
const activeType = ref('all')
const unread = ref(0)

const TYPE_TEXT = {
  plan_expire: '套餐到期',
  reserve_remind: '预约提醒',
  violation: '违规通知',
  announce: '公告',
  system: '系统消息'
}

const typeText = (t) => TYPE_TEXT[t] || '消息'

function tagClass(t) {
  if (t === 'violation') return 'k-tag--pink'
  if (t === 'plan_expire') return 'k-tag--orange'
  if (t === 'announce') return 'k-tag--teal'
  return ''
}

async function load() {
  loading.value = true
  try {
    list.value = await listNotifications(activeType.value === 'all' ? null : activeType.value)
    unread.value = await unreadCount()
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function open(n) {
  if (n.isRead === 0) {
    try {
      await markRead(n.id)
      n.isRead = 1
      unread.value = Math.max(0, unread.value - 1)
    } catch (e) {
      // 忽略
    }
  }
  if (n.link) {
    router.push(n.link)
  }
}

async function readAll() {
  try {
    await markAllRead()
    list.value.forEach((n) => { n.isRead = 1 })
    unread.value = 0
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(load)
</script>

<style scoped>
.notify { display: flex; flex-direction: column; gap: 14px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; flex-wrap: wrap; }
.page-head h1 { margin: 0 0 6px; font-size: var(--fs-h1); }
.sub { margin: 0; color: var(--ink-2); font-size: var(--fs-sm); }
.list { display: flex; flex-direction: column; gap: 10px; }
.item { padding: 14px 16px; cursor: pointer; }
.item.unread { border-left: 3px solid var(--brand); }
.item-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.item-head b { font-size: var(--fs-sm); }
.time { margin-left: auto; color: var(--ink-3); font-size: var(--fs-xs); }
.dot-unread { width: 8px; height: 8px; border-radius: 50%; background: var(--accent); }
.content { margin: 8px 0 0; color: var(--ink-2); font-size: var(--fs-sm); line-height: 1.8; }
.empty { color: var(--ink-3); font-size: var(--fs-sm); padding: 40px 0; text-align: center; }
</style>
