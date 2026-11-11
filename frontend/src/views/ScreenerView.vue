<template>
  <div>
    <!-- ① 조건 (맨 위) -->
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
import { ref } from 'vue';

import ConditionPanel, { defaultCondition } from '../components/ConditionPanel.vue';
import ResultList from '../components/ResultList.vue';
import ChartPanel from '../components/ChartPanel.vue';
import { screen } from '../api/backendApi';

const condition = ref(defaultCondition());
const result = ref(null);
const error = ref('');
const loading = ref(false);
const selected = ref(null);

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
    error.value =
      e.response?.data?.message ??
      (e.response ? `검색 실패 (HTTP ${e.response.status})` : '백엔드 서버(8080)에 연결할 수 없어요. 서버가 켜져 있는지 확인해주세요.');
  } finally {
    loading.value = false;
  }
};
</script>
