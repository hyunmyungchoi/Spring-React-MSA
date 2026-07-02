import type { AdminUserResponse } from '../types/adminUser'
import { adminFetchJson } from './adminFetch'

// Loads the current admin user profile from the user service.
export function fetchAdminUserMe(signal?: AbortSignal): Promise<unknown> {
  return adminFetchJson<unknown>('/admin-bff/user/me', { signal })
}

// Loads all users available to admin management.
export function fetchAdminUsers(signal?: AbortSignal): Promise<AdminUserResponse[]> {
  return adminFetchJson<AdminUserResponse[]>('/admin-bff/user/admin/users', { signal })
}

// Loads one user detail record for admin management.
export function fetchAdminUserDetail(userId: string, signal?: AbortSignal): Promise<AdminUserResponse> {
  return adminFetchJson<AdminUserResponse>(`/admin-bff/user/admin/users/${userId}`, {
    signal,
  })
}
