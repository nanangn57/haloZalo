
import { getAuth } from './authSession'
import type { ApiErrorResponse } from './types/auth'
import type {
  Group,
  GroupListResponse,
  CreateGroupRequest,
  AddMemberRequest,
  Membership,
} from './types/group'

const API_BASE_URL = 'http://localhost:8080'

async function getErrorMessage(
  response: Response,
): Promise<string> {
  const data: unknown = await response.json().catch(() => null)
  const errorData = data as ApiErrorResponse | null

  return errorData?.error?.message
    ?? `Yêu cầu thất bại (${response.status})`
}

function getAccessToken(): string {
  const auth = getAuth()

  if (!auth?.accessToken) {
    throw new Error('Bạn cần đăng nhập để thực hiện thao tác này.')
  }

  return auth.accessToken
}

// 1. Lấy danh sách nhóm của user hiện tại
export async function getGroupsApi(): Promise<Group[]> {
  const token = getAccessToken()

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
  const token = getAccessToken()

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
  const token = getAccessToken()

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
  const token = getAccessToken()

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
