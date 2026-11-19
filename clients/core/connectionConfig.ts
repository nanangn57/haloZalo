export type HttpAuthMode = 'TOKEN' | 'USER_ID'

export type WsAuthMode =
  | 'TOKEN_QUERY'
  | 'USER_ID_QUERY'

export const connectionConfig = {
  // Identity Service: always uses accessToken
  identityHttpAuthMode: 'TOKEN' as const,

  // Messaging Service: supports TOKEN and USER_ID
  // messageHttpAuthMode: (
  //   import.meta.env.VITE_MESSAGE_HTTP_AUTH_MODE || 'TOKEN'
  // ) as HttpAuthMode,
  // TODO: Change to TOKEN when done testing
  messageHttpAuthMode: (
    'USER_ID'
  ) as HttpAuthMode,

  // WebSocket authentication
  wsAuthMode: (
    import.meta.env.VITE_WS_AUTH_MODE || 'TOKEN_QUERY'
  ) as WsAuthMode,

  messageBaseUrl:
    import.meta.env.VITE_API_MESSAGE_BASE_URL ||
    'http://localhost:8082',

  wsBaseUrl:
    import.meta.env.VITE_WS_BASE_URL ||
    'ws://localhost:8082',

  identityBaseUrl:
    import.meta.env.VITE_API_IDENTITY_BASE_URL ||
    'http://localhost:8080',
}