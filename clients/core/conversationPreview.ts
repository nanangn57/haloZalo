import type { Conversation } from './types/conversation'

const EMOTION_EMOJI: Record<string, string> = {
  like: '👍',
  love: '❤️',
  haha: '😄',
  wow: '😮',
  sad: '😢',
  angry: '😠',
}

const MAX_PREVIEW = 60

/**
 * Dòng xem trước tin cuối của hội thoại, hoặc `null` nếu server chưa trả `lastMessage`
 * (khi đó UI giữ phụ đề cũ). `currentUserId` để thêm tiền tố "Bạn: ".
 */
export function getLastMessagePreview(
  conversation: Conversation,
  currentUserId?: string | null,
): string | null {
  const last = conversation.lastMessage
  if (!last) {
    return null
  }

  let text: string
  if (last.status === 'DELETED') {
    text = 'Tin nhắn đã được thu hồi'
  } else {
    switch (last.type) {
      case 'IMAGE':
        text = last.caption?.trim() ? `🖼 ${last.caption.trim()}` : '🖼 Hình ảnh'
        break
      case 'VIDEO':
        text = last.caption?.trim() ? `🎬 ${last.caption.trim()}` : '🎬 Video'
        break
      case 'DOCUMENT':
        text = `📎 ${last.fileName?.trim() || 'Tệp đính kèm'}`
        break
      case 'AUDIO':
        text = '🎤 Tin nhắn thoại'
        break
      case 'EMOTION':
        text = EMOTION_EMOJI[last.body ?? ''] ?? last.body ?? ''
        break
      case 'SYSTEM':
        text = (last.body ?? '').replace(/\s+/g, ' ').trim()
        break
      default:
        // TEXT và tin forward (cùng type gốc, có thể kèm forwardedFrom ở Message đầy đủ).
        text = (last.body ?? '').replace(/\s+/g, ' ').trim()
    }
  }

  if (!text) {
    return null
  }
  if (text.length > MAX_PREVIEW) {
    text = `${text.slice(0, MAX_PREVIEW - 1)}…`
  }

  const mine = Boolean(currentUserId) && last.senderId === currentUserId
  return mine ? `Bạn: ${text}` : text
}
