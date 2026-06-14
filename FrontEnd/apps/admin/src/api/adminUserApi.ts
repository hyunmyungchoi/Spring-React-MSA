import { ADMIN_GATEWAY_BASE_URL } from '../config/adminEnv'
import { adminFetchJson } from './adminFetch'

export type AdminUserResponse = {
    userId: number
    loginId: string
    email: string
    username: string
    enabled: boolean
    roles: string[]
}

export const fetchAdminUserMe = async (signal?: AbortSignal): Promise<unknown> => {
    return adminFetchJson<unknown>(
        `${ADMIN_GATEWAY_BASE_URL}/admin-bff/user/me`,
        { signal }
    )
}

export const fetchAdminUsers = async (signal?: AbortSignal): Promise<AdminUserResponse[]> => {
    return adminFetchJson<AdminUserResponse[]>(
        `${ADMIN_GATEWAY_BASE_URL}/admin-bff/user/admin/users`,
        { signal }
    )
}

export const fetchAdminUserDetail = async (
    userId: string,
    signal?: AbortSignal
): Promise<AdminUserResponse> => {
    return adminFetchJson<AdminUserResponse>(
        `${ADMIN_GATEWAY_BASE_URL}/admin-bff/user/admin/users/${userId}`,
        { signal }
    )
}