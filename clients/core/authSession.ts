
import type { LoginResponse } from './types/auth.ts'

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

export function clearAuth(): void {
  sessionStorage.removeItem(AUTH_KEY)
}
