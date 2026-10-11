import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/auth': {
        target: process.env.GATEWAY_URL ?? 'http://localhost:8080',
        changeOrigin: true,
      },
      '/tracker': {
        target: process.env.GATEWAY_URL ?? 'http://localhost:8080',
        changeOrigin: true,
        // Навигация браузера на SPA-маршрут /tracker не должна уходить в API,
        // иначе пользователь не дойдёт до клиентского редиректа на вход.
        bypass(req) {
          if ((req.headers.accept ?? '').includes('text/html')) {
            return req.url
          }
          return undefined
        },
      },
      '/finances': {
        target: process.env.GATEWAY_URL ?? 'http://localhost:8080',
        changeOrigin: true,
        // Навигация браузера на SPA-маршрут /finances не должна уходить в API,
        // иначе пользователь не дойдёт до клиентского редиректа на вход.
        bypass(req) {
          if ((req.headers.accept ?? '').includes('text/html')) {
            return req.url
          }
          return undefined
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    include: ['test/**/*.test.{js,jsx}'],
    setupFiles: './test/setup.js',
    passWithNoTests: true,
  },
})
