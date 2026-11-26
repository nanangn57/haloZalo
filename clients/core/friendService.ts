import { getIdentityApiHeaders } from './apiClient'
import { connectionConfig } from './connectionConfig'
import { requestJson, toApiError } from './httpError'

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

export interface FriendRequestList {
  incoming: FriendRequest[]
  outgoing: FriendRequest[]
}

export async function listFriendsApi(): Promise<Friend[]> {
  const data = await requestJson<{ friends?: Friend[] }>(
    `${API_BASE_URL}/friends`,
    { method: 'GET', headers: getIdentityApiHeaders() },
    'Cannot load friends',
  )
  return data.friends ?? []
}

export async function sendFriendRequestApi(userId: string): Promise<FriendRequest> {
  return requestJson<FriendRequest>(
    `${API_BASE_URL}/friends/requests`,
    {
      method: 'POST',
      headers: getIdentityApiHeaders(true),
      body: JSON.stringify({ userId }),
    },
    'Cannot send friend request',
  )
}

export async function acceptFriendRequestApi(fromUserId: string): Promise<Friend> {
  return requestJson<Friend>(
    `${API_BASE_URL}/friends/requests/${encodeURIComponent(fromUserId)}/accept`,
    { method: 'POST', headers: getIdentityApiHeaders() },
    'Cannot accept friend request',
  )
}

// Lời mời đến và đi.
export async function listFriendRequestsApi(): Promise<FriendRequestList> {
  const data = await requestJson<Partial<FriendRequestList>>(
    `${API_BASE_URL}/friends/requests`,
    { method: 'GET', headers: getIdentityApiHeaders() },
    'Cannot load friend requests',
  )
  return { incoming: data.incoming ?? [], outgoing: data.outgoing ?? [] }
}

async function expectNoContent(
  url: string,
  init: RequestInit,
  fallback: string,
): Promise<void> {
  const response = await fetch(url, init)
  if (!response.ok) {
    throw await toApiError(response, fallback)
  }
}

export function rejectFriendRequestApi(fromUserId: string): Promise<void> {
  return expectNoContent(
    `${API_BASE_URL}/friends/requests/${encodeURIComponent(fromUserId)}/reject`,
    { method: 'POST', headers: getIdentityApiHeaders() },
    'Cannot reject friend request',
  )
}

// Huỷ lời mời đã gửi tới toUserId.
export function cancelFriendRequestApi(toUserId: string): Promise<void> {
  return expectNoContent(
    `${API_BASE_URL}/friends/requests/${encodeURIComponent(toUserId)}`,
    { method: 'DELETE', headers: getIdentityApiHeaders() },
    'Cannot cancel friend request',
  )
}

export function unfriendApi(userId: string): Promise<void> {
  return expectNoContent(
    `${API_BASE_URL}/friends/${encodeURIComponent(userId)}`,
    { method: 'DELETE', headers: getIdentityApiHeaders() },
    'Cannot unfriend',
  )
}

export function blockUserApi(userId: string): Promise<void> {
  return expectNoContent(
    `${API_BASE_URL}/blocks`,
    {
      method: 'POST',
      headers: getIdentityApiHeaders(true),
      body: JSON.stringify({ userId }),
    },
    'Cannot block user',
  )
}

export function unblockUserApi(userId: string): Promise<void> {
  return expectNoContent(
    `${API_BASE_URL}/blocks/${encodeURIComponent(userId)}`,
    { method: 'DELETE', headers: getIdentityApiHeaders() },
    'Cannot unblock user',
  )
}

export async function listBlocksApi(): Promise<Friend[]> {
  // openapi FriendList: { "friends": [...] }. Giữ fallback "blocks" cho bản server cũ.
  const data = await requestJson<{ friends?: Friend[]; blocks?: Friend[] }>(
    `${API_BASE_URL}/blocks`,
    { method: 'GET', headers: getIdentityApiHeaders() },
    'Cannot load blocked users',
  )
  return data.friends ?? data.blocks ?? []
}

// System admin (platformRole ADMIN).
export function banUserApi(userId: string): Promise<void> {
  return expectNoContent(
    `${API_BASE_URL}/admin/users/${encodeURIComponent(userId)}/ban`,
    { method: 'POST', headers: getIdentityApiHeaders() },
    'Cannot ban user',
  )
}

export function unbanUserApi(userId: string): Promise<void> {
  return expectNoContent(
    `${API_BASE_URL}/admin/users/${encodeURIComponent(userId)}/unban`,
    { method: 'POST', headers: getIdentityApiHeaders() },
    'Cannot unban user',
  )
}
