export interface LoginRequest {
  login: string
  password: string
  /** Id thiết bị bền — bắt buộc theo hợp đồng. */
  deviceId: string
  deviceLabel?: string
  userAgent?: string
}

export type PlatformRole = 'USER' | 'ADMIN'

export interface AuthUser {
  userId: string
  username: string
  email: string
  platformRole?: PlatformRole
  displayName?: string
  avatar?: string
}

export interface TokenResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: AuthUser
}

/** Giữ tên cũ cho code đang import `LoginResponse`. */
export type LoginResponse = TokenResponse

export interface RegisterRequest {
  email: string
  password: string
  displayName: string
  /** Tuỳ chọn. Server derive từ email khi bỏ trống. */
  username?: string
}

export interface RegisterAccepted {
  email: string
  status: 'PENDING_VERIFICATION' | 'ACTIVE'
}

export interface SessionInfo {
  sessionId: string
  deviceId?: string
  deviceLabel?: string
  userAgent?: string
  createdAt: string
  expiresAt: string
  lastSeenAt?: string
}

export interface PasswordChangeRequest {
  currentPassword: string
  newPassword: string
  revokeOtherSessions?: boolean
}

export interface ApiErrorResponse {
  error: {
    code: string
    message: string
  }
}
