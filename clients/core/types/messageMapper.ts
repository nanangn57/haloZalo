import type { Message } from '../types/message'
import type { MessageCreatedPayload } from '../types/event'

export function mapEventToMessage(
  payload: MessageCreatedPayload
): Message {
  return {
    id: payload.messageId,
    conversationId: payload.conversationId,
    senderId: payload.senderId,
    type: payload.type,
    content: payload.body,
    seq: payload.seq,
    clientMsgId: payload.clientMsgId,
    sentAt: Math.floor(
      new Date(payload.createdAt).getTime() / 1000
    ),
    status: 'sent',
    replyTo: payload.replyTo,
    forwardedFrom: payload.forwardedFrom,
  }
}