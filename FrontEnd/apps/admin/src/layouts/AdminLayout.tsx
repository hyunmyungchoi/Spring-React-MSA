import { Outlet } from 'react-router-dom'
import AdminHeader from '../components/AdminHeader'

function AdminLayout() {
    return (
        <main style={{ padding: 40 }}>
            <AdminHeader />
            <Outlet />
        </main>
    )
}

export default AdminLayout