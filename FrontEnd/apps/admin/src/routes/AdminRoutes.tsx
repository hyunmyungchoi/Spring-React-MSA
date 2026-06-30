import { Navigate, Route, Routes } from 'react-router-dom'
import AdminAuthLayout from '../layouts/AdminAuthLayout'
import AdminLayout from '../layouts/AdminLayout'
import AdminAuthPage from '../pages/AdminAuthPage'
import ManageHomePage from '../pages/ManageHomePage'
import ManageUsersPage from '../pages/ManageUsersPage'

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
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default AdminRoutes
