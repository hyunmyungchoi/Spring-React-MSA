import { ADMIN_GATEWAY_BASE_URL } from '../../config/adminEnv'
import { adminFetchJson } from '../../common/api/adminFetch'

export type ManageUserResponse = {
    userId: number
    loginId: string
    email: string
    username: string
    enabled: boolean
    roles: string[]
}

export const fetchManageUserMe = async (signal?: AbortSignal): Promise<unknown> => {
    return adminFetchJson<unknown>(
        `${ADMIN_GATEWAY_BASE_URL}/admin-bff/user/me`,
        { signal }
    )
}

export const fetchManageUsers = async (signal?: AbortSignal): Promise<ManageUserResponse[]> => {
    return adminFetchJson<ManageUserResponse[]>(
        `${ADMIN_GATEWAY_BASE_URL}/admin-bff/user/admin/users`,
        { signal }
    )
}

export const fetchManageUserDetail = async (
    userId: string,
    signal?: AbortSignal
): Promise<ManageUserResponse> => {
    return adminFetchJson<ManageUserResponse>(
        `${ADMIN_GATEWAY_BASE_URL}/admin-bff/user/admin/users/${userId}`,
        { signal }
    )
}
