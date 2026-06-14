type AdminActionPanelProps = {
    onLogin: () => void
    onLoadMe: () => void
    onLogout: () => void
    onLoadUserMe: () => void
}

function AdminActionPanel({
                              onLogin,
                              onLoadMe,
                              onLogout,
                              onLoadUserMe,
                          }: AdminActionPanelProps) {
    return (
        <div style={{ display: 'flex', gap: 12, marginBottom: 24, flexWrap: 'wrap' }}>
            <button onClick={onLogin}>Admin Login</button>
            <button onClick={onLoadMe}>Admin Me</button>
            <button onClick={onLogout}>Admin Logout</button>
            <button onClick={onLoadUserMe}>Admin User Me</button>
        </div>
    )
}

export default AdminActionPanel