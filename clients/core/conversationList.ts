import type { Conversation, ConversationLastMessage } from './types/conversation'
import type { Message } from './types/message'

function sortPinnedFirst(list: Conversation[]): Conversation[] {
  return [
    ...list.filter((conversation) => conversation.pinned),
    ...list.filter((conversation) => !conversation.pinned),
  ]
}

/** Map tin nhắn client → tóm tắt tin cuối cho danh sách hội thoại. */
export function toLastMessage(message: Message): ConversationLastMessage {
  return {
    messageId: message.messageId,
    seq: message.seq,
    senderId: message.senderId,
    type: message.type as ConversationLastMessage['type'],
    body: message.deleted ? undefined : message.content,
    status: message.deleted ? 'DELETED' : undefined,
    createdAt:
      typeof message.sentAt === 'number'
        ? new Date(message.sentAt * 1000).toISOString()
        : undefined,
  }
}

/**
 * Cập nhật lastMessage / lastSeq / unread khi có tin mới (HTTP hoặc WebSocket).
 * Hội thoại đang mở hoặc tin của mình → không tăng unread (đang mở thì về 0).
 */
export function applyMessageToConversations(
  list: Conversation[],
  message: Message,
  options: {
    currentUserId?: string | null
    activeConversationId?: string | null
  } = {},
): Conversation[] {
  const conversationId = message.conversationId
  if (!conversationId) return list

  const { currentUserId, activeConversationId } = options
  const isActive = activeConversationId === conversationId
  const fromSelf = Boolean(currentUserId) && message.senderId === currentUserId
  const incomingSeq =
    typeof message.seq === 'number' && Number.isFinite(message.seq)
      ? message.seq
      : null

  let found = false
  const mapped = list.map((conversation) => {
    if (conversation.conversationId !== conversationId) {
      return conversation
    }
    found = true

    const previousSeq = conversation.lastSeq ?? 0
    if (incomingSeq !== null && incomingSeq < previousSeq) {
      return conversation
    }

    let unreadCount = conversation.unreadCount ?? 0
    if (isActive) {
      unreadCount = 0
    } else if (!fromSelf && !message.deleted) {
      unreadCount += 1
    }

    return {
      ...conversation,
      lastSeq: incomingSeq !== null ? Math.max(previousSeq, incomingSeq) : previousSeq,
      lastMessageAt: toLastMessage(message).createdAt ?? conversation.lastMessageAt,
      lastMessage: toLastMessage(message),
      unreadCount,
    }
  })

  if (!found) {
    return list
  }

  const updated = mapped.find((item) => item.conversationId === conversationId)!
  const rest = mapped.filter((item) => item.conversationId !== conversationId)
  return sortPinnedFirst([updated, ...rest])
}

/** Đánh dấu đã đọc trên UI ngay khi gọi mark-read (không chờ event). */
export function clearConversationUnread(
  list: Conversation[],
  conversationId: string,
): Conversation[] {
  return list.map((conversation) =>
    conversation.conversationId === conversationId
      ? { ...conversation, unreadCount: 0 }
      : conversation,
  )
}
