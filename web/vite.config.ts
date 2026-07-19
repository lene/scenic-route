import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// Dev proxy → backend API (Server.scala on :8080). Prod uses VITE_API_BASE.
const api = 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/routes': api,
      '/geocode': api,
      '/health': api,
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})
