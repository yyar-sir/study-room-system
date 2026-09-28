<template>
  <button
    type="button"
    class="theme-toggle"
    :title="label"
    :aria-label="label"
    :aria-pressed="theme === 'dark'"
    @click="toggleTheme"
  >
    <span aria-hidden="true">{{ theme === 'dark' ? '☀️' : '🌙' }}</span>
  </button>
</template>

<script setup>
import { computed } from 'vue'
import { useTheme } from '@/composables/useTheme'

const { theme, toggleTheme } = useTheme()

const label = computed(() =>
  theme.value === 'dark' ? '切换到浅色主题' : '切换到深色主题'
)
</script>

<style scoped>
.theme-toggle {
  display: inline-grid;
  place-items: center;
  width: 34px;
  height: 34px;
  padding: 0;
  border: 0;
  border-radius: var(--radius-pill);
  background: var(--surface-soft);
  cursor: pointer;
  font-size: var(--fs-body);
  line-height: 1;
  transition: background 0.18s, transform 0.18s;
}
.theme-toggle:hover { background: var(--brand-soft); }
.theme-toggle:active { transform: scale(0.94); }
</style>
