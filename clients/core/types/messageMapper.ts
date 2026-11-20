import type { Message } from '../types/message'
import type { MessageCreatedPayload } from '../types/event'
import { mapApiMessage } from '../messageService'

// Payload của messaging.message.created là Message của openapi.yaml,
// nên map giống hệt tin lấy qua HTTP, kể cả resolve link media.
export function mapEventToMessage(
  payload: MessageCreatedPayload
): Message {
  return mapApiMessage(payload)
}
