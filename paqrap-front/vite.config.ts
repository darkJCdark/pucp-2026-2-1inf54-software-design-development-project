import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  plugins: [react(), tailwindcss()],

  resolve: {
    alias: {
      "@": fileURLToPath(
        new URL("./src", import.meta.url),
      ),
    },
  },

  server: {
    port: 5173,

    proxy: {
      "/api": {
        // Por defecto 8080. Si el backend corre por Docker (8081):
        // $env:VITE_API_PROXY="http://localhost:8081"; npm run dev
        target: process.env.VITE_API_PROXY ?? "http://localhost:8080",
        changeOrigin: true,
        secure: false,
      },
    },
  },
});