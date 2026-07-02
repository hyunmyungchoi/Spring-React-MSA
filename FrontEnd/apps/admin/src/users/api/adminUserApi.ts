import { adminFetchJson } from '../../api/adminFetch'
import { unwrapAdminApiResponse } from '../../api/adminApiContract'
import type { AdminApiResponse } from '../../types/adminResponse'
import type { AdminUserResponse } from '../../types/adminUser'

// Loads the current admin user profile from the user service.
export async function fetchAdminUserMe(signal?: AbortSignal): Promise<unknown> {
  const response = await adminFetchJson<AdminApiResponse<unknown>>('/admin-bff/user/me', { signal })
  return unwrapAdminApiResponse(response)
}

// Loads all users available to admin management.
export async function fetchAdminUsers(signal?: AbortSignal): Promise<AdminUserResponse[]> {
  const response = await adminFetchJson<AdminApiResponse<AdminUserResponse[]>>('/admin-bff/user/admin/users', { signal })
  return unwrapAdminApiResponse(response)
}

// Loads one user detail record for admin management.
export async function fetchAdminUserDetail(userId: string, signal?: AbortSignal): Promise<AdminUserResponse> {
  const response = await adminFetchJson<AdminApiResponse<AdminUserResponse>>(`/admin-bff/user/admin/users/${userId}`, {
    signal,
  })
  return unwrapAdminApiResponse(response)
}
