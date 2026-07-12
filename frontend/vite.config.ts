import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      "/payments": "http://localhost:8080",
      "/orders": "http://localhost:8080",
      "/auth": "http://localhost:8080",
      "/notifications": "http://localhost:8080",
      "/geo": "http://localhost:8080",
    },
  },
});
