import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// В режиме разработки запросы /api проксируются на backend (порт 8080), CORS не нужен.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
