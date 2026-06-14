type AdminUserActionPanelProps = {
    adminUserId: string
    onAdminUserIdChange: (adminUserId: string) => void
    onLoadAdminUsers: () => void
    onLoadAdminUserDetail: () => void
}

function AdminUserActionPanel({
                                  adminUserId,
                                  onAdminUserIdChange,
                                  onLoadAdminUsers,
                                  onLoadAdminUserDetail,
                              }: AdminUserActionPanelProps) {
    const trimmedAdminUserId = adminUserId.trim()
    const isAdminUserDetailDisabled = !/^\d+$/.test(trimmedAdminUserId)

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