type AdminUserActionPanelProps = {
    adminUserId: string
    onAdminUserIdChange: (adminUserId: string) => void
    onLoadAdminUsers: () => void
    onLoadAdminUserDetail: () => void
}
import { isValidAdminUserId } from '../utils/adminUserId'

function AdminUserActionPanel({
                                  adminUserId,
                                  onAdminUserIdChange,
                                  onLoadAdminUsers,
                                  onLoadAdminUserDetail,
                              }: AdminUserActionPanelProps) {

    const isAdminUserDetailDisabled = !isValidAdminUserId(adminUserId)

    return (
        <div style={{ display: 'flex', gap: 12, marginBottom: 24, flexWrap: 'wrap' }}>
            <button onClick={onLoadAdminUsers}>Admin Users</button>

            <input
                type="number"
                min="1"
                value={adminUserId}
                onChange={(event) => onAdminUserIdChange(event.target.value)}
                placeholder="User ID"
                style={{ width: 80 }}
            />

            <button
                onClick={onLoadAdminUserDetail}
                disabled={isAdminUserDetailDisabled}
            >
                Admin User Detail
            </button>
        </div>
    )
}

export default AdminUserActionPanel