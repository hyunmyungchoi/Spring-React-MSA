export type AdminSessionMe = {
  sub?: string
  name?: string
  userId?: number
  loginId?: string
  email?: string
  roles?: string[]
}

export type AdminMeResponse = {
  authenticated: boolean
  user: AdminSessionMe | null
  reason: string | null
}

export type AdminSessionState = {
  me: AdminSessionMe | null
  isAuthenticated: boolean
  loading: boolean
  error: string | null
}
