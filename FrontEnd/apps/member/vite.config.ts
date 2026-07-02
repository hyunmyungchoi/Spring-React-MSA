import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { resolve } from 'node:path'

const gatewayTarget = 'http://localhost:8080'
const projectRoot = __dirname
const rootByMode: Record<string, string> = {
  community: resolve(projectRoot, 'src/community'),
  stock: resolve(projectRoot, 'src/stock'),
}
const entryByMode: Record<string, string> = {
  community: 'community.html',
  stock: 'stock.html',
}
const outDirByMode: Record<string, string> = {
  community: 'dist/community',
  stock: 'dist/stock',
}
const baseByMode: Record<string, string> = {
  community: './',
  stock: './',
}

export default defineConfig(({ mode }) => {
  const root = rootByMode[mode] ?? projectRoot

  return {
    root,
    base: baseByMode[mode] ?? '/',
    publicDir: resolve(projectRoot, 'public'),
    plugins: [react(), tailwindcss()],
    build: {
      outDir: resolve(projectRoot, outDirByMode[mode] ?? 'dist'),
      emptyOutDir: true,
      rollupOptions: {
        input: resolve(root, entryByMode[mode] ?? 'index.html'),
      },
    },
    server: {
      port: 5173,
      strictPort: true,
      proxy: {
        '/bff': {
          target: gatewayTarget,
          changeOrigin: true,
        },
        '/oauth2': {
          target: gatewayTarget,
          changeOrigin: true,
        },
        '/login': {
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
  }
})
