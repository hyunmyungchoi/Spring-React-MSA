import { Outlet } from 'react-router-dom'

function AdminLayout() {
    return (
        <main style={{ padding: 40 }}>
            <h1>Spring MSA Admin Frontend</h1>
            <Outlet />
        </main>
    )
}

export default AdminLayout