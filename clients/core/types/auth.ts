export interface LoginRequest {
  login: string
  password: string
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

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: AuthUser
}

export interface ApiErrorResponse {
  error: {
    code: string
    message: string
  }
}
