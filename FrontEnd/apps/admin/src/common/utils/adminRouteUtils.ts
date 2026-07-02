import type { AdminLogoutResponse } from '../types/adminAuth'

// Chooses the browser redirect target after admin logout.
export function getAdminLogoutRedirectUrl(response: AdminLogoutResponse): string {
  if (response.authServerLogoutRequired && response.authServerLogoutUrl) {
    return response.authServerLogoutUrl
  }

  return '/auth'
}

// Trims user IDs before validation or API calls.
export function normalizeAdminUserId(userId: string): string {
  return userId.trim()
}

// Checks whether a user ID contains only digits.
export function isValidAdminUserId(userId: string): boolean {
  return /^\d+$/.test(normalizeAdminUserId(userId))
}
