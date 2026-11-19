
import { getAccessToken, getUserId} from './authSession'

import { connectionConfig } from './connectionConfig'

// const WS_BASE_URL = 'ws://localhost:8082'

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

function createSocket(): WebSocket {
  const url = new URL('/ws', connectionConfig.wsBaseUrl)

  switch (connectionConfig.wsAuthMode) {
    case 'TOKEN_QUERY': {
      const token = getAccessToken()

      if (!token) {
        throw new Error('Không tìm thấy access token.')
      }

      url.searchParams.set('access_token', token)
      break
    }

    case 'USER_ID_QUERY': {
      const userId = getUserId()

      if (!userId) {
        throw new Error('Không tìm thấy userId.')
      }

      url.searchParams.set('userId', userId)
      break
    }
  }

  return new WebSocket(url.toString())
}

class SocketClient {
  private socket: WebSocket | null = null

  private eventListeners = new Set<EventListener>()
  private statusListeners = new Set<StatusListener>()

  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private reconnectAttempts = 0
  private manuallyDisconnected = false

  private readonly maxReconnectAttempts = 10
  private readonly reconnectDelay = 3000

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
        this.reconnectAttempts = 0
        this.updateStatus('connected')
        console.log('WebSocket connected')
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

    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      this.connect()
    }, this.reconnectDelay)
  }

  subscribe(listener: EventListener): () => void {
    this.eventListeners.add(listener)

    return () => this.eventListeners.delete(listener)
  }

  subscribeStatus(listener: StatusListener): () => void {
    this.statusListeners.add(listener)

    return () => this.statusListeners.delete(listener)
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

    const socket = this.socket
    this.socket = null

    socket?.close()
    this.updateStatus('disconnected')
  }
}

export const socketClient = new SocketClient()
