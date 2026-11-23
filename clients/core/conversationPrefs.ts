import type { Conversation, ConversationPrefsPatch } from './types/conversation'

/** Ghim lên đầu, giữ nguyên thứ tự còn lại (server đã sắp theo hoạt động gần nhất). */
export function sortPinnedFirst(list: Conversation[]): Conversation[] {
  return [
    ...list.filter((conversation) => conversation.pinned),
    ...list.filter((conversation) => !conversation.pinned),
  ]
}

/**
 * Áp prefs lên danh sách hội thoại đang hiển thị.
 * Hội thoại vừa lưu trữ bị bỏ khỏi danh sách (server mặc định cũng ẩn archived).
 */
export function applyConversationPrefs(
  list: Conversation[],
  conversationId: string,
  patch: ConversationPrefsPatch,
): Conversation[] {
  const next = list
    .map((conversation) =>
      conversation.conversationId === conversationId
        ? { ...conversation, ...patch }
        : conversation,
    )
    .filter((conversation) => !conversation.archived)
  return sortPinnedFirst(next)
}
