// 화면 공통 표시 형식

const pad = (n) => String(n).padStart(2, '0');

// 가격대에 맞춰 소수점 자릿수 조절 (0.008원 같은 코인도 보이도록)
export const formatPrice = (v) => {
  if (v == null || Number.isNaN(v)) return '';
  const abs = Math.abs(v);
  const digits = abs >= 100 ? 0 : abs >= 1 ? 2 : 4;
  return v.toLocaleString('ko-KR', { maximumFractionDigits: digits });
};

export const formatWon = (v) => (v == null ? '—' : Math.round(v).toLocaleString('ko-KR') + '원');

export const formatEok = (won) => {
  const eok = won / 100_000_000;
  if (eok >= 10000) return (eok / 10000).toFixed(1) + '조';
  return Math.round(eok).toLocaleString('ko-KR') + '억';
};

export const formatQty = (q) =>
  q == null ? '' : q.toLocaleString('ko-KR', { maximumFractionDigits: q >= 1 ? 4 : 8 });

// 2026-11-11 15:04
export const formatDateTime = (ms) => {
  if (ms == null) return '';
  const d = new Date(ms);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
};

export const formatTime = (ms) => {
  const d = new Date(ms);
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
};

// 보유 시간: 3일 4시간 / 2시간 15분 / 7분
export const formatDuration = (ms) => {
  const min = Math.max(0, Math.floor(ms / 60000));
  const d = Math.floor(min / 1440);
  const h = Math.floor((min % 1440) / 60);
  const m = min % 60;
  if (d > 0) return `${d}일 ${h}시간`;
  if (h > 0) return `${h}시간 ${m}분`;
  return `${m}분`;
};

const UNIT_LABEL = { MIN15: '15분봉', MIN60: '1시간봉', MIN240: '4시간봉', DAY: '일봉' };

// 저장된 검색 조건 → 사람이 읽는 꼬리표
export const conditionLabels = (c) => {
  if (!c) return [];
  const out = [UNIT_LABEL[c.unit ?? 'MIN60']];
  const range = (min, max, unit, label) => {
    if (min != null && max != null) out.push(`${label} ${min}~${max}${unit}`);
    else if (min != null) out.push(`${label} ≥ ${min}${unit}`);
    else if (max != null) out.push(`${label} ≤ ${max}${unit}`);
  };

  if (c.excludeWarning) out.push('유의종목 제외');
  range(c.minPrice, c.maxPrice, '원', '가격');
  if (c.minTradeAmount != null) out.push(`거래대금 ≥ ${formatEok(c.minTradeAmount)}`);
  range(c.minChangeRate, c.maxChangeRate, '%', '등락률');
  if (c.maAligned) out.push('정배열');
  if (c.priceAboveMa20) out.push('현재가 > MA20');
  if (c.goldenCrossWithin != null) out.push(`골든크로스 ${c.goldenCrossWithin}봉 이내`);
  range(c.rsiMin, c.rsiMax, '', 'RSI');
  if (c.macdCrossWithin != null) out.push(`MACD 돌파 ${c.macdCrossWithin}봉 이내`);
  if (c.bbLowerTouchWithin != null) out.push(`볼린저 하단 반등 ${c.bbLowerTouchWithin}봉 이내`);
  if (c.volumeSurgeRatio != null) out.push(`거래량 ${c.volumeSurgeRatio}배↑`);
  range(c.atrPctMin, c.atrPctMax, '%', 'ATR');
  range(c.dropFromHighMin, c.dropFromHighMax, '%', '고점대비 하락');
  return out;
};

// 매도 규칙 요약
export const ruleLabels = (s) => {
  const out = [];
  if (s.takeProfitPct != null) out.push(`익절 +${s.takeProfitPct}%`);
  if (s.stopLossPct != null) out.push(`손절 -${s.stopLossPct}%`);
  if (s.trailingStopPct != null) out.push(`트레일링 -${s.trailingStopPct}%`);
  if (s.maxHoldHours != null) out.push(`최대 ${s.maxHoldHours}시간 보유`);
  if (s.dailyLossLimitKrw != null) out.push(`일일 손실 한도 ${formatWon(s.dailyLossLimitKrw)}`);
  return out;
};

export const apiError = (e, fallback) =>
  e.response?.data?.message ??
  (e.response ? `${fallback} (HTTP ${e.response.status})` : '백엔드 서버(8080)에 연결할 수 없어요. 서버가 켜져 있는지 확인해주세요.');
