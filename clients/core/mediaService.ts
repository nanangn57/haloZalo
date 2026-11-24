import { getAccessToken, requireUserId } from './authSession'
import { connectionConfig } from './connectionConfig'
import { getApiHeaders } from './apiClient'
import {
  assertMediaWithinLimit,
  guessMimeType,
  mediaMaxLabel,
  resolveMediaUrlWithBase,
  type MediaUploadType,
} from './mediaMime'

export type { MediaUploadType } from './mediaMime'
export {
  assertMediaWithinLimit,
  guessMimeType,
  mediaMaxLabel,
  mediaMessageBody,
  MEDIA_MAX_BYTES,
} from './mediaMime'

export interface MediaUploadResult {
  mediaId: string
  url: string
  type: MediaUploadType
  contentType: string
  fileName: string
  size: number
}

/** React Native multipart part — `fetch(uri).blob()` often loses MIME and breaks server checks. */
export interface NativeUploadFile {
  uri: string
  name: string
  type: string
}

export type UploadFileSource = Blob | NativeUploadFile

/** Resolve media message body against the active messaging API base. */
export function resolveMediaUrl(content: string | null | undefined): string {
  return resolveMediaUrlWithBase(content, connectionConfig.messageBaseUrl)
}

function isNativeUpload(file: UploadFileSource): file is NativeUploadFile {
  return (
    typeof file === 'object' &&
    file !== null &&
    'uri' in file &&
    typeof (file as NativeUploadFile).uri === 'string'
  )
}

/** Expo web / browser — not Hermes RN. Object `{uri,name,type}` becomes "[object Object]" in FormData. */
function isReactNativeRuntime(): boolean {
  return (
    typeof navigator !== 'undefined' &&
    (navigator as { product?: string }).product === 'ReactNative'
  )
}

function blobWithType(file: Blob, contentType: string): Blob {
  if (file.type && file.type !== 'application/octet-stream') {
    return file
  }
  return new Blob([file], { type: contentType })
}

async function appendNativeFilePart(
  form: FormData,
  file: NativeUploadFile,
  fileName: string,
  contentType: string,
): Promise<void> {
  const mime = contentType || guessMimeType(fileName, 'DOCUMENT')
  const uri = file.uri
  // Expo web + blob:/http URIs: must append a real Blob or Spring sees no "file" part.
  const mustUseBlob =
    !isReactNativeRuntime() ||
    uri.startsWith('blob:') ||
    /^https?:\/\//i.test(uri)
  if (mustUseBlob) {
    const res = await fetch(uri)
    if (!res.ok) {
      throw new Error('Cannot read selected media file')
    }
    form.append('file', blobWithType(await res.blob(), mime), fileName)
    return
  }
  // React Native native FormData file part (file:// / content:// / ph://).
  form.append('file', {
    uri,
    name: fileName,
    type: mime,
  } as unknown as Blob)
}

/**
 * RN `fetch` + multipart file parts is flaky; XHR reliably streams {uri,name,type}.
 * Browsers keep using fetch.
 */
function postMultipart(
  url: string,
  form: FormData,
  headers: Record<string, string>,
): Promise<Response> {
  if (!isReactNativeRuntime() || typeof XMLHttpRequest === 'undefined') {
    return fetch(url, { method: 'POST', headers, body: form })
  }
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', url)
    for (const [key, value] of Object.entries(headers)) {
      xhr.setRequestHeader(key, value)
    }
    xhr.onload = () => {
      resolve(
        new Response(xhr.responseText, {
          status: xhr.status,
          statusText: xhr.statusText,
        }),
      )
    }
    xhr.onerror = () => reject(new Error('Network request failed'))
    xhr.send(form)
  })
}

export async function uploadMediaApi(
  file: UploadFileSource,
  type: MediaUploadType,
  options?: {
    fileName?: string
    conversationId?: string
    contentType?: string
    /** Known size from picker/File; checked before upload. */
    size?: number
  },
): Promise<MediaUploadResult> {
  const fileName =
    options?.fileName ??
    (isNativeUpload(file) ? file.name : 'upload.bin')
  const contentType =
    options?.contentType ??
    (isNativeUpload(file)
      ? file.type
      : file.type || guessMimeType(fileName, type))

  const knownSize =
    options?.size ??
    (!isNativeUpload(file) && typeof file.size === 'number' ? file.size : undefined)
  assertMediaWithinLimit(type, knownSize)

  const form = new FormData()
  if (isNativeUpload(file)) {
    await appendNativeFilePart(
      form,
      file,
      fileName,
      contentType || guessMimeType(fileName, type),
    )
  } else {
    form.append(
      'file',
      blobWithType(file, contentType || guessMimeType(fileName, type)),
      fileName,
    )
  }
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

  const response = await postMultipart(
    `${connectionConfig.messageBaseUrl}/media/uploads`,
    form,
    headers,
  )

  if (!response.ok) {
    if (response.status === 413) {
      throw new Error(
        `${type} is too large. Max ${mediaMaxLabel(type)}.`,
      )
    }
    const rawBody = await response.text().catch(() => '')
    let message = `Upload failed (${response.status})`
    try {
      const data = JSON.parse(rawBody) as { error?: { message?: string } }
      if (data?.error?.message) {
        message = data.error.message
      }
    } catch {
      if (/file.*(required|not present)/i.test(rawBody)) {
        message = 'Upload failed: file part missing (reload Expo and retry).'
      }
    }
    throw new Error(message)
  }

  return (await response.json()) as MediaUploadResult
}

/** Headers helper kept for callers that already build multipart themselves. */
export function mediaAuthHeaders(): HeadersInit {
  return getApiHeaders(false)
}
