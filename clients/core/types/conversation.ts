export type ConversationType = 'DIRECT' | 'GROUP'

export interface Conversation {
  conversationId: string
  type: ConversationType
  memberIds: string[]
  lastSeq: number
  createdAt: string
  lastMessageAt?: string
  lastMessage?: unknown
  lastReadSeq?: number
  unreadCount?: number
}

export interface ConversationListResponse {
  conversations: Conversation[]
  nextCursor?: string
}

export interface CreateDirectConversationRequest {
  userId: string
}