type AdminActionPanelProps = {
    adminUserId: string
    onAdminUserIdChange: (adminUserId: string) => void
    onLogin: () => void
    onLoadMe: () => void
    onLogout: () => void
    onLoadUserMe: () => void
    onLoadAdminUsers: () => void
    onLoadAdminUserDetail: () => void
}

function AdminActionPanel({
                              adminUserId,
                              onAdminUserIdChange,
                              onLogin,
                              onLoadMe,
                              onLogout,
                              onLoadUserMe,
                              onLoadAdminUsers,
                              onLoadAdminUserDetail,
                          }: AdminActionPanelProps) {
    return (
        <div style={{ display: 'flex', gap: 12, marginBottom: 24, flexWrap: 'wrap' }}>
            <button onClick={onLogin}>Admin Login</button>
            <button onClick={onLoadMe}>Admin Me</button>
            <button onClick={onLogout}>Admin Logout</button>
            <button onClick={onLoadUserMe}>Admin User Me</button>
            <button onClick={onLoadAdminUsers}>Admin Users</button>

            <input
                value={adminUserId}
                onChange={(event) => onAdminUserIdChange(event.target.value)}
                placeholder="User ID"
                style={{ width: 80 }}
            />

            <button onClick={onLoadAdminUserDetail}>Admin User Detail</button>
        </div>
    )
}

export default AdminActionPanel