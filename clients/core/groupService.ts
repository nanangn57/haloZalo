
import { getAuth } from './authSession'
import type { ApiErrorResponse } from './types/auth'
import type {
  Group,
  GroupListResponse,
  CreateGroupRequest,
  AddMemberRequest,
  Membership,
  MemberListResponse,
  UpdateGroupRequest,
} from './types/group'
import { connectionConfig } from './connectionConfig'

const API_BASE_URL = connectionConfig.identityBaseUrl

async function getErrorMessage(
  response: Response,
): Promise<string> {
  const data: unknown = await response.json().catch(() => null)
  const errorData = data as ApiErrorResponse | null

  return errorData?.error?.message
    ?? `Yêu cầu thất bại (${response.status})`
}

function requireAccessToken(): string {
  const auth = getAuth()

  if (!auth?.accessToken) {
    throw new Error('Bạn cần đăng nhập để thực hiện thao tác này.')
  }

  return auth.accessToken
}

// 1. Lấy danh sách nhóm của user hiện tại
export async function getGroupsApi(): Promise<Group[]> {
  const token = requireAccessToken()

  const response = await fetch(`${API_BASE_URL}/groups`, {
    method: 'GET',
    headers: {
      Authorization: `Bearer ${token}`,
    },
  })

  if (!response.ok) {
    throw new Error(await getErrorMessage(response))
  }

  const data = (await response.json()) as GroupListResponse

  return data.groups
}

// 2. Tạo nhóm mới
export async function createGroupApi(
  request: CreateGroupRequest,
): Promise<Group> {
  const token = requireAccessToken()

  const response = await fetch(`${API_BASE_URL}/groups`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    throw new Error(await getErrorMessage(response))
  }

  return (await response.json()) as Group
}

// 3. Thêm thành viên vào nhóm (Owner)
export async function addGroupMemberApi(
  groupId: string,
  request: AddMemberRequest,
): Promise<Membership> {
  const token = requireAccessToken()

  const response = await fetch(
    `${API_BASE_URL}/groups/${encodeURIComponent(groupId)}/members`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify(request),
    },
  )

  if (!response.ok) {
    throw new Error(await getErrorMessage(response))
  }

  return (await response.json()) as Membership
}

// 4. Owner xoá thành viên hoặc Member tự rời nhóm
export async function removeGroupMemberApi(
  groupId: string,
  userId: string,
): Promise<void> {
  const token = requireAccessToken()

  const response = await fetch(
    `${API_BASE_URL}/groups/${encodeURIComponent(groupId)}/members/${encodeURIComponent(userId)}`,
    {
      method: 'DELETE',
      headers: {
        Authorization: `Bearer ${token}`,
      },
    },
  )

  if (!response.ok) {
    throw new Error(await getErrorMessage(response))
  }
}
// 5. Lấy danh sách thành viên nhóm
export async function getGroupMembersApi(
  groupId: string,
): Promise<MemberListResponse> {
  const token = requireAccessToken()

  const response = await fetch(
    `${API_BASE_URL}/groups/${encodeURIComponent(groupId)}/members`,
    {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${token}`,
      },
    },
  )

  if (!response.ok) {
    throw new Error(await getErrorMessage(response))
  }

  return response.json()
}

// 6. Owner chuyển quyền owner cho member khác (POST /groups/{id}/transfer-owner)
export async function transferGroupOwnerApi(
  groupId: string,
  userId: string,
): Promise<Membership> {
  const token = requireAccessToken()

  const response = await fetch(
    `${API_BASE_URL}/groups/${encodeURIComponent(groupId)}/transfer-owner`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ userId }),
    },
  )

  if (!response.ok) {
    throw new Error(await getErrorMessage(response))
  }

  return (await response.json()) as Membership
}

// 7. Owner giải tán nhóm (DELETE /groups/{id})
export async function dissolveGroupApi(groupId: string): Promise<void> {
  const token = requireAccessToken()

  const response = await fetch(
    `${API_BASE_URL}/groups/${encodeURIComponent(groupId)}`,
    {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${token}` },
    },
  )

  if (!response.ok) {
    throw new Error(await getErrorMessage(response))
  }
}

// 8. Owner sửa tên / mô tả / avatar nhóm (PATCH /groups/{id})
export async function updateGroupApi(
  groupId: string,
  patch: UpdateGroupRequest,
): Promise<Group> {
  const token = requireAccessToken()

  const response = await fetch(
    `${API_BASE_URL}/groups/${encodeURIComponent(groupId)}`,
    {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify(patch),
    },
  )

  if (!response.ok) {
    throw new Error(await getErrorMessage(response))
  }

  return (await response.json()) as Group
}
