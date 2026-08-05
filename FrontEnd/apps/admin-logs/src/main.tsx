import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { Provider } from 'react-redux'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import '@springmsa/admin-common/App.css'
import '@springmsa/admin-common/index.css'
import AdminAuthLayout from '@springmsa/admin-common/layouts/AdminAuthLayout'
import AdminLayout from '@springmsa/admin-common/layouts/AdminLayout'
import AdminAuthPage from '@springmsa/admin-common/pages/AdminAuthPage'
import { adminStore } from '@springmsa/admin-common/store/adminStore'
import ManageLogsPage from './pages/ManageLogsPage'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Provider store={adminStore}>
      <BrowserRouter>
        <Routes>
          <Route element={<AdminAuthLayout />}>
            <Route path="/auth" element={<AdminAuthPage />} />
          </Route>
          <Route element={<AdminLayout />}>
            <Route path="*" element={<ManageLogsPage />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </Provider>
  </StrictMode>,
)

