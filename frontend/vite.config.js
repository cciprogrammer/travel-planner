import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    open: true,
    proxy: {
      // 后端地址：可用环境变量 VITE_PROXY_TARGET 覆盖（默认与 application-local.yml 的 8081 对齐）
      '/api': {
        target: process.env.VITE_PROXY_TARGET || 'http://localhost:8081',
        changeOrigin: true
      }
    }
  }
})
