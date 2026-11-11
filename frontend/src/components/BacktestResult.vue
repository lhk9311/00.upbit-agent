<template>
  <div class="bt">

    <div class="period">
      {{ UNIT_LABEL[result.unit] }} · {{ result.bars }}봉
      <template v-if="result.from">· {{ formatDateTime(result.from) }} ~ {{ formatDateTime(result.to) }}</template>
      · 대상 {{ result.marketCount }}종목
    </div>

    <!-- 요약 -->
    <div class="stats">
      <div class="stat">
        <div class="label">총손익</div>
        <div class="value"><PnlText :value="result.totalPnl" unit="원" /></div>
        <div class="sub">투입 한도 {{ formatWon(result.capital) }} 대비 <PnlText :value="result.returnPct" /></div>
      </div>
      <div class="stat">
        <div class="label">거래 · 승률</div>
        <div class="value">{{ result.tradeCount }}건</div>
        <div class="sub">승률 {{ result.winRate == null ? '—' : result.winRate.toFixed(1) + '%' }}</div>
      </div>
      <div class="stat">
        <div class="label">최대 낙폭</div>
        <div class="value pnl-down">{{ result.maxDrawdown > 0 ? '-' + formatWon(result.maxDrawdown) : '0원' }}</div>
        <div class="sub">{{ result.maxDrawdownPct == null ? '' : '한도 대비 -' + result.maxDrawdownPct.toFixed(2) + '%' }}</div>
      </div>
      <div class="stat">
        <div class="label">비트코인 그냥 보유</div>
        <div class="value"><PnlText :value="result.benchmarkReturnPct" /></div>
        <div class="sub">평균 보유 {{ result.avgHoldHours == null ? '—' : result.avgHoldHours.toFixed(1) + '시간' }}</div>
      </div>
    </div>

    <!-- 수익곡선 -->
    <div v-if="result.equity.length" class="curve">
      <apexchart type="area" height="200" :options="chartOptions" :series="series" />
    </div>

    <!-- 거래 목록 -->
    <div v-if="result.trades.length" class="trades">
      <table class="table table-dark table-sm align-middle mb-0">
        <thead>
          <tr>
            <th>종목</th>
            <th>매수</th>
            <th class="text-end">매수가</th>
            <th>매도</th>
            <th class="text-end">매도가</th>
            <th class="text-end">수익률</th>
            <th>사유</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(t, i) in result.trades" :key="i">
            <td>{{ t.koreanName }}</td>
            <td class="small">{{ formatDateTime(t.buyAt).slice(5) }}</td>
            <td class="text-end">{{ formatPrice(t.buyPrice) }}</td>
            <td class="small">{{ formatDateTime(t.sellAt).slice(5) }}</td>
            <td class="text-end">{{ formatPrice(t.sellPrice) }}</td>
            <td class="text-end"><PnlText :value="t.pnlRate" /></td>
            <td class="small text-secondary">{{ t.reason }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-else class="empty">이 기간에는 조건에 맞아 매수한 종목이 없어요.</div>

    <!-- 주의사항 -->
    <details class="notes">
      <summary>계산 방식과 한계</summary>
      <ul>
        <li v-for="n in result.notes" :key="n">{{ n }}</li>
      </ul>
    </details>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import PnlText from './PnlText.vue';
import { formatWon, formatPrice, formatDateTime } from '../utils/format';

const props = defineProps({
  result: { type: Object, required: true }
});

const UNIT_LABEL = { MIN15: '15분봉', MIN60: '1시간봉', MIN240: '4시간봉', DAY: '일봉' };

const series = computed(() => [{
  name: '누적 손익(원)',
  data: props.result.equity.map((p) => ({ x: p.time, y: Math.round(p.pnl) }))
}]);

// 최종 손익이 플러스면 빨강, 마이너스면 파랑
const chartOptions = computed(() => {
  const color = props.result.totalPnl >= 0 ? '#ef4444' : '#3b82f6';
  return {
    chart: { type: 'area', background: 'transparent', toolbar: { show: false }, animations: { enabled: false } },
    theme: { mode: 'dark' },
    colors: [color],
    stroke: { width: 2, curve: 'straight' },
    fill: { type: 'solid', opacity: 0.12 },
    dataLabels: { enabled: false },
    grid: { borderColor: '#30363d' },
    xaxis: { type: 'datetime', labels: { datetimeUTC: false } },
    yaxis: { labels: { formatter: (v) => Math.round(v).toLocaleString('ko-KR') } },
    tooltip: { x: { format: 'MM-dd HH:mm' } },
    annotations: { yaxis: [{ y: 0, borderColor: '#8b949e', strokeDashArray: 4 }] }
  };
});
</script>

<style scoped>
.bt {
  text-align: left;
}

.period {
  color: #8b949e;
  font-size: 0.82rem;
  margin-bottom: 10px;
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

@media (max-width: 700px) {
  .stats {
    grid-template-columns: repeat(2, 1fr);
  }
}

.stat {
  background: #0d1117;
  border-radius: 8px;
  padding: 10px 12px;
}

.stat .label {
  color: #8b949e;
  font-size: 0.75rem;
}

.stat .value {
  font-size: 1.1rem;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.stat .sub {
  color: #8b949e;
  font-size: 0.78rem;
}

.curve {
  margin-top: 10px;
}

.trades {
  max-height: 240px;
  overflow: auto;
  margin-top: 6px;
}

th {
  font-size: 0.75rem;
  color: #8b949e;
  white-space: nowrap;
  position: sticky;
  top: 0;
}

td {
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
  font-size: 0.85rem;
}

.empty {
  color: #8b949e;
  text-align: center;
  padding: 18px 0;
}

.notes {
  margin-top: 10px;
  font-size: 0.8rem;
  color: #8b949e;
}

.notes summary {
  cursor: pointer;
}

.notes ul {
  margin: 6px 0 0;
  padding-left: 18px;
}
</style>
