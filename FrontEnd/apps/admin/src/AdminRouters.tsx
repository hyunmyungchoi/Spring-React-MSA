import { Navigate, Route, Routes } from 'react-router-dom'
import AdminAuthLayout from './common/layouts/AdminAuthLayout'
import AdminLayout from './common/layouts/AdminLayout'
import AdminAuthPage from './common/pages/AdminAuthPage'
import ManageHomePage from './common/pages/ManageHomePage'
import ManageLogsPage from './logs/pages/ManageLogsPage'
import ManageUsersPage from './users/pages/ManageUsersPage'

// Defines the admin web page routers.
function AdminRouters() {
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

export default AdminRouters
