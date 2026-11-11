<template>
  <!-- 기존 "실시간 리스트" 화면 그대로 -->
  <div class="row g-3">

    <!-- 코인 목록 -->
    <div class="col-lg-6">
      <CoinList @selectCoin="selectCoin" />
    </div>

    <!-- 차트 -->
    <div class="col-lg-6">
      <ChartPanel
        :market="selectedCoin?.market"
        :title="selectedCoin?.korean_name"
      />
    </div>

  </div>
</template>

<script setup>
import { ref } from 'vue';

import CoinList from '../components/CoinList.vue';
import ChartPanel from '../components/ChartPanel.vue';

const selectedCoin = ref(null);

const selectCoin = (coin) => {
  selectedCoin.value = coin;
};
</script>
