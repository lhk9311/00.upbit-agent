<template>
  <div class="modal-backdrop-custom" @click.self="$emit('close')">
    <div class="modal-box" role="dialog" aria-modal="true" aria-labelledby="strategy-title">

      <h5 id="strategy-title">이 조건으로 에이전트 시작</h5>
      <p class="paper-note">
        <b>모의매매</b>로 실행돼요. 실제 주문은 나가지 않고, 현재가로 체결됐다고 가정해 기록만 해요.
        (수수료 0.05% · 체결가 0.1% 불리하게 반영)
      </p>

      <!-- 매수 조건 -->
      <div class="section">
        <div class="section-title">매수 조건</div>
        <div v-if="naturalText" class="natural">“{{ naturalText }}”</div>
        <div class="chips">
          <span v-for="l in conditionLabels(condition)" :key="l" class="chip">{{ l }}</span>
        </div>
      </div>

      <div class="row g-2">
        <label class="col-12 field">
          <span>전략 이름</span>
          <input v-model="form.name" type="text" maxlength="100" placeholder="예: 1시간봉 과매도 반등" />
        </label>

        <label class="col-6 field">
          <span>1회 매수 금액(원)</span>
          <input v-model.number="form.orderKrw" type="number" min="5000" step="1000" />
        </label>
        <label class="col-6 field">
          <span>최대 보유 종목 수</span>
          <input v-model.number="form.maxPositions" type="number" min="1" max="10" />
        </label>
      </div>

      <div class="section-title mt-3">매도 규칙 <small>(먼저 걸리는 것으로 매도, 하나 이상 필요)</small></div>
      <div class="row g-2">
        <label class="col-6 field">
          <span>익절: 매수가 대비 +%</span>
          <input v-model.number="form.takeProfitPct" type="number" min="0" step="0.5" placeholder="안 씀" />
        </label>
        <label class="col-6 field">
          <span>손절: 매수가 대비 -%</span>
          <input v-model.number="form.stopLossPct" type="number" min="0" step="0.5" placeholder="안 씀" />
        </label>
        <label class="col-6 field">
          <span>트레일링: 최고가 대비 -%</span>
          <input v-model.number="form.trailingStopPct" type="number" min="0" step="0.5" placeholder="안 씀" />
        </label>
        <label class="col-6 field">
          <span>최대 보유 시간</span>
          <input v-model.number="form.maxHoldHours" type="number" min="1" placeholder="안 씀" />
        </label>
      </div>

      <div class="section-title mt-3">리스크</div>
      <div class="row g-2">
        <label class="col-6 field">
          <span>일일 손실 한도(원)</span>
          <input v-model.number="form.dailyLossLimitKrw" type="number" min="0" step="1000" placeholder="안 씀" />
        </label>
        <div class="col-6 field">
          <span>최대 투입 금액</span>
          <div class="calc">{{ formatWon(maxExposure) }}</div>
        </div>
      </div>

      <div v-if="error" class="alert alert-warning py-2 mt-3 mb-0">{{ error }}</div>

      <div class="actions">
        <button class="btn btn-outline-secondary" :disabled="saving" @click="$emit('close')">취소</button>
        <button class="btn btn-primary" :disabled="saving" @click="save">
          {{ saving ? '저장 중…' : '저장하고 시작' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted, onBeforeUnmount } from 'vue';
import { createStrategy } from '../api/backendApi';
import { conditionLabels, formatWon, apiError } from '../utils/format';

const props = defineProps({
  condition: { type: Object, required: true },   // 백엔드 형식 ScreenerCondition
  naturalText: { type: String, default: '' }
});

const emit = defineEmits(['close', 'saved']);

const form = reactive({
  name: props.naturalText ? props.naturalText.slice(0, 30) : '',
  orderKrw: 10000,
  maxPositions: 3,
  takeProfitPct: 5,
  stopLossPct: 3,
  trailingStopPct: null,
  maxHoldHours: 24,
  dailyLossLimitKrw: 30000
});

const saving = ref(false);
const error = ref('');

const maxExposure = computed(() => (form.orderKrw || 0) * (form.maxPositions || 0));

// 빈칸('') → null
const num = (v) => (typeof v === 'number' && !Number.isNaN(v) ? v : null);

const save = async () => {
  saving.value = true;
  error.value = '';
  try {
    const saved = await createStrategy({
      name: form.name,
      condition: props.condition,
      naturalText: props.naturalText || null,
      orderKrw: num(form.orderKrw),
      maxPositions: num(form.maxPositions),
      takeProfitPct: num(form.takeProfitPct),
      stopLossPct: num(form.stopLossPct),
      trailingStopPct: num(form.trailingStopPct),
      maxHoldHours: num(form.maxHoldHours),
      dailyLossLimitKrw: num(form.dailyLossLimitKrw),
      enabled: true
    });
    emit('saved', saved);
  } catch (e) {
    error.value = apiError(e, '저장 실패');
  } finally {
    saving.value = false;
  }
};

// ESC 로 닫기
const onKey = (e) => {
  if (e.key === 'Escape' && !saving.value) emit('close');
};
onMounted(() => window.addEventListener('keydown', onKey));
onBeforeUnmount(() => window.removeEventListener('keydown', onKey));
</script>

<style scoped>
.modal-backdrop-custom {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 16px;
}

.modal-box {
  text-align: left;
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: 12px;
  padding: 22px 24px;
  width: 100%;
  max-width: 560px;
  max-height: 90vh;
  overflow-y: auto;
}

.paper-note {
  font-size: 0.85rem;
  color: #8b949e;
  background: #0d1117;
  border-radius: 8px;
  padding: 8px 10px;
}

.section-title {
  font-size: 0.8rem;
  color: #8b949e;
  margin-bottom: 6px;
}

.section-title small {
  color: #6e7681;
}

.natural {
  color: #c9d1d9;
  font-size: 0.9rem;
  margin-bottom: 6px;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 12px;
}

.chip {
  background: #30363d;
  color: #c9d1d9;
  border-radius: 6px;
  padding: 2px 8px;
  font-size: 0.78rem;
}

.field {
  font-size: 0.85rem;
}

.field > span {
  display: block;
  color: #c9d1d9;
  margin-bottom: 4px;
}

.field input {
  width: 100%;
  background: #0d1117;
  color: white;
  border: 1px solid #30363d;
  border-radius: 6px;
  padding: 6px 8px;
}

.field input:focus {
  outline: none;
  border-color: #3b82f6;
}

.calc {
  padding: 6px 0;
  font-variant-numeric: tabular-nums;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 18px;
}
</style>
