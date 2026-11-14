import { getAuth } from './authSession'
import type { ApiErrorResponse } from './types/auth'
import type {
  Conversation,
  ConversationListResponse,
  CreateDirectConversationRequest,
} from './types/conversation'

const API_BASE_URL = 'http://localhost:8082'

// Lấy access token từ session
function getAccessToken(): string {
  const auth = getAuth()

  if (!auth?.accessToken) {
    throw new Error('Bạn cần đăng nhập để thực hiện thao tác này.')
  }

  return auth.accessToken
}

// Xử lý lỗi từ API
async function getErrorMessage(
  response: Response,
): Promise<string> {
  const data: unknown = await response.json().catch(() => null)
  const errorData = data as ApiErrorResponse | null

  return (
    errorData?.error?.message
    ?? `Yêu cầu thất bại (${response.status})`
  )
}

// 1. Lấy danh sách hội thoại
export async function getConversationsApi(
  cursor?: string,
  limit = 50,
): Promise<ConversationListResponse> {
  const token = getAccessToken()

  const params = new URLSearchParams({
    limit: String(limit),
  })

  if (cursor) {
    params.set('cursor', cursor)
  }

  const response = await fetch(
    `${API_BASE_URL}/conversations?${params.toString()}`,
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

  return (await response.json()) as ConversationListResponse
}

// 2. Tạo hoặc lấy cuộc trò chuyện cá nhân
export async function createDirectConversationApi(
  request: CreateDirectConversationRequest,
): Promise<Conversation> {
  const token = getAccessToken()

  const response = await fetch(
    `${API_BASE_URL}/conversations/direct`,
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

  return (await response.json()) as Conversation
}