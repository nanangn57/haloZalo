import { getAccessToken, requireUserId } from './authSession'
import { connectionConfig } from './connectionConfig'
import { getApiHeaders } from './apiClient'

export type MediaUploadType = 'IMAGE' | 'DOCUMENT' | 'VIDEO'

export interface MediaUploadResult {
  mediaId: string
  url: string
  type: MediaUploadType
  contentType: string
  fileName: string
  size: number
}

/**
 * Turns relative `/media/...` (or bare media id) into an absolute URL for Image/Video.
 * Absolute http(s) URLs are returned unchanged.
 */
export function resolveMediaUrl(content: string | null | undefined): string {
  const raw = (content ?? '').trim()
  if (!raw) {
    return ''
  }
  if (/^https?:\/\//i.test(raw) || /^blob:/i.test(raw) || /^data:/i.test(raw)) {
    return raw
  }
  const base = connectionConfig.messageBaseUrl.replace(/\/$/, '')
  if (raw.startsWith('/')) {
    return `${base}${raw}`
  }
  if (raw.startsWith('media/')) {
    return `${base}/${raw}`
  }
  return `${base}/media/${raw}`
}

export async function uploadMediaApi(
  file: Blob,
  type: MediaUploadType,
  options?: {
    fileName?: string
    conversationId?: string
  },
): Promise<MediaUploadResult> {
  const form = new FormData()
  form.append('file', file, options?.fileName ?? 'upload.bin')
  form.append('type', type)
  if (options?.conversationId) {
    form.append('conversationId', options.conversationId)
  }

  const headers: Record<string, string> = {}
  if (connectionConfig.messageHttpAuthMode === 'TOKEN') {
    const token = getAccessToken()
    if (!token) {
      throw new Error('Bạn cần đăng nhập để upload.')
    }
    headers.Authorization = `Bearer ${token}`
  } else {
    headers['X-User-Id'] = requireUserId()
  }

  const response = await fetch(
    `${connectionConfig.messageBaseUrl}/media/uploads`,
    {
      method: 'POST',
      headers,
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

  return (await response.json()) as MediaUploadResult
}

/** Headers helper kept for callers that already build multipart themselves. */
export function mediaAuthHeaders(): HeadersInit {
  return getApiHeaders(false)
}
