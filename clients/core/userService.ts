
import type { AuthUser, ApiErrorResponse } from './types/auth'
import { getAuth } from './authSession'
import { getIdentityApiHeaders } from './apiClient';

const API_BASE_URL = import.meta.env.VITE_API_IDENTITY_BASE_URL;

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
