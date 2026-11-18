export type HttpAuthMode = 'TOKEN' | 'USER_ID'

export type WsAuthMode =
  | 'TOKEN_QUERY'
  | 'USER_ID_QUERY'

export const connectionConfig = {
  httpAuthMode: (
    import.meta.env.VITE_HTTP_AUTH_MODE || 'TOKEN'
  ) as HttpAuthMode,

  wsAuthMode: (
    import.meta.env.VITE_WS_AUTH_MODE || 'TOKEN_QUERY'
  ) as WsAuthMode,

  messageBaseUrl:
    import.meta.env.VITE_API_MESSAGE_BASE_URL || 'http://localhost:8082',

  wsBaseUrl:
    import.meta.env.VITE_WS_BASE_URL || 'ws://localhost:8082',

  identityBaseUrl:
    import.meta.env.VITE_API_IDENTITY_BASE_URL || 'http://localhost:8080',
}