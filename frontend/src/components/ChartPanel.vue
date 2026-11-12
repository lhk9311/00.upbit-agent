<template>
  <div class="chart-card">

    <h3>
      {{ market ? `${title} (${market})` : '차트 영역' }}
    </h3>

    <!-- 기간 버튼 -->
    <div class="chart-buttons">
      <button
        v-for="p in PERIODS"
        :key="p.value"
        :class="{ active: isActive(p.value) }"
        @click="changePeriod(p.value)"
      >
        {{ p.label }}
      </button>
    </div>

    <div class="chart-body">
      <CoinChart :chartData="chartData" />
    </div>

  </div>
</template>

<script setup>
// 차트 카드: 기간 버튼 + 캔들 차트 + 실시간(웹소켓)
// 실시간 리스트 탭과 종목 검색 탭에서 함께 사용한다.
import { ref, watch, onBeforeUnmount, onActivated, onDeactivated } from 'vue';
import axios from 'axios';

import CoinChart from './CoinChart.vue';

const props = defineProps({
  market: { type: String, default: '' },   // 예: KRW-BTC
  title: { type: String, default: '' }     // 예: 비트코인
});

const PERIODS = [
  { label: '실시간', value: 'realtime' },
  { label: '1분', value: 1 },
  { label: '3분', value: 3 },
  { label: '5분', value: 5 },
  { label: '10분', value: 10 },
  { label: '15분', value: 15 },
  { label: '30분', value: 30 },
  { label: '1시간', value: 60 },
  { label: '4시간', value: 240 },
  { label: '하루', value: 'day' },
  { label: '주간', value: 'week' },
  { label: '월간', value: 'month' }
];

const MAX_CANDLES = 50;
const MINUTE = 60 * 1000;

// 조회 기간 (숫자 = 분봉, 'day' | 'week' | 'month')
const currentPeriod = ref('day');

// 실시간 모드 여부 (기간 값과 분리해서 관리)
const isRealtime = ref(false);

// 차트 데이터
const chartData = ref([]);

// 웹소켓
let socket = null;

const isActive = (value) =>
  value === 'realtime'
    ? isRealtime.value
    : !isRealtime.value && currentPeriod.value === value;

// ---------- 종목 변경 ----------
watch(
  () => props.market,
  async (market) => {
    if (!market) {
      closeSocket();
      chartData.value = [];
      return;
    }

    if (isRealtime.value) {
      await startRealtime(market);
    } else {
      await loadChart(market);
    }
  },
  { immediate: true }
);

onBeforeUnmount(closeSocket);

// 다른 탭으로 가면 실시간 연결을 닫고, 돌아오면 다시 연결 (안 보이는 차트에 체결을 받을 필요 없음)
onDeactivated(closeSocket);
onActivated(() => {
  if (isRealtime.value && props.market && !socket) {
    startRealtime(props.market);
  }
});

// ---------- 기간 변경 ----------
async function changePeriod(period) {
  if (period === 'realtime') {
    isRealtime.value = true;
    currentPeriod.value = 1;
  } else {
    isRealtime.value = false;
    currentPeriod.value = period;
    closeSocket();
  }

  if (!props.market) {
    return;
  }

  if (isRealtime.value) {
    await startRealtime(props.market);
  } else {
    await loadChart(props.market);
  }
}

// ---------- 캔들 조회 (REST) ----------
function candleUrl(period) {
  if (typeof period === 'number') {
    return `https://api.upbit.com/v1/candles/minutes/${period}`;
  }
  return `https://api.upbit.com/v1/candles/${period}s`; // days, weeks, months
}

// 업비트 캔들 → 차트 데이터 { x: UTC 밀리초, y: [시가, 고가, 저가, 종가] }
// candle_date_time_utc 기준으로 변환해야 브라우저 시간대와 상관없이 정확함
function toChartCandle(candle) {
  return {
    x: new Date(candle.candle_date_time_utc + 'Z').getTime(),
    y: [
      candle.opening_price,
      candle.high_price,
      candle.low_price,
      candle.trade_price
    ]
  };
}

async function loadChart(market) {
  try {
    const res = await axios.get(candleUrl(currentPeriod.value), {
      params: { market, count: MAX_CANDLES }
    });

    // 그 사이 다른 종목을 눌렀으면 늦게 온 응답은 버린다
    if (market !== props.market) {
      return;
    }

    // 응답은 최신순 → 오래된 순으로 뒤집기
    chartData.value = res.data.reverse().map(toChartCandle);
  } catch (err) {
    console.error('캔들 조회 실패', err);
  }
}

// ---------- 실시간 (웹소켓) ----------
function closeSocket() {
  if (socket) {
    socket.onmessage = null;
    socket.close();
    socket = null;
  }
}

async function startRealtime(market) {
  closeSocket();

  // 직전 1분봉 50개를 먼저 깔고, 그 위에 실시간 체결을 이어 붙인다
  await loadChart(market);

  const ws = new WebSocket('wss://api.upbit.com/websocket/v1');
  socket = ws;

  ws.onopen = () => {
    ws.send(JSON.stringify([
      { ticket: `upbit-chart-${Date.now()}` },
      { type: 'ticker', codes: [market] }
    ]));
  };

  ws.onmessage = async (event) => {
    const data = JSON.parse(await event.data.text());

    // 다른 코인 데이터나 이미 닫힌 소켓의 메시지는 무시
    if (ws !== socket || data.code !== market) {
      return;
    }

    applyTick(data.trade_price, data.trade_timestamp);
  };

  ws.onerror = (err) => console.error('웹소켓 오류', err);
}

// 체결 1건을 1분봉에 반영
function applyTick(price, timestamp) {
  // 체결 시각을 분 단위로 내림 → REST 1분봉의 시각과 정확히 맞물림
  const bucket = Math.floor(timestamp / MINUTE) * MINUTE;

  const candles = chartData.value;
  const last = candles[candles.length - 1];

  if (last && last.x === bucket) {
    // 같은 분: 고가·저가·종가만 갱신
    const [open, high, low] = last.y;
    candles[candles.length - 1] = {
      x: bucket,
      y: [open, Math.max(high, price), Math.min(low, price), price]
    };
  } else if (!last || bucket > last.x) {
    // 새로운 분: 새 캔들 추가
    candles.push({ x: bucket, y: [price, price, price, price] });

    if (candles.length > MAX_CANDLES) {
      candles.shift();
    }
  } else {
    // 이미 지난 분의 늦게 도착한 체결은 무시
    return;
  }

  chartData.value = [...candles];
}
</script>

<style scoped>
.chart-card {
  background-color: #161b22;
  border-radius: 12px;
  padding: 20px;
  height: 700px;
  color: white;
}

.chart-card h3 {
  text-align: center;
}

.chart-body {
  height: 500px;
}

.chart-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 15px;
  justify-content: center;
}

.chart-buttons button {
  border: none;
  background: #30363d;
  color: white;
  padding: 6px 12px;
  border-radius: 6px;
  cursor: pointer;
}

.chart-buttons button:hover,
.chart-buttons button.active {
  background: #3b82f6;
}
</style>
