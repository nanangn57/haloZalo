import { logoutApi, verifyEmailApi } from './authService'
import { connectionConfig } from './connectionConfig'
import { requestJson } from './httpError'
import type { AuthUser, LoginResponse, TokenResponse } from './types/auth'

const AUTH_KEY = 'auth'

const memoryStore = new Map<string, string>()

/** Storage đồng bộ tối thiểu. Mobile có thể gắn AsyncStorage đã nạp sẵn qua `setAuthStorage`. */
export interface AuthStorage {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
  removeItem(key: string): void
}

let customStorage: AuthStorage | null = null

export function setAuthStorage(storage: AuthStorage | null): void {
  customStorage = storage
}

interface StoredAuth extends TokenResponse {
  /** Epoch ms lúc lưu, để tính hết hạn từ `expiresIn`. */
  issuedAt?: number
}

function webStorage(): Storage | AuthStorage | null {
  if (customStorage) {
    return customStorage
  }

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
  const stored: StoredAuth = { ...auth, issuedAt: Date.now() }
  writeRaw(JSON.stringify(stored))
}

function getStoredAuth(): StoredAuth | null {
  const value = readRaw()

  if (!value) return null

  try {
    return JSON.parse(value) as StoredAuth
  } catch {
    return null
  }
}

export function getAuth(): LoginResponse | null {
  return getStoredAuth()
}

/** Token đã quá `expiresIn` kể từ lúc lưu. Không có `issuedAt` thì coi như còn hạn (server vẫn kiểm tra). */
export function isSessionExpired(): boolean {
  const stored = getStoredAuth()
  if (!stored?.issuedAt || !stored.expiresIn) {
    return false
  }
  return Date.now() >= stored.issuedAt + stored.expiresIn * 1000
}

/** Có token còn hạn trong storage (đồng bộ, không gọi server). */
export function hasSession(): boolean {
  const stored = getStoredAuth()
  return Boolean(stored?.accessToken && stored.user?.userId) && !isSessionExpired()
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

/**
 * Đăng xuất: gọi server xoá session rồi mới xoá token local.
 * Lỗi mạng hoặc 401 (session đã chết) vẫn xoá local để user không bị kẹt.
 */
export async function logout(): Promise<void> {
  const token = getAccessToken()
  try {
    if (token) {
      await logoutApi(token)
    }
  } catch {
    // Server không với tới được hoặc session đã hết hạn: vẫn xoá local.
  } finally {
    clearAuth()
  }
}

/** Verify email → server cấp TokenResponse → lưu session. */
export async function verifyAndSaveSession(
  verifyToken: string,
): Promise<TokenResponse> {
  const auth = await verifyEmailApi(verifyToken)
  if (!auth.accessToken) {
    throw new Error('Phản hồi xác minh không có access token.')
  }
  saveAuth(auth)
  return auth
}

/**
 * Khôi phục session đã lưu khi mở app. Trả auth nếu token còn dùng được.
 * - Hết hạn local hoặc server trả 401 → xoá, trả null.
 * - Lỗi mạng → giữ session đã lưu (offline vẫn vào được app).
 */
export async function restoreSession(): Promise<LoginResponse | null> {
  if (!hasSession()) {
    clearAuth()
    return null
  }

  try {
    const profile = await requestJson<AuthUser>(
      `${connectionConfig.identityBaseUrl}/me`,
      { method: 'GET', headers: { Authorization: `Bearer ${getAccessToken()}` } },
      'Cannot load profile',
    )
    updateStoredUser(profile)
  } catch (error) {
    const status = (error as { status?: number } | null)?.status
    if (status === 401) {
      clearAuth()
      return null
    }
  }

  return getAuth()
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
