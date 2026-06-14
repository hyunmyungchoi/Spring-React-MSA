import { Outlet } from 'react-router-dom'
import AdminHeader from '../components/AdminHeader'
import AdminNav from '../components/AdminNav'

function AdminLayout() {
    return (
        <main style={{ padding: 40 }}>
            <AdminHeader />
            <AdminNav />
            <Outlet />
        </main>
    )
}

export default AdminLayout