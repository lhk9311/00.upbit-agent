<template>
  <div class="container-fluid app-container">

    <h1 class="main-title">
      업비트 에이전트
    </h1>

    <!-- 현재 날짜·시간 -->
    <p class="now-clock">
      {{ nowText }}
    </p>

    <!-- 상단 탭 -->
    <ul class="nav nav-tabs app-tabs">
      <li v-for="tab in tabs" :key="tab.path" class="nav-item">
        <RouterLink :to="tab.path" class="nav-link" active-class="active">
          {{ tab.label }}
        </RouterLink>
      </li>
    </ul>

    <!-- 탭 내용. KeepAlive: 탭을 오가도 검색 조건·결과가 유지되도록 -->
    <RouterView v-slot="{ Component }">
      <KeepAlive>
        <component :is="Component" />
      </KeepAlive>
    </RouterView>

  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue';
import { tabs } from './router';

// ---------- 현재 날짜·시간 ----------
const nowText = ref('');
let clockTimer = null;

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
const pad = (n) => String(n).padStart(2, '0');

const updateClock = () => {
  const d = new Date();
  nowText.value =
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
    `(${WEEKDAYS[d.getDay()]}) ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
};

onMounted(() => {
  updateClock();
  clockTimer = setInterval(updateClock, 1000);
});

onBeforeUnmount(() => {
  clearInterval(clockTimer);
});
</script>

<style>
body {
  background-color: #0d1117;
  color: white;
  margin: 0;
}

#app {
  background-color: #0d1117;
  min-height: 100vh;
  /* 템플릿 기본(style.css)의 1126px 고정 폭을 풀어 표가 잘리지 않게 */
  width: 100%;
  max-width: 1600px;
  border-inline: none;
}

.app-container {
  padding: 30px;
}

.main-title {
  text-align: center;
  margin-bottom: 8px;
  color: white;
}

.now-clock {
  text-align: center;
  color: #8b949e;
  font-variant-numeric: tabular-nums;
  margin-bottom: 20px;
}

/* 상단 탭 */
.app-tabs {
  border-bottom-color: #30363d;
  margin-bottom: 20px;
}

.app-tabs .nav-link {
  color: #8b949e;
  border: none;
  border-bottom: 2px solid transparent;
}

.app-tabs .nav-link:hover {
  color: white;
  border-bottom-color: #30363d;
}

.app-tabs .nav-link.active {
  color: white;
  background: transparent;
  border-bottom-color: #3b82f6;
}

/* 수익률·등락률 색 (한국 관례: 상승 빨강, 하락 파랑) */
.pnl-up {
  color: #ef4444;
}

.pnl-down {
  color: #3b82f6;
}

.pnl-flat {
  color: #8b949e;
}
</style>
