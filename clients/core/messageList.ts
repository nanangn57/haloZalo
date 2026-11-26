import type { Message } from './types/message'

/**
 * Gộp tin theo messageId hoặc clientMsgId để tránh trùng HTTP response + WebSocket.
 */
export function upsertMessage(previous: Message[], incoming: Message): Message[] {
  const index = previous.findIndex(
    (message) =>
      (incoming.messageId && message.messageId === incoming.messageId) ||
      (incoming.clientMsgId &&
        message.clientMsgId &&
        message.clientMsgId === incoming.clientMsgId) ||
      message.id === incoming.id,
  )
  if (index >= 0) {
    const next = previous.slice()
    next[index] = { ...previous[index], ...incoming }
    return next
  }
  return [...previous, incoming]
}
