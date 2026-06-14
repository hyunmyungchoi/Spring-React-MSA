import { ADMIN_GATEWAY_BASE_URL } from '../config/adminEnv'

export type AdminUserResponse = {
    userId: number
    loginId: string
    email: string
    username: string
    enabled: boolean
    roles: string[]
}

export const fetchAdminUserMe = async (signal?: AbortSignal): Promise<unknown> => {
    const response = await fetch(`${ADMIN_GATEWAY_BASE_URL}/admin-bff/user/me`, {
        method: 'GET',
        credentials: 'include',
        signal,
    })

    return await response.json()
}

export const fetchAdminUsers = async (signal?: AbortSignal): Promise<AdminUserResponse[]> => {
    const response = await fetch(`${ADMIN_GATEWAY_BASE_URL}/admin-bff/user/admin/users`, {
        method: 'GET',
        credentials: 'include',
        signal,
    })

    return (await response.json()) as AdminUserResponse[]
}

export const fetchAdminUserDetail = async (
    userId: string,
    signal?: AbortSignal
): Promise<AdminUserResponse> => {
    const response = await fetch(`${ADMIN_GATEWAY_BASE_URL}/admin-bff/user/admin/users/${userId}`, {
        method: 'GET',
        credentials: 'include',
        signal,
    })

    return (await response.json()) as AdminUserResponse
}