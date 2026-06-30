import { defineConfig } from 'vite'
import type { ProxyOptions } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

const gatewayTarget = 'http://localhost:8080'
const devServerOrigin = 'http://localhost:5173'

function gatewayProxy(): ProxyOptions {
  return {
    target: gatewayTarget,
    changeOrigin: true,
    configure(proxy) {
      proxy.on('proxyRes', (proxyRes) => {
        const location = proxyRes.headers.location

        if (location) {
          proxyRes.headers.location = rewriteGatewayLocation(location)
        }
      })
    },
  }
}

// Keeps local OAuth redirects on the Vite origin during fetch-based login preparation.
function rewriteGatewayLocation(location: string) {
  return location.startsWith(gatewayTarget)
    ? location.replace(gatewayTarget, devServerOrigin)
    : location
}

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/bff': gatewayProxy(),
      '/oauth2': gatewayProxy(),
      '/.well-known': gatewayProxy(),
      '/login': gatewayProxy(),
      '/logout': gatewayProxy(),
      '/connect': gatewayProxy(),
      '/userinfo': gatewayProxy(),
    },
  },
})
