import { apiRequest } from '../api/client';

export interface LoginRequest {
  login: string;
  password: string;
}

export interface AuthUser {
  userId: string;
  username: string;
  email: string;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: AuthUser;
}

export function login(
  credentials: LoginRequest,
): Promise<TokenResponse> {
  return apiRequest<TokenResponse>('/auth/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(credentials),
  });
}