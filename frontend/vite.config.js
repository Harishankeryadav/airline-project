import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Port 5173 is fixed on purpose: the api-gateway's default CORS_ALLOWED_ORIGINS allows http://localhost:5173.
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, strictPort: true },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: "./src/test/setup.js",
    css: false,
  },
});
