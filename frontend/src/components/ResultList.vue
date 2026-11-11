<template>
  <div class="result-card">

    <div class="result-header">
      <h5 class="mb-0">검색 결과</h5>

      <!-- 조건이 세밀할수록 줄어드는 과정이 보이도록 -->
      <small v-if="result" class="funnel">
        전체 {{ result.evaluatedCount }}개
        → 조건 통과 <b>{{ result.matchedCount }}</b>개
        → 상위 <b>{{ result.items.length }}</b>개
        <span v-if="result.cacheUpdatedAt" class="ms-2">
          (캔들 기준 {{ formatTime(result.cacheUpdatedAt) }})
        </span>
      </small>
    </div>

    <div v-if="error" class="alert alert-warning py-2 mt-3 mb-0">{{ error }}</div>

    <div v-else-if="!result" class="empty">
      위에서 조건을 고르고 <b>검색</b>을 눌러주세요.
    </div>

    <div v-else-if="result.items.length === 0" class="empty">
      조건을 모두 만족하는 종목이 없어요. 조건을 조금 완화해보세요.
    </div>

    <div v-else class="table-wrap">
      <table class="table table-dark table-hover table-sm align-middle mb-0">
        <thead>
          <tr>
            <th>#</th>
            <th>종목</th>
            <th class="text-end">현재가</th>
            <th class="text-end">전일대비</th>
            <th class="text-end">거래대금</th>
            <th class="text-end">RSI</th>
            <th class="text-end">거래량</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="(item, i) in result.items" :key="item.market">
            <tr
              :class="{ selected: item.market === selectedMarket }"
              @click="$emit('select', item)"
            >
              <td>{{ i + 1 }}</td>
              <td>
                <div>{{ item.koreanName }}</div>
                <small class="text-secondary">{{ item.market }}</small>
              </td>
              <td class="text-end">{{ formatPrice(item.tradePrice) }}</td>
              <td class="text-end"><PnlText :value="item.changeRate" /></td>
              <td class="text-end">{{ formatEok(item.accTradePrice24h) }}</td>
              <td class="text-end">{{ fixed(item.rsi, 1) }}</td>
              <td class="text-end">{{ item.volumeRatio == null ? '—' : fixed(item.volumeRatio, 1) + '배' }}</td>
            </tr>
            <tr
              v-if="item.matched.length"
              class="matched-row"
              :class="{ selected: item.market === selectedMarket }"
              @click="$emit('select', item)"
            >
              <td></td>
              <td colspan="6">
                <span v-for="m in item.matched" :key="m" class="badge me-1">{{ m }}</span>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>

    <!-- 검색을 한 번 해야 어떤 조건인지 확인한 뒤 시작할 수 있다 -->
    <button
      class="btn btn-outline-light btn-sm w-100 mt-3"
      :disabled="!result"
      :title="result ? '' : '먼저 검색해서 조건을 확인해주세요'"
      @click="$emit('start-agent')"
    >
      이 조건으로 에이전트 시작 (모의매매)
    </button>

  </div>
</template>

<script setup>
import PnlText from './PnlText.vue';
import { formatPrice, formatEok, formatTime } from '../utils/format';

defineProps({
  result: { type: Object, default: null },
  error: { type: String, default: '' },
  selectedMarket: { type: String, default: '' }
});

defineEmits(['select', 'start-agent']);

const fixed = (v, d) => (v == null ? '—' : v.toFixed(d));

</script>

<style scoped>
.result-card {
  text-align: left;
  background-color: #1a1a1a;
  border-radius: 12px;
  padding: 20px;
  height: 700px;
  display: flex;
  flex-direction: column;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 6px;
}

.funnel {
  color: #8b949e;
}

.funnel b {
  color: white;
}

.empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #8b949e;
  text-align: center;
}

.table-wrap {
  flex: 1;
  overflow: auto;
  margin-top: 12px;
}

th {
  white-space: nowrap;
  font-size: 0.8rem;
  color: #8b949e;
}

td {
  white-space: nowrap;
  cursor: pointer;
  font-size: 0.9rem;
}

.matched-row td {
  padding-top: 0;
  border-top: none;
}

/* 종목 행과 통과 조건 행 사이 선 제거 */
tbody tr:not(.matched-row):has(+ .matched-row) td {
  border-bottom: none;
}

.matched-row .badge {
  background: #30363d;
  color: #c9d1d9;
  font-weight: 400;
}

tr.selected td {
  --bs-table-bg: #1f2a3a;
}
</style>
