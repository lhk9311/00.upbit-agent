<template>
  <div class="login-wrap">
    <form class="login-card" @submit.prevent="submit">
      <h2>업비트 에이전트</h2>
      <p class="sub">로그인이 필요해요</p>

      <label class="field">
        <span>아이디</span>
        <input v-model="username" type="text" autocomplete="username" autofocus required />
      </label>

      <label class="field">
        <span>비밀번호</span>
        <input v-model="password" type="password" autocomplete="current-password" required />
      </label>

      <div v-if="error" class="alert alert-warning py-2 mb-3">{{ error }}</div>

      <button class="btn btn-primary w-100" type="submit" :disabled="loading || !username || !password">
        {{ loading ? '로그인 중…' : '로그인' }}
      </button>

    </form>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { useRoute } from 'vue-router';
import { login } from '../api/backendApi';
import { saveSession } from '../auth';
import { apiError } from '../utils/format';

const route = useRoute();

const username = ref('admin');
const password = ref('');
const loading = ref(false);
const error = ref('');

// 로그인 전에 가려던 화면으로 돌려보낸다. 외부 주소로 튕기지 않도록 우리 사이트 경로만 허용
const redirectTarget = () => {
  const r = route.query.redirect;
  return typeof r === 'string' && r.startsWith('/') && !r.startsWith('//') && !r.startsWith('/login')
    ? r
    : '/screener';
};

const submit = async () => {
  loading.value = true;
  error.value = '';
  try {
    const res = await login(username.value.trim(), password.value);
    saveSession({ token: res.token, username: res.username, expiresAt: res.expiresAt });
    // 새로고침으로 이동: 화면들이 토큰을 가진 상태로 처음부터 데이터를 불러오도록
    window.location.assign(redirectTarget());
  } catch (e) {
    error.value = apiError(e, '로그인 실패');
    password.value = '';
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
.login-wrap {
  min-height: 70vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}

.login-card {
  text-align: left;
  width: 100%;
  max-width: 380px;
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: 12px;
  padding: 28px 24px;
}

.login-card h2 {
  margin: 0;
  font-size: 1.4rem;
}

.sub {
  color: #8b949e;
  margin: 4px 0 20px;
}

.field {
  display: block;
  margin-bottom: 14px;
  font-size: 0.9rem;
}

.field span {
  display: block;
  color: #c9d1d9;
  margin-bottom: 4px;
}

.field input {
  width: 100%;
  background: #0d1117;
  color: white;
  border: 1px solid #30363d;
  border-radius: 6px;
  padding: 8px 10px;
}

.field input:focus {
  outline: none;
  border-color: #3b82f6;
}

.hint {
  color: #6e7681;
  font-size: 0.8rem;
  margin: 14px 0 0;
}
</style>
