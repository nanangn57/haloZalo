export interface LoginRequest {
  login: string
  password: string
}

export interface AuthUser {
  userId: string
  username: string
  email?: string
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