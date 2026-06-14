import { Navigate, Route, Routes } from 'react-router-dom'
import AdminHomePage from '../pages/AdminHomePage'

function AdminRoutes() {
    return (
        <Routes>
            <Route path="/" element={<AdminHomePage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
    )
}

export default AdminRoutes