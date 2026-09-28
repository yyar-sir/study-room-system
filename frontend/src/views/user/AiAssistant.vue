<template>
  <div class="ai">
    <div class="page-head">
      <div>
        <h1>AI 智能自习助手</h1>
        <p class="sub">可以问套餐规则、座位分布、预约政策、营业时间，也能让它给学习计划与座位建议。</p>
      </div>
    </div>

    <section class="k-card chat">
      <div ref="scrollBox" class="messages">
        <EmptyState
          v-if="!messages.length"
          size="md"
          icon="🤖"
          title="你好，我是自习室 AI 助手"
          hint="可以问我套餐价格与规则、座位类型、预约与取消政策、营业时间，也能让我帮你安排学习计划。"
        >
          <template #action>
            <div class="quick">
              <button
                v-for="q in quickQuestions"
                :key="q"
                type="button"
                @click="send(q)"
              >{{ q }}</button>
            </div>
          </template>
        </EmptyState>

        <div
          v-for="(m, i) in messages"
          :key="i"
          class="bubble-row"
          :class="m.role === 'user' ? 'right' : 'left'"
        >
          <div class="bubble" :class="m.role">
            <div class="text">{{ m.content }}</div>
            <!-- 降级提示：让用户知道这是知识库兜底而不是模型回答 -->
            <div v-if="m.degraded" class="degrade">
              AI 暂不可用，以上为知识库答案{{ m.degradeReason ? `（${m.degradeReason}）` : '' }}
            </div>
          </div>
        </div>

        <div v-if="loading" class="bubble-row left">
          <div class="bubble assistant thinking">AI 正在思考…</div>
        </div>
      </div>

      <div class="composer">
        <el-input
          v-model="question"
          type="textarea"
          :rows="2"
          maxlength="500"
          show-word-limit
          resize="none"
          placeholder="输入你的问题，Enter 发送，Shift+Enter 换行"
          @keydown.enter.exact.prevent="send()"
        />
        <el-button type="primary" :loading="loading" :disabled="!question.trim()" @click="send()">
          发送
        </el-button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { ask, aiHistory } from '@/api/ai'
import EmptyState from '@/components/EmptyState.vue'

const messages = ref([])
const question = ref('')
const loading = ref(false)
const scrollBox = ref(null)

/**
 * 会话 id：一次进入页面算一个会话，便于历史归组。
 *
 * 用 ref 而不是普通常量：挂载时若成功恢复出历史记录，会被改成记录里最后一条的
 * sessionId，这样用户刷新页面后的追问仍接在原来那轮会话上，而不是另起新会话
 * （否则历史记了却永远读不到第二次）。
 */
const sessionId = ref('web-' + Date.now().toString(36))

const quickQuestions = [
  '有哪些会员套餐？',
  '月卡和季卡哪个划算？',
  '座位有哪些类型？',
  '可以提前几天预约？',
  '爽约了会怎么样？',
  '营业时间是几点到几点？'
]

async function scrollToBottom() {
  await nextTick()
  if (scrollBox.value) {
    scrollBox.value.scrollTop = scrollBox.value.scrollHeight
  }
}

/**
 * 恢复历史对话。
 *
 * 后端按 createdAt 升序返回（最早在前），刚好与气泡从上往下的展示顺序一致，
 * 所以直接映射即可，**不要** reverse —— 反转后问答会倒着排。
 *
 * 不传 sessionId 时后端回该用户最近的记录（可能跨多次会话，上限 100 条）。
 * 取最后一条的 sessionId 回填，是为了让接下来的追问接在最近这次会话上。
 */
async function restoreHistory() {
  try {
    const records = await aiHistory()
    if (!Array.isArray(records) || !records.length) return

    messages.value = records
      .filter((r) => r && r.content)
      .map((r) => ({
        role: r.role,
        content: r.content,
        // 库里 degraded 是 Integer(0/1)，用 Boolean() 包一层同时兼容 1 与 true
        degraded: Boolean(r.degraded)
        // 记录表没有持久化 degradeReason，所以降级提示只显示基础文案
      }))

    const last = records[records.length - 1]
    if (last && last.sessionId) {
      sessionId.value = last.sessionId
    }

    await scrollToBottom()
  } catch (e) {
    // 拦截器已提示；历史拉不到就当作新会话开始，不影响继续提问
  }
}

async function send(preset) {
  const q = (preset || question.value).trim()
  if (!q || loading.value) return

  messages.value.push({ role: 'user', content: q })
  question.value = ''
  loading.value = true
  await scrollToBottom()

  try {
    const res = await ask(q, sessionId.value)
    messages.value.push({
      role: 'assistant',
      content: res.answer,
      degraded: res.degraded,
      degradeReason: res.degradeReason
    })
  } catch (e) {
    // 限流(6002)等业务错误由拦截器提示；这里不再追加气泡，避免把错误当成回答
  } finally {
    loading.value = false
    await scrollToBottom()
  }
}

// 挂载即拉历史：后端一直在写 ai_chat_record，不读的话刷新一次对话就没了
onMounted(restoreHistory)
</script>

<style scoped>
.ai { display: flex; flex-direction: column; gap: 18px; }
.page-head h1 { margin: 0 0 6px; font-size: var(--fs-h1); }
.sub { margin: 0; color: var(--ink-2); font-size: var(--fs-sm); }
.chat { display: flex; flex-direction: column; gap: 14px; min-height: 460px; }
.messages { flex: 1; overflow-y: auto; max-height: 52vh; display: flex; flex-direction: column; gap: 12px; padding-right: 4px; }
.quick { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
.quick button {
  padding: 6px 14px; border-radius: var(--radius-pill); background: var(--surface-soft);
  color: var(--brand-deep); font-size: var(--fs-sm); cursor: pointer; transition: background .18s;
  border: 0; font-family: inherit;
}
.quick button:hover { background: var(--brand-soft); }
.bubble-row { display: flex; }
.bubble-row.right { justify-content: flex-end; }
.bubble { max-width: 76%; padding: 10px 14px; border-radius: var(--radius-md); font-size: var(--fs-sm); line-height: 1.75; white-space: pre-wrap; }
.bubble.user { background: var(--brand); color: #fff; }
.bubble.assistant { background: var(--surface-soft); color: var(--ink); border: 1px solid var(--line); }
.bubble.thinking { color: var(--ink-3); }
.degrade { margin-top: 8px; padding-top: 8px; border-top: 1px dashed var(--line); color: var(--accent-deep); font-size: var(--fs-xs); }
.composer { display: flex; gap: 10px; align-items: flex-end; }
.composer .el-button { height: 44px; }
</style>
