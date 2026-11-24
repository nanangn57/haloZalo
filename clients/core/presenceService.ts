import { getApiHeaders } from './apiClient'
import { connectionConfig } from './connectionConfig'
import { isMissingEndpoint, toApiError } from './httpError'

const API_MESSAGE_BASE_URL = connectionConfig.messageBaseUrl

/** Server nhận tối đa bấy nhiêu id một lần (giống `/users/lookup`). */
const MAX_IDS_PER_CALL = 100

export interface UserPresence {
  userId: string
  online: boolean
  lastSeenAt?: string
}

interface RawPresence {
  userId?: string
  online?: boolean
  status?: string
  lastSeenAt?: string
  lastSeen?: string
}

function toPresence(raw: RawPresence, fallbackUserId?: string): UserPresence | null {
  const userId = raw.userId ?? fallbackUserId
  if (!userId) {
    return null
  }
  const online =
    typeof raw.online === 'boolean'
      ? raw.online
      : raw.status?.toLowerCase() === 'online'
  return {
    userId,
    online,
    lastSeenAt: raw.lastSeenAt ?? raw.lastSeen,
  }
}

// OpenAPI: `{ presences: [...] }`. Cũng chấp nhận `presence` / `users`, mảng trần, hoặc map.
function parsePresence(data: unknown): UserPresence[] {
  if (!data || typeof data !== 'object') {
    return []
  }

  const body = data as {
    presences?: unknown
    presence?: unknown
    users?: unknown
  }
  const list = Array.isArray(data)
    ? data
    : body.presences ?? body.presence ?? body.users

  if (Array.isArray(list)) {
    return list
      .map((item) => toPresence(item as RawPresence))
      .filter((item): item is UserPresence => item !== null)
  }

  if (!list || typeof list !== 'object' || Array.isArray(list)) {
    return []
  }

  return Object.entries(list as Record<string, unknown>)
    .map(([userId, value]) => {
      if (typeof value === 'boolean') {
        return { userId, online: value } as UserPresence
      }
      if (value && typeof value === 'object') {
        return toPresence(value as RawPresence, userId)
      }
      return null
    })
    .filter((item): item is UserPresence => item !== null)
}

/**
 * Trạng thái online của nhiều user: GET `/presence?ids=a,b,c`.
 * Endpoint chưa có (404/405/501) thì trả `{}` để UI hiện Offline mà không báo lỗi.
 * Id không có trong kết quả coi như offline.
 */
export async function getPresenceApi(
  ids: string[],
): Promise<Record<string, UserPresence>> {
  const unique = [...new Set(ids)].filter(Boolean)
  if (unique.length === 0) {
    return {}
  }

  const result: Record<string, UserPresence> = {}

  for (let i = 0; i < unique.length; i += MAX_IDS_PER_CALL) {
    const batch = unique.slice(i, i + MAX_IDS_PER_CALL)
    const params = new URLSearchParams({ ids: batch.join(',') })
    const response = await fetch(
      `${API_MESSAGE_BASE_URL}/presence?${params.toString()}`,
      { method: 'GET', headers: getApiHeaders() },
    )

    if (!response.ok) {
      const error = await toApiError(response, 'Không tải được trạng thái online')
      if (isMissingEndpoint(error)) {
        return {}
      }
      throw error
    }

    const data: unknown = await response.json().catch(() => null)
    parsePresence(data).forEach((item) => {
      result[item.userId] = item
    })
  }

  return result
}
