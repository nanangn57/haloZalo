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
 * RN `fetch` + multipart file parts is flaky; XHR streams {uri,name,type}.
 * Prefer expo-file-system for native files when available (large video over cellular).
 */
function postMultipart(
  url: string,
  form: FormData,
  headers: Record<string, string>,
  onProgress?: (ratio: number) => void,
): Promise<Response> {
  if (typeof XMLHttpRequest === 'undefined') {
    return fetch(url, { method: 'POST', headers, body: form })
  }
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', url)
    xhr.timeout = 180_000
    for (const [key, value] of Object.entries(headers)) {
      xhr.setRequestHeader(key, value)
    }
    if (onProgress && xhr.upload) {
      xhr.upload.onprogress = (event) => {
        if (!event.lengthComputable || event.total <= 0) return
        onProgress(Math.min(1, event.loaded / event.total))
      }
    }
    xhr.onload = () => {
      resolve(
        new Response(xhr.responseText, {
          status: xhr.status,
          statusText: xhr.statusText,
        }),
      )
    }
    xhr.ontimeout = () =>
      reject(
        new Error(
          'Upload timed out. Use Wi‑Fi or a shorter video (max 20MB).',
        ),
      )
    xhr.onerror = () =>
      reject(
        new Error(
          'Network request failed while uploading. Check connection / try a shorter video (max 20MB).',
        ),
      )
    xhr.send(form)
  })
}

type ExpoLegacyFileSystem = {
  FileSystemUploadType: { MULTIPART: number }
  FileSystemSessionType: { FOREGROUND: number }
  getInfoAsync?: (
    fileUri: string,
    options?: { size?: boolean },
  ) => Promise<{ exists: boolean; size?: number; isDirectory?: boolean }>
  createUploadTask: (
    url: string,
    fileUri: string,
    options: Record<string, unknown>,
    callback?: (data: { totalBytesSent: number; totalBytesExpectedToSend: number }) => void,
  ) => { uploadAsync: () => Promise<{ status: number; body: string }> }
}

/** Size from disk when the picker omits fileSize (common on Android / after URI rewrite). */
async function resolveNativeFileBytes(uri: string): Promise<number | undefined> {
  const FileSystem = loadExpoFileSystem()
  if (!FileSystem?.getInfoAsync) {
    return undefined
  }
  try {
    const info = await FileSystem.getInfoAsync(uri, { size: true })
    if (info.exists && !info.isDirectory && typeof info.size === 'number' && info.size > 0) {
      return info.size
    }
  } catch {
    /* ignore */
  }
  return undefined
}

function loadExpoFileSystem(): ExpoLegacyFileSystem | null {
  if (!isReactNativeRuntime()) {
    return null
  }
  try {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    return require('expo-file-system/legacy') as ExpoLegacyFileSystem
  } catch {
    return null
  }
}

async function uploadNativeWithFileSystem(
  file: NativeUploadFile,
  uploadUrl: string,
  headers: Record<string, string>,
  type: MediaUploadType,
  fileName: string,
  contentType: string,
  conversationId: string | undefined,
  onProgress?: (ratio: number) => void,
): Promise<MediaUploadResult | null> {
  const FileSystem = loadExpoFileSystem()
  if (!FileSystem?.createUploadTask) {
    return null
  }
  const parameters: Record<string, string> = { type }
  if (conversationId) {
    parameters.conversationId = conversationId
  }
  const task = FileSystem.createUploadTask(
    uploadUrl,
    file.uri,
    {
      httpMethod: 'POST',
      uploadType: FileSystem.FileSystemUploadType.MULTIPART,
      fieldName: 'file',
      mimeType: contentType,
      parameters,
      headers,
      sessionType: FileSystem.FileSystemSessionType.FOREGROUND,
    },
    onProgress
      ? (data) => {
          if (data.totalBytesExpectedToSend > 0) {
            onProgress(
              Math.min(1, data.totalBytesSent / data.totalBytesExpectedToSend),
            )
          }
        }
      : undefined,
  )
  const result = await task.uploadAsync()
  if (result.status < 200 || result.status >= 300) {
    if (result.status === 413) {
      throw new Error(`${type} is too large. Max ${mediaMaxLabel(type)}.`)
    }
    let message = `Upload failed (${result.status})`
    try {
      const data = JSON.parse(result.body) as { error?: { message?: string } }
      if (data?.error?.message) {
        message = data.error.message
      }
    } catch {
      /* keep status message */
    }
    throw new Error(message)
  }
  return JSON.parse(result.body) as MediaUploadResult
}

function parseUploadFailure(response: Response, type: MediaUploadType): Promise<never> {
  return (async () => {
    if (response.status === 413) {
      throw new Error(`${type} is too large. Max ${mediaMaxLabel(type)}.`)
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
  })()
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
    /** 0..1 while bytes are sent (XHR / FileSystem). */
    onProgress?: (ratio: number) => void
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

  let knownSize =
    options?.size ??
    (!isNativeUpload(file) && typeof file.size === 'number' ? file.size : undefined)
  if (
    (knownSize == null || !Number.isFinite(knownSize) || knownSize <= 0) &&
    isNativeUpload(file)
  ) {
    knownSize = await resolveNativeFileBytes(file.uri)
  }
  if (knownSize == null || !Number.isFinite(knownSize) || knownSize <= 0) {
    throw new Error(
      `Cannot determine ${type} size before upload. Pick the file again.`,
    )
  }
  assertMediaWithinLimit(type, knownSize)

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

  const uploadUrl = `${connectionConfig.messageBaseUrl}/media/uploads`
  const mime = contentType || guessMimeType(fileName, type)

  if (isNativeUpload(file) && isReactNativeRuntime()) {
    try {
      const viaFs = await uploadNativeWithFileSystem(
        file,
        uploadUrl,
        headers,
        type,
        fileName,
        mime,
        options?.conversationId,
        options?.onProgress,
      )
      if (viaFs) {
        return viaFs
      }
    } catch (err) {
      // Fall through to FormData XHR once; keep FileSystem's clear size errors.
      if (err instanceof Error && /too large|Upload failed \(/i.test(err.message)) {
        throw err
      }
      console.warn('expo-file-system upload failed, falling back to XHR', err)
    }
  }

  const form = new FormData()
  if (isNativeUpload(file)) {
    await appendNativeFilePart(form, file, fileName, mime)
  } else {
    form.append('file', blobWithType(file, mime), fileName)
  }
  form.append('type', type)
  if (options?.conversationId) {
    form.append('conversationId', options.conversationId)
  }

  const response = await postMultipart(
    uploadUrl,
    form,
    headers,
    options?.onProgress,
  )

  if (!response.ok) {
    await parseUploadFailure(response, type)
  }

  return (await response.json()) as MediaUploadResult
}

/** Headers helper kept for callers that already build multipart themselves. */
export function mediaAuthHeaders(): HeadersInit {
  return getApiHeaders(false)
}
