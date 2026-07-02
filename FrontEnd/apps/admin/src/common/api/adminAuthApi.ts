import type {
  AdminLogoutResponse,
  AdminPasswordLoginResponse,
  AdminSignupRequest,
  AdminSignupResponse,
} from '../types/adminAuth'
import type { AdminApiResponse } from '../types/adminResponse'
import type { AdminMeResponse } from '../types/adminSession'
import { adminFetchJson } from './adminFetch'

const ADMIN_OAUTH2_LOGIN_PATH = '/admin-bff/oauth2/authorization/admin-bff'

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
export async function signupAdmin(request: AdminSignupRequest): Promise<AdminSignupResponse> {
  const response = await adminFetchJson<AdminApiResponse<AdminSignupResponse>>('/admin-bff/registration/admin', {
    method: 'POST',
    body: request,
  })
  return response.data
}

// Starts an admin password login and redirects to the authorization flow.
export async function loginAdminWithPassword(loginId: string, password: string): Promise<AdminPasswordLoginResponse> {
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

  const redirectUrl = response.redirectUrl ?? ADMIN_OAUTH2_LOGIN_PATH
  redirectToAdminOAuth2Login(redirectUrl)

  return {
    ...response,
    redirectUrl,
  }
}

function redirectToAdminOAuth2Login(redirectUrl: string) {
  window.location.href = redirectUrl
}
