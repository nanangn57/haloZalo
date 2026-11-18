
import type { LoginResponse } from './types/auth'

const AUTH_KEY = 'auth'

export function saveAuth(auth: LoginResponse): void {
  sessionStorage.setItem(AUTH_KEY, JSON.stringify(auth))
}

export function getAuth(): LoginResponse | null {
  const value = sessionStorage.getItem(AUTH_KEY)

  if (!value) return null

  try {
    return JSON.parse(value) as LoginResponse
  } catch {
    return null
  }
}

export function getAccessToken(): string | null {
  return getAuth()?.accessToken ?? null
}

export function getUserId(): string | null {
  return getAuth()?.user?.userId ?? null
}

export function clearAuth(): void {
  sessionStorage.removeItem(AUTH_KEY)
}

export function requireAccessToken(): string {
  const token = getAccessToken()

  if (!token) {
    throw new Error('Bạn cần đăng nhập để thực hiện thao tác này.')
  }

  return token
}

export function requireUserId(): string {
  const userId = getUserId()

  if (!userId) {
    throw new Error('Không tìm thấy userId. Vui lòng đăng nhập lại.')
  }

  return userId
}