import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";

// Порты берутся из .env в корне проекта (FRONTEND_PORT, BACKEND_PORT).
const ROOT = fileURLToPath(new URL("..", import.meta.url));

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, ROOT, "");
  const frontendPort = Number(env.FRONTEND_PORT || 5173);
  const backendPort = Number(env.BACKEND_PORT || 8080);
  return {
    plugins: [react()],
    envDir: ROOT,
    server: {
      port: frontendPort,
      strictPort: true,
      host: true,
      proxy: {
        "/api": {
          target: `http://localhost:${backendPort}`,
          changeOrigin: true,
        },
      },
    },
  };
});
