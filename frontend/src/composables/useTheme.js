/* ============================================================
   主题切换（浅色 / 深色）
   ------------------------------------------------------------
   为什么不用 Pinia：主题只是「一个字符串 + 一次写 DOM」，
   引入 store 反而要处理 store 注册顺序（登录页在 app.use 之前
   就要读取主题）。这里用模块级 ref，任何组件 import 进来的
   都是同一份状态，效果等价而依赖更少。

   两处需要同步：
     ① <html data-theme="dark">  → 我们自己的 CSS 变量（main.css）
     ② <html class="dark">       → Element Plus 的暗色变量（html.dark）
   少写一个，就会出现「卡片变深了、表格还是白的」这种半深色。
   ============================================================ */
import { ref } from 'vue'

const STORAGE_KEY = 'theme'

/** 当前主题，'light' | 'dark' */
const theme = ref('light')

const systemPrefersDark = () =>
  window.matchMedia('(prefers-color-scheme: dark)').matches

/** 把主题写到 DOM 上（唯一一处做这件事的地方） */
function applyTheme(t) {
  const root = document.documentElement
  root.dataset.theme = t
  // Element Plus 的暗色模式靠 <html class="dark"> 激活
  root.classList.toggle('dark', t === 'dark')
  // 让浏览器原生控件（滚动条、日期选择器、输入光标）也跟着走
  root.style.colorScheme = t
}

/** 读取本次会话该用什么主题（用户显式选择优先，否则跟随系统） */
function resolveInitial() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved === 'dark' || saved === 'light') return saved
  } catch (e) {
    // localStorage 不可用（隐私模式）时静默降级到跟随系统
  }
  return systemPrefersDark() ? 'dark' : 'light'
}

/**
 * 应用初始主题。main.js 挂载前调用。
 * index.html 里已有一段同逻辑的内联脚本负责「防闪烁」，
 * 这里再调一次是为了让 ref 与 DOM 状态一致（幂等）。
 */
export function initTheme() {
  theme.value = resolveInitial()
  applyTheme(theme.value)

  // 未显式选择过时，跟随系统实时变化
  const mq = window.matchMedia('(prefers-color-scheme: dark)')
  const onChange = (e) => {
    let saved = null
    try { saved = localStorage.getItem(STORAGE_KEY) } catch (err) { /* 忽略 */ }
    if (saved) return
    theme.value = e.matches ? 'dark' : 'light'
    applyTheme(theme.value)
  }
  // 旧浏览器（Safari < 14）只有已废弃的 addListener。
  // 这里不能用 addEventListener 直接调：不存在时会抛错，导致应用挂载失败。
  if (mq.addEventListener) mq.addEventListener('change', onChange)
  else if (mq.addListener) mq.addListener(onChange)
}

export function useTheme() {
  function setTheme(t) {
    theme.value = t === 'dark' ? 'dark' : 'light'
    try { localStorage.setItem(STORAGE_KEY, theme.value) } catch (e) { /* 忽略 */ }
    applyTheme(theme.value)
  }

  function toggleTheme() {
    setTheme(theme.value === 'dark' ? 'light' : 'dark')
  }

  return { theme, setTheme, toggleTheme }
}
