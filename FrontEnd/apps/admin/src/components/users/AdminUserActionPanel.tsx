import { isValidAdminUserId } from '../../utils/adminRouteUtils'

type AdminUserActionPanelProps = {
  userId: string
  onUserIdChange: (userId: string) => void
  onLoadUsers: () => void
  onLoadUserDetail: () => void
}

// Renders admin user lookup actions.
function AdminUserActionPanel({ userId, onUserIdChange, onLoadUsers, onLoadUserDetail }: AdminUserActionPanelProps) {
  const isUserDetailDisabled = !isValidAdminUserId(userId)

  return (
    <div className="admin-user-actions">
      <button type="button" onClick={onLoadUsers}>
        유저 목록 조회
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
        상세 조회
      </button>
    </div>
  )
}

export default AdminUserActionPanel
