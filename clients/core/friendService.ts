import { getIdentityApiHeaders } from './apiClient'
import { connectionConfig } from './connectionConfig'

const API_BASE_URL = connectionConfig.identityBaseUrl

export interface Friend {
  userId: string
  username: string
  displayName?: string
}

export interface FriendRequest {
  fromUserId: string
  toUserId: string
}

export async function listFriendsApi(): Promise<Friend[]> {
  const response = await fetch(`${API_BASE_URL}/friends`, {
    method: 'GET',
    headers: getIdentityApiHeaders(),
  })

  if (!response.ok) {
    throw new Error(`Cannot load friends: ${response.status}`)
  }

  const data = (await response.json()) as { friends: Friend[] }
  return data.friends ?? []
}

export async function sendFriendRequestApi(userId: string): Promise<FriendRequest> {
  const response = await fetch(`${API_BASE_URL}/friends/requests`, {
    method: 'POST',
    headers: {
      ...getIdentityApiHeaders(),
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ userId }),
  })

  const data = await response.json().catch(() => null)
  if (!response.ok) {
    const message =
      data && typeof data === 'object' && 'error' in data
        ? (data as { error?: { message?: string } }).error?.message
        : undefined
    throw new Error(message ?? `Cannot send friend request: ${response.status}`)
  }

  return data as FriendRequest
}

export async function acceptFriendRequestApi(fromUserId: string): Promise<Friend> {
  const response = await fetch(
    `${API_BASE_URL}/friends/requests/${encodeURIComponent(fromUserId)}/accept`,
    {
      method: 'POST',
      headers: getIdentityApiHeaders(),
    },
  )

  const data = await response.json().catch(() => null)
  if (!response.ok) {
    const message =
      data && typeof data === 'object' && 'error' in data
        ? (data as { error?: { message?: string } }).error?.message
        : undefined
    throw new Error(message ?? `Cannot accept friend request: ${response.status}`)
  }

  return data as Friend
}
