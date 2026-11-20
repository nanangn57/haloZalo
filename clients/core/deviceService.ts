import { getIdentityApiHeaders } from './apiClient'
import { connectionConfig } from './connectionConfig'

const API_BASE_URL = connectionConfig.identityBaseUrl

export async function registerDeviceTokenApi(
  token: string,
  platform: 'ios' | 'android' | 'web' | 'unknown' = 'unknown',
): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/devices`, {
    method: 'POST',
    headers: {
      ...getIdentityApiHeaders(),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ token, platform }),
  })

  if (!response.ok) {
    throw new Error(`Cannot register device token: ${response.status}`)
  }
}

export async function unregisterDeviceTokenApi(token: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/devices`, {
    method: 'DELETE',
    headers: {
      ...getIdentityApiHeaders(),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ token }),
  })

  if (!response.ok) {
    throw new Error(`Cannot unregister device token: ${response.status}`)
  }
}
