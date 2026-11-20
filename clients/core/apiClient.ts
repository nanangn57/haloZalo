import {
  requireAccessToken,
  requireUserId,
} from './authSession'

import { connectionConfig } from './connectionConfig'

export function getApiHeaders(
  includeJsonContentType = false,
): HeadersInit {
  const headers: Record<string, string> = {}

  switch (connectionConfig.messageHttpAuthMode) {
    case 'TOKEN':
      headers.Authorization = `Bearer ${requireAccessToken()}`
      break

    case 'USER_ID':
      headers['X-User-Id'] = requireUserId()
      break
  }

  if (includeJsonContentType) {
    headers['Content-Type'] = 'application/json'
  }

  return headers
}

// Headers dành riêng cho Identity Service.
export function getIdentityApiHeaders(
  includeJsonContentType = false,
): HeadersInit {
  const headers: Record<string, string> = {
    Authorization: `Bearer ${requireAccessToken()}`,
  }

  if (includeJsonContentType) {
    headers['Content-Type'] = 'application/json'
  }

  return headers
}