import { ADMIN_GATEWAY_BASE_URL } from '../../config/adminEnv'
import { adminFetchJson } from '../../common/api/adminFetch'

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

export type AdminSignupRequest = {
    loginId: string
    email: string
    password: string
    username: string
    phoneNumber?: string
    whatsappNumber?: string
}

export type AdminSignupResponse = {
    userId: number
    loginId: string
    email: string
    username: string
    enabled: boolean
    roles: string[]
}

export type AdminEmailOtpSendResponse = {
    sent: boolean
    expiresInSeconds: number
    devOtp?: string
}

export type AdminEmailOtpVerifyResponse = {
    verified: boolean
    authenticated: boolean
    redirectUrl?: string
    user?: unknown
}

export type AdminPasswordLoginResponse = {
    authenticated: boolean
    redirectUrl?: string
    user?: unknown
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

export const signupAdmin = async (
    request: AdminSignupRequest
): Promise<AdminSignupResponse> => {
    return adminFetchJson<AdminSignupResponse>(
        `${ADMIN_GATEWAY_BASE_URL}/admin-bff/auth/signup`,
        {
            method: 'POST',
            body: request,
        }
    )
}

export const loginAdminWithPassword = async (
    loginId: string,
    password: string
): Promise<AdminPasswordLoginResponse> => {
    await prepareAdminAuthorizationRequest()

    const response = await adminFetchJson<AdminPasswordLoginResponse>(
        `${ADMIN_GATEWAY_BASE_URL}/login/password`,
        {
            method: 'POST',
            body: { loginId, password },
        }
    )

    if (!response.redirectUrl) {
        throw new Error('관리자 로그인 이동 경로를 찾지 못했습니다.')
    }

    window.location.href = response.redirectUrl

    return response
}

export const sendAdminEmailOtp = async (
    email: string
): Promise<AdminEmailOtpSendResponse> => {
    await prepareAdminAuthorizationRequest()

    return adminFetchJson<AdminEmailOtpSendResponse>(
        `${ADMIN_GATEWAY_BASE_URL}/login/email/send-otp`,
        {
            method: 'POST',
            body: { email },
        }
    )
}

export const verifyAdminEmailOtp = async (
    email: string,
    otp: string
): Promise<AdminEmailOtpVerifyResponse> => {
    const response = await adminFetchJson<AdminEmailOtpVerifyResponse>(
        `${ADMIN_GATEWAY_BASE_URL}/login/email/verify`,
        {
            method: 'POST',
            body: { email, otp },
        }
    )

    if (response.redirectUrl) {
        window.location.href = response.redirectUrl
    } else {
        throw new Error('관리자 로그인 이동 경로를 찾지 못했습니다.')
    }

    return response
}

const prepareAdminAuthorizationRequest = async () => {
    const response = await fetch(`${ADMIN_GATEWAY_BASE_URL}/admin-bff/auth/login`, {
        credentials: 'include',
        headers: {
            Accept: 'text/html',
        },
    })

    if (!response.ok) {
        throw new Error('관리자 로그인 세션을 만들지 못했습니다.')
    }
}
