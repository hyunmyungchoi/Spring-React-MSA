import { isValidAdminUserId } from '../../common/utils/adminRouteUtils'

type AdminUserActionPanelProps = {
  userId: string
  onUserIdChange: (userId: string) => void
  onLoadUsers: () => void
  onLoadUserDetail: () => void
  onLoadMemberSessions: () => void
  onLoadMemberPresenceEvents: () => void
}

// Renders admin user lookup actions.
function AdminUserActionPanel({
  userId,
  onUserIdChange,
  onLoadUsers,
  onLoadUserDetail,
  onLoadMemberSessions,
  onLoadMemberPresenceEvents,
}: AdminUserActionPanelProps) {
  const isUserDetailDisabled = !isValidAdminUserId(userId)

  return (
    <div className="admin-user-actions">
      <button type="button" onClick={onLoadUsers}>
        Load users
      </button>

      <label>
        User ID
        <input
          type="number"
          min="1"
          value={userId}
          onChange={(event) => onUserIdChange(event.target.value)}
          placeholder="1"
        />
      </label>

      <button type="button" onClick={onLoadUserDetail} disabled={isUserDetailDisabled}>
        Load detail
      </button>

      <button type="button" onClick={onLoadMemberSessions}>
        Load member sessions
      </button>

      <button type="button" onClick={onLoadMemberPresenceEvents}>
        Load member events
      </button>
    </div>
  )
}

export default AdminUserActionPanel
