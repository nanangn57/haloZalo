export type HttpAuthMode = 'TOKEN' | 'USER_ID'

export type WsAuthMode =
  | 'TOKEN_QUERY'
  | 'USER_ID_QUERY'

/**
 * Reads Vite (VITE_*) and Expo (EXPO_PUBLIC_*) env without using import.meta,
 * so the same module works in Vite web and Metro/React Native.
 */
type ProcessEnv = { env?: Record<string, string | undefined> }

function readEnv(viteKey: string): string | undefined {
  // Avoid depending on @types/node so the same file typechecks in Expo and Vite.
  const env = (globalThis as { process?: ProcessEnv }).process?.env
  if (!env) {
    return undefined
  }

  const expoKey = `EXPO_PUBLIC_${viteKey.replace(/^VITE_/, '')}`
  const value = env[expoKey] ?? env[viteKey]
  return typeof value === 'string' && value.length > 0 ? value : undefined
}

export const connectionConfig = {
  identityHttpAuthMode: 'TOKEN' as const,

  messageHttpAuthMode: (
    readEnv('VITE_MESSAGE_HTTP_AUTH_MODE') || 'TOKEN'
  ) as HttpAuthMode,

  wsAuthMode: (
    readEnv('VITE_WS_AUTH_MODE') || 'TOKEN_QUERY'
  ) as WsAuthMode,

  messageBaseUrl:
    readEnv('VITE_API_MESSAGE_BASE_URL') || 'http://localhost:8080',

  wsBaseUrl:
    readEnv('VITE_WS_BASE_URL') || 'ws://localhost:8080',

  identityBaseUrl:
    readEnv('VITE_API_IDENTITY_BASE_URL') || 'http://localhost:8080',
}
