/**
 * deviceId bền trên thiết bị — gửi kèm login/verify (hợp đồng Identity).
 * Pattern: ^[A-Za-z0-9_-]{8,64}$
 */

const STORAGE_KEY = 'halozalo.deviceId'
const memory = { value: null as string | null }

/** Storage đồng bộ (mobile gắn cùng AuthStorage). */
export interface DeviceIdStorage {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
}

let customStorage: DeviceIdStorage | null = null

export function setDeviceIdStorage(storage: DeviceIdStorage | null): void {
  customStorage = storage
}

function webStorage(): Storage | DeviceIdStorage | null {
  if (customStorage) {
    return customStorage
  }
  try {
    if (typeof localStorage !== 'undefined') {
      return localStorage
    }
  } catch {
    // Private mode / RN may throw.
  }
  return null
}

function randomDeviceId(): string {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-'
  const bytes =
    typeof crypto !== 'undefined' && 'getRandomValues' in crypto
      ? crypto.getRandomValues(new Uint8Array(24))
      : Array.from({ length: 24 }, () => Math.floor(Math.random() * 256))
  let out = 'hz_'
  for (let i = 0; i < bytes.length && out.length < 32; i++) {
    out += alphabet[(bytes[i] as number) % alphabet.length]
  }
  return out
}

/** Lấy hoặc tạo deviceId bền (localStorage / memory). */
export function getOrCreateDeviceId(): string {
  if (memory.value && /^[A-Za-z0-9_-]{8,64}$/.test(memory.value)) {
    return memory.value
  }

  const storage = webStorage()
  const stored = storage?.getItem(STORAGE_KEY) ?? null
  if (stored && /^[A-Za-z0-9_-]{8,64}$/.test(stored)) {
    memory.value = stored
    return stored
  }

  const created = randomDeviceId()
  memory.value = created
  try {
    storage?.setItem(STORAGE_KEY, created)
  } catch {
    // Ignore quota / private mode.
  }
  return created
}

/** Nhãn thiết bị ngắn (optional deviceLabel). */
export function defaultDeviceLabel(): string {
  if (typeof navigator === 'undefined') {
    return 'App'
  }
  const ua = navigator.userAgent || ''
  if (/Mobile|Android|iPhone|iPad/i.test(ua)) {
    return 'Mobile'
  }
  if (/Chrome/i.test(ua)) {
    return 'Chrome'
  }
  if (/Firefox/i.test(ua)) {
    return 'Firefox'
  }
  if (/Safari/i.test(ua)) {
    return 'Safari'
  }
  return 'Web'
}
