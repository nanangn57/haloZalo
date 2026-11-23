
/**
 * Event Envelope v1
 * Shared structure for all WebSocket events.
 */
export type EventProducer =
  | 'identity'
  | 'messaging'
  | 'analytics'

export interface EventEnvelope<TPayload = unknown> {
  eventId: string
  type: string
  occurredAt: string
  producer: EventProducer
  payload: TPayload
}

/**
 * Message types defined by the current API contract.
 */
export type ApiMessageType = 'TEXT' | 'EMOTION' | 'IMAGE' | 'VIDEO' | 'DOCUMENT' | 'AUDIO'

/**
 * messaging.message.created
 *
 * Payload follows the Message schema in openapi.yaml.
 */
export interface MessageCreatedPayload {
  messageId: string
  conversationId: string
  seq: number
  senderId: string
  type: ApiMessageType
  body: string
  clientMsgId: string
  createdAt: string
  replyTo?: string
  forwardedFrom?: string
}

export type MessageCreatedEvent =
  EventEnvelope<MessageCreatedPayload> & {
    type: 'messaging.message.created'
    producer: 'messaging'
  }

/**
 * messaging.message.deleted
 */
export interface MessageDeletedPayload {
  messageId: string
  conversationId: string
  deletedAt: string
}

export type MessageDeletedEvent =
  EventEnvelope<MessageDeletedPayload> & {
    type: 'messaging.message.deleted'
    producer: 'messaging'
  }

/**
 * messaging.reaction.added
 * messaging.reaction.removed
 */
export type ReactionCode =
  | 'like'
  | 'love'
  | 'haha'
  | 'wow'
  | 'sad'
  | 'angry'

export interface ReactionPayload {
  messageId: string
  conversationId: string
  userId: string
  code: ReactionCode
}

export type ReactionAddedEvent =
  EventEnvelope<ReactionPayload> & {
    type: 'messaging.reaction.added'
    producer: 'messaging'
  }

export type ReactionRemovedEvent =
  EventEnvelope<ReactionPayload> & {
    type: 'messaging.reaction.removed'
    producer: 'messaging'
  }

/**
 * messaging.read.updated
 */
export interface ReadUpdatedPayload {
  conversationId: string
  userId: string
  lastReadSeq: number
}

export type ReadUpdatedEvent =
  EventEnvelope<ReadUpdatedPayload> & {
    type: 'messaging.read.updated'
    producer: 'messaging'
  }

/**
 * Union of all supported messaging WebSocket events.
 */
export type MessagingEvent =
  | MessageCreatedEvent
  | MessageDeletedEvent
  | ReactionAddedEvent
  | ReactionRemovedEvent
  | ReadUpdatedEvent
