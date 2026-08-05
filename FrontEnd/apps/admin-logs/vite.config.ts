import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
    base: "./",
    plugins: [react()],
    build: {
        outDir: "dist",
        emptyOutDir: true,
    },
    server: {
        port: 5178,
        strictPort: true,
        proxy: {
            "/admin-bff": { target: "http://localhost:8090", changeOrigin: true },
            "/oauth2": { target: "http://localhost:8090", changeOrigin: true },
            "/.well-known": { target: "http://localhost:8090", changeOrigin: true },
            "/login": { target: "http://localhost:8090", changeOrigin: true },
            "/logout": { target: "http://localhost:8090", changeOrigin: true },
            "/connect": { target: "http://localhost:8090", changeOrigin: true },
            "/userinfo": { target: "http://localhost:8090", changeOrigin: true },
        },
    },
});
