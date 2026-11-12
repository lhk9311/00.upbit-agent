<template>
  <div class="settings-view">

    <div v-if="loadError" class="alert alert-warning py-2">{{ loadError }}</div>

    <div v-if="form" class="row g-3">

      <!-- 에이전트 -->
      <div class="col-xl-6">
        <section class="card-box">
          <h5>에이전트</h5>

          <div class="pause" :class="{ on: form.buyingPaused }">
            <div>
              <b>모든 전략 신규 매수 중단</b>
              <p>켜면 실행 중인 전략도 새로 사지 않아요. 보유 종목은 익절·손절 규칙대로 계속 관리해요.</p>
            </div>
            <label class="switch">
              <input v-model="form.buyingPaused" type="checkbox" aria-label="신규 매수 전체 중단" />
              <span></span>
            </label>
          </div>

          <div class="row g-2 mt-1">
            <label class="col-sm-4 field">
              <span>거래 수수료 (%)</span>
              <input v-model.number="form.feePct" type="number" min="0" max="1" step="0.01" />
              <small>업비트 KRW 마켓 0.05%</small>
            </label>
            <label class="col-sm-4 field">
              <span>모의 체결가 불리 (%)</span>
              <input v-model.number="form.slippagePct" type="number" min="0" max="2" step="0.05" />
              <small>살 때 비싸게, 팔 때 싸게</small>
            </label>
            <label class="col-sm-4 field">
              <span>재진입 대기 (분)</span>
              <input v-model.number="form.reentryCooldownMinutes" type="number" min="0" step="10" />
              <small>판 종목을 다시 안 사는 시간</small>
            </label>
          </div>
          <p class="note">모의매매와 백테스트에 같이 적용돼요. 판단 주기(1분)는 서버 설정(application.yml)에서 바꿔요.</p>
        </section>
      </div>

      <!-- 새 전략 기본값 -->
      <div class="col-xl-6">
        <section class="card-box">
          <h5>새 전략 기본값</h5>
          <p class="note mt-0">"이 조건으로 에이전트 시작" 창에 처음 채워지는 값이에요. 빈칸은 그 규칙을 끈 상태로 시작해요.</p>
          <div class="row g-2">
            <label class="col-sm-6 field">
              <span>1회 매수 금액 (원)</span>
              <input v-model.number="form.d.orderKrw" type="number" min="5000" step="1000" />
            </label>
            <label class="col-sm-6 field">
              <span>최대 보유 종목 수</span>
              <input v-model.number="form.d.maxPositions" type="number" min="1" max="10" />
            </label>
            <label class="col-sm-4 field">
              <span>익절 +%</span>
              <input v-model.number="form.d.takeProfitPct" type="number" min="0" step="0.5" placeholder="안 씀" />
            </label>
            <label class="col-sm-4 field">
              <span>손절 -%</span>
              <input v-model.number="form.d.stopLossPct" type="number" min="0" step="0.5" placeholder="안 씀" />
            </label>
            <label class="col-sm-4 field">
              <span>트레일링 -%</span>
              <input v-model.number="form.d.trailingStopPct" type="number" min="0" step="0.5" placeholder="안 씀" />
            </label>
            <label class="col-sm-6 field">
              <span>최대 보유 시간</span>
              <input v-model.number="form.d.maxHoldHours" type="number" min="1" placeholder="안 씀" />
            </label>
            <label class="col-sm-6 field">
              <span>일일 손실 한도 (원)</span>
              <input v-model.number="form.d.dailyLossLimitKrw" type="number" min="0" step="1000" placeholder="안 씀" />
            </label>
          </div>
        </section>
      </div>
    </div>

    <!-- 저장 -->
    <div v-if="form" class="save-bar">
      <span v-if="saveError" class="text-warning">{{ saveError }}</span>
      <span v-else-if="dirty" class="text-secondary">저장하지 않은 변경이 있어요</span>
      <span v-else-if="savedAt" class="text-secondary">{{ formatTime(savedAt) }} 저장됨</span>
      <button class="btn btn-outline-secondary btn-sm" :disabled="saving || !dirty" @click="reset">되돌리기</button>
      <button class="btn btn-primary btn-sm px-3" :disabled="saving || !dirty" @click="save">
        {{ saving ? '저장 중…' : '저장' }}
      </button>
    </div>

    <div class="row g-3 mt-1">
      <!-- 계정 -->
      <div class="col-xl-6">
        <section class="card-box">
          <h5>계정</h5>
          <p class="note mt-0">로그인: <b>{{ auth.session?.username }}</b> · 로그인 유지 {{ expiresText }}</p>

          <form class="row g-2" @submit.prevent="submitPassword">
            <label class="col-sm-4 field">
              <span>현재 비밀번호</span>
              <input v-model="pw.current" type="password" autocomplete="current-password" required />
            </label>
            <label class="col-sm-4 field">
              <span>새 비밀번호 (8자 이상)</span>
              <input v-model="pw.next" type="password" autocomplete="new-password" minlength="8" required />
            </label>
            <label class="col-sm-4 field">
              <span>새 비밀번호 확인</span>
              <input v-model="pw.confirm" type="password" autocomplete="new-password" minlength="8" required />
            </label>
            <div class="col-12 d-flex align-items-center gap-2">
              <button class="btn btn-outline-light btn-sm" type="submit" :disabled="pwSaving">
                {{ pwSaving ? '변경 중…' : '비밀번호 변경' }}
              </button>
              <span v-if="pwError" class="text-warning small">{{ pwError }}</span>
              <span v-else-if="pwDone" class="text-success small">변경했어요. 다른 브라우저의 로그인은 풀려요.</span>
            </div>
          </form>
        </section>
      </div>

      <!-- 시스템 상태 -->
      <div class="col-xl-6">
        <section class="card-box">
          <div class="d-flex justify-content-between align-items-center">
            <h5 class="mb-0">시스템 상태</h5>
            <button class="btn btn-sm btn-outline-secondary" @click="loadStatus">새로고침</button>
          </div>

          <div v-if="status" class="status">
            <div class="row-s"><span>서버 시작</span><b>{{ formatDateTime(status.serverStartedAt) }}</b></div>
            <div class="row-s">
              <span>시세 수집</span>
              <b>{{ status.collector.marketCount }}종목 · 현재가 {{ status.collector.tickerUpdatedAt ? formatTime(status.collector.tickerUpdatedAt) + ' 갱신' : '대기 중' }}</b>
            </div>
            <div v-if="status.collector.collecting" class="row-s">
              <span>지금 수집 중</span>
              <b>{{ status.collector.collecting.label }} {{ status.collector.collecting.done }}/{{ status.collector.collecting.total }}</b>
            </div>
            <div v-for="(u, key) in status.collector.units" :key="key" class="row-s">
              <span>{{ u.label }}</span>
              <b :class="u.ready ? 'ok' : 'wait'">
                {{ u.ready ? '준비됨' : '수집 대기' }}<template v-if="u.updatedAt"> · {{ formatTime(u.updatedAt) }}</template>
              </b>
            </div>
            <div class="row-s">
              <span>자연어 검색</span>
              <b :class="status.naturalSearchEnabled ? 'ok' : 'wait'">
                {{ status.naturalSearchEnabled ? '사용 가능' : 'API 키 없음 (ANTHROPIC_API_KEY)' }}
              </b>
            </div>
            <div class="row-s">
              <span>매매 방식</span>
              <b>모의매매만 지원 (실매매 미연결)</b>
            </div>
          </div>
        </section>
      </div>
    </div>

  </div>
</template>

<script setup>
import { ref, reactive, computed, onActivated } from 'vue';
import { getSettings, saveSettings, systemStatus, changePassword } from '../api/backendApi';
import { auth, saveSession } from '../auth';
import { formatTime, formatDateTime, apiError } from '../utils/format';

const form = ref(null);
let original = '';
const loadError = ref('');
const saving = ref(false);
const saveError = ref('');
const savedAt = ref(null);
const status = ref(null);

// 서버 형식(비율) ↔ 화면 형식(%)
// 0.0005 → 0.05(%). 부동소수점 오차(0.049999…)가 보이지 않게 반올림
const toPct = (rate) => Math.round(rate * 100 * 10000) / 10000;

const toForm = (s) => ({
  buyingPaused: s.buyingPaused,
  feePct: toPct(s.feeRate),
  slippagePct: toPct(s.paperSlippage),
  reentryCooldownMinutes: s.reentryCooldownMinutes,
  d: { ...s.strategyDefaults }
});

// 빈칸('') → null
const num = (v) => (typeof v === 'number' && !Number.isNaN(v) ? v : null);

const toServer = (f) => ({
  buyingPaused: f.buyingPaused,
  feeRate: (num(f.feePct) ?? 0) / 100,
  paperSlippage: (num(f.slippagePct) ?? 0) / 100,
  reentryCooldownMinutes: num(f.reentryCooldownMinutes) ?? 0,
  strategyDefaults: Object.fromEntries(Object.entries(f.d).map(([k, v]) => [k, num(v)]))
});

const dirty = computed(() => !!form.value && JSON.stringify(toServer(form.value)) !== original);

const apply = (s) => {
  form.value = toForm(s);
  original = JSON.stringify(toServer(form.value));
};

const load = async () => {
  try {
    apply(await getSettings());
    loadError.value = '';
  } catch (e) {
    loadError.value = apiError(e, '설정을 불러오지 못했어요');
  }
};

const loadStatus = async () => {
  try {
    status.value = await systemStatus();
  } catch {
    // 상태는 못 불러와도 설정 화면은 쓸 수 있게
  }
};

// 탭을 열 때마다 최신 값으로 (저장 안 한 변경이 있으면 덮어쓰지 않음)
// KeepAlive 안에서는 처음 열릴 때도 onActivated 가 호출된다
onActivated(() => {
  if (!dirty.value) load();
  loadStatus();
});

const save = async () => {
  saving.value = true;
  saveError.value = '';
  try {
    apply(await saveSettings(toServer(form.value)));
    savedAt.value = Date.now();
  } catch (e) {
    saveError.value = apiError(e, '저장 실패');
  } finally {
    saving.value = false;
  }
};

const reset = () => {
  const s = JSON.parse(original);
  form.value = toForm(s);
  saveError.value = '';
};

// ---------- 비밀번호 ----------
const pw = reactive({ current: '', next: '', confirm: '' });
const pwSaving = ref(false);
const pwError = ref('');
const pwDone = ref(false);

const submitPassword = async () => {
  pwError.value = '';
  pwDone.value = false;
  if (pw.next !== pw.confirm) {
    pwError.value = '새 비밀번호 확인이 일치하지 않아요.';
    return;
  }
  pwSaving.value = true;
  try {
    const res = await changePassword(pw.current, pw.next);
    // 비밀번호를 바꾸면 예전 토큰은 서버에서 거부되므로 새 토큰으로 교체
    saveSession({ token: res.token, username: res.username, expiresAt: res.expiresAt });
    Object.assign(pw, { current: '', next: '', confirm: '' });
    pwDone.value = true;
  } catch (e) {
    pwError.value = apiError(e, '변경 실패');
  } finally {
    pwSaving.value = false;
  }
};

const expiresText = computed(() =>
  auth.session ? formatDateTime(auth.session.expiresAt) + '까지' : ''
);
</script>

<style scoped>
.settings-view {
  text-align: left;
}

.card-box {
  background: #161b22;
  border-radius: 12px;
  padding: 16px 18px;
  height: 100%;
}

.card-box h5 {
  margin-bottom: 12px;
}

.pause {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  background: #0d1117;
  border: 1px solid #30363d;
  border-radius: 10px;
  padding: 12px 14px;
}

.pause.on {
  border-color: #f0b429;
  background: rgba(240, 180, 41, 0.08);
}

.pause p {
  margin: 2px 0 0;
  color: #8b949e;
  font-size: 0.82rem;
}

/* 토글 스위치 */
.switch {
  position: relative;
  width: 44px;
  height: 24px;
  flex-shrink: 0;
}

.switch input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
  margin: 0;
  z-index: 1;
}

.switch span {
  position: absolute;
  inset: 0;
  background: #30363d;
  border-radius: 12px;
  transition: background 0.2s;
}

.switch span::after {
  content: '';
  position: absolute;
  top: 3px;
  left: 3px;
  width: 18px;
  height: 18px;
  background: white;
  border-radius: 50%;
  transition: transform 0.2s;
}

.switch input:checked + span {
  background: #f0b429;
}

.switch input:checked + span::after {
  transform: translateX(20px);
}

.switch input:focus-visible + span {
  outline: 2px solid #3b82f6;
  outline-offset: 2px;
}

.field {
  font-size: 0.85rem;
}

.field > span {
  display: block;
  color: #c9d1d9;
  margin-bottom: 4px;
}

.field small {
  display: block;
  color: #6e7681;
  margin-top: 2px;
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

.note {
  color: #6e7681;
  font-size: 0.8rem;
  margin: 10px 0 0;
}

.save-bar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  font-size: 0.85rem;
}

.status {
  margin-top: 10px;
  font-size: 0.85rem;
}

.row-s {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 0;
  border-bottom: 1px solid #21262d;
}

.row-s span {
  color: #8b949e;
}

.row-s b {
  font-weight: 500;
  text-align: right;
}

.ok {
  color: #4ade80;
}

.wait {
  color: #f0b429;
}
</style>
