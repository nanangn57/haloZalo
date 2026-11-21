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

function createSocket(): WebSocket {
  const href = buildMessagingWsUrl(connectionConfig.wsBaseUrl, {
    mode: connectionConfig.wsAuthMode,
    accessToken: getAccessToken(),
    userId: getUserId(),
  })
  return new WebSocket(href)
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

  connect(): void {
    this.manuallyDisconnected = false

    if (
      this.socket &&
      (this.socket.readyState === WebSocket.CONNECTING ||
        this.socket.readyState === WebSocket.OPEN)
    ) {
      return
    }

    this.updateStatus('connecting')

    try {
      const socket = createSocket()
      this.socket = socket

      socket.onopen = () => {
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

      socket.onerror = (error) => {
        console.error('WebSocket error:', error)
        this.updateStatus('error')
      }

      socket.onclose = () => {
        if (this.socket === socket) {
          this.socket = null
        }
        this.updateStatus('disconnected')
        if (!this.manuallyDisconnected) {
          this.scheduleReconnect()
        }
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
    if (
      this.manuallyDisconnected ||
      this.reconnectTimer ||
      this.reconnectAttempts >= this.maxReconnectAttempts
    ) {
      return
    }

    this.reconnectAttempts++
    const delay = Math.min(
      this.reconnectDelay * this.reconnectAttempts,
      15000,
    )

    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      this.connect()
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
