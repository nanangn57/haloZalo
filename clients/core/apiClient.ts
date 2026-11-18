import {
  requireAccessToken,
  requireUserId,
} from './authSession'

import { connectionConfig } from './connectionConfig'

export function getApiHeaders(
  includeJsonContentType = false,
): HeadersInit {
  const headers: Record<string, string> = {}

  switch (connectionConfig.httpAuthMode) {
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