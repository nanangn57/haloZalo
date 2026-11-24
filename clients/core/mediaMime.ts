export type MediaUploadType = 'IMAGE' | 'DOCUMENT' | 'VIDEO' | 'AUDIO'

/** Matches MediaController limits (bytes). */
export const MEDIA_MAX_BYTES: Record<MediaUploadType, number> = {
  IMAGE: 5 * 1024 * 1024,
  AUDIO: 5 * 1024 * 1024,
  DOCUMENT: 10 * 1024 * 1024,
  VIDEO: 20 * 1024 * 1024,
}

function extensionOf(fileName: string): string {
  const base = fileName.split(/[\\/]/).pop() ?? ''
  const dot = base.lastIndexOf('.')
  return dot >= 0 ? base.slice(dot + 1).toLowerCase() : ''
}

/** Guess MIME when the browser/OS leaves File.type empty (common for gallery/MOV). */
export function guessMimeType(
  fileName: string,
  fallbackType: MediaUploadType,
): string {
  switch (extensionOf(fileName)) {
    case 'jpg':
    case 'jpeg':
      return 'image/jpeg'
    case 'png':
      return 'image/png'
    case 'gif':
      return 'image/gif'
    case 'webp':
      return 'image/webp'
    case 'heic':
      return 'image/heic'
    case 'heif':
      return 'image/heif'
    case 'mp4':
    case 'm4v':
      return 'video/mp4'
    case 'webm':
      return 'video/webm'
    case 'mov':
      return 'video/quicktime'
    case 'avi':
      return 'video/x-msvideo'
    case 'mpeg':
    case 'mpg':
      return 'video/mpeg'
    case '3gp':
    case '3gpp':
      return fallbackType === 'AUDIO' ? 'audio/3gpp' : 'video/3gpp'
    case 'm4a':
      return 'audio/mp4'
    case 'mp3':
      return 'audio/mpeg'
    case 'aac':
      return 'audio/aac'
    case 'wav':
      return 'audio/wav'
    case 'ogg':
      return 'audio/ogg'
    case 'pdf':
      return 'application/pdf'
    default:
      switch (fallbackType) {
        case 'IMAGE':
          return 'image/jpeg'
        case 'VIDEO':
          return 'video/mp4'
        case 'AUDIO':
          return 'audio/mp4'
        default:
          return 'application/octet-stream'
      }
  }
}

export function mediaMaxLabel(type: MediaUploadType): string {
  return `${Math.round(MEDIA_MAX_BYTES[type] / (1024 * 1024))}MB`
}

/** Throws when size is over the server limit so UI can show a clear message before upload. */
export function assertMediaWithinLimit(
  type: MediaUploadType,
  sizeBytes: number | null | undefined,
): void {
  if (sizeBytes == null || !Number.isFinite(sizeBytes) || sizeBytes <= 0) {
    return
  }
  const max = MEDIA_MAX_BYTES[type]
  if (sizeBytes > max) {
    const mb = (sizeBytes / (1024 * 1024)).toFixed(1)
    throw new Error(
      `${type} is too large (${mb}MB). Max ${mediaMaxLabel(type)}.`,
    )
  }
}

/**
 * Path-only body for IMAGE/VIDEO/AUDIO/DOCUMENT messages.
 * Upload responses advertise an absolute public URL (often localhost in local Docker);
 * phones on LAN cannot fetch that host, so store `/media/{id}` and resolve at render time.
 */
export function mediaMessageBody(uploadUrl: string | null | undefined): string {
  const raw = (uploadUrl ?? '').trim()
  if (!raw) {
    return ''
  }
  try {
    if (/^https?:\/\//i.test(raw)) {
      const pathname = new URL(raw).pathname
      const marker = pathname.indexOf('/media/')
      if (marker >= 0) {
        const id = pathname.slice(marker + '/media/'.length).split('/')[0]
        if (id && /^[A-Za-z0-9._-]+$/.test(id)) {
          return `/media/${id}`
        }
      }
      return raw
    }
  } catch {
    /* keep raw */
  }
  if (raw.startsWith('/media/')) {
    return raw.split(/[?#]/)[0]
  }
  if (/^[A-Za-z0-9._-]+$/.test(raw)) {
    return `/media/${raw}`
  }
  return raw
}

function isLoopbackHost(hostname: string): boolean {
  return hostname === 'localhost' || hostname === '127.0.0.1' || hostname === '::1'
}

/**
 * Join message media content with the active API base.
 * Loopback absolute URLs from local Docker are rewritten so Expo on LAN can play them.
 */
export function resolveMediaUrlWithBase(
  content: string | null | undefined,
  messageBaseUrl: string,
): string {
  const raw = (content ?? '').trim()
  if (!raw) {
    return ''
  }
  if (/^blob:/i.test(raw) || /^data:/i.test(raw)) {
    return raw
  }
  const base = messageBaseUrl.replace(/\/$/, '')
  if (/^https?:\/\//i.test(raw)) {
    try {
      const absolute = new URL(raw)
      const marker = absolute.pathname.indexOf('/media/')
      if (marker >= 0 && isLoopbackHost(absolute.hostname)) {
        return `${base}${absolute.pathname.slice(marker)}${absolute.search}`
      }
    } catch {
      return raw
    }
    return raw
  }
  if (raw.startsWith('/')) {
    return `${base}${raw}`
  }
  if (raw.startsWith('media/')) {
    return `${base}/${raw}`
  }
  return `${base}/media/${raw}`
}
