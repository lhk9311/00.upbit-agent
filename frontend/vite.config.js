import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    // 개발 중 /api 요청은 Spring Boot(8080)로 넘긴다 → CORS 설정 불필요
    proxy: {
      '/api': 'http://localhost:8080'
    }
  }
})
