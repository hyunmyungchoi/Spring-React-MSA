import { Navigate, Route, Routes } from 'react-router-dom'
import AdminAuthLayout from '@springmsa/admin-common/layouts/AdminAuthLayout'
import AdminLayout from '@springmsa/admin-common/layouts/AdminLayout'
import AdminAuthPage from '@springmsa/admin-common/pages/AdminAuthPage'
import ManageHomePage from '@springmsa/admin-common/pages/ManageHomePage'

function AdminRouters() {
  return (
    <Routes>
      <Route element={<AdminAuthLayout />}>
        <Route path="/auth" element={<AdminAuthPage />} />
      </Route>
      <Route element={<AdminLayout />}>
        <Route path="/" element={<ManageHomePage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default AdminRouters
