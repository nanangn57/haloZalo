import { getIdentityApiHeaders } from './apiClient'
import { connectionConfig } from './connectionConfig'
import { toApiError } from './httpError'
import type { PasswordChangeRequest, SessionInfo } from './types/auth'

const API_BASE_URL = connectionConfig.identityBaseUrl

export async function listSessionsApi(): Promise<SessionInfo[]> {
  const response = await fetch(`${API_BASE_URL}/auth/sessions`, {
    method: 'GET',
    headers: getIdentityApiHeaders(),
  })

  if (!response.ok) {
    throw await toApiError(response, 'Không tải được danh sách phiên')
  }

  const data = (await response.json()) as { sessions?: SessionInfo[] }
  return data.sessions ?? []
}

export async function revokeSessionApi(sessionId: string): Promise<void> {
  const response = await fetch(
    `${API_BASE_URL}/auth/sessions/${encodeURIComponent(sessionId)}`,
    { method: 'DELETE', headers: getIdentityApiHeaders() },
  )

  if (!response.ok) {
    throw await toApiError(response, 'Không đăng xuất được phiên này')
  }
}

export async function changePasswordApi(
  request: PasswordChangeRequest,
): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/auth/password/change`, {
    method: 'POST',
    headers: getIdentityApiHeaders(true),
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    throw await toApiError(response, 'Không đổi được mật khẩu')
  }
}
