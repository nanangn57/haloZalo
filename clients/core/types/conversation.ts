export type ConversationType = 'DIRECT' | 'GROUP'

// Tóm tắt tin cuối trong hội thoại (schema Message trong openapi). Mọi field đều tuỳ chọn vì server có thể bỏ.
export interface ConversationLastMessage {
  messageId?: string
  seq?: number
  senderId?: string
  type?: 'TEXT' | 'EMOTION' | 'IMAGE' | 'VIDEO' | 'DOCUMENT' | 'AUDIO'
  body?: string
  caption?: string
  fileName?: string
  status?: 'DELETED'
  createdAt?: string
}

export interface Conversation {
  conversationId: string
  type: ConversationType
  memberIds: string[]
  lastSeq: number
  createdAt: string
  lastMessageAt?: string
  lastMessage?: ConversationLastMessage
  lastReadSeq?: number
  unreadCount?: number

  // Prefs per-user (decisions.md). Chưa có trong openapi: server có thể chưa trả.
  pinned?: boolean
  muted?: boolean
  archived?: boolean
}

export interface ConversationListResponse {
  conversations: Conversation[]
  nextCursor?: string
}

export interface CreateDirectConversationRequest {
  userId: string
}
export interface ConversationPrefsPatch {
  pinned?: boolean
  muted?: boolean
  archived?: boolean
}
