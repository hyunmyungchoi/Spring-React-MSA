import type { AdminSessionMe } from './adminSession'

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
}

export type AdminSignupResponse = {
  userId: number
  loginId: string
  email: string
  username: string
  enabled: boolean
  roles: string[]
}

export type AdminPasswordLoginResponse = {
  authenticated: boolean
  redirectUrl?: string
  user?: AdminSessionMe
}
