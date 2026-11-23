import { resolveMediaUrl } from './mediaService'
import type { Message } from './types/message'

export type GalleryMediaType = 'IMAGE' | 'VIDEO' | 'DOCUMENT' | 'AUDIO'

export interface GalleryItem {
  id: string
  type: GalleryMediaType
  url: string
  senderId: string
  sentAt: number
}

const GALLERY_TYPES: ReadonlySet<string> = new Set<GalleryMediaType>([
  'IMAGE',
  'VIDEO',
  'DOCUMENT',
  'AUDIO',
])

/** Lọc tin media (chưa thu hồi, có URL) từ lịch sử đã tải, mới nhất trước. */
export function buildMediaGallery(
  messages: Message[],
  filter?: GalleryMediaType,
): GalleryItem[] {
  return messages
    .filter(
      (message) =>
        !message.deleted &&
        GALLERY_TYPES.has(message.type) &&
        (!filter || message.type === filter),
    )
    .map((message) => ({
      id: message.id,
      type: message.type as GalleryMediaType,
      url: resolveMediaUrl(message.content),
      senderId: message.senderId,
      sentAt: message.sentAt,
    }))
    .filter((item) => item.url !== '')
    .sort((a, b) => b.sentAt - a.sentAt)
}
