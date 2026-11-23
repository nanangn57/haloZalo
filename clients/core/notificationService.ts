import { getApiHeaders } from './apiClient'
import { connectionConfig } from './connectionConfig'
import { isMissingEndpoint, toApiError } from './httpError'

/** Prefs nằm ở messaging (`/me/notifications`), cùng process monolith nhưng base URL messaging. */
const API_BASE_URL = connectionConfig.messageBaseUrl

export interface NotificationPrefs {
  pushEnabled: boolean
  previewEnabled: boolean
}

export type NotificationPrefsPatch = Partial<NotificationPrefs>

export const DEFAULT_NOTIFICATION_PREFS: NotificationPrefs = {
  pushEnabled: true,
  previewEnabled: true,
}

function normalize(data: unknown): NotificationPrefs {
  const raw = (data && typeof data === 'object' ? data : {}) as Partial<NotificationPrefs>
  return {
    pushEnabled: raw.pushEnabled ?? DEFAULT_NOTIFICATION_PREFS.pushEnabled,
    previewEnabled: raw.previewEnabled ?? DEFAULT_NOTIFICATION_PREFS.previewEnabled,
  }
}

/**
 * GET `/me/notifications`.
 * Trả `null` khi server chưa có endpoint (404/405/501): UI chỉ nhớ lựa chọn trên thiết bị.
 */
export async function getNotificationPrefsApi(): Promise<NotificationPrefs | null> {
  const response = await fetch(`${API_BASE_URL}/me/notifications`, {
    method: 'GET',
    headers: getApiHeaders(),
  })

  if (!response.ok) {
    const error = await toApiError(response, 'Không tải được cài đặt thông báo')
    if (isMissingEndpoint(error)) {
      return null
    }
    throw error
  }

  return normalize(await response.json().catch(() => null))
}

/**
 * PATCH `/me/notifications`, chỉ field có mặt mới đổi.
 * Trả prefs sau khi sửa, hoặc `null` khi server chưa có endpoint.
 */
export async function updateNotificationPrefsApi(
  patch: NotificationPrefsPatch,
): Promise<NotificationPrefs | null> {
  const response = await fetch(`${API_BASE_URL}/me/notifications`, {
    method: 'PATCH',
    headers: getApiHeaders(true),
    body: JSON.stringify(patch),
  })

  if (!response.ok) {
    const error = await toApiError(response, 'Không cập nhật được cài đặt thông báo')
    if (isMissingEndpoint(error)) {
      return null
    }
    throw error
  }

  return normalize(await response.json().catch(() => null))
}
