import AdminActionPanel from '../components/AdminActionPanel'
import AdminDashboardSections from '../components/AdminDashboardSections'
import { useAdminDashboard } from '../hooks/useAdminDashboard'

function AdminHomePage() {
    const {
        me,
        message,
        userMe,
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

            <AdminDashboardSections
                me={me}
                userMe={userMe}
                adminUsers={adminUsers}
                adminUserDetail={adminUserDetail}
                message={message}
            />
        </>
    )
}

export default AdminHomePage