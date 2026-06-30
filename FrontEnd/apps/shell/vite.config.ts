import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

const gatewayTarget = 'http://localhost:8080'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/bff': {
        target: gatewayTarget,
        changeOrigin: true,
      },
      '/api': {
        target: gatewayTarget,
        changeOrigin: true,
      },
      '/oauth2': {
        target: gatewayTarget,
        changeOrigin: true,
      },
      '/login/password': {
        target: gatewayTarget,
        changeOrigin: true,
      },
      '/login/email': {
        target: gatewayTarget,
        changeOrigin: true,
      },
      '/login/whatsapp': {
        target: gatewayTarget,
        changeOrigin: true,
      },
      '/.well-known': {
        target: gatewayTarget,
        changeOrigin: true,
      },
      '/logout': {
        target: gatewayTarget,
        changeOrigin: true,
      },
      '/connect': {
        target: gatewayTarget,
        changeOrigin: true,
      },
      '/userinfo': {
        target: gatewayTarget,
        changeOrigin: true,
      },
    },
  },
})
