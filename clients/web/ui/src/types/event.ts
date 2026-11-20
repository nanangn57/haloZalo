
export interface EventEnvelope<TPayload = unknown> {
  eventId: string
  type: string
  occurredAt: string
  producer: 'identity' | 'messaging' | 'analytics'
  payload: TPayload
}

export interface MessageCreatedPayload {
  messageId: string
  conversationId: string
  seq: number
  senderId: string
  type: 'TEXT' | 'EMOTION'
  body: string
  clientMsgId: string
  createdAt: string
}

export type MessageCreatedEvent =
  EventEnvelope<MessageCreatedPayload> & {
    type: 'messaging.message.created'
    producer: 'messaging'
  }

export interface MessageDeletedPayload {
  messageId: string
  conversationId: string
  deletedAt: string
}

export interface ReactionPayload {
  messageId: string
  conversationId: string
  userId: string
  code: 'like' | 'love' | 'haha' | 'wow' | 'sad' | 'angry'
}

export interface ReadUpdatedPayload {
  conversationId: string
  userId: string
  lastReadSeq: number
}

export type MessagingEvent =
  | MessageCreatedEvent
  | (EventEnvelope<MessageDeletedPayload> & {
      type: 'messaging.message.deleted'
      producer: 'messaging'
    })
  | (EventEnvelope<ReactionPayload> & {
      type: 'messaging.reaction.added' | 'messaging.reaction.removed'
      producer: 'messaging'
    })
  | (EventEnvelope<ReadUpdatedPayload> & {
      type: 'messaging.read.updated'
      producer: 'messaging'
    })
