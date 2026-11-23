import AsyncStorage from '@react-native-async-storage/async-storage';
import type { AuthStorage } from '@core/authSession';

/**
 * `AuthStorage` của core cần getItem/setItem đồng bộ, còn AsyncStorage thì bất đồng bộ.
 * Cầu nối: nạp toàn bộ key của app vào Map lúc khởi động (`hydrateAuthStorage`),
 * sau đó đọc từ Map, còn ghi thì cập nhật Map ngay và đẩy xuống AsyncStorage (không chờ).
 */
const KEY_PREFIX = 'halozalo:';

const cache = new Map<string, string>();
let hydrated = false;
let hydratePromise: Promise<void> | null = null;

function persistedKey(key: string): string {
  return `${KEY_PREFIX}${key}`;
}

async function loadFromDisk(): Promise<void> {
  try {
    const allKeys = await AsyncStorage.getAllKeys();
    const appKeys = allKeys.filter((key) => key.startsWith(KEY_PREFIX));
    if (appKeys.length === 0) {
      return;
    }
    const entries = await AsyncStorage.multiGet(appKeys);
    entries.forEach(([key, value]) => {
      // Ghi trước khi hydrate xong (đăng nhập sớm) thắng giá trị cũ trên đĩa.
      const plainKey = key.slice(KEY_PREFIX.length);
      if (value !== null && !cache.has(plainKey)) {
        cache.set(plainKey, value);
      }
    });
  } catch {
    // Đọc đĩa lỗi: coi như chưa có session, user đăng nhập lại.
  }
}

/** Nạp AsyncStorage vào bộ nhớ. Gọi nhiều lần vẫn chỉ đọc đĩa một lần. */
export function hydrateAuthStorage(): Promise<void> {
  if (!hydratePromise) {
    hydratePromise = loadFromDisk().then(() => {
      hydrated = true;
    });
  }
  return hydratePromise;
}

export function isAuthStorageHydrated(): boolean {
  return hydrated;
}

export const asyncAuthStorage: AuthStorage = {
  getItem(key) {
    return cache.get(key) ?? null;
  },
  setItem(key, value) {
    cache.set(key, value);
    void AsyncStorage.setItem(persistedKey(key), value).catch(() => undefined);
  },
  removeItem(key) {
    cache.delete(key);
    void AsyncStorage.removeItem(persistedKey(key)).catch(() => undefined);
  },
};
