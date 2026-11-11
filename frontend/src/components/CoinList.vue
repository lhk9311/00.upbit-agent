<template>
  <div class="coin-list-container">

    <h2 class="title">
      코인 목록
    </h2>

    <!-- 상승률 TOP5 -->
    <div class="top-coins">

      <h5>
        🚀 상승률 TOP5 (24시간)
      </h5>

      <div
        v-for="coin in topCoins"
        :key="coin.market"
        class="top-coin-item"
      >

        <span>
          {{ coin.korean_name }}
        </span>

        <span class="text-danger">
          +{{ (coin.change_rate * 100).toFixed(2) }}%
        </span>

      </div>

    </div>


    <div class="search-box">
      <input
        type="text"
        v-model="searchKeyword"
        placeholder="코인 이름으로 검색"
      />

      <span class="search-icon">
        🔍
      </span>
    </div>

    <table class="table table-dark table-hover align-middle">
      <thead>
        <tr>
          <th @click="sortBy('korean_name')">
            한글명 ↕
          </th>

          <th @click="sortBy('trade_price')">
            현재가 ↕
          </th>

          <th @click="sortBy('change_rate')">
            전일대비 ↕
          </th>

          <th @click="sortBy('volume')">
            거래량 ↕
          </th>
        </tr>
      </thead>

      <tbody>
        <tr
          v-for="coin in filteredCoins"
          :key="coin.market"
          @click="selectCoin(coin)"
          style="cursor:pointer"
        >
          <td>
            {{ coin.korean_name }}
          </td>

          <td class="text-end">
            {{ coin.trade_price.toLocaleString() }}
          </td>

          <td
            class="text-end"
            :class="
              coin.change_rate >= 0
                ? 'text-danger'
                : 'text-primary'
            "
          >
            {{ (coin.change_rate * 100).toFixed(2) }}%
          </td>

          <td class="text-end">
            {{ formatVolume(coin.volume) }}
          </td>
        </tr>
      </tbody>
    </table>

  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue';
import api from '../api/upbitApi';

// 부모(App.vue)에게 이벤트 전달
const emit = defineEmits(['selectCoin']);

// 검색어
const searchKeyword = ref('');

// 코인 목록
const coins = ref([]);

// 정렬 상태
const sortField = ref('');
const sortDirection = ref('asc');

// 검색 결과
const filteredCoins = computed(() => {
  return coins.value.filter((coin) =>
    coin.korean_name.includes(searchKeyword.value)
  );
});

// 상승률 TOP5
const topCoins = computed(() => {

  return [...coins.value]
    .sort(
      (a, b) =>
        b.change_rate - a.change_rate
    )
    .slice(0, 5);

});

// 거래량 포맷
const formatVolume = (value) => {
  if (value >= 1000000000000) {
    return (
      (value / 1000000000000).toFixed(1) + '조'
    );
  }

  if (value >= 100000000) {
    return (
      (value / 100000000).toFixed(0) + '억'
    );
  }

  return Math.floor(value).toLocaleString();
};

// 정렬
const sortBy = (field) => {
  if (sortField.value === field) {
    sortDirection.value =
      sortDirection.value === 'asc'
        ? 'desc'
        : 'asc';
  } else {
    sortField.value = field;
    sortDirection.value = 'asc';
  }

  applySort();
};

// 현재 정렬 상태를 목록에 적용 (시세 갱신 후에도 순서 유지)
const applySort = () => {
  const field = sortField.value;

  if (!field) {
    return;
  }

  coins.value.sort((a, b) => {
    const valueA = a[field];
    const valueB = b[field];

    if (typeof valueA === 'string') {
      return sortDirection.value === 'asc'
        ? valueA.localeCompare(valueB)
        : valueB.localeCompare(valueA);
    }

    return sortDirection.value === 'asc'
      ? valueA - valueB
      : valueB - valueA;
  });
};

// 코인 선택
const selectCoin = (coin) => {
  emit('selectCoin', coin);
};

// 시세 자동 갱신 주기 (ms)
const REFRESH_MS = 3000;

// KRW 마켓 정보 (한글명 등) — 최초 1회 조회
let krwMarkets = [];
let refreshTimer = null;

// 현재가 조회: 마켓 전체를 한 번의 요청으로 가져온다
const loadTickers = async () => {
  try {
    const tickerRes = await api.get('/ticker', {
      params: {
        markets: krwMarkets.map(item => item.market).join(','),
      },
    });

    coins.value = tickerRes.data.map((ticker) => {

      const marketInfo = krwMarkets.find(
        item => item.market === ticker.market
      );

      return {
        market: ticker.market,

        korean_name:
          marketInfo?.korean_name ?? '',

        trade_price:
          ticker.trade_price,

        change_rate:
          ticker.signed_change_rate,

        change_price:
          ticker.signed_change_price,

        volume:
          ticker.acc_trade_price_24h,
      };
    });

    applySort();

  } catch (error) {
    console.error('시세 조회 실패', error);
  }
};

// 최초 조회 + 주기적 갱신
onMounted(async () => {
  try {
    const marketRes = await api.get('/market/all');

    krwMarkets = marketRes.data.filter(
      item => item.market.startsWith('KRW-')
    );

    await loadTickers();

    refreshTimer = setInterval(loadTickers, REFRESH_MS);

  } catch (error) {
    console.error(error);
  }
});

onBeforeUnmount(() => {
  clearInterval(refreshTimer);
});
</script>

<style scoped>
.coin-list-container {
  background-color: #1a1a1a;
  border-radius: 12px;
  padding: 20px;

  height: 700px;

  overflow-y: auto;
  overflow-x: auto;
}

.title {
  text-align: center;
  margin-bottom: 20px;
  color: white;
}

.search-box {
  position: relative;
  margin-bottom: 15px;
}

.search-box input {
  width: 100%;
  padding: 10px 40px 10px 12px;

  border-radius: 8px;
  border: 1px solid #444;

  background-color: #252525;
  color: white;
}

.search-icon {
  position: absolute;

  right: 12px;
  top: 10px;
}

th {
  cursor: pointer;
  user-select: none;
  white-space: nowrap;
}

td {
  white-space: nowrap;
}

.table {
  margin-bottom: 0;
}

.top-coins {

  background: #252525;

  padding: 12px;

  border-radius: 8px;

  margin-bottom: 15px;

}

.top-coins h5 {

  color: gold;

  text-align: center;

  margin-bottom: 10px;

}

.top-coin-item {

  display: flex;

  justify-content: space-between;

  padding: 4px 0;

  border-bottom: 1px solid #333;

}

.top-coin-item:last-child {

  border-bottom: none;

}

</style>