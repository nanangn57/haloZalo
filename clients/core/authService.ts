import type { LoginRequest, LoginResponse } from './types/auth'
import { connectionConfig } from './connectionConfig'

const API_BASE_URL = connectionConfig.identityBaseUrl

export async function loginApi(
  credentials: LoginRequest,
): Promise<LoginResponse> {
  const response = await fetch(`${API_BASE_URL}/auth/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(credentials),
  })

  const data = await readJsonBody(response)

  if (!response.ok) {
    const error = new Error(
      data?.error?.message ?? 'Đăng nhập thất bại',
    ) as Error & { code?: string; status?: number }

    error.code = data?.error?.code
    error.status = response.status

    throw error
  }

  return data as unknown as LoginResponse
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
}

export interface RegisterAccepted {
  email: string
  status: 'PENDING_VERIFICATION' | 'ACTIVE'
}

export async function registerApi(
  credentials: RegisterRequest,
): Promise<RegisterAccepted> {
  const response = await fetch(`${API_BASE_URL}/auth/register`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(credentials),
  })

  const data = await readJsonBody(response)

  if (!response.ok) {
    const error = new Error(
      data?.error?.message ?? 'Đăng ký thất bại',
    ) as Error & { code?: string; status?: number }

    error.code = data?.error?.code
    error.status = response.status
    throw error
  }

  return data as unknown as RegisterAccepted
}

export interface VerifyResponse {
  userId: string
  username: string
  email: string
  platformRole?: string
}

export async function verifyEmailApi(token: string): Promise<VerifyResponse> {
  const response = await fetch(
    `${API_BASE_URL}/auth/verify?token=${encodeURIComponent(token)}`,
  )

  const data = await readJsonBody(response)

  if (!response.ok) {
    const error = new Error(
      data?.error?.message ?? 'Xác minh email thất bại',
    ) as Error & { code?: string; status?: number }

    error.code = data?.error?.code
    error.status = response.status
    throw error
  }

  return data as unknown as VerifyResponse
}

async function readJsonBody(
  response: Response,
): Promise<{ error?: { code?: string; message?: string } } & Record<string, unknown>> {
  const text = await response.text()
  if (!text) {
    return {}
  }
  try {
    return JSON.parse(text) as {
      error?: { code?: string; message?: string }
    } & Record<string, unknown>
  } catch {
    throw new Error(
      response.ok
        ? 'Phản hồi máy chủ không phải JSON.'
        : `Máy chủ lỗi (${response.status}). Thử lại sau.`,
    )
  }
}
