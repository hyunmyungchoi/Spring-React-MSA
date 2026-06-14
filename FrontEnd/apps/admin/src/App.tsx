import AdminActionPanel from './components/AdminActionPanel'
import AdminDashboardSections from './components/AdminDashboardSections'
import { useAdminDashboard } from './hook/useAdminDashboard'
import './App.css'

function App() {
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
      <main style={{ padding: 40 }}>
        <h1>Spring MSA Admin Frontend</h1>

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
      </main>
  )
}

export default App