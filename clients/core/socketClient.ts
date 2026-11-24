import { getAccessToken, getUserId } from './authSession'
import { connectionConfig } from './connectionConfig'

export interface MessagingEvent {
  eventId: string
  type: string
  occurredAt: string
  producer: string
  payload: Record<string, unknown>
}

export type SocketStatus =
  | 'connecting'
  | 'connected'
  | 'disconnected'
  | 'error'

type EventListener = (event: MessagingEvent) => void
type StatusListener = (status: SocketStatus) => void
type ReconnectListener = () => void

/** Join messaging WS path onto base URL without replacing `/api/messaging` with `/ws`. */
export function buildMessagingWsUrl(
  wsBaseUrl: string,
  auth: { mode: typeof connectionConfig.wsAuthMode; accessToken?: string | null; userId?: string | null },
): string {
  const base = wsBaseUrl.replace(/\/?$/, '/')
  const url = new URL('ws', base)

  switch (auth.mode) {
    case 'TOKEN_QUERY': {
      if (!auth.accessToken) {
        throw new Error('Không tìm thấy access token.')
      }
      url.searchParams.set('access_token', auth.accessToken)
      break
    }
    case 'USER_ID_QUERY': {
      if (!auth.userId) {
        throw new Error('Không tìm thấy userId.')
      }
      url.searchParams.set('userId', auth.userId)
      break
    }
  }

  return url.toString()
}

class SocketClient {
  private socket: WebSocket | null = null

  private eventListeners = new Set<EventListener>()
  private statusListeners = new Set<StatusListener>()
  private reconnectListeners = new Set<ReconnectListener>()

  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private reconnectAttempts = 0
  private manuallyDisconnected = false
  private hadConnection = false

  private readonly maxReconnectAttempts = 20
  private readonly reconnectDelay = 2000

  /**
   * @param fromScheduler - internal backoff retry; keep streak. UI/mount/foreground
   *   should call without this so a stuck `error` can recover.
   */
  connect(fromScheduler = false): void {
    this.manuallyDisconnected = false

    if (!fromScheduler) {
      this.reconnectAttempts = 0
      if (this.reconnectTimer) {
        clearTimeout(this.reconnectTimer)
        this.reconnectTimer = null
      }
    }

    if (
      this.socket &&
      (this.socket.readyState === WebSocket.CONNECTING ||
        this.socket.readyState === WebSocket.OPEN)
    ) {
      return
    }

    // Cloud/local both require a session query param; don't open a doomed socket.
    if (
      connectionConfig.wsAuthMode === 'TOKEN_QUERY' &&
      !getAccessToken()
    ) {
      this.updateStatus('disconnected')
      return
    }
    if (
      connectionConfig.wsAuthMode === 'USER_ID_QUERY' &&
      !getUserId()
    ) {
      this.updateStatus('disconnected')
      return
    }

    this.updateStatus('connecting')

    try {
      const href = buildMessagingWsUrl(connectionConfig.wsBaseUrl, {
        mode: connectionConfig.wsAuthMode,
        accessToken: getAccessToken(),
        userId: getUserId(),
      })
      // Help diagnose phone/cloud mismatches without printing the token.
      try {
        const debug = new URL(href)
        console.log('WebSocket connecting', `${debug.protocol}//${debug.host}${debug.pathname}`)
      } catch {
        console.log('WebSocket connecting')
      }
      const socket = new WebSocket(href)
      this.socket = socket

      socket.onopen = () => {
        if (this.socket !== socket) return
        const isReconnect = this.hadConnection
        this.reconnectAttempts = 0
        this.hadConnection = true
        this.updateStatus('connected')
        console.log('WebSocket connected')
        if (isReconnect) {
          this.reconnectListeners.forEach((listener) => listener())
        }
      }

      socket.onmessage = (message: MessageEvent) => {
        if (this.socket !== socket) return
        try {
          const event = JSON.parse(message.data) as MessagingEvent
          if (!event.type || !event.payload) {
            console.warn('Invalid WebSocket event:', event)
            return
          }
          this.eventListeners.forEach((listener) => listener(event))
        } catch (error) {
          console.error('Cannot parse WebSocket event:', error)
        }
      }

      // RN often fires error without detail; a remount can also deliver stale error
      // from the previous socket and wipe a healthy "connected" status.
      socket.onerror = () => {
        if (this.socket !== socket) return
        console.warn('WebSocket error event')
      }

      socket.onclose = () => {
        if (this.socket !== socket) return
        this.socket = null
        if (this.manuallyDisconnected) {
          this.updateStatus('disconnected')
          return
        }
        this.updateStatus('disconnected')
        this.scheduleReconnect()
      }
    } catch (error) {
      console.error('Cannot create WebSocket:', error)
      this.updateStatus('error')
      if (!this.manuallyDisconnected) {
        this.scheduleReconnect()
      }
    }
  }

  private scheduleReconnect(): void {
    if (this.manuallyDisconnected || this.reconnectTimer) {
      return
    }

    this.reconnectAttempts++
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      // Surface error in UI but keep retrying slowly — mobile networks recover.
      this.updateStatus('error')
    }

    const delay = Math.min(
      this.reconnectDelay * Math.min(this.reconnectAttempts, this.maxReconnectAttempts),
      15000,
    )

    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      this.connect(true)
    }, delay)
  }

  subscribe(listener: EventListener): () => void {
    this.eventListeners.add(listener)
    return () => {
      this.eventListeners.delete(listener)
    }
  }

  subscribeStatus(listener: StatusListener): () => void {
    this.statusListeners.add(listener)
    return () => {
      this.statusListeners.delete(listener)
    }
  }

  /**
   * Fires after a successful reconnect (not the first connect). Callers catch up with afterSeq.
   */
  subscribeReconnect(listener: ReconnectListener): () => void {
    this.reconnectListeners.add(listener)
    return () => {
      this.reconnectListeners.delete(listener)
    }
  }

  private updateStatus(status: SocketStatus): void {
    this.statusListeners.forEach((listener) => listener(status))
  }

  disconnect(): void {
    this.manuallyDisconnected = true

    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }

    this.reconnectAttempts = 0
    this.hadConnection = false

    const socket = this.socket
    this.socket = null
    socket?.close()
    this.updateStatus('disconnected')
  }

  /**
   * Sends an ephemeral client signal (typing / presence). No-op when the socket is not open.
   */
  send(payload: Record<string, unknown>): void {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      return
    }
    this.socket.send(JSON.stringify(payload))
  }

  sendTyping(conversationId: string, typing: boolean): void {
    this.send({
      type: 'messaging.typing',
      payload: { conversationId, typing },
    })
  }

  sendPresence(conversationId: string, status: 'online' | 'offline'): void {
    this.send({
      type: 'messaging.presence',
      payload: { conversationId, status },
    })
  }
}

export const socketClient = new SocketClient()
