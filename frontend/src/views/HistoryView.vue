<template>
  <div class="history-view">

    <!-- 필터 -->
    <div class="filters">
      <div class="btn-group" role="group" aria-label="상태">
        <button
          v-for="o in STATUS_OPTIONS"
          :key="o.label"
          class="btn btn-sm"
          :class="filter.status === o.value ? 'btn-primary' : 'btn-outline-secondary'"
          @click="filter.status = o.value"
        >
          {{ o.label }}
        </button>
      </div>

      <label class="f">
        <span>전략</span>
        <select v-model="filter.strategyId">
          <option :value="null">전체</option>
          <option v-for="s in strategies" :key="s.id" :value="s.id">{{ s.name }}</option>
        </select>
      </label>

      <label class="f">
        <span>매수일</span>
        <input v-model="filter.from" type="date" />
        <em>~</em>
        <input v-model="filter.to" type="date" />
      </label>

      <button class="btn btn-sm btn-outline-secondary" @click="resetFilter">초기화</button>
    </div>

    <div v-if="error" class="alert alert-warning py-2">{{ error }}</div>

    <!-- 요약 -->
    <div class="summary">
      <span>조회 <b>{{ rows.length }}</b>건 (진행 중 {{ openCount }} · 완료 {{ closedRows.length }})</span>
      <span>완료 거래 실현손익 <PnlText :value="closedPnl" unit="원" /></span>
      <span>승률 <b>{{ winRate == null ? '—' : winRate.toFixed(1) + '%' }}</b></span>
    </div>

    <div class="card-box">
      <div v-if="!rows.length" class="empty">조건에 맞는 거래가 없어요.</div>

      <div v-else class="table-wrap">
        <table class="table table-dark table-sm table-hover align-middle mb-0">
          <thead>
            <tr>
              <th class="sortable" @click="sortBy('market')">종목 <i>{{ arrow('market') }}</i></th>
              <th>전략</th>
              <th>상태</th>
              <th class="sortable" @click="sortBy('buyAt')">매수일시 <i>{{ arrow('buyAt') }}</i></th>
              <th class="sortable text-end" @click="sortBy('buyPrice')">매수가 <i>{{ arrow('buyPrice') }}</i></th>
              <th class="text-end">수량</th>
              <th class="text-end">매수금액</th>
              <th class="sortable" @click="sortBy('sellAt')">매도일시 <i>{{ arrow('sellAt') }}</i></th>
              <th class="sortable text-end" @click="sortBy('sellPrice')">매도가 <i>{{ arrow('sellPrice') }}</i></th>
              <th class="sortable text-end" @click="sortBy('pnlRate')">수익률 <i>{{ arrow('pnlRate') }}</i></th>
              <th class="sortable text-end" @click="sortBy('pnl')">손익 <i>{{ arrow('pnl') }}</i></th>
              <th>매도 사유</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="t in rows" :key="t.id" :class="{ open: t.status === 'OPEN' }">
              <td>
                <div>{{ t.koreanName }}</div>
                <small class="text-secondary">{{ t.market }}</small>
              </td>
              <td class="small text-secondary">{{ t.strategyName }}</td>
              <td>
                <span class="status" :class="t.status">{{ t.status === 'OPEN' ? '진행 중' : '완료' }}</span>
              </td>
              <td class="small">{{ formatDateTime(t.buyAt) }}</td>
              <td class="text-end">{{ formatPrice(t.buyPrice) }}</td>
              <td class="text-end small">{{ formatQty(t.quantity) }}</td>
              <td class="text-end small">{{ formatWon(t.buyAmount + t.buyFee) }}</td>

              <!-- 진행 중 거래는 매수 정보만 표시 -->
              <template v-if="t.status === 'CLOSED'">
                <td class="small">{{ formatDateTime(t.sellAt) }}</td>
                <td class="text-end">{{ formatPrice(t.sellPrice) }}</td>
                <td class="text-end"><PnlText :value="t.pnlRate" /></td>
                <td class="text-end"><PnlText :value="t.pnl" unit="원" /></td>
                <td class="small text-secondary">{{ t.sellReason }}</td>
              </template>
              <template v-else>
                <td></td>
                <td></td>
                <td></td>
                <td></td>
                <td></td>
              </template>
            </tr>
          </tbody>
        </table>
      </div>
      <small class="note">모의매매 기록이에요. 손익은 수수료(0.05%×2)와 체결가 불리(0.1%×2)를 반영한 값이에요.</small>
    </div>

  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onActivated } from 'vue';
import PnlText from '../components/PnlText.vue';
import { listTrades, listStrategies } from '../api/backendApi';
import { formatPrice, formatWon, formatQty, formatDateTime, apiError } from '../utils/format';

const STATUS_OPTIONS = [
  { label: '전체', value: null },
  { label: '진행 중', value: 'OPEN' },
  { label: '완료', value: 'CLOSED' }
];

const rows = ref([]);
const strategies = ref([]);
const error = ref('');

const emptyFilter = () => ({ status: null, strategyId: null, from: '', to: '' });
const filter = reactive(emptyFilter());
const sort = reactive({ key: 'buyAt', dir: 'desc' });

// 날짜(yyyy-mm-dd, 브라우저 시간대) → epoch ms. to 는 그날 끝까지 포함
const dayStart = (d) => (d ? new Date(`${d}T00:00:00`).getTime() : null);
const dayEnd = (d) => (d ? new Date(`${d}T00:00:00`).getTime() + 86_400_000 : null);

const load = async () => {
  try {
    const [t, s] = await Promise.all([
      listTrades({
        status: filter.status ?? undefined,
        strategyId: filter.strategyId ?? undefined,
        from: dayStart(filter.from) ?? undefined,
        to: dayEnd(filter.to) ?? undefined,
        sort: sort.key,
        dir: sort.dir
      }),
      listStrategies()
    ]);
    rows.value = t;
    strategies.value = s;
    error.value = '';
  } catch (e) {
    error.value = apiError(e, '불러오기 실패');
  }
};

watch([filter, sort], load, { deep: true });
onActivated(load); // 처음 열릴 때와 다른 탭에서 돌아올 때마다 새로 조회

// 같은 열을 다시 누르면 방향 전환. 종목명은 가나다순(오름차순)으로 시작
const sortBy = (key) => {
  if (sort.key === key) {
    sort.dir = sort.dir === 'desc' ? 'asc' : 'desc';
  } else {
    sort.key = key;
    sort.dir = key === 'market' ? 'asc' : 'desc';
  }
};

const arrow = (key) => (sort.key !== key ? '↕' : sort.dir === 'desc' ? '↓' : '↑');

const resetFilter = () => Object.assign(filter, emptyFilter());

const closedRows = computed(() => rows.value.filter((t) => t.status === 'CLOSED'));
const openCount = computed(() => rows.value.length - closedRows.value.length);
const closedPnl = computed(() =>
  closedRows.value.length ? closedRows.value.reduce((sum, t) => sum + t.pnl, 0) : null
);
const winRate = computed(() =>
  closedRows.value.length
    ? (closedRows.value.filter((t) => t.pnl > 0).length * 100) / closedRows.value.length
    : null
);
</script>

<style scoped>
.history-view {
  text-align: left;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  background: #161b22;
  border-radius: 12px;
  padding: 12px 16px;
  margin-bottom: 12px;
}

.f {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.85rem;
  color: #c9d1d9;
}

.f em {
  font-style: normal;
  color: #8b949e;
}

.f select,
.f input {
  background: #0d1117;
  color: white;
  border: 1px solid #30363d;
  border-radius: 6px;
  padding: 4px 8px;
  font-size: 0.85rem;
  color-scheme: dark;
}

.summary {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  color: #8b949e;
  font-size: 0.9rem;
  margin: 0 4px 10px;
}

.summary b {
  color: white;
}

.card-box {
  background: #161b22;
  border-radius: 12px;
  padding: 12px 16px;
}

.empty {
  color: #8b949e;
  padding: 32px 0;
  text-align: center;
}

.table-wrap {
  overflow-x: auto;
}

th {
  font-size: 0.78rem;
  color: #8b949e;
  white-space: nowrap;
  user-select: none;
}

th.sortable {
  cursor: pointer;
}

th.sortable:hover {
  color: white;
}

th i {
  font-style: normal;
  opacity: 0.7;
}

td {
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.status {
  font-size: 0.75rem;
  border-radius: 4px;
  padding: 1px 6px;
}

.status.OPEN {
  background: rgba(240, 180, 41, 0.18);
  color: #f0b429;
}

.status.CLOSED {
  background: #30363d;
  color: #8b949e;
}

.note {
  display: block;
  color: #6e7681;
  margin-top: 8px;
}
</style>
