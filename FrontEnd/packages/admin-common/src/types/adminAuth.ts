import type { AdminSessionMe } from './adminSession'

export type AdminLogoutResponse = {
  logout: string
  authServerLogoutRequired?: boolean
  authServerLogoutUrl?: string
}

export type AdminPasswordLoginResponse = {
  authenticated: boolean
  redirectUrl?: string
  user?: AdminSessionMe
}
