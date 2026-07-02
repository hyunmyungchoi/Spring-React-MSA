import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { Provider } from 'react-redux'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import '../App.css'
import '../index.css'
import AdminAuthLayout from '../common/layouts/AdminAuthLayout'
import AdminLayout from '../common/layouts/AdminLayout'
import AdminAuthPage from '../common/pages/AdminAuthPage'
import { adminStore } from '../common/store/adminStore'
import ManageUsersPage from './pages/ManageUsersPage'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Provider store={adminStore}>
      <BrowserRouter>
        <Routes>
          <Route element={<AdminAuthLayout />}>
            <Route path="/auth" element={<AdminAuthPage />} />
          </Route>
          <Route element={<AdminLayout />}>
            <Route path="*" element={<ManageUsersPage />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </Provider>
  </StrictMode>,
)
