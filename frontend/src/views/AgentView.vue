<template>
  <div class="agent-view">

    <div v-if="error" class="alert alert-warning py-2">{{ error }}</div>

    <!-- 요약 -->
    <div class="row g-3 mb-3">
      <div class="col-6 col-lg-3">
        <div class="stat">
          <div class="label">보유 평가손익</div>
          <div class="value"><PnlText :value="summary?.evalPnl" unit="원" /></div>
          <div class="sub"><PnlText :value="summary?.evalPnlRate" /> · {{ summary?.openCount ?? 0 }}종목</div>
        </div>
      </div>
      <div class="col-6 col-lg-3">
        <div class="stat">
          <div class="label">오늘 실현손익</div>
          <div class="value"><PnlText :value="summary?.todayRealizedPnl" unit="원" /></div>
        </div>
      </div>
      <div class="col-6 col-lg-3">
        <div class="stat">
          <div class="label">누적 실현손익</div>
          <div class="value"><PnlText :value="summary?.realizedPnl" unit="원" /></div>
          <div class="sub"><PnlText :value="summary?.realizedPnlRate" /></div>
        </div>
      </div>
      <div class="col-6 col-lg-3">
        <div class="stat">
          <div class="label">승률</div>
          <div class="value">{{ summary?.winRate == null ? '—' : summary.winRate.toFixed(1) + '%' }}</div>
          <div class="sub">완료 {{ summary?.closedCount ?? 0 }}건</div>
        </div>
      </div>
    </div>

    <div class="row g-3">
      <!-- 보유 중 -->
      <div class="col-xl-8">
        <div class="card-box">
          <div class="box-header">
            <h5 class="mb-0">보유 중</h5>
            <small class="text-secondary">{{ formatTime(updatedAt) }} 갱신 · 5초마다</small>
          </div>

          <div v-if="!positions.length" class="empty">보유 중인 종목이 없어요.</div>

          <div v-else class="table-wrap">
            <table class="table table-dark table-sm align-middle mb-0">
              <thead>
                <tr>
                  <th>종목</th>
                  <th>전략</th>
                  <th>매수일시</th>
                  <th class="text-end">매수가</th>
                  <th class="text-end">현재가</th>
                  <th class="text-end">평가수익률</th>
                  <th class="text-end">평가손익</th>
                  <th class="text-end">보유</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="p in positions" :key="p.trade.id">
                  <td>
                    <div>{{ p.trade.koreanName }}</div>
                    <small class="text-secondary">{{ p.trade.market }}</small>
                  </td>
                  <td class="text-secondary small">{{ p.trade.strategyName }}</td>
                  <td class="small">{{ formatDateTime(p.trade.buyAt) }}</td>
                  <td class="text-end">{{ formatPrice(p.trade.buyPrice) }}</td>
                  <td class="text-end">{{ p.currentPrice == null ? '—' : formatPrice(p.currentPrice) }}</td>
                  <td class="text-end"><PnlText :value="p.evalPnlRate" /></td>
                  <td class="text-end"><PnlText :value="p.evalPnl" unit="원" /></td>
                  <td class="text-end small">{{ formatDuration(now - p.trade.buyAt) }}</td>
                  <td class="text-end">
                    <button class="btn btn-sm btn-outline-secondary" :disabled="busy" @click="sellNow(p)">매도</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <small class="note">평가손익은 지금 팔았을 때 기준(수수료·체결가 불리 반영)이에요.</small>
        </div>
      </div>

      <!-- 활동 기록 -->
      <div class="col-xl-4">
        <div class="card-box">
          <div class="box-header">
            <h5 class="mb-0">활동 기록</h5>
            <button class="btn btn-sm btn-outline-secondary" :disabled="busy" title="다음 주기(1분)를 기다리지 않고 바로 판단" @click="runNow">
              지금 실행
            </button>
          </div>
          <div v-if="!logs.length" class="empty">아직 기록이 없어요.</div>
          <ul v-else class="log-list">
            <li v-for="l in logs" :key="l.id" :class="'lv-' + l.level">
              <span class="t">{{ formatDateTime(l.createdAt).slice(5) }}</span>
              <span class="badge-lv">{{ LEVEL[l.level] ?? l.level }}</span>
              <span class="m">{{ l.message }}</span>
            </li>
          </ul>
        </div>
      </div>
    </div>

    <!-- 전략 -->
    <div class="card-box mt-3">
      <div class="box-header">
        <h5 class="mb-0">전략</h5>
        <RouterLink to="/screener" class="btn btn-sm btn-primary">+ 종목 검색에서 새 전략</RouterLink>
      </div>

      <div v-if="!strategies.length" class="empty">
        아직 전략이 없어요. 종목 검색 탭에서 조건을 검색한 뒤 "이 조건으로 에이전트 시작"을 눌러주세요.
      </div>

      <div v-for="s in strategies" :key="s.id" class="strategy">
        <div class="strategy-head">
          <div>
            <span class="state" :class="s.enabled ? 'on' : 'off'">{{ s.enabled ? '실행 중' : '정지' }}</span>
            <b>{{ s.name }}</b>
            <span class="mode">{{ s.mode === 'PAPER' ? '모의매매' : '실매매' }}</span>
          </div>
          <div class="d-flex gap-2">
            <button class="btn btn-sm btn-outline-light" :disabled="busy" @click="btStrategy = s">백테스트</button>
            <button v-if="s.enabled" class="btn btn-sm btn-outline-warning" :disabled="busy" @click="toggle(s)">정지</button>
            <button v-else class="btn btn-sm btn-outline-success" :disabled="busy" @click="toggle(s)">시작</button>
            <button class="btn btn-sm btn-outline-danger" :disabled="busy" @click="remove(s)">삭제</button>
          </div>
        </div>
        <div v-if="s.naturalText" class="natural">“{{ s.naturalText }}”</div>
        <div class="chips">
          <span v-for="l in conditionLabels(s.condition)" :key="l" class="chip">{{ l }}</span>
        </div>
        <div class="rules">
          1회 {{ formatWon(s.orderKrw) }} · 최대 {{ s.maxPositions }}종목
          <template v-for="r in ruleLabels(s)" :key="r"> · {{ r }}</template>
        </div>
      </div>
    </div>

    <BacktestModal v-if="btStrategy" :strategy="btStrategy" @close="btStrategy = null" />
  </div>
</template>

<script setup>
import { ref, onActivated, onDeactivated, onBeforeUnmount } from 'vue';
import PnlText from '../components/PnlText.vue';
import BacktestModal from '../components/BacktestModal.vue';
import {
  agentSummary, listPositions, agentLogs, listStrategies,
  startStrategy, stopStrategy, deleteStrategy, closeTrade, runAgentNow
} from '../api/backendApi';
import {
  formatPrice, formatWon, formatDateTime, formatTime, formatDuration,
  conditionLabels, ruleLabels, apiError
} from '../utils/format';

const LEVEL = { BUY: '매수', SELL: '매도', INFO: '안내', WARN: '주의' };
const REFRESH_MS = 5000;

const summary = ref(null);
const positions = ref([]);
const logs = ref([]);
const strategies = ref([]);
const error = ref('');
const busy = ref(false);
const updatedAt = ref(Date.now());
const now = ref(Date.now());
const btStrategy = ref(null);

let timer = null;

const load = async () => {
  try {
    const [s, p, l, st] = await Promise.all([agentSummary(), listPositions(), agentLogs(40), listStrategies()]);
    summary.value = s;
    positions.value = p;
    logs.value = l;
    strategies.value = st;
    error.value = '';
    updatedAt.value = Date.now();
  } catch (e) {
    error.value = apiError(e, '불러오기 실패');
  }
  now.value = Date.now();
};

// 이 탭이 보일 때만 5초마다 갱신 (KeepAlive 라 다른 탭으로 가면 멈춤)
// onActivated 는 처음 마운트될 때도 호출된다
const startPolling = () => {
  stopPolling();
  load();
  timer = setInterval(load, REFRESH_MS);
};
const stopPolling = () => {
  if (timer) clearInterval(timer);
  timer = null;
};

onActivated(startPolling);
onDeactivated(stopPolling);
onBeforeUnmount(stopPolling);

const act = async (fn, fallback) => {
  busy.value = true;
  try {
    await fn();
    await load();
  } catch (e) {
    error.value = apiError(e, fallback);
  } finally {
    busy.value = false;
  }
};

const toggle = (s) => act(() => (s.enabled ? stopStrategy(s.id) : startStrategy(s.id)), '변경 실패');

const remove = (s) => {
  if (!confirm(`"${s.name}" 전략을 삭제할까요? 거래 기록은 남아요.`)) return;
  act(() => deleteStrategy(s.id), '삭제 실패');
};

const sellNow = (p) => {
  if (!confirm(`${p.trade.koreanName}을(를) 현재가로 매도할까요? (모의매매)`)) return;
  act(() => closeTrade(p.trade.id), '매도 실패');
};

const runNow = () => act(runAgentNow, '실행 실패');
</script>

<style scoped>
.agent-view {
  text-align: left;
}

.stat {
  background: #161b22;
  border-radius: 12px;
  padding: 14px 16px;
  height: 100%;
}

.stat .label {
  color: #8b949e;
  font-size: 0.8rem;
}

.stat .value {
  font-size: 1.35rem;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.stat .sub {
  font-size: 0.85rem;
  color: #8b949e;
}

.card-box {
  background: #161b22;
  border-radius: 12px;
  padding: 16px 18px;
  height: 100%;
}

.box-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  gap: 8px;
}

.empty {
  color: #8b949e;
  padding: 24px 0;
  text-align: center;
}

.table-wrap {
  overflow-x: auto;
}

th {
  font-size: 0.78rem;
  color: #8b949e;
  white-space: nowrap;
}

td {
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.note {
  display: block;
  color: #6e7681;
  margin-top: 8px;
}

.log-list {
  list-style: none;
  padding: 0;
  margin: 0;
  max-height: 360px;
  overflow-y: auto;
  font-size: 0.82rem;
}

.log-list li {
  display: flex;
  gap: 6px;
  padding: 5px 0;
  border-bottom: 1px solid #21262d;
}

.log-list .t {
  color: #6e7681;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.badge-lv {
  white-space: nowrap;
  border-radius: 4px;
  padding: 0 5px;
  background: #30363d;
  color: #c9d1d9;
  height: fit-content;
}

.lv-BUY .badge-lv { background: rgba(239, 68, 68, 0.2); color: #ef4444; }
.lv-SELL .badge-lv { background: rgba(59, 130, 246, 0.2); color: #60a5fa; }
.lv-WARN .badge-lv { background: rgba(240, 180, 41, 0.2); color: #f0b429; }

.log-list .m {
  color: #c9d1d9;
  word-break: break-all;
}

.strategy {
  border-top: 1px solid #21262d;
  padding: 12px 0;
}

.strategy-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.state {
  font-size: 0.75rem;
  border-radius: 4px;
  padding: 1px 6px;
  margin-right: 6px;
}

.state.on { background: rgba(34, 197, 94, 0.2); color: #4ade80; }
.state.off { background: #30363d; color: #8b949e; }

.mode {
  font-size: 0.75rem;
  color: #8b949e;
  margin-left: 6px;
}

.natural {
  color: #c9d1d9;
  font-size: 0.88rem;
  margin-top: 6px;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 6px;
}

.chip {
  background: #30363d;
  color: #c9d1d9;
  border-radius: 6px;
  padding: 1px 7px;
  font-size: 0.76rem;
}

.rules {
  font-size: 0.82rem;
  color: #8b949e;
  margin-top: 6px;
}
</style>
