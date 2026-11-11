<template>
  <div class="nl-card">
    <label class="nl-label" for="nl-text">어떤 종목을 찾을까요?</label>

    <div class="nl-row">
      <textarea
        id="nl-text"
        v-model="text"
        rows="2"
        maxlength="500"
        :disabled="loading || disabled"
        :placeholder="disabled
          ? 'Claude API 키(ANTHROPIC_API_KEY)를 설정하면 문장으로 조건을 입력할 수 있어요. 지금은 아래 패널에서 직접 골라주세요.'
          : '예) 1시간봉에서 거래대금 100억 이상이고, 과매도였다가 볼린저 하단 찍고 반등하는 코인'"
        @keydown.enter.exact.prevent="submit"
      ></textarea>

      <button class="btn btn-primary nl-btn" :disabled="loading || disabled || !text.trim()" @click="submit">
        {{ loading ? '해석 중…' : '조건으로 검색' }}
      </button>
    </div>

    <!-- 예시 문장 -->
    <div v-if="!disabled" class="examples">
      <button v-for="ex in EXAMPLES" :key="ex" class="example" :disabled="loading" @click="useExample(ex)">
        {{ ex }}
      </button>
    </div>

    <!-- 해석 결과 -->
    <div v-if="error" class="alert alert-warning py-2 mt-3 mb-0">{{ error }}</div>

    <div v-else-if="parsed" class="interpretation mt-3">
      <div><b>이렇게 해석했어요</b> · {{ parsed.explanation || '설명 없음' }}</div>
      <div v-if="parsed.unsupported.length" class="warn">
        반영하지 못한 요청: {{ parsed.unsupported.join(', ') }}
      </div>
      <div v-if="parsed.adjustments.length" class="warn">
        자동으로 바로잡은 값: {{ parsed.adjustments.join(' / ') }}
      </div>
      <div class="hint">아래 조건 패널에 채워 넣었어요. 해석이 다르면 패널에서 고친 뒤 검색을 다시 누르세요.</div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';

defineProps({
  loading: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  error: { type: String, default: '' },
  parsed: { type: Object, default: null }   // { explanation, unsupported[], adjustments[] }
});

const emit = defineEmits(['submit']);

const text = ref('');

const EXAMPLES = [
  '일봉 상승 추세인데 고점에서 살짝 눌린 거래대금 큰 코인',
  '15분봉에서 거래량 터지면서 골든크로스 난 종목, 유의 종목은 빼고',
  '오늘 많이 떨어졌는데 RSI 과매도인 코인 과매도 심한 순으로'
];

const submit = () => {
  if (text.value.trim()) {
    emit('submit', text.value.trim());
  }
};

const useExample = (ex) => {
  text.value = ex;
  submit();
};
</script>

<style scoped>
.nl-card {
  text-align: left;
  background: linear-gradient(180deg, #172033 0%, #161b22 100%);
  border: 1px solid #263248;
  border-radius: 12px;
  padding: 16px 20px;
  margin-bottom: 16px;
}

.nl-label {
  display: block;
  font-weight: 600;
  margin-bottom: 8px;
}

.nl-row {
  display: flex;
  gap: 10px;
  align-items: stretch;
}

textarea {
  flex: 1;
  resize: vertical;
  background: #0d1117;
  color: white;
  border: 1px solid #30363d;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 0.95rem;
}

textarea:focus {
  outline: none;
  border-color: #3b82f6;
}

textarea:disabled {
  opacity: 0.7;
}

.nl-btn {
  white-space: nowrap;
  min-width: 120px;
}

.examples {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.example {
  background: #0d1117;
  border: 1px solid #30363d;
  color: #8b949e;
  border-radius: 999px;
  padding: 3px 10px;
  font-size: 0.8rem;
}

.example:hover:not(:disabled) {
  color: white;
  border-color: #3b82f6;
}

.interpretation {
  background: #0d1117;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 0.9rem;
  color: #c9d1d9;
}

.interpretation .warn {
  color: #f0b429;
  margin-top: 4px;
}

.interpretation .hint {
  color: #8b949e;
  font-size: 0.8rem;
  margin-top: 6px;
}

@media (max-width: 600px) {
  .nl-row {
    flex-direction: column;
  }
}
</style>
