<template>
  <div class="k-empty" :class="`k-empty--${size}`" role="status">
    <div class="k-empty__icon" aria-hidden="true">
      <slot name="icon">{{ icon }}</slot>
    </div>
    <p class="k-empty__title">{{ title }}</p>
    <p v-if="hint" class="k-empty__hint">{{ hint }}</p>
    <div v-if="$slots.action" class="k-empty__action">
      <slot name="action" />
    </div>
  </div>
</template>

<script setup>
/**
 * 统一的空状态占位。
 *
 * 此前列表页在无数据时要么是一片空白，要么只在表格里留一行灰色小字，
 * 用户看不出是「还没加载」「加载失败」还是「确实没有数据」。
 * 这里统一成「图标 + 结论 + 下一步提示」三段式，并把动作按钮交给调用方。
 */
defineProps({
  /** 一句话说明「当前没有数据」 */
  title: { type: String, required: true },
  /** 补充说明，通常告诉用户怎么才能产生数据 */
  hint: { type: String, default: '' },
  /** 装饰图标，默认空收件箱 */
  icon: { type: String, default: '📭' },
  /** sm 用于表格内嵌（避免整块表格被撑高），md 用于整页/整卡 */
  size: { type: String, default: 'md' }
})
</script>

<style scoped>
.k-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  text-align: center;
  color: var(--ink-3);
}
.k-empty--md { padding: 44px 20px; }
.k-empty--sm { padding: 22px 16px; }

.k-empty__icon {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  margin-bottom: 4px;
  border-radius: var(--radius-lg);
  background: var(--surface-soft);
  font-size: 24px;
  line-height: 1;
}
.k-empty--sm .k-empty__icon {
  width: 40px;
  height: 40px;
  font-size: var(--fs-lg);
  border-radius: var(--radius-md);
}

.k-empty__title {
  margin: 0;
  font-size: var(--fs-body);
  font-weight: 600;
  color: var(--ink-2);
}
.k-empty--sm .k-empty__title { font-size: var(--fs-sm); }

.k-empty__hint {
  margin: 0;
  max-width: 34ch;
  font-size: var(--fs-sm);
  line-height: 1.6;
  color: var(--ink-3);
}
.k-empty--sm .k-empty__hint { font-size: var(--fs-xs); }

.k-empty__action { margin-top: 10px; }
</style>
