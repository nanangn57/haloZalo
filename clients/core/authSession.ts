import type { LoginResponse } from './types/auth'

const AUTH_KEY = 'auth'

const memoryStore = new Map<string, string>()

function webStorage(): Storage | null {
  try {
    if (typeof sessionStorage !== 'undefined') {
      return sessionStorage
    }
  } catch {
    // Private mode / React Native may throw.
  }

  try {
    if (typeof localStorage !== 'undefined') {
      return localStorage
    }
  } catch {
    // Ignore.
  }

  return null
}

function readRaw(): string | null {
  const storage = webStorage()
  if (storage) {
    return storage.getItem(AUTH_KEY)
  }
  return memoryStore.get(AUTH_KEY) ?? null
}

function writeRaw(value: string | null): void {
  const storage = webStorage()
  if (storage) {
    if (value === null) {
      storage.removeItem(AUTH_KEY)
    } else {
      storage.setItem(AUTH_KEY, value)
    }
    return
  }

  if (value === null) {
    memoryStore.delete(AUTH_KEY)
  } else {
    memoryStore.set(AUTH_KEY, value)
  }
}

export function saveAuth(auth: LoginResponse): void {
  writeRaw(JSON.stringify(auth))
}

export function getAuth(): LoginResponse | null {
  const value = readRaw()

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
  writeRaw(null)
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

export function logout(): void {
  clearAuth()
}

export function updateStoredUser(user: LoginResponse['user']): void {
  const current = getAuth()
  if (!current) {
    return
  }
  saveAuth({ ...current, user })
}

export function isPlatformAdmin(): boolean {
  return getAuth()?.user?.platformRole === 'ADMIN'
}
