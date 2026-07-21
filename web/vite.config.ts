import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

// Dev proxy → backend API (Server.scala on :8080). Prod uses VITE_API_BASE.
const api = 'http://localhost:8080'

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      // App shell is precached; routing itself always needs the network (NetworkOnly by default).
      includeAssets: ['favicon.svg', 'apple-touch-icon.png'],
      manifest: {
        name: 'scenic-route',
        short_name: 'scenic-route',
        description: 'Plan scenic bicycle routes of a target distance and export them as GPX.',
        theme_color: '#2e7d32',
        background_color: '#ffffff',
        display: 'standalone',
        start_url: '/',
        icons: [
          { src: 'pwa-192x192.png', sizes: '192x192', type: 'image/png' },
          { src: 'pwa-512x512.png', sizes: '512x512', type: 'image/png' },
          {
            src: 'pwa-maskable-512x512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'maskable',
          },
        ],
      },
    }),
  ],
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
