
import { getApiHeaders } from './apiClient'
import { connectionConfig } from './connectionConfig'
import type {
  MediaKind,
  Message,
  MessageMedia,
} from './types/message'

// =========================
// CONFIG
// =========================

const API_MESSAGE_BASE_URL = connectionConfig.messageBaseUrl

// =========================
// TYPES
// =========================

export type SendableMessageType =
  | 'TEXT'
  | 'EMOTION'
  | MediaKind

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
  | {
      // Phải bằng kind của media đã upload.
      type: MediaKind
      mediaId: string
      // Chú thích, không bắt buộc.
      body?: string
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
  // IMAGE, VIDEO, DOCUMENT. url tương đối với API_MESSAGE_BASE_URL.
  media?: MessageMedia
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

// Link media là đường dẫn tương đối, có hạn. Resolve theo base URL của messaging.
// Link hết hạn (403) thì tải lại tin là có link mới.
export function resolveMediaUrl(url: string): string {
  return new URL(url, API_MESSAGE_BASE_URL).toString()
}

function mapMedia(media: MessageMedia): MessageMedia {
  return { ...media, url: resolveMediaUrl(media.url) }
}

export function mapApiMessage(message: ApiMessage): Message {
  return {
    id: message.messageId,
    messageId: message.messageId,
    conversationId: message.conversationId,
    senderId: message.senderId,
    type: message.type,
    content: message.body ?? '',
    media: message.media ? mapMedia(message.media) : undefined,
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
// 1b. UPLOAD MEDIA (bước một của gửi file)
// =========================

// Ảnh 10MB, video 25MB, tài liệu 10MB; server trả 413 khi vượt.
// Sau khi upload, gửi tin { type: media.kind, mediaId: media.mediaId }.
export async function uploadMediaApi(
  conversationId: string,
  file: File,
): Promise<MessageMedia> {
  const url =
    `${API_MESSAGE_BASE_URL}/conversations/` +
    `${encodeURIComponent(conversationId)}/media`

  const form = new FormData()
  form.append('file', file, file.name)

  // Không đặt Content-Type: trình duyệt tự thêm boundary cho multipart.
  const response = await fetch(url, {
    method: 'POST',
    headers: getApiHeaders(),
    body: form,
  })

  if (!response.ok) {
    throw new Error(await getErrorMessage(response))
  }

  const data: MessageMedia = await response.json()

  return mapMedia(data)
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