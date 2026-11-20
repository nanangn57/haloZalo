import { getIdentityApiHeaders } from './apiClient'
import type { ActivityStats } from './types/stat';

const API_BASE_URL = import.meta.env.VITE_API_IDENTITY_BASE_URL;

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