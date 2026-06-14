import AdminUserActionPanel from '../components/AdminUserActionPanel'
import AdminUserDetailCard from '../components/AdminUserDetailCard'
import AdminUsersTable from '../components/AdminUsersTable'
import { useAdminUsers } from '../hooks/useAdminUsers'

function AdminUsersPage() {
    const {
        message,
        adminUsers,
        adminUserDetail,
        adminUserId,
        setAdminUserId,
        loadAdminUsers,
        loadAdminUserDetail,
    } = useAdminUsers()

    return (
        <>
            <AdminUserActionPanel
                adminUserId={adminUserId}
                onAdminUserIdChange={setAdminUserId}
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