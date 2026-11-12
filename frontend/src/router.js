import { createRouter, createWebHistory } from 'vue-router';

import ScreenerView from './views/ScreenerView.vue';
import MarketView from './views/MarketView.vue';
import AgentView from './views/AgentView.vue';
import HistoryView from './views/HistoryView.vue';
import SettingsView from './views/SettingsView.vue';
import LoginView from './views/LoginView.vue';
import { isLoggedIn } from './auth';

// 상단 탭 = 라우트. 탭마다 주소가 따로 생긴다.
export const tabs = [
  { path: '/screener', label: '종목 검색', component: ScreenerView },
  { path: '/market', label: '실시간 시세', component: MarketView },
  { path: '/agent', label: '에이전트 현황', component: AgentView },
  { path: '/history', label: '투자 히스토리', component: HistoryView },
  { path: '/settings', label: '설정', component: SettingsView }
];

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/screener' },
    { path: '/login', component: LoginView, meta: { public: true } },
    ...tabs.map(({ path, component, label }) => ({ path, component, meta: { label } })),
    { path: '/:pathMatch(.*)*', redirect: '/screener' }
  ]
});

// 화면을 옮길 때마다 확인: 로그인 안 했으면 로그인 화면으로 보낸다
// (진짜 보안은 서버가 한다. 이건 화면 편의를 위한 것)
router.beforeEach((to) => {
  if (to.meta.public) {
    // 이미 로그인했는데 로그인 화면으로 가면 첫 탭으로
    return to.path === '/login' && isLoggedIn() ? '/screener' : true;
  }
  if (!isLoggedIn()) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  return true;
});

export default router;
