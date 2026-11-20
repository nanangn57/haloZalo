import { getApiHeaders } from './apiClient'
import { connectionConfig } from './connectionConfig'
import type { ApiErrorResponse } from './types/auth'
import type {
  Conversation,
  ConversationListResponse,
  CreateDirectConversationRequest,
} from './types/conversation'

const API_MESSAGE_BASE_URL = connectionConfig.messageBaseUrl

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
  const params = new URLSearchParams({
    limit: String(limit),
  })

  if (cursor) {
    params.set('cursor', cursor)
  }

  const response = await fetch(
    `${API_MESSAGE_BASE_URL}/conversations?${params.toString()}`,
    {
      method: 'GET',
      headers: getApiHeaders(),
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
  const response = await fetch(
    `${API_MESSAGE_BASE_URL}/conversations/direct`,
    {
      method: 'POST',
      headers: getApiHeaders(true),
      body: JSON.stringify(request),
    },
  )

  if (!response.ok) {
    throw new Error(await getErrorMessage(response))
  }

  return (await response.json()) as Conversation
}
