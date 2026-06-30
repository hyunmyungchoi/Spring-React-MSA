import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const gatewayTarget = 'http://localhost:8090'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5176,
    strictPort: true,
    proxy: {
      '/admin-bff': {
        target: gatewayTarget,
        changeOrigin: true,
      },
      '/oauth2': {
        target: gatewayTarget,
        changeOrigin: true,
      },
      '/.well-known': {
        target: gatewayTarget,
        changeOrigin: true,
      },
      '/login': {
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