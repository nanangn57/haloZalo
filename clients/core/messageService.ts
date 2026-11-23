import { getApiHeaders } from './apiClient'
import { connectionConfig } from './connectionConfig'
import { isMissingEndpoint, toApiError } from './httpError'
import type { ConversationPrefsPatch } from './types/conversation'
import type { Message } from './types/message'
import type { ReactionCode } from './types/event'

const API_MESSAGE_BASE_URL = connectionConfig.messageBaseUrl

export type SendableMessageType =
  | 'TEXT'
  | 'EMOTION'
  | 'IMAGE'
  | 'VIDEO'
  | 'DOCUMENT'
  | 'AUDIO'

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
  | {
      type: 'AUDIO'
      body: string
      clientMsgId: string
      mimeType: string
      size: number
      duration?: number
      fileName?: string
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

export function mapApiMessage(message: ApiMessage): Message {
  const deleted = message.status === 'DELETED'
  return {
    id: message.messageId,
    messageId: message.messageId,
    conversationId: message.conversationId,
    senderId: message.senderId,
    type: message.type,
    content: deleted ? '' : (message.body ?? ''),
    seq: message.seq,
    clientMsgId: message.clientMsgId,
    sentAt: Math.floor(new Date(message.createdAt).getTime() / 1000),
    status: 'sent',
    replyTo: message.replyTo,
    forwardedFrom: message.forwardedFrom,
    deleted: deleted || undefined,
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

// ---------------------------------------------------------------------------
// Thu hồi / reaction / chuyển tiếp / prefs
// ---------------------------------------------------------------------------

function messageUrl(conversationId: string, messageId: string): string {
  return (
    `${API_MESSAGE_BASE_URL}/conversations/${encodeURIComponent(conversationId)}` +
    `/messages/${encodeURIComponent(messageId)}`
  )
}

// Thu hồi: chỉ người gửi, delete-for-everyone, không hạn thời gian.
export async function deleteMessageApi(
  conversationId: string,
  messageId: string,
): Promise<void> {
  const response = await fetch(messageUrl(conversationId, messageId), {
    method: 'DELETE',
    headers: getApiHeaders(),
  })

  if (!response.ok) {
    throw await toApiError(response, 'Không thu hồi được tin nhắn')
  }
}

export type { ReactionCode }

export interface MessageReaction {
  userId: string
  code: ReactionCode
  createdAt: string
}

export async function addReactionApi(
  conversationId: string,
  messageId: string,
  code: ReactionCode,
): Promise<void> {
  const response = await fetch(
    `${messageUrl(conversationId, messageId)}/reactions/${encodeURIComponent(code)}`,
    { method: 'PUT', headers: getApiHeaders() },
  )

  if (!response.ok) {
    throw await toApiError(response, 'Không thả được reaction')
  }
}

export async function removeReactionApi(
  conversationId: string,
  messageId: string,
  code: ReactionCode,
): Promise<void> {
  const response = await fetch(
    `${messageUrl(conversationId, messageId)}/reactions/${encodeURIComponent(code)}`,
    { method: 'DELETE', headers: getApiHeaders() },
  )

  if (!response.ok) {
    throw await toApiError(response, 'Không gỡ được reaction')
  }
}

// Endpoint reaction có thể chưa có trên messaging: trả [] thay vì ném lỗi 404/405/501.
export async function listReactionsApi(
  conversationId: string,
  messageId: string,
): Promise<MessageReaction[]> {
  const response = await fetch(
    `${messageUrl(conversationId, messageId)}/reactions`,
    { method: 'GET', headers: getApiHeaders() },
  )

  if (!response.ok) {
    const error = await toApiError(response, 'Không tải được reaction')
    if (isMissingEndpoint(error)) {
      return []
    }
    throw error
  }

  const data = (await response.json()) as { reactions?: MessageReaction[] }
  return data.reactions ?? []
}

export interface ForwardMessageRequest {
  messageId: string
  clientMsgId: string
}

// Chuyển tiếp một tin vào hội thoại đích (`conversationId` là hội thoại nhận).
export async function forwardMessageApi(
  conversationId: string,
  request: ForwardMessageRequest,
): Promise<Message> {
  const response = await fetch(
    `${API_MESSAGE_BASE_URL}/conversations/${encodeURIComponent(conversationId)}/forwards`,
    {
      method: 'POST',
      headers: getApiHeaders(true),
      body: JSON.stringify(request),
    },
  )

  if (!response.ok) {
    throw await toApiError(response, 'Không chuyển tiếp được tin nhắn')
  }

  return mapApiMessage((await response.json()) as ApiMessage)
}

/**
 * Prefs pin / mute / archive theo user (decisions.md). Hợp đồng OpenAPI chưa có đường dẫn này,
 * nên gọi `PATCH /conversations/{id}/prefs` theo kiểu "nếu server có".
 * Trả `false` khi endpoint chưa có (404/405/501) để UI giữ nguyên trạng thái cũ và không báo lỗi.
 */
export async function updateConversationPrefsApi(
  conversationId: string,
  prefs: ConversationPrefsPatch,
): Promise<boolean> {
  const response = await fetch(
    `${API_MESSAGE_BASE_URL}/conversations/${encodeURIComponent(conversationId)}/prefs`,
    {
      method: 'PATCH',
      headers: getApiHeaders(true),
      body: JSON.stringify(prefs),
    },
  )

  if (response.ok) {
    return true
  }

  const error = await toApiError(response, 'Không cập nhật được tuỳ chọn hội thoại')
  if (isMissingEndpoint(error)) {
    return false
  }
  throw error
}
