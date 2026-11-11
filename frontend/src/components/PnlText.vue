<template>
  <!-- 수익률·등락률 표시: 플러스 빨강, 마이너스 파랑 -->
  <span :class="cls">{{ text }}</span>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  value: { type: Number, default: null },   // % 단위 (3.5 = +3.5%)
  digits: { type: Number, default: 2 }
});

const cls = computed(() => {
  if (props.value == null || props.value === 0) return 'pnl-flat';
  return props.value > 0 ? 'pnl-up' : 'pnl-down';
});

const text = computed(() => {
  if (props.value == null) return '—';
  const sign = props.value > 0 ? '+' : '';
  return `${sign}${props.value.toFixed(props.digits)}%`;
});
</script>
