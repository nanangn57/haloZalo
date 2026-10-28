import path from 'node:path'
import { fileURLToPath } from 'node:url'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

const uiDir = path.dirname(fileURLToPath(import.meta.url))
const coreDir = path.resolve(uiDir, '../../core')

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    watch: {
      usePolling: true,
    },
    fs: {
      allow: [uiDir, coreDir, '/app', '/workspace/clients'],
    },
  },
})
