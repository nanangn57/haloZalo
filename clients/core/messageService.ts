
import { getApiHeaders } from './apiClient'
import { connectionConfig } from './connectionConfig'
import type { Message } from './types/message'

// =========================
// CONFIG
// =========================

const API_MESSAGE_BASE_URL = connectionConfig.messageBaseUrl

// =========================
// TYPES
// =========================

export type SendableMessageType = 'TEXT' | 'EMOTION'

export type SendMessageRequest =
  | {
      type: 'TEXT'
      body: string
      clientMsgId: string
      replyTo?: string
    }
  | {
      type: 'EMOTION'
      body: 'like' | 'love' | 'haha' | 'wow' | 'sad' | 'angry'
      clientMsgId: string
      replyTo?: string
    }

export interface ApiMessage {
  messageId: string
  conversationId: string
  seq: number
  senderId: string
  type: SendableMessageType
  body?: string
  clientMsgId: string
  createdAt: string
  replyTo?: string
  forwardedFrom?: string
  status?: 'DELETED'
  deletedAt?: string
}

export interface MessagePageResponse {
  messages: ApiMessage[]
  hasMore: boolean
}

interface ApiErrorResponse {
  error?: {
    code?: string
    message?: string
  }
}

// =========================
// ERROR HANDLING
// =========================

async function getErrorMessage(
  response: Response,
): Promise<string> {
  const data: unknown = await response.json().catch(() => null)

  const errorData = data as ApiErrorResponse | null

  return (
    errorData?.error?.message ??
    `Request failed with status ${response.status}`
  )
}

// =========================
// MAP API MESSAGE TO FRONTEND MESSAGE
// =========================

function mapApiMessage(message: ApiMessage): Message {
  return {
    id: message.messageId,
    messageId: message.messageId,
    conversationId: message.conversationId,
    senderId: message.senderId,
    type: message.type,
    content: message.body ?? '',
    seq: message.seq,
    clientMsgId: message.clientMsgId,
    sentAt: Math.floor(
      new Date(message.createdAt).getTime() / 1000,
    ),
    status: 'sent',
  }
}

// =========================
// 1. SEND MESSAGE
// =========================

export async function sendMessageApi(
  conversationId: string,
  request: SendMessageRequest,
): Promise<Message> {
  const url =
    `${API_MESSAGE_BASE_URL}/conversations/` +
    `${encodeURIComponent(conversationId)}/messages`

  const response = await fetch(url, {
    method: 'POST',
    headers: getApiHeaders(true),
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    throw new Error(await getErrorMessage(response))
  }

  const data: ApiMessage = await response.json()

  return mapApiMessage(data)
}

// =========================
// 2. GET MESSAGE HISTORY
// =========================

export async function getMessagesApi(
  conversationId: string,
  options?: {
    afterSeq?: number
    beforeSeq?: number
    limit?: number
  },
): Promise<{
  messages: Message[]
  hasMore: boolean
}> {
  const params = new URLSearchParams()

  if (options?.afterSeq !== undefined) {
    params.set('afterSeq', String(options.afterSeq))
  }

  if (options?.beforeSeq !== undefined) {
    params.set('beforeSeq', String(options.beforeSeq))
  }

  if (options?.limit !== undefined) {
    params.set('limit', String(options.limit))
  }

  const queryString = params.toString()

  const url =
    `${API_MESSAGE_BASE_URL}/conversations/` +
    `${encodeURIComponent(conversationId)}/messages` +
    (queryString ? `?${queryString}` : '')

  const response = await fetch(url, {
    method: 'GET',
    headers: getApiHeaders(),
  })

  if (!response.ok) {
    throw new Error(await getErrorMessage(response))
  }

  const data: MessagePageResponse = await response.json()

  return {
    messages: data.messages.map(mapApiMessage),
    hasMore: data.hasMore,
  }
}

export async function createGroupConversationApi(
  groupId: string
): Promise<void> {
  const response = await fetch(
    `${API_MESSAGE_BASE_URL}/internal/conversations`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        conversationId: groupId,
        type: 'GROUP',
      }),
    }
  )

  // Conversation đã tồn tại thì vẫn có thể tiếp tục mở chat.
  if (response.status === 409) {
    return
  }

  if (!response.ok) {
    throw new Error(
      `Cannot initialize group conversation: ${response.status}`
    )
  }
}