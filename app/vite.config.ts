import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      manifest: {
        name: 'Go 9x9 Teaching',
        short_name: 'Go9',
        description: '9x9 Go vs KataGo HumanSL — learn with n-choice',
        theme_color: '#c19a5d',
        background_color: '#f0d9a0',
        display: 'standalone',
        icons: [{ src: '/favicon.svg', sizes: '512x512', type: 'image/svg+xml' }],
      },
      workbox: { globPatterns: ['**/*.{js,css,html,svg}'] },
    }),
  ],
  server: { proxy: { '/api': { target: 'http://localhost:3001', rewrite: p => p.replace(/^\/api/, '') } } },
})
