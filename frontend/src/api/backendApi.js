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

export default backend;
