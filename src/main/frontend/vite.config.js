import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import path from "node:path";

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "src")
    }
  },
  server: {
    port: 4173
  },
  build: {
    // Output to the Spring static folder src/main/web
    outDir: path.resolve(__dirname, "../web"),
    emptyOutDir: true,
    sourcemap: false,
    rollupOptions: {
      input: {
        index: path.resolve(__dirname, "index.html"),
        login: path.resolve(__dirname, "login.html"),
        settings: path.resolve(__dirname, "settings.html"),
        rules: path.resolve(__dirname, "rules.html")
      }
    }
  }
});
