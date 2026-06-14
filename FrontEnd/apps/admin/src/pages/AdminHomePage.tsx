import AdminActionPanel from '../components/AdminActionPanel'
import AdminDashboardSections from '../components/AdminDashboardSections'
import { useAdminDashboard } from '../hooks/useAdminDashboard'

function AdminHomePage() {
    const {
        me,
        message,
        userMe,
        login,
        loadMe,
        logout,
        loadUserMe,
    } = useAdminDashboard()

    return (
        <>
            <AdminActionPanel
                onLogin={login}
                onLoadMe={loadMe}
                onLogout={logout}
                onLoadUserMe={loadUserMe}
            />

            <AdminDashboardSections
                me={me}
                userMe={userMe}
                message={message}
            />
        </>
    )
}

export default AdminHomePage