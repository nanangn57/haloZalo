
import type { AuthUser, ApiErrorResponse } from '../types/auth'
import { getAuth } from './authSession'

const API_BASE_URL = 'http://localhost:8080'

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
