import { ADMIN_GATEWAY_BASE_URL } from '../config/adminEnv'
import { adminFetchJson } from './adminFetch'

export type AdminMeUser = {
    sub?: string
    name?: string
    userId?: number
    loginId?: string
    email?: string
    roles?: string[]
}

export type AdminMeResponse = {
    authenticated: boolean
    user: AdminMeUser | null
    reason: string | null
}

export type AdminLogoutResponse = {
    logout: string
    authServerLogoutRequired?: boolean
    authServerLogoutUrl?: string
}



export const fetchAdminMe = async (signal?: AbortSignal): Promise<AdminMeResponse> => {
    return adminFetchJson<AdminMeResponse>(
        `${ADMIN_GATEWAY_BASE_URL}/admin-bff/auth/me`,
        { signal }
    )
}


export const requestAdminLogout = async (): Promise<AdminLogoutResponse> => {
    return adminFetchJson<AdminLogoutResponse>(
        `${ADMIN_GATEWAY_BASE_URL}/admin-bff/auth/logout`,
        { method: 'POST' }
    )
}