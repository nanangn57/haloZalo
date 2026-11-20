import { getIdentityApiHeaders } from './apiClient'
import type { ActivityStats } from './types/stat'
import { connectionConfig } from './connectionConfig'

const API_BASE_URL = connectionConfig.identityBaseUrl

export async function getActivityStatsApi(): Promise<ActivityStats> {
  const response = await fetch(`${API_BASE_URL}/stats`, {
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

export interface GroupTotal {
  groupId: string
  total: number
}

export interface DashboardStats {
  messagesByDay: DayTotal[]
  activeUsers: number
  topGroups: GroupTotal[]
}

export interface MyActivity {
  logins: number
  messagesSent: number
  groupsCreated: number
}

export async function getDashboardStatsApi(): Promise<DashboardStats> {
  const response = await fetch(`${API_BASE_URL}/stats/dashboard`, {
    method: 'GET',
    headers: getIdentityApiHeaders(),
  })

  if (!response.ok) {
    throw new Error('Failed to load dashboard')
  }

  return response.json()
}

export async function getMyActivityApi(): Promise<MyActivity> {
  const response = await fetch(`${API_BASE_URL}/me/activity`, {
    method: 'GET',
    headers: getIdentityApiHeaders(),
  })

  if (!response.ok) {
    throw new Error('Failed to load my activity')
  }

  return response.json()
}
