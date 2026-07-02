export type AdminUserResponse = {
  userId: number
  loginId: string
  email: string
  username: string
  enabled: boolean
  roles: string[]
}

export type MemberSessionResponse = {
  sessionId: string
  userId: number | null
  loginId: string | null
  name: string | null
  username: string | null
  email: string | null
  roles: string[]
  createdAt: string | null
  lastAccessedAt: string | null
  maxInactiveIntervalSeconds: number
  expiresAt: string | null
  online: boolean
  lastHeartbeatAt: string | null
  onlineExpiresAt: string | null
  onlineTtlSeconds: number | null
}

export type MemberPresenceEventResponse = {
  streamId: string
  eventType: string | null
  sessionFingerprint: string | null
  userId: number | null
  loginId: string | null
  username: string | null
  roles: string[]
  occurredAt: string | null
}
