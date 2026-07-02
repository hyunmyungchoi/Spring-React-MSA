import { Navigate, Route, Routes } from 'react-router-dom'
import AdminAuthLayout from '../layouts/AdminAuthLayout'
import AdminLayout from '../layouts/AdminLayout'
import ManageLogsPage from '../logs/pages/ManageLogsPage'
import AdminAuthPage from '../pages/AdminAuthPage'
import ManageHomePage from '../pages/ManageHomePage'
import ManageUsersPage from '../users/pages/ManageUsersPage'

// Defines the admin web page routes.
function AdminRoutes() {
  return (
    <Routes>
      <Route element={<AdminAuthLayout />}>
        <Route path="/auth" element={<AdminAuthPage />} />
      </Route>

      <Route element={<AdminLayout />}>
        <Route path="/" element={<ManageHomePage />} />
        <Route path="/manage/users" element={<ManageUsersPage />} />
        <Route path="/manage/logs" element={<ManageLogsPage />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default AdminRoutes
