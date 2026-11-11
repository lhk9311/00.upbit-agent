<template>
  <div class="backdrop" @click.self="$emit('close')">
    <div class="box" role="dialog" aria-modal="true" aria-labelledby="bt-title">
      <div class="head">
        <h5 id="bt-title" class="mb-0">백테스트 · {{ strategy.name }}</h5>
        <button class="btn btn-sm btn-outline-secondary" aria-label="닫기" @click="$emit('close')">닫기</button>
      </div>
      <p class="sub">이 전략을 서버가 가진 과거 캔들에 그대로 적용해본 결과예요.</p>

      <div v-if="loading" class="loading">계산 중…</div>
      <div v-else-if="error" class="alert alert-warning py-2 mb-0">{{ error }}</div>
      <BacktestResult v-else-if="result" :result="result" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue';
import BacktestResult from './BacktestResult.vue';
import { backtestSaved } from '../api/backendApi';
import { apiError } from '../utils/format';

const props = defineProps({
  strategy: { type: Object, required: true }
});

const emit = defineEmits(['close']);

const loading = ref(true);
const error = ref('');
const result = ref(null);

onMounted(async () => {
  window.addEventListener('keydown', onKey);
  try {
    result.value = await backtestSaved(props.strategy.id);
  } catch (e) {
    error.value = apiError(e, '백테스트 실패');
  } finally {
    loading.value = false;
  }
});

const onKey = (e) => {
  if (e.key === 'Escape') emit('close');
};
onBeforeUnmount(() => window.removeEventListener('keydown', onKey));
</script>

<style scoped>
.backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 16px;
}

.box {
  text-align: left;
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: 12px;
  padding: 20px 22px;
  width: 100%;
  max-width: 760px;
  max-height: 90vh;
  overflow-y: auto;
}

.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.sub {
  color: #8b949e;
  font-size: 0.85rem;
  margin: 6px 0 12px;
}

.loading {
  color: #8b949e;
  text-align: center;
  padding: 40px 0;
}
</style>
