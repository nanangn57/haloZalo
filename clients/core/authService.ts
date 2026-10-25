import type { LoginRequest, LoginResponse } from './types/auth.ts'

const API_BASE_URL = "http://localhost:8080"

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

  const data = await response.json()

  if (!response.ok) {
    const error = new Error(
      data?.error?.message ?? 'Đăng nhập thất bại',
    ) as Error & { code?: string; status?: number }

    error.code = data?.error?.code
    error.status = response.status

    throw error
  }

  return data as LoginResponse
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
}

export interface RegisterAccepted {
  email: string
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

  const data = await response.json()

  if (!response.ok) {
    const error = new Error(
      data?.error?.message ?? 'Đăng ký thất bại',
    ) as Error & { code?: string; status?: number }

    error.code = data?.error?.code
    error.status = response.status
    throw error
  }

  return data as RegisterAccepted
}
