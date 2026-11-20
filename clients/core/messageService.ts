import { getApiHeaders } from './apiClient'
import { connectionConfig } from './connectionConfig'
import type { Message } from './types/message'

const API_MESSAGE_BASE_URL = connectionConfig.messageBaseUrl

export type SendableMessageType =
  | 'TEXT'
  | 'EMOTION'
  | 'IMAGE'
  | 'VIDEO'
  | 'DOCUMENT'

export type EmotionCode =
  | 'like'
  | 'love'
  | 'haha'
  | 'wow'
  | 'sad'
  | 'angry'

export type SendMessageRequest =
  | {
      type: 'TEXT'
      body: string
      clientMsgId: string
      replyTo?: string
    }
  | {
      type: 'EMOTION'
      body: EmotionCode
      clientMsgId: string
      replyTo?: string
    }
  | {
      type: 'IMAGE'
      body: string
      clientMsgId: string
      thumbnailUrl?: string
      width?: number
      height?: number
      caption?: string
      replyTo?: string
    }
  | {
      type: 'VIDEO'
      body: string
      clientMsgId: string
      thumbnailUrl?: string
      width?: number
      height?: number
      duration?: number
      caption?: string
      replyTo?: string
    }
  | {
      type: 'DOCUMENT'
      body: string
      clientMsgId: string
      fileName: string
      mimeType: string
      size: number
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

async function getErrorMessage(response: Response): Promise<string> {
  const data: unknown = await response.json().catch(() => null)
  const errorData = data as ApiErrorResponse | null
  return (
    errorData?.error?.message ??
    `Request failed with status ${response.status}`
  )
}

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
    sentAt: Math.floor(new Date(message.createdAt).getTime() / 1000),
    status: 'sent',
  }
}

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
  groupId: string,
): Promise<void> {
  const response = await fetch(
    `${API_MESSAGE_BASE_URL}/conversations/group`,
    {
      method: 'POST',
      headers: getApiHeaders(true),
      body: JSON.stringify({ groupId }),
    },
  )

  if (response.ok || response.status === 409) {
    return
  }

  throw new Error(await getErrorMessage(response))
}

export interface ReadState {
  conversationId: string
  lastReadSeq: number
  unreadCount: number
}

export async function markConversationReadApi(
  conversationId: string,
  seq: number,
): Promise<ReadState> {
  const response = await fetch(
    `${API_MESSAGE_BASE_URL}/conversations/` +
      `${encodeURIComponent(conversationId)}/read`,
    {
      method: 'PUT',
      headers: getApiHeaders(true),
      body: JSON.stringify({ seq }),
    },
  )

  if (!response.ok) {
    throw new Error(await getErrorMessage(response))
  }

  return (await response.json()) as ReadState
}
