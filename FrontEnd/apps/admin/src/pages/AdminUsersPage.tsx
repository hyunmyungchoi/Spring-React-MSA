import AdminActionPanel from '../components/AdminActionPanel'
import AdminUserDetailCard from '../components/AdminUserDetailCard'
import AdminUsersTable from '../components/AdminUsersTable'
import { useAdminDashboard } from '../hooks/useAdminDashboard'

function AdminUsersPage() {
    const {
        message,
        adminUsers,
        adminUserDetail,
        adminUserId,
        setAdminUserId,
        login,
        loadMe,
        logout,
        loadUserMe,
        loadAdminUsers,
        loadAdminUserDetail,
    } = useAdminDashboard()

    return (
        <>
            <AdminActionPanel
                adminUserId={adminUserId}
                onAdminUserIdChange={setAdminUserId}
                onLogin={login}
                onLoadMe={loadMe}
                onLogout={logout}
                onLoadUserMe={loadUserMe}
                onLoadAdminUsers={loadAdminUsers}
                onLoadAdminUserDetail={loadAdminUserDetail}
            />

            <section>
                <h2>Admin Users</h2>
                <AdminUsersTable users={adminUsers} />
            </section>

            <section>
                <h2>Admin User Detail</h2>
                <AdminUserDetailCard user={adminUserDetail} />
            </section>

            <section>
                <h2>Message</h2>
                <pre>{message || 'No message'}</pre>
            </section>
        </>
    )
}

export default AdminUsersPage