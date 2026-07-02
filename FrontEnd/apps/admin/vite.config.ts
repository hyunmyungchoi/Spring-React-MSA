import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { resolve } from 'node:path'

const gatewayTarget = 'http://localhost:8090'
const projectRoot = __dirname
const rootByMode: Record<string, string> = {
  users: resolve(projectRoot, 'src/users'),
  logs: resolve(projectRoot, 'src/logs'),
}
const entryByMode: Record<string, string> = {
  users: 'users.html',
  logs: 'logs.html',
}
const outDirByMode: Record<string, string> = {
  users: 'dist/users',
  logs: 'dist/logs',
}
const baseByMode: Record<string, string> = {
  users: './',
  logs: './',
}

export default defineConfig(({ mode }) => {
  const root = rootByMode[mode] ?? projectRoot

  return {
    root,
    base: baseByMode[mode] ?? '/',
    publicDir: resolve(projectRoot, 'public'),
    plugins: [react()],
    build: {
      outDir: resolve(projectRoot, outDirByMode[mode] ?? 'dist'),
      emptyOutDir: true,
      rollupOptions: {
        input: resolve(root, entryByMode[mode] ?? 'index.html'),
      },
    },
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
  }
})
