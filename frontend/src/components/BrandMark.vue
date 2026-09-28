<template>
  <!--
    品牌标志：一本摊开的书 + 橙色书签。
    原先三处（登录页、用户端顶栏、管理端侧栏）都用 📚 emoji，
    但 emoji 的字形由操作系统决定（Windows 是 Segoe UI Emoji 的彩色书堆，
    macOS / Android 各不相同），既不可控也谈不上设计感。
    改用内联 SVG：颜色走 currentColor，自动跟随所在主题与深色模式。
  -->
  <svg
    class="brand-mark"
    :width="size"
    :height="size"
    viewBox="0 0 32 32"
    aria-hidden="true"
    focusable="false"
  >
    <!-- 左页：半透明，制造前后层次 -->
    <path d="M16 8.5C12.5 6.5 8.5 5.5 5 5.5v19c3.5 0 7.5 1 11 3z" fill="currentColor" opacity=".45" />
    <!-- 右页 -->
    <path d="M16 8.5c3.5-2 7.5-3 11-3v19c-3.5 0-7.5 1-11 3z" fill="currentColor" />
    <!-- 书签：唯一带品牌橙的部件 -->
    <path d="M14.6 2.8h2.8v6.6l-1.4-1.6-1.4 1.6z" fill="var(--accent)" />
  </svg>
</template>

<script setup>
defineProps({
  /** 边长（px），默认 28 */
  size: { type: [Number, String], default: 28 }
})
</script>

<style scoped>
.brand-mark { display: block; flex-shrink: 0; }
</style>
