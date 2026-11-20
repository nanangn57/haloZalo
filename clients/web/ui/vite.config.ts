import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import path from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],

  resolve: {
    alias: {
      '@core': path.resolve(__dirname, '../../core'),
    },
  },

  server: {
    host: '0.0.0.0',
    watch: {
      usePolling: true,
    },
    fs: {
      allow: ['/app', '/workspace/clients'],
    },
  },
})