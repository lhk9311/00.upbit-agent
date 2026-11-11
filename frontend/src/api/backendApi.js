import axios from 'axios';

// 우리 백엔드(Spring Boot). 개발 중엔 Vite proxy, 배포 땐 nginx 가 /api 를 넘겨준다.
const backend = axios.create({
  baseURL: '/api',
  timeout: 10000
});

export const screen = (condition) =>
  backend.post('/screener', condition).then(res => res.data);

// 자연어 → 조건 변환 (Claude API 호출이라 시간이 조금 걸릴 수 있음)
export const parseCondition = (text) =>
  backend.post('/screener/parse', { text }, { timeout: 30000 }).then(res => res.data);

export const parseStatus = () =>
  backend.get('/screener/parse/status').then(res => res.data);

export const collectorStatus = () =>
  backend.get('/collector/status').then(res => res.data);

// ---------- 전략 ----------
const data = (res) => res.data;

export const listStrategies = () => backend.get('/strategies').then(data);
export const createStrategy = (req) => backend.post('/strategies', req).then(data);
export const startStrategy = (id) => backend.post(`/strategies/${id}/start`).then(data);
export const stopStrategy = (id) => backend.post(`/strategies/${id}/stop`).then(data);
export const deleteStrategy = (id) => backend.delete(`/strategies/${id}`);

// ---------- 포지션·거래 ----------
export const listPositions = () => backend.get('/positions').then(data);
export const closeTrade = (id) => backend.post(`/trades/${id}/close`).then(data);

// params: { status, strategyId, from, to, sort, dir }
export const listTrades = (params) => backend.get('/trades', { params }).then(data);

// ---------- 에이전트 ----------
export const agentSummary = () => backend.get('/agent/summary').then(data);
export const agentLogs = (limit = 50) => backend.get('/agent/logs', { params: { limit } }).then(data);
export const runAgentNow = () => backend.post('/agent/run').then(data);

// ---------- 백테스트 ----------
// 저장 전 전략(설정 창 값)으로
export const backtest = (req) => backend.post('/backtest', req, { timeout: 60000 }).then(data);
// 저장된 전략으로
export const backtestSaved = (id) => backend.post(`/strategies/${id}/backtest`, null, { timeout: 60000 }).then(data);

export default backend;
