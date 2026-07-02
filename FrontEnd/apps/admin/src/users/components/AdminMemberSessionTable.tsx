import type { MemberSessionResponse } from '../../common/types/adminUser'

type AdminMemberSessionTableProps = {
  sessions: MemberSessionResponse[] | null
}

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  dateStyle: 'short',
  timeStyle: 'medium',
})

// Renders member BFF login sessions backed by Spring Session Redis.
function AdminMemberSessionTable({ sessions }: AdminMemberSessionTableProps) {
  if (!sessions || sessions.length === 0) {
    return <p className="admin-empty">No member sessions loaded.</p>
  }

  return (
    <div className="admin-table-wrap">
      <table className="admin-users-table admin-sessions-table">
        <thead>
          <tr>
            <th>Session</th>
            <th>Status</th>
            <th>User ID</th>
            <th>Login ID</th>
            <th>Username</th>
            <th>Email</th>
            <th>Roles</th>
            <th>Last heartbeat</th>
            <th>Last accessed</th>
            <th>Expires</th>
          </tr>
        </thead>
        <tbody>
          {sessions.map((session) => (
            <tr key={session.sessionId}>
              <td title={session.sessionId}>{shortSessionId(session.sessionId)}</td>
              <td>
                <span className={`admin-status-pill ${session.online ? 'online' : 'offline'}`}>
                  {session.online ? 'Online' : 'Offline'}
                </span>
              </td>
              <td>{formatValue(session.userId)}</td>
              <td>{formatValue(session.loginId)}</td>
              <td>{formatValue(session.username ?? session.name)}</td>
              <td>{formatValue(session.email)}</td>
              <td>{session.roles.length > 0 ? session.roles.join(', ') : '-'}</td>
              <td>{formatDate(session.lastHeartbeatAt)}</td>
              <td>{formatDate(session.lastAccessedAt)}</td>
              <td>{formatDate(session.online ? session.onlineExpiresAt : session.expiresAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function shortSessionId(sessionId: string) {
  if (sessionId.length <= 12) {
    return sessionId
  }

  return `${sessionId.slice(0, 12)}...`
}

function formatValue(value: string | number | null) {
  return value ?? '-'
}

function formatDate(value: string | null) {
  if (!value) {
    return '-'
  }

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value
  }

  return dateFormatter.format(date)
}

export default AdminMemberSessionTable
