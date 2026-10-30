import path from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// 打包到 Android App 的 assets/web,電視上的 ParentServer 直接送這些檔案
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: { alias: { '@': path.resolve(__dirname, './src') } },
  base: '/',
  build: {
    outDir: '../app/src/main/assets/web',
    emptyOutDir: true,
    assetsDir: 'assets',
  },
  // npm run dev 時把 API 轉到電視
  // 例:TV=http://192.168.1.50:8765 npm run dev
  server: { proxy: { '/api': process.env.TV ?? 'http://localhost:8765' } },
})
