import { connectionConfig } from './connectionConfig'
import { getApiHeaders } from './apiClient'
import type { MediaKind, MessageMedia } from './types/message'

// Gửi file hai bước (openapi.yaml, tag Media):
//   1. uploadMediaApi(conversationId, file) -> MessageMedia có mediaId và kind
//   2. sendMessageApi(conversationId, { type: media.kind, mediaId: media.mediaId, clientMsgId })
// Ảnh 10MB, video 25MB, tài liệu 10MB; vượt thì server trả 413.

export type MediaUploadType = MediaKind

/**
 * Link media do server ký, có hạn, dạng `/media/{id}?expires=&sig=`, tương đối với base URL của messaging.
 * Nối thẳng vào sau base thay vì `new URL(url, base)`, để giữ tiền tố như `/api/messaging` khi đi qua gateway.
 * Link hết hạn (403) thì tải lại tin là có link mới. URL tuyệt đối, blob: và data: giữ nguyên.
 */
export function resolveMediaUrl(url: string | null | undefined): string {
  const raw = (url ?? '').trim()
  if (!raw) {
    return ''
  }
  if (/^(https?:|blob:|data:)/i.test(raw)) {
    return raw
  }
  const base = connectionConfig.messageBaseUrl.replace(/\/+$/, '')
  return `${base}${raw.startsWith('/') ? '' : '/'}${raw}`
}

export function mapMedia(media: MessageMedia): MessageMedia {
  return { ...media, url: resolveMediaUrl(media.url) }
}

/**
 * Bước một của gửi file. Chỉ thành viên hội thoại upload được. Server tự suy ra kind từ MIME,
 * hoặc từ đuôi file khi Blob không có type (React Native), nên luôn truyền fileName có đuôi.
 */
export async function uploadMediaApi(
  conversationId: string,
  file: Blob,
  fileName?: string,
): Promise<MessageMedia> {
  // File does not exist in React Native, so check before instanceof.
  const name =
    fileName ??
    (typeof File !== 'undefined' && file instanceof File ? file.name : 'upload.bin')
  const form = new FormData()
  form.append('file', file, name)

  // Không đặt Content-Type: runtime tự thêm boundary cho multipart.
  const response = await fetch(
    `${connectionConfig.messageBaseUrl}/conversations/${encodeURIComponent(conversationId)}/media`,
    {
      method: 'POST',
      headers: getApiHeaders(),
      body: form,
    },
  )

  if (!response.ok) {
    const data = (await response.json().catch(() => null)) as {
      error?: { message?: string }
    } | null
    throw new Error(
      data?.error?.message ?? `Upload failed (${response.status})`,
    )
  }

  return mapMedia((await response.json()) as MessageMedia)
}
