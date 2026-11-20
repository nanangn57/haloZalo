export type MessageType =
  | 'TEXT'
  | 'EMOTION'
  | 'IMAGE'
  | 'DOCUMENT'
  | 'VIDEO'

export type MediaKind = 'IMAGE' | 'VIDEO' | 'DOCUMENT'

// Media trong openapi.yaml. url từ API là đường dẫn tương đối và có hạn;
// sau khi map (messageService) url đã là URL đầy đủ, dùng thẳng cho img/video/a.
export interface MessageMedia {
  mediaId: string
  kind: MediaKind
  url: string
  fileName: string
  mimeType: string
  size: number
  width?: number
  height?: number
}

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
  // TEXT/EMOTION: nội dung. IMAGE/VIDEO/DOCUMENT: chú thích, có thể rỗng.
  content: string

  // Chỉ có ở IMAGE, VIDEO, DOCUMENT. Vắng khi tin đã xoá.
  media?: MessageMedia

  seq?: number
  clientMsgId: string
  sentAt: number

  // Trạng thái phục vụ UI, không nhất thiết là field từ API
  status: MessageStatus
}