import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'
import path from 'path'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const envDir = path.resolve(__dirname, '..')
  const env = loadEnv(mode, envDir, '')

  const keys = [
    'VITE_API_IDENTITY_BASE_URL',
    'VITE_API_MESSAGE_BASE_URL',
    'VITE_WS_BASE_URL',
    'VITE_MESSAGE_HTTP_AUTH_MODE',
    'VITE_WS_AUTH_MODE',
    'VITE_HTTP_AUTH_MODE',
  ] as const

  const define: Record<string, string> = {}
  for (const key of keys) {
    // Docker Compose injects .env.dev into process.env; loadEnv covers local .env*
    define[`process.env.${key}`] = JSON.stringify(
      process.env[key] ?? env[key] ?? '',
    )
  }

  return {
    plugins: [react()],
    envDir,
    define,
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
        // Docker paths + local checkout (bootstrap-icons fonts live in node_modules)
        allow: [
          '/app',
          '/workspace',
          path.resolve(__dirname, '../..'),
          path.resolve(__dirname),
        ],
      },
    },
  }
})
