export type AdminMeResponse = {
    authenticated?: boolean
    sub?: string
    userId?: number
    loginId?: string
    email?: string
    roles?: string[]
    reason?: string
    user?: null
}

export type AdminLogoutResponse = {
    logout: string
    authServerLogoutRequired?: boolean
    authServerLogoutUrl?: string
}

export const ADMIN_GATEWAY_BASE_URL = 'http://localhost:8090'

export const fetchAdminMe = async (signal?: AbortSignal): Promise<AdminMeResponse> => {
    const response = await fetch(`${ADMIN_GATEWAY_BASE_URL}/admin-bff/auth/me`, {
        method: 'GET',
        credentials: 'include',
        signal,
    })

    return (await response.json()) as AdminMeResponse
}

export const requestAdminLogout = async (): Promise<AdminLogoutResponse> => {
    const response = await fetch(`${ADMIN_GATEWAY_BASE_URL}/admin-bff/auth/logout`, {
        method: 'POST',
        credentials: 'include',
    })

    return (await response.json()) as AdminLogoutResponse
}