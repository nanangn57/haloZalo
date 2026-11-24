import { getIdentityApiHeaders } from './apiClient'
import type { ActivityStats } from './types/stat'
import type { AuthUser } from './types/auth'
import { connectionConfig } from './connectionConfig'

const API_BASE_URL = connectionConfig.identityBaseUrl

export interface DateRange {
  /** ISO date yyyy-MM-dd (bao gồm cả hai đầu). Bỏ trống = không giới hạn. */
  from?: string
  to?: string
}

function withRange(path: string, range?: DateRange): string {
  const params = new URLSearchParams()
  if (range?.from) params.set('from', range.from)
  if (range?.to) params.set('to', range.to)
  const query = params.toString()
  return `${API_BASE_URL}${path}${query ? `?${query}` : ''}`
}

export async function getActivityStatsApi(
  range?: DateRange,
): Promise<ActivityStats> {
  const response = await fetch(withRange('/stats', range), {
    method: 'GET',
    headers: getIdentityApiHeaders(),
  })

  if (!response.ok) {
    throw new Error('Failed to load activity stats')
  }

  return response.json()
}

export interface DayTotal {
  day: string
  total: number
}

export interface MessageDayTotal {
  day: string
  sent: number
  received: number
}

export interface GroupTotal {
  groupId: string
  total: number
}

export interface MediaTextSplit {
  text: number
  media: number
}

export interface DirectGroupSplit {
  direct: number
  group: number
}

export interface HeatmapCell {
  weekday: number
  hour: number
  total: number
}

export interface FriendActivity {
  count: number
  requestsSent: number
  requestsReceived: number
  acceptRate: number
}

export interface ConversationTotal {
  conversationId: string
  total: number
}

export interface VerifyFunnel {
  registered: number
  verified: number
  firstMessage: number
}

export interface MediaDayTotal {
  day: string
  type: 'IMAGE' | 'DOCUMENT' | 'VIDEO' | 'AUDIO' | string
  total: number
}

export interface DashboardStats {
  messagesByDay: DayTotal[]
  activeUsers: number
  topGroups: GroupTotal[]
  dau: number
  wau: number
  mau: number
  newUsersByDay: DayTotal[]
  verifyFunnel: VerifyFunnel
  messagesPerActiveUser: number
  activeMessengersRate: number
  friendRequests: number
  friendAcceptRate: number
  groupsCreated: number
  avgMembersPerGroup: number
  mediaByDay: MediaDayTotal[]
  heatmap: HeatmapCell[]
  from?: string
  to?: string
}

export interface MyActivity {
  logins: number
  messagesSent: number
  messagesReceived: number
  groupsCreated: number
  activeConversations: number
  messagesByDay: MessageDayTotal[]
  mediaVsText: MediaTextSplit
  directVsGroup: DirectGroupSplit
  heatmap: HeatmapCell[]
  friends: FriendActivity
  topConversations: ConversationTotal[]
  from?: string
  to?: string
}

export interface AdminUserSummary {
  user: AuthUser & { displayName?: string; avatar?: string; bio?: string }
  activity: MyActivity
}

export function emptyMyActivity(): MyActivity {
  return {
    logins: 0,
    messagesSent: 0,
    messagesReceived: 0,
    groupsCreated: 0,
    activeConversations: 0,
    messagesByDay: [],
    mediaVsText: { text: 0, media: 0 },
    directVsGroup: { direct: 0, group: 0 },
    heatmap: [],
    friends: { count: 0, requestsSent: 0, requestsReceived: 0, acceptRate: 0 },
    topConversations: [],
  }
}

export async function getDashboardStatsApi(
  range?: DateRange,
): Promise<DashboardStats> {
  const response = await fetch(withRange('/stats/dashboard', range), {
    method: 'GET',
    headers: getIdentityApiHeaders(),
  })

  if (!response.ok) {
    throw new Error('Failed to load dashboard')
  }

  return normalizeDashboardStats(await response.json())
}

export function normalizeDashboardStats(
  raw: Partial<DashboardStats> | null | undefined,
): DashboardStats {
  return {
    messagesByDay: Array.isArray(raw?.messagesByDay) ? raw.messagesByDay : [],
    activeUsers: numberOr(raw?.activeUsers, 0),
    topGroups: Array.isArray(raw?.topGroups) ? raw.topGroups : [],
    dau: numberOr(raw?.dau, 0),
    wau: numberOr(raw?.wau, 0),
    mau: numberOr(raw?.mau, 0),
    newUsersByDay: Array.isArray(raw?.newUsersByDay) ? raw.newUsersByDay : [],
    verifyFunnel: {
      registered: numberOr(raw?.verifyFunnel?.registered, 0),
      verified: numberOr(raw?.verifyFunnel?.verified, 0),
      firstMessage: numberOr(raw?.verifyFunnel?.firstMessage, 0),
    },
    messagesPerActiveUser: numberOr(raw?.messagesPerActiveUser, 0),
    activeMessengersRate: numberOr(raw?.activeMessengersRate, 0),
    friendRequests: numberOr(raw?.friendRequests, 0),
    friendAcceptRate: numberOr(raw?.friendAcceptRate, 0),
    groupsCreated: numberOr(raw?.groupsCreated, 0),
    avgMembersPerGroup: numberOr(raw?.avgMembersPerGroup, 0),
    mediaByDay: Array.isArray(raw?.mediaByDay) ? raw.mediaByDay : [],
    heatmap: Array.isArray(raw?.heatmap) ? raw.heatmap : [],
    from: raw?.from,
    to: raw?.to,
  }
}

export async function getMyActivityApi(range?: DateRange): Promise<MyActivity> {
  const response = await fetch(withRange('/me/activity', range), {
    method: 'GET',
    headers: getIdentityApiHeaders(),
  })

  if (!response.ok) {
    throw new Error('Failed to load my activity')
  }

  return normalizeMyActivity(await response.json())
}

/** Fill missing fields when talking to an older server or a partial payload. */
export function normalizeMyActivity(raw: Partial<MyActivity> | null | undefined): MyActivity {
  const base = emptyMyActivity()
  if (!raw || typeof raw !== 'object') {
    return base
  }
  return {
    logins: numberOr(raw.logins, 0),
    messagesSent: numberOr(raw.messagesSent, 0),
    messagesReceived: numberOr(raw.messagesReceived, 0),
    groupsCreated: numberOr(raw.groupsCreated, 0),
    activeConversations: numberOr(raw.activeConversations, 0),
    messagesByDay: Array.isArray(raw.messagesByDay) ? raw.messagesByDay : [],
    mediaVsText: {
      text: numberOr(raw.mediaVsText?.text, 0),
      media: numberOr(raw.mediaVsText?.media, 0),
    },
    directVsGroup: {
      direct: numberOr(raw.directVsGroup?.direct, 0),
      group: numberOr(raw.directVsGroup?.group, 0),
    },
    heatmap: Array.isArray(raw.heatmap) ? raw.heatmap : [],
    friends: {
      count: numberOr(raw.friends?.count, 0),
      requestsSent: numberOr(raw.friends?.requestsSent, 0),
      requestsReceived: numberOr(raw.friends?.requestsReceived, 0),
      acceptRate: numberOr(raw.friends?.acceptRate, 0),
    },
    topConversations: Array.isArray(raw.topConversations) ? raw.topConversations : [],
    from: raw.from,
    to: raw.to,
  }
}

function numberOr(value: unknown, fallback: number): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

export async function getAdminUserSummaryApi(
  userId: string,
): Promise<AdminUserSummary> {
  const response = await fetch(
    `${API_BASE_URL}/admin/users/${encodeURIComponent(userId)}`,
    {
      method: 'GET',
      headers: getIdentityApiHeaders(),
    },
  )

  if (!response.ok) {
    throw new Error('Failed to load user summary')
  }

  return response.json()
}

/** Preset helpers for Today / 7d / 30d filters (ISO yyyy-MM-dd, UTC). */
export function rangePreset(
  preset: 'today' | '7d' | '30d',
  now = new Date(),
): DateRange {
  const to = isoDate(now)
  if (preset === 'today') {
    return { from: to, to }
  }
  const days = preset === '7d' ? 6 : 29
  const from = new Date(now)
  from.setUTCDate(from.getUTCDate() - days)
  return { from: isoDate(from), to }
}

function isoDate(value: Date): string {
  return value.toISOString().slice(0, 10)
}
