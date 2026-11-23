export type MessageType =
  | 'TEXT'
  | 'EMOTION'
  | 'IMAGE'
  | 'DOCUMENT'
  | 'VIDEO'
  | 'AUDIO'

/** Trạng thái gửi local (optimistic UI). Không có delivered/seen per-message — đã đọc là read marker hội thoại. */
export type MessageStatus = 'sending' | 'sent' | 'failed'

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

  // Trả lời / chuyển tiếp (messageId của tin gốc)
  replyTo?: string
  forwardedFrom?: string

  // Tin đã thu hồi (API status DELETED, không có body)
  deleted?: boolean
  sentAt: number

  // Trạng thái gửi trên client (sending → sent | failed)
  status: MessageStatus
}