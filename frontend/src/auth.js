// 로그인 상태 관리
//
// 로그인하면 서버가 JWT 토큰을 준다. 그걸 브라우저(localStorage)에 저장해두고,
// 우리 백엔드로 가는 모든 요청 헤더에 "Authorization: Bearer 토큰" 을 붙인다.
//
// 참고: localStorage 는 같은 사이트의 스크립트가 읽을 수 있어서, 화면에 남의 스크립트가 끼어드는(XSS)
// 상황에선 토큰이 노출될 수 있다. 실무에서는 HttpOnly 쿠키에 담는 방식도 많이 쓴다.
import { reactive } from 'vue';

const KEY = 'upbit-agent-auth';

const load = () => {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY));
    if (saved?.token && saved.expiresAt > Date.now()) return saved;
  } catch {
    // 저장소를 못 쓰는 환경(시크릿 모드 등)이면 로그인 정보 없이 시작
  }
  return null;
};

// { token, username, expiresAt }
export const auth = reactive({ session: load() });

export const isLoggedIn = () => !!auth.session && auth.session.expiresAt > Date.now();

export const saveSession = (session) => {
  auth.session = session;
  try {
    localStorage.setItem(KEY, JSON.stringify(session));
  } catch {
    // 저장 실패해도 이번 탭에서는 로그인 상태 유지
  }
};

export const clearSession = () => {
  auth.session = null;
  try {
    localStorage.removeItem(KEY);
  } catch {
    // 무시
  }
};

// 로그아웃: 저장된 토큰을 지우고 로그인 화면으로 (새로고침해서 화면에 남은 데이터도 비움)
export const logout = () => {
  clearSession();
  window.location.assign('/login');
};

// axios 인스턴스에 토큰 처리를 붙인다 (backendApi.js 에서 호출)
export const installAuth = (http) => {
  // 요청 보내기 전: 토큰이 있으면 헤더에 붙이기
  http.interceptors.request.use((config) => {
    if (auth.session?.token) {
      config.headers.Authorization = `Bearer ${auth.session.token}`;
    }
    return config;
  });

  // 응답 받은 뒤: 401(로그인 필요)이면 로그인 화면으로
  http.interceptors.response.use(
    (res) => res,
    (error) => {
      const url = error.config?.url ?? '';
      // 로그인 요청 자체의 401(비밀번호 틀림)은 로그인 화면이 직접 메시지를 보여준다
      if (error.response?.status === 401 && !url.startsWith('/auth/login')) {
        clearSession();
        const here = window.location.pathname + window.location.search;
        window.location.assign('/login?redirect=' + encodeURIComponent(here));
        return new Promise(() => {}); // 이동하는 동안 화면에 에러가 뜨지 않게 멈춰둔다
      }
      return Promise.reject(error);
    }
  );
};
