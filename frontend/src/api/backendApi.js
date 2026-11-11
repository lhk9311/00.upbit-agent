import axios from 'axios';

// 우리 백엔드(Spring Boot). 개발 중엔 Vite proxy, 배포 땐 nginx 가 /api 를 넘겨준다.
const backend = axios.create({
  baseURL: '/api',
  timeout: 10000
});

export const screen = (condition) =>
  backend.post('/screener', condition).then(res => res.data);

export const collectorStatus = () =>
  backend.get('/collector/status').then(res => res.data);

export default backend;
