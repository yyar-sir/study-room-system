/* ============================================================
   数字滚动（count-up）
   ------------------------------------------------------------
   ⚠️ 这里曾用 GSAP 实现，但 `gsap` + `gsap/ScrollTrigger` 会给看板页
      额外带进约 110KB 产物（Dashboard 分块 121KB，其它页面仅 3~9KB），
      而全项目其实只用得到「数字从 0 滚到目标值」这一个效果，
      ScrollTrigger 一次都没用上。
      改成 requestAnimationFrame 手写后产物零依赖，视觉效果一致。

   用法：目标元素上放 data-count="目标值"、data-decimals="小数位"（可选）。
   动画只改 textContent，不触发布局抖动。
   ============================================================ */

/** 先快后慢，收尾更自然 */
const easeOutQuad = (t) => 1 - (1 - t) * (1 - t)

/**
 * 用户是否要求减少动效。
 * 注意：main.css 里的 prefers-reduced-motion 只能关掉 CSS 动画，
 * 管不住 JS 驱动的动画，所以这里必须显式判断一次。
 */
export function prefersReducedMotion() {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/** 单个元素从 0 滚到 data-count 指定的目标值 */
export function countUpTo(el, { duration = 1200 } = {}) {
  const target = parseFloat(el?.dataset?.count)
  if (!Number.isFinite(target)) return
  const decimals = Number(el.dataset.decimals || 0)

  // 减少动效偏好：直接落到终值，不做动画
  if (prefersReducedMotion()) {
    el.textContent = target.toFixed(decimals)
    return
  }

  const start = performance.now()
  const step = (now) => {
    const t = Math.min((now - start) / duration, 1)
    el.textContent = (target * easeOutQuad(t)).toFixed(decimals)
    if (t < 1) requestAnimationFrame(step)
  }
  requestAnimationFrame(step)
}

/**
 * 批量数字滚动：对容器内所有 [data-count] 元素逐个 count-up，略作错峰。
 * duration / stagger 单位均为毫秒。
 */
export function countUpGroup(scope, { duration = 1200, stagger = 100 } = {}) {
  if (!scope) return
  const els = Array.from(scope.querySelectorAll('[data-count]'))
  els.forEach((el, i) => {
    if (i === 0) countUpTo(el, { duration })
    else setTimeout(() => countUpTo(el, { duration }), i * stagger)
  })
}
