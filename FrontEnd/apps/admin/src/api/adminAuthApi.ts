import { ADMIN_GATEWAY_BASE_URL } from '../config/adminEnv'
import { ADMIN_ERROR_MESSAGES } from '../messages/adminErrorMessages'
import type {
  AdminEmailOtpSendResponse,
  AdminEmailOtpVerifyResponse,
  AdminLogoutResponse,
  AdminPasswordLoginResponse,
  AdminSignupRequest,
  AdminSignupResponse,
} from '../types/adminAuth'
import type { AdminMeResponse } from '../types/adminSession'
import { adminFetchJson } from './adminFetch'

// Loads the current admin authentication session.
export function fetchAdminMe(signal?: AbortSignal): Promise<AdminMeResponse> {
  return adminFetchJson<AdminMeResponse>('/admin-bff/auth/me', { signal })
}

// Requests admin logout from the BFF.
export function requestAdminLogout(): Promise<AdminLogoutResponse> {
  return adminFetchJson<AdminLogoutResponse>('/admin-bff/auth/logout', {
    method: 'POST',
  })
}

// Creates an admin account through the BFF.
export function signupAdmin(request: AdminSignupRequest): Promise<AdminSignupResponse> {
  return adminFetchJson<AdminSignupResponse>('/admin-bff/auth/signup', {
    method: 'POST',
    body: request,
  })
}

// Starts an admin password login and redirects to the authorization flow.
export async function loginAdminWithPassword(loginId: string, password: string): Promise<AdminPasswordLoginResponse> {
  await prepareAdminAuthorizationRequest()

  const existingSession = await fetchAdminMe()

  if (existingSession.authenticated) {
    window.location.href = '/'
    return {
      authenticated: true,
      redirectUrl: '/',
      user: existingSession.user ?? undefined,
    }
  }

  const response = await adminFetchJson<AdminPasswordLoginResponse>('/login/password', {
    method: 'POST',
    body: { loginId, password },
  })

  if (!response.redirectUrl) {
    throw new Error(ADMIN_ERROR_MESSAGES.LOGIN_REDIRECT_PATH_NOT_FOUND)
  }

  window.location.href = response.redirectUrl
  return response
}

// Sends an email OTP for admin login.
export async function sendAdminEmailOtp(email: string): Promise<AdminEmailOtpSendResponse> {
  await prepareAdminAuthorizationRequest()

  return adminFetchJson<AdminEmailOtpSendResponse>('/login/email/send-otp', {
    method: 'POST',
    body: { email },
  })
}

// Verifies an admin email OTP and redirects to the authorization flow.
export async function verifyAdminEmailOtp(email: string, otp: string): Promise<AdminEmailOtpVerifyResponse> {
  const response = await adminFetchJson<AdminEmailOtpVerifyResponse>('/login/email/verify', {
    method: 'POST',
    body: { email, otp },
  })

  if (!response.redirectUrl) {
    throw new Error(ADMIN_ERROR_MESSAGES.LOGIN_REDIRECT_PATH_NOT_FOUND)
  }

  window.location.href = response.redirectUrl
  return response
}

// Prepares the server-side authorization request before credential submission.
async function prepareAdminAuthorizationRequest() {
  const response = await fetch(`${ADMIN_GATEWAY_BASE_URL}/admin-bff/auth/login`, {
    credentials: 'include',
    headers: {
      Accept: 'text/html',
    },
  })

  if (!response.ok) {
    throw new Error(ADMIN_ERROR_MESSAGES.LOGIN_SESSION_CREATE_FAILED)
  }
}
