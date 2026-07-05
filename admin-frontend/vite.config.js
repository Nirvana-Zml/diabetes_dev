import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

const gatewayTarget = process.env.VITE_PROXY_TARGET || 'http://localhost:8099'
const userServiceTarget = process.env.VITE_USER_SERVICE_TARGET || gatewayTarget
const difyProxyTarget = process.env.VITE_DIFY_PROXY_TARGET || 'http://localhost:58080'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5174,
    host: true,
    allowedHosts: ['.ngrok-free.dev', '.ngrok-free.app', '.ngrok.io'],
    proxy: {
      '^/api/v1/(auth|user|admin/stats)(/|$)': {
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
      '/dify-proxy': {
        target: difyProxyTarget,
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/dify-proxy/, ''),
        timeout: 300000,
        proxyTimeout: 300000,
      },
    },
  },
  test: {
    environment: 'happy-dom',
    globals: true,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'json-summary', 'json'],
      reportsDirectory: './coverage',
      include: ['src/**/*.{js,vue}'],
      exclude: [
        'src/**/*.test.js',
        'src/**/*.spec.js',
        'src/**/__tests__/**',
      ],
      thresholds: {
        statements: 0,
        branches: 0,
        functions: 0,
        lines: 0,
      },
    },
  },
})
