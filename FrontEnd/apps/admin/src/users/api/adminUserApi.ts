import { adminFetchJson } from '../../common/api/adminFetch'
import type { AdminUserResponse, MemberPresenceEventResponse, MemberSessionResponse } from '../../common/types/adminUser'

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

// Loads member BFF login sessions stored through Spring Session Redis.
export function fetchMemberSessions(signal?: AbortSignal): Promise<MemberSessionResponse[]> {
  return adminFetchJson<MemberSessionResponse[]>('/admin-bff/sessions/member', {
    signal,
  })
}

// Loads recent member BFF presence events stored in Redis Stream.
export function fetchMemberPresenceEvents(signal?: AbortSignal): Promise<MemberPresenceEventResponse[]> {
  return adminFetchJson<MemberPresenceEventResponse[]>('/admin-bff/sessions/member/events', { signal })
}
