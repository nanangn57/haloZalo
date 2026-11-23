export interface ApiError extends Error {
  code?: string
  status?: number
}

export function createApiError(
  message: string,
  status?: number,
  code?: string,
): ApiError {
  const error = new Error(message) as ApiError
  error.status = status
  error.code = code
  return error
}

/** 404 / 405 / 501: endpoint chưa có ở server (messaging có thể đang bắt kịp hợp đồng). */
export function isMissingEndpoint(error: unknown): boolean {
  const status = (error as ApiError | null)?.status
  return status === 404 || status === 405 || status === 501
}

async function readBody(response: Response): Promise<unknown> {
  const text = await response.text().catch(() => '')
  if (!text) {
    return null
  }
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

export async function toApiError(
  response: Response,
  fallback: string,
): Promise<ApiError> {
  const data = (await readBody(response)) as {
    error?: { code?: string; message?: string }
  } | null
  return createApiError(
    data?.error?.message ?? `${fallback} (${response.status})`,
    response.status,
    data?.error?.code,
  )
}

/** Gọi fetch, ném ApiError khi không ok, trả JSON (hoặc undefined khi 204/rỗng). */
export async function requestJson<T>(
  input: string,
  init: RequestInit,
  fallback: string,
): Promise<T> {
  const response = await fetch(input, init)
  if (!response.ok) {
    throw await toApiError(response, fallback)
  }
  const data = await readBody(response)
  return data as T
}
