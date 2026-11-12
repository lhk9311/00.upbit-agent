<template>
  <div class="condition-card">

    <!-- 헤더 -->
    <div class="condition-header">
      <button class="btn-link-plain" @click="open = !open">
        {{ open ? '▾' : '▸' }} 검색 조건
        <span class="badge bg-primary ms-1">{{ activeCount }}개 사용 중</span>
      </button>

      <div class="d-flex gap-2 flex-wrap">
        <button
          v-for="p in PRESETS"
          :key="p.name"
          class="btn btn-sm btn-outline-secondary"
          :title="p.desc"
          @click="applyPreset(p)"
        >
          {{ p.name }}
        </button>
        <button class="btn btn-sm btn-outline-secondary" @click="reset">초기화</button>
        <button class="btn btn-sm btn-primary px-3" :disabled="loading" @click="$emit('search')">
          {{ loading ? '검색 중…' : '검색' }}
        </button>
      </div>
    </div>

    <div v-show="open" class="row g-3 mt-1">

      <!-- 기본 -->
      <div class="col-md-6 col-xl-3">
        <div class="group">
          <h6>기본</h6>

          <label class="field">
            <span>기준봉</span>
            <select v-model="model.unit">
              <option value="MIN15">15분봉</option>
              <option value="MIN60">1시간봉</option>
              <option value="MIN240">4시간봉</option>
              <option value="DAY">일봉</option>
            </select>
          </label>

          <div class="field">
            <span>현재가(원)</span>
            <div class="range">
              <input v-model.number="model.minPrice" type="number" min="0" placeholder="최소" />
              <em>~</em>
              <input v-model.number="model.maxPrice" type="number" min="0" placeholder="최대" />
            </div>
          </div>

          <label class="field">
            <span>24h 거래대금 ≥</span>
            <div class="unit-input">
              <input v-model.number="model.minTradeAmountEok" type="number" min="0" placeholder="—" />
              <em>억</em>
            </div>
          </label>

          <div class="field">
            <span>전일대비(%)</span>
            <div class="range">
              <input v-model.number="model.minChangeRate" type="number" step="0.1" placeholder="최소" />
              <em>~</em>
              <input v-model.number="model.maxChangeRate" type="number" step="0.1" placeholder="최대" />
            </div>
          </div>

          <label class="check">
            <input v-model="model.excludeWarning" type="checkbox" />
            유의 종목 제외
          </label>
        </div>
      </div>

      <!-- 추세 -->
      <div class="col-md-6 col-xl-3">
        <div class="group">
          <h6>추세</h6>

          <label class="check">
            <input v-model="model.maAligned" type="checkbox" />
            이평선 정배열 (MA5 &gt; MA20 &gt; MA60)
          </label>

          <label class="check">
            <input v-model="model.priceAboveMa20" type="checkbox" />
            현재가 &gt; MA20
          </label>

          <label class="field">
            <span>골든크로스 (MA5↑MA20)</span>
            <div class="unit-input">
              <em>최근</em>
              <input v-model.number="model.goldenCrossWithin" type="number" min="1" placeholder="—" />
              <em>봉 이내</em>
            </div>
          </label>
        </div>
      </div>

      <!-- 모멘텀 -->
      <div class="col-md-6 col-xl-3">
        <div class="group">
          <h6>모멘텀</h6>

          <div class="field">
            <span>RSI(14)</span>
            <div class="range">
              <input v-model.number="model.rsiMin" type="number" min="0" max="100" placeholder="최소" />
              <em>~</em>
              <input v-model.number="model.rsiMax" type="number" min="0" max="100" placeholder="최대" />
            </div>
          </div>

          <label class="field">
            <span>MACD 시그널 상향돌파</span>
            <div class="unit-input">
              <em>최근</em>
              <input v-model.number="model.macdCrossWithin" type="number" min="1" placeholder="—" />
              <em>봉 이내</em>
            </div>
          </label>

          <label class="field">
            <span>볼린저 하단 터치 후 반등</span>
            <div class="unit-input">
              <em>최근</em>
              <input v-model.number="model.bbLowerTouchWithin" type="number" min="1" placeholder="—" />
              <em>봉 이내</em>
            </div>
          </label>
        </div>
      </div>

      <!-- 거래량·변동성 -->
      <div class="col-md-6 col-xl-3">
        <div class="group">
          <h6>거래량 · 변동성</h6>

          <label class="field">
            <span>거래량 급증 (직전봉 / 20봉 평균)</span>
            <div class="unit-input">
              <input v-model.number="model.volumeSurgeRatio" type="number" min="0" step="0.5" placeholder="—" />
              <em>배 이상</em>
            </div>
          </label>

          <div class="field">
            <span>ATR(14) 변동성(%)</span>
            <div class="range">
              <input v-model.number="model.atrPctMin" type="number" min="0" step="0.1" placeholder="최소" />
              <em>~</em>
              <input v-model.number="model.atrPctMax" type="number" min="0" step="0.1" placeholder="최대" />
            </div>
          </div>

          <div class="field">
            <span>20봉 고점 대비 하락(%)</span>
            <div class="range">
              <input v-model.number="model.dropFromHighMin" type="number" min="0" step="0.5" placeholder="최소" />
              <em>~</em>
              <input v-model.number="model.dropFromHighMax" type="number" min="0" step="0.5" placeholder="최대" />
            </div>
          </div>

          <label class="field">
            <span>정렬 기준</span>
            <select v-model="model.sortBy">
              <option value="VOLUME_SURGE">거래량 급증 배수 높은 순</option>
              <option value="TRADE_AMOUNT">거래대금 높은 순</option>
              <option value="CHANGE_RATE">등락률 높은 순</option>
              <option value="RSI_ASC">RSI 낮은 순</option>
            </select>
          </label>
        </div>
      </div>

    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';

defineProps({
  loading: { type: Boolean, default: false }
});

defineEmits(['search']);

// 부모(ScreenerView)와 v-model 로 연결된 조건 객체
const model = defineModel({ type: Object, required: true });

const open = ref(true);

// 예시 조건 묶음 — 시작점일 뿐 수익을 보장하지 않음
const PRESETS = [
  {
    name: '거래량 돌파',
    desc: '1시간봉 정배열 + 거래량 3배 이상 급증',
    values: { unit: 'MIN60', maAligned: true, priceAboveMa20: true, volumeSurgeRatio: 3, sortBy: 'VOLUME_SURGE' }
  },
  {
    name: '눌림목',
    desc: '일봉 정배열 + 고점 대비 3~10% 조정 + RSI 40~60',
    values: { unit: 'DAY', maAligned: true, dropFromHighMin: 3, dropFromHighMax: 10, rsiMin: 40, rsiMax: 60, sortBy: 'TRADE_AMOUNT' }
  },
  {
    name: '과매도 반등',
    desc: '1시간봉 RSI 35 이하 + 볼린저 하단 터치 후 반등',
    values: { unit: 'MIN60', rsiMax: 35, bbLowerTouchWithin: 3, sortBy: 'RSI_ASC' }
  },
  {
    name: '장 시작 +5%',
    desc: '오늘 09:00 이후 5% 이상 오른 종목 (업비트 등락률은 매일 09:00 기준). 전략 창에서 매수 시간대 09:00~09:10 과 같이 쓰기',
    values: { unit: 'MIN15', minChangeRate: 5, sortBy: 'CHANGE_RATE' }
  }
];

const reset = () => {
  Object.assign(model.value, defaultCondition());
};

const applyPreset = (preset) => {
  Object.assign(model.value, defaultCondition(), preset.values);
};

// 켜져 있는 조건 수 (기준봉·정렬은 조건이 아니므로 제외)
const activeCount = computed(() => {
  const skip = ['unit', 'sortBy'];
  return Object.entries(model.value)
    .filter(([k, v]) => !skip.includes(k))
    .filter(([, v]) => v === true || (typeof v === 'number' && !Number.isNaN(v)))
    .length;
});
</script>

<script>
// 조건 기본값. 숫자 조건은 null(빈칸) = 사용 안 함
export function defaultCondition() {
  return {
    unit: 'MIN60',
    minPrice: null,
    maxPrice: null,
    minTradeAmountEok: 50,
    minChangeRate: null,
    maxChangeRate: null,
    excludeWarning: true,
    maAligned: false,
    priceAboveMa20: false,
    goldenCrossWithin: null,
    rsiMin: null,
    rsiMax: null,
    macdCrossWithin: null,
    bbLowerTouchWithin: null,
    volumeSurgeRatio: null,
    atrPctMin: null,
    atrPctMax: null,
    dropFromHighMin: null,
    dropFromHighMax: null,
    sortBy: 'VOLUME_SURGE'
  };
}
</script>

<style scoped>
.condition-card {
  text-align: left;
  background-color: #161b22;
  border-radius: 12px;
  padding: 16px 20px;
  margin-bottom: 16px;
}

.condition-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.btn-link-plain {
  background: none;
  border: none;
  color: white;
  font-size: 1.1rem;
  font-weight: 600;
  padding: 0;
}

.group {
  background: #0d1117;
  border: 1px solid #30363d;
  border-radius: 10px;
  padding: 12px 14px;
  height: 100%;
}

.group h6 {
  color: #8b949e;
  font-size: 0.8rem;
  letter-spacing: 0.05em;
  margin-bottom: 10px;
}

.field {
  display: block;
  margin-bottom: 10px;
  font-size: 0.85rem;
}

.field > span {
  display: block;
  color: #c9d1d9;
  margin-bottom: 4px;
}

.check {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.85rem;
  color: #c9d1d9;
  margin-bottom: 10px;
  cursor: pointer;
}

input[type='number'],
select {
  width: 100%;
  min-width: 0;
  background: #161b22;
  color: white;
  border: 1px solid #30363d;
  border-radius: 6px;
  padding: 5px 8px;
  font-size: 0.85rem;
}

input[type='number']:focus,
select:focus {
  outline: none;
  border-color: #3b82f6;
}

.range,
.unit-input {
  display: flex;
  align-items: center;
  gap: 6px;
}

.range em,
.unit-input em {
  font-style: normal;
  color: #8b949e;
  white-space: nowrap;
}

.unit-input input {
  max-width: 90px;
}
</style>
