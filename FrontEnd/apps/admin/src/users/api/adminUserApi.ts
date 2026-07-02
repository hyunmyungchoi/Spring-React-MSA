import { unwrapAdminApiResponse } from '../../common/api/adminApiContract'
import { adminFetchJson } from '../../common/api/adminFetch'
import type { AdminApiResponse } from '../../common/types/adminResponse'
import type { AdminUserResponse, MemberPresenceEventResponse, MemberSessionResponse } from '../../common/types/adminUser'

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

// Loads member BFF login sessions stored through Spring Session Redis.
export async function fetchMemberSessions(signal?: AbortSignal): Promise<MemberSessionResponse[]> {
  const response = await adminFetchJson<AdminApiResponse<MemberSessionResponse[]>>('/admin-bff/sessions/member', {
    signal,
  })
  return unwrapAdminApiResponse(response)
}

// Loads recent member BFF presence events stored in Redis Stream.
export async function fetchMemberPresenceEvents(signal?: AbortSignal): Promise<MemberPresenceEventResponse[]> {
  const response = await adminFetchJson<AdminApiResponse<MemberPresenceEventResponse[]>>(
    '/admin-bff/sessions/member/events',
    { signal },
  )
  return unwrapAdminApiResponse(response)
}
