
import type { AuthUser, ApiErrorResponse } from './types/auth'
import { getAuth } from './authSession'
import { getIdentityApiHeaders } from './apiClient'
import { connectionConfig } from './connectionConfig'

const API_BASE_URL = connectionConfig.identityBaseUrl

export async function findUserApi(
  query: string,
): Promise<AuthUser> {
  const auth = getAuth()

  if (!auth?.accessToken) {
    throw new Error('Bạn cần đăng nhập để tìm người dùng.')
  }

  const trimmedQuery = query.trim()

  if (!trimmedQuery) {
    throw new Error('Vui lòng nhập username hoặc email.')
  }

  const params = new URLSearchParams({ q: trimmedQuery })

  const response = await fetch(
    `${API_BASE_URL}/users?${params.toString()}`,
    {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${auth.accessToken}`,
      },
    },
  )

  const data: unknown = await response.json().catch(() => null)

  if (!response.ok) {
    const errorData = data as ApiErrorResponse | null
    throw new Error(
      errorData?.error?.message ?? 'Không tìm thấy người dùng.',
    )
  }

  return data as AuthUser
}

export async function getMyProfileApi(): Promise<AuthUser> {
  const response = await fetch(`${API_BASE_URL}/me`, {
    method: 'GET',
    headers: getIdentityApiHeaders(),
  })

  if (!response.ok) {
    throw new Error(`Cannot load profile: ${response.status}`)
  }

  return response.json()
}

export interface UpdateProfileRequest {
  displayName?: string | null
  avatar?: string | null
}

export async function updateProfileApi(
  patch: UpdateProfileRequest,
): Promise<AuthUser> {
  const response = await fetch(`${API_BASE_URL}/me`, {
    method: 'PATCH',
    headers: {
      ...getIdentityApiHeaders(),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(patch),
  })

  const data = await response.json().catch(() => null)
  if (!response.ok) {
    const message =
      data && typeof data === 'object' && 'error' in data
        ? (data as { error?: { message?: string } }).error?.message
        : undefined
    throw new Error(message ?? `Cannot update profile: ${response.status}`)
  }

  return data as AuthUser
}

export interface UserSummary {
  userId: string
  username: string
  displayName?: string
  /** URL ảnh; không có thì client hiện chữ cái. */
  avatar?: string
}

// Tên của nhiều user theo userId, cho header chat và danh sách hội thoại.
// Id không có tài khoản bị bỏ qua. Tối đa 100 id một lần.
export async function lookupUsersApi(
  userIds: string[],
): Promise<UserSummary[]> {
  const ids = [...new Set(userIds)]

  if (ids.length === 0) {
    return []
  }

  const params = new URLSearchParams({ ids: ids.join(',') })

  const response = await fetch(`${API_BASE_URL}/users/lookup?${params.toString()}`, {
    method: 'GET',
    headers: getIdentityApiHeaders(),
  })

  if (!response.ok) {
    throw new Error(`Cannot load users: ${response.status}`)
  }

  const data = (await response.json()) as { users: UserSummary[] }
  return data.users
}
