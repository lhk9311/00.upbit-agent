import { createRouter, createWebHistory } from 'vue-router';

import ScreenerView from './views/ScreenerView.vue';
import MarketView from './views/MarketView.vue';
import AgentView from './views/AgentView.vue';
import HistoryView from './views/HistoryView.vue';
import SettingsView from './views/SettingsView.vue';

// 상단 탭 = 라우트. 탭마다 주소가 따로 생긴다.
export const tabs = [
  { path: '/screener', label: '종목 검색', component: ScreenerView },
  { path: '/market', label: '실시간 시세', component: MarketView },
  { path: '/agent', label: '에이전트 현황', component: AgentView },
  { path: '/history', label: '투자 히스토리', component: HistoryView },
  { path: '/settings', label: '설정', component: SettingsView }
];

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/screener' },
    ...tabs.map(({ path, component, label }) => ({ path, component, meta: { label } }))
  ]
});
