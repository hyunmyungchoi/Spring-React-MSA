import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
    base: "./",
    plugins: [react(), tailwindcss()],
    build: {
        outDir: "dist",
        emptyOutDir: true,
    },
    server: {
        port: 5175,
        strictPort: true,
        proxy: {
            "/bff": { target: "http://localhost:8080", changeOrigin: true, ws: true },
            "/oauth2": { target: "http://localhost:8080", changeOrigin: true },
            "/login": { target: "http://localhost:8080", changeOrigin: true },
            "/.well-known": { target: "http://localhost:8080", changeOrigin: true },
            "/logout": { target: "http://localhost:8080", changeOrigin: true },
            "/connect": { target: "http://localhost:8080", changeOrigin: true },
            "/userinfo": { target: "http://localhost:8080", changeOrigin: true },
        },
    },
});
