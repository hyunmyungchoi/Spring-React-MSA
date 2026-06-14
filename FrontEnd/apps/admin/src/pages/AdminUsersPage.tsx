import AdminUserActionPanel from '../components/AdminUserActionPanel'
import AdminUsersSections from '../components/AdminUsersSections'
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

            <AdminUsersSections
                adminUsers={adminUsers}
                adminUserDetail={adminUserDetail}
                message={message}
            />
        </>
    )
}

export default AdminUsersPage