export type HttpAuthMode = 'TOKEN' | 'USER_ID'

export type WsAuthMode =
  | 'TOKEN_QUERY'
  | 'USER_ID_QUERY'

/**
 * Reads Vite (VITE_*) and Expo (EXPO_PUBLIC_*) env without using import.meta,
 * so the same module works in Vite web and Metro/React Native.
 *
 * Vite replaces static `process.env.VITE_*` at build time (see clients/web/ui/vite.config.ts).
 * Expo injects EXPO_PUBLIC_* into process.env at runtime.
 */
type ProcessEnv = { env?: Record<string, string | undefined> }

function readExpo(viteKey: string): string | undefined {
  const env = (globalThis as { process?: ProcessEnv }).process?.env
  if (!env) {
    return undefined
  }
  const expoKey = `EXPO_PUBLIC_${viteKey.replace(/^VITE_/, '')}`
  const value = env[expoKey]
  return typeof value === 'string' && value.length > 0 ? value : undefined
}

function pick(viteValue: string | undefined, viteKey: string, fallback: string): string {
  if (typeof viteValue === 'string' && viteValue.length > 0) {
    return viteValue
  }
  return readExpo(viteKey) || fallback
}

// Static process.env.VITE_* reads so Vite `define` can bake cloud/local URLs into the bundle.
declare const process: { env: Record<string, string | undefined> }

export const connectionConfig = {
  identityHttpAuthMode: 'TOKEN' as const,

  messageHttpAuthMode: pick(
    process.env.VITE_MESSAGE_HTTP_AUTH_MODE,
    'VITE_MESSAGE_HTTP_AUTH_MODE',
    'TOKEN',
  ) as HttpAuthMode,

  wsAuthMode: pick(
    process.env.VITE_WS_AUTH_MODE,
    'VITE_WS_AUTH_MODE',
    'TOKEN_QUERY',
  ) as WsAuthMode,

  messageBaseUrl: pick(
    process.env.VITE_API_MESSAGE_BASE_URL,
    'VITE_API_MESSAGE_BASE_URL',
    'http://localhost:8080',
  ),

  wsBaseUrl: pick(
    process.env.VITE_WS_BASE_URL,
    'VITE_WS_BASE_URL',
    'ws://localhost:8080',
  ),

  identityBaseUrl: pick(
    process.env.VITE_API_IDENTITY_BASE_URL,
    'VITE_API_IDENTITY_BASE_URL',
    'http://localhost:8080',
  ),
}
