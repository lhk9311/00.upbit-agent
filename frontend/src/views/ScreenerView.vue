<template>
  <div>
    <!-- ① 조건 (맨 위): 자연어 입력 → 조건 패널에 채움 → 검색 -->
    <NaturalInput
      :loading="nlLoading"
      :disabled="!nlEnabled"
      :error="nlError"
      :parsed="nlParsed"
      @submit="searchByText"
    />

    <ConditionPanel v-model="condition" :loading="loading" @search="search" />

    <!-- ② 결과 최대 10개 + 차트 -->
    <div class="row g-3">
      <div class="col-lg-6">
        <ResultList
          :result="result"
          :error="error"
          :selectedMarket="selected?.market"
          @select="selected = $event"
        />
      </div>

      <div class="col-lg-6">
        <ChartPanel :market="selected?.market" :title="selected?.koreanName" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';

import NaturalInput from '../components/NaturalInput.vue';
import ConditionPanel, { defaultCondition } from '../components/ConditionPanel.vue';
import ResultList from '../components/ResultList.vue';
import ChartPanel from '../components/ChartPanel.vue';
import { screen, parseCondition, parseStatus } from '../api/backendApi';

const condition = ref(defaultCondition());
const result = ref(null);
const error = ref('');
const loading = ref(false);
const selected = ref(null);

// 자연어 검색 상태
const nlEnabled = ref(true);
const nlLoading = ref(false);
const nlError = ref('');
const nlParsed = ref(null);

onMounted(async () => {
  try {
    nlEnabled.value = (await parseStatus()).enabled;
  } catch {
    // 백엔드가 꺼져 있으면 입력은 열어두고, 실제 요청 때 에러를 보여준다
  }
});

const errorMessage = (e, fallback) =>
  e.response?.data?.message ??
  (e.response ? `${fallback} (HTTP ${e.response.status})` : '백엔드 서버(8080)에 연결할 수 없어요. 서버가 켜져 있는지 확인해주세요.');

// 화면 입력값 → 백엔드 요청 형식
// 빈칸('')은 null(조건 끔)로, 거래대금은 억 → 원으로
const toRequest = (c) => {
  const num = (v) => (typeof v === 'number' && !Number.isNaN(v) ? v : null);
  const flag = (v) => (v ? true : null);

  return {
    unit: c.unit,
    minPrice: num(c.minPrice),
    maxPrice: num(c.maxPrice),
    minTradeAmount: num(c.minTradeAmountEok) == null ? null : c.minTradeAmountEok * 100_000_000,
    minChangeRate: num(c.minChangeRate),
    maxChangeRate: num(c.maxChangeRate),
    excludeWarning: flag(c.excludeWarning),
    maAligned: flag(c.maAligned),
    priceAboveMa20: flag(c.priceAboveMa20),
    goldenCrossWithin: num(c.goldenCrossWithin),
    rsiMin: num(c.rsiMin),
    rsiMax: num(c.rsiMax),
    macdCrossWithin: num(c.macdCrossWithin),
    bbLowerTouchWithin: num(c.bbLowerTouchWithin),
    volumeSurgeRatio: num(c.volumeSurgeRatio),
    atrPctMin: num(c.atrPctMin),
    atrPctMax: num(c.atrPctMax),
    dropFromHighMin: num(c.dropFromHighMin),
    dropFromHighMax: num(c.dropFromHighMax),
    sortBy: c.sortBy,
    limit: 10
  };
};

// 백엔드 조건(ScreenerCondition) → 화면 입력값
// 문장에서 말하지 않은 조건은 모두 끈 상태로 시작한다. 단, 유의 종목 제외는 안전을 위해 기본으로 켠다.
const toForm = (c) => ({
  ...defaultCondition(),
  unit: c.unit ?? 'MIN60',
  minPrice: c.minPrice,
  maxPrice: c.maxPrice,
  minTradeAmountEok: c.minTradeAmount == null ? null : c.minTradeAmount / 100_000_000,
  minChangeRate: c.minChangeRate,
  maxChangeRate: c.maxChangeRate,
  excludeWarning: true,
  maAligned: !!c.maAligned,
  priceAboveMa20: !!c.priceAboveMa20,
  goldenCrossWithin: c.goldenCrossWithin,
  rsiMin: c.rsiMin,
  rsiMax: c.rsiMax,
  macdCrossWithin: c.macdCrossWithin,
  bbLowerTouchWithin: c.bbLowerTouchWithin,
  volumeSurgeRatio: c.volumeSurgeRatio,
  atrPctMin: c.atrPctMin,
  atrPctMax: c.atrPctMax,
  dropFromHighMin: c.dropFromHighMin,
  dropFromHighMax: c.dropFromHighMax,
  sortBy: c.sortBy ?? 'VOLUME_SURGE'
});

// 문장 → 조건 변환 → 패널에 채우고 바로 검색
const searchByText = async (text) => {
  nlLoading.value = true;
  nlError.value = '';

  try {
    const parsed = await parseCondition(text);
    nlParsed.value = parsed;
    condition.value = toForm(parsed.condition);
    await search();
  } catch (e) {
    nlParsed.value = null;
    nlError.value = errorMessage(e, '조건 변환 실패');
  } finally {
    nlLoading.value = false;
  }
};

const search = async () => {
  loading.value = true;
  error.value = '';

  try {
    result.value = await screen(toRequest(condition.value));

    // 1위 종목 차트를 바로 보여준다 (이전 선택이 결과에 남아 있으면 유지)
    const items = result.value.items;
    if (!items.some((i) => i.market === selected.value?.market)) {
      selected.value = items[0] ?? null;
    }
  } catch (e) {
    result.value = null;
    error.value = errorMessage(e, '검색 실패');
  } finally {
    loading.value = false;
  }
};
</script>
