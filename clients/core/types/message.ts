export type MessageType =
  | 'TEXT'
  | 'EMOTION'
  | 'IMAGE'
  | 'DOCUMENT'
  | 'VIDEO'

export type MessageStatus =
  | 'sending'
  | 'sent'
  | 'delivered'
  | 'seen'
  | 'failed'

export interface Message {
  // ID ổn định để frontend quản lý message và React key
  id: string

  // ID chính thức do backend cấp, có thể chưa tồn tại khi optimistic update
  messageId?: string

  conversationId: string
  senderId: string
  type: MessageType
  content: string

  seq?: number
  clientMsgId: string
  sentAt: number

  // Trạng thái phục vụ UI, không nhất thiết là field từ API
  status: MessageStatus
}