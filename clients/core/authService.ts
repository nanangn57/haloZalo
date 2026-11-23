import { connectionConfig } from './connectionConfig'
import { defaultDeviceLabel, getOrCreateDeviceId } from './deviceId'
import { createApiError, requestJson, toApiError } from './httpError'
import type {
  LoginRequest,
  LoginResponse,
  RegisterAccepted,
  RegisterRequest,
  TokenResponse,
} from './types/auth'

export type { RegisterAccepted, RegisterRequest, TokenResponse }

const API_BASE_URL = connectionConfig.identityBaseUrl

const JSON_HEADERS = { 'Content-Type': 'application/json' }

function deviceFields(override?: Partial<Pick<LoginRequest, 'deviceId' | 'deviceLabel' | 'userAgent'>>) {
  const deviceId = override?.deviceId?.trim() || getOrCreateDeviceId()
  const deviceLabel = override?.deviceLabel?.trim() || defaultDeviceLabel()
  const userAgent =
    override?.userAgent?.trim() ||
    (typeof navigator !== 'undefined' ? navigator.userAgent : undefined)
  return { deviceId, deviceLabel, userAgent }
}

// Đăng nhập: email + mật khẩu + deviceId (decisions.md).
export async function loginApi(
  credentials: Omit<LoginRequest, 'deviceId'> & Partial<Pick<LoginRequest, 'deviceId' | 'deviceLabel' | 'userAgent'>>,
): Promise<LoginResponse> {
  const device = deviceFields(credentials)
  return requestJson<LoginResponse>(
    `${API_BASE_URL}/auth/login`,
    {
      method: 'POST',
      headers: JSON_HEADERS,
      body: JSON.stringify({
        login: credentials.login.trim(),
        password: credentials.password,
        deviceId: device.deviceId,
        deviceLabel: device.deviceLabel,
        userAgent: device.userAgent,
      }),
    },
    'Đăng nhập thất bại',
  )
}

// Đăng ký: email + password + displayName. `username` do server derive từ email.
export async function registerApi(
  request: RegisterRequest,
): Promise<RegisterAccepted> {
  const body: RegisterRequest = {
    email: request.email.trim(),
    password: request.password,
    displayName: request.displayName.trim(),
  }
  if (request.username?.trim()) {
    body.username = request.username.trim()
  }

  return requestJson<RegisterAccepted>(
    `${API_BASE_URL}/auth/register`,
    { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify(body) },
    'Đăng ký thất bại',
  )
}

// Verify trả TokenResponse (đã đăng nhập). Lưu session bằng `verifyAndSaveSession` trong authSession.
export async function verifyEmailApi(token: string): Promise<TokenResponse> {
  const device = deviceFields()
  const params = new URLSearchParams({
    token,
    deviceId: device.deviceId,
    deviceLabel: device.deviceLabel,
  })
  return requestJson<TokenResponse>(
    `${API_BASE_URL}/auth/verify?${params.toString()}`,
    { method: 'GET' },
    'Xác minh email thất bại',
  )
}

export async function resendVerificationApi(
  email: string,
): Promise<RegisterAccepted> {
  const response = await fetch(`${API_BASE_URL}/auth/verify/resend`, {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify({ email: email.trim() }),
  })

  if (response.status === 429) {
    throw createApiError(
      'Gửi lại quá nhanh. Vui lòng đợi một lúc rồi thử lại.',
      429,
      'RATE_LIMITED',
    )
  }
  if (!response.ok) {
    throw await toApiError(response, 'Không gửi lại được email xác minh')
  }

  const data = (await response.json().catch(() => null)) as RegisterAccepted | null
  return data ?? { email: email.trim(), status: 'PENDING_VERIFICATION' }
}

// Nhận token tường minh để authSession.logout không phải import apiClient (tránh vòng import).
export async function logoutApi(accessToken: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/auth/logout`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${accessToken}` },
  })

  if (!response.ok && response.status !== 401) {
    throw await toApiError(response, 'Đăng xuất thất bại')
  }
}

// Luôn 202, không lộ email có tồn tại hay không.
export async function forgotPasswordApi(email: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/auth/password/forgot`, {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify({ email: email.trim() }),
  })

  if (!response.ok) {
    throw await toApiError(response, 'Không gửi được email đặt lại mật khẩu')
  }
}

export async function resetPasswordApi(
  token: string,
  password: string,
): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/auth/password/reset`, {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify({ token, password }),
  })

  if (!response.ok) {
    throw await toApiError(response, 'Không đặt lại được mật khẩu')
  }
}

export {
  loginApi as login,
  registerApi as register,
  verifyEmailApi as verify,
  resendVerificationApi as resendVerification,
  forgotPasswordApi as forgotPassword,
  resetPasswordApi as resetPassword,
}
