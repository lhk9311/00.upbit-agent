<template>
  <div class="chart-container">
    <apexchart ref="chartRef" type="candlestick" height="500" :options="chartOptions" :series="series" />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue';

const props = defineProps({
  chartData: { type: Array, default: () => [] }
});

const chartRef = ref(null);

const series = computed(() => [{ name: 'price', data: props.chartData }]);

// 가격대에 맞춰 소수점 자릿수 조절 (0.008원 같은 코인도 표시되도록)
function formatPrice(v) {
  if (v == null || Number.isNaN(v)) return '';
  const abs = Math.abs(v);
  const digits = abs >= 100 ? 0 : abs >= 1 ? 2 : 4;
  return v.toLocaleString('ko-KR', { maximumFractionDigits: digits });
}

function xAxis(data) {
  const axis = { type: 'datetime', labels: { datetimeUTC: false } };
  if (data.length < 2) return axis;
  // 양 끝 캔들이 잘리지 않도록 캔들 간격의 절반만큼 여유를 둔다
  const gap = (data[data.length - 1].x - data[0].x) / (data.length - 1);
  return { ...axis, min: data[0].x - gap / 2, max: data[data.length - 1].x + gap / 2 };
}

function yAxis(data) {
  const axis = { tickAmount: 6, labels: { formatter: formatPrice } };
  if (!data.length) return axis;
  let low = Infinity, high = -Infinity;
  for (const c of data) {
    low = Math.min(low, c.y[2]);
    high = Math.max(high, c.y[1]);
  }
  const pad = (high - low) * 0.05 || high * 0.01;
  return { ...axis, min: low - pad, max: high + pad };
}

// 옵션은 고정. x축·y축은 데이터가 바뀔 때마다 직접 갱신한다.
// (ApexCharts는 처음 그릴 때의 축 눈금을 계속 유지하는 문제가 있음)
const chartOptions = {
  chart: { type: 'candlestick', background: '#161b22', animations: { enabled: false } },
  theme: { mode: 'dark' },
  // 한국 거래소 관례: 상승 빨강, 하락 파랑
  plotOptions: {
    candlestick: { colors: { upward: '#ef4444', downward: '#3b82f6' } }
  },
  xaxis: xAxis([]),
  tooltip: { x: { format: 'yyyy-MM-dd HH:mm' } },
  yaxis: yAxis([])
};

watch(
  () => props.chartData,
  (data) => {
    chartRef.value?.updateOptions({ xaxis: xAxis(data), yaxis: yAxis(data) }, false, false);
  }
);
</script>
