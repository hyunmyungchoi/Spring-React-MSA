import type { MemberPresenceEventResponse } from '../../common/types/adminUser'

type AdminMemberPresenceEventTableProps = {
  events: MemberPresenceEventResponse[] | null
}

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  dateStyle: 'short',
  timeStyle: 'medium',
})

// Renders recent member presence events stored in Redis Stream.
function AdminMemberPresenceEventTable({ events }: AdminMemberPresenceEventTableProps) {
  if (!events || events.length === 0) {
    return <p className="admin-empty">No member presence events loaded.</p>
  }

  return (
    <div className="admin-table-wrap">
      <table className="admin-users-table admin-events-table">
        <thead>
          <tr>
            <th>Time</th>
            <th>Event</th>
            <th>Session FP</th>
            <th>User ID</th>
            <th>Login ID</th>
            <th>Username</th>
            <th>Roles</th>
            <th>Stream ID</th>
          </tr>
        </thead>
        <tbody>
          {events.map((event) => (
            <tr key={event.streamId}>
              <td>{formatDate(event.occurredAt)}</td>
              <td>
                <span className={`admin-event-pill ${event.eventType?.toLowerCase() ?? 'unknown'}`}>
                  {event.eventType ?? '-'}
                </span>
              </td>
              <td title={event.sessionFingerprint ?? undefined}>{shortValue(event.sessionFingerprint)}</td>
              <td>{formatValue(event.userId)}</td>
              <td>{formatValue(event.loginId)}</td>
              <td>{formatValue(event.username)}</td>
              <td>{event.roles.length > 0 ? event.roles.join(', ') : '-'}</td>
              <td>{event.streamId}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function shortValue(value: string | null) {
  if (!value) {
    return '-'
  }

  if (value.length <= 12) {
    return value
  }

  return `${value.slice(0, 12)}...`
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

export default AdminMemberPresenceEventTable
