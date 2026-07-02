import { useEffect } from 'react'
import { Navigate, Outlet } from 'react-router-dom'
import { useAdminMe } from '../hooks/useAdminMe'

// Wraps admin authentication pages and redirects signed-in admins.
function AdminAuthLayout() {
  const { isAuthenticated, loading, loadMe } = useAdminMe()

  useEffect(() => {
    void loadMe()
  }, [loadMe])

  if (loading) {
    return <div className="admin-loader">Loading...</div>
  }

  if (isAuthenticated) {
    return <Navigate to="/" replace />
  }

  return (
    <main className="admin-auth-page">
      <Outlet />
    </main>
  )
}

export default AdminAuthLayout
