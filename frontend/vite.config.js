import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

const gatewayTarget = process.env.VITE_PROXY_TARGET || 'http://localhost:8099'
// Docker 内直连 user-service，避免 Vite 代理与 Spring Cloud Gateway(Netty) 的 HTTP 兼容问题
const userServiceTarget = process.env.VITE_USER_SERVICE_TARGET || gatewayTarget

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    host: true,
    proxy: {
      '^/api/v1/(auth|user)(/|$)': {
        target: userServiceTarget,
        changeOrigin: true,
        timeout: 60000,
        proxyTimeout: 60000,
      },
      '^/api/v2/(auth|user)(/|$)': {
 	         target: userServiceTarget,
 	         changeOrigin: true,
 	         timeout: 60000,
 	         proxyTimeout: 60000,
 	    },
      '/api/v1': {
        target: gatewayTarget,
        changeOrigin: true,
        timeout: 60000,
        proxyTimeout: 60000,
      },
      '/api/v2': {
 	         target: gatewayTarget,
 	         changeOrigin: true,
 	         timeout: 60000,
 	         proxyTimeout: 60000,
 	    },
    },
  },
})

