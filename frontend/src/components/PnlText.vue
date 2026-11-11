<template>
  <!-- 수익률·손익 표시: 플러스 빨강, 마이너스 파랑 -->
  <span :class="cls">{{ text }}</span>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  value: { type: Number, default: null },
  unit: { type: String, default: '%' },     // '%' 또는 '원'
  digits: { type: Number, default: 2 }       // % 일 때 소수점 자릿수
});

const cls = computed(() => {
  if (props.value == null || Math.abs(props.value) < 1e-9) return 'pnl-flat';
  return props.value > 0 ? 'pnl-up' : 'pnl-down';
});

const text = computed(() => {
  if (props.value == null) return '—';
  const sign = props.value > 0 ? '+' : '';
  if (props.unit === '원') {
    return `${sign}${Math.round(props.value).toLocaleString('ko-KR')}원`;
  }
  return `${sign}${props.value.toFixed(props.digits)}%`;
});
</script>
