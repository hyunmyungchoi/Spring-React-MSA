import { useEffect } from 'react'
import { Navigate, Outlet } from 'react-router-dom'
import AdminNavbar from '../components/layout/AdminNavbar'
import { useAdminMe } from '../hooks/useAdminMe'

// Wraps authenticated admin pages and guards them with session state.
function AdminLayout() {
  const { isAuthenticated, loading, loadMe } = useAdminMe()

  useEffect(() => {
    void loadMe()
  }, [loadMe])

  if (loading) {
    return <div className="admin-loader">Loading...</div>
  }

  if (!isAuthenticated) {
    return <Navigate to="/auth" replace />
  }

  return (
    <main className="admin-shell">
      <AdminNavbar />
      <Outlet />
    </main>
  )
}

export default AdminLayout
