import { useEffect, useState } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { getAdminErrorMessage } from '../common/api/adminErrorMessage'
import AdminLayout from '../common/components/AdminLayout'
import {
    fetchAdminMe,
    requestAdminLogout,
    type AdminMeResponse,
} from '../features/auth/adminAuthApi'
import AdminLoginPage from '../pages/auth/AdminLoginPage'
import ManageHomePage from '../pages/manage/ManageHomePage'
import ManageUsersPage from '../pages/manage/ManageUsersPage'

function AdminRoutes() {
    const [me, setMe] = useState<AdminMeResponse | null>(null)
    const [loading, setLoading] = useState(true)
    const [message, setMessage] = useState('')

    useEffect(() => {
        const controller = new AbortController()

        const loadSession = async () => {
            setLoading(true)

            try {
                const data = await fetchAdminMe(controller.signal)
                setMe(data.authenticated ? data : null)
                setMessage(data.reason ?? '')
            } catch (error) {
                setMe(null)
                setMessage(getAdminErrorMessage(error, '관리자 세션을 불러오지 못했습니다.'))
            } finally {
                setLoading(false)
            }
        }

        void loadSession()

        return () => controller.abort()
    }, [])

    const logout = async () => {
        try {
            const data = await requestAdminLogout()
            setMe(null)

            if (data.authServerLogoutUrl) {
                window.location.href = data.authServerLogoutUrl
            }
        } catch (error) {
            setMessage(getAdminErrorMessage(error, '관리자 로그아웃 실패'))
        }
    }

    if (loading) {
        return <div className="admin-loader">Loading...</div>
    }

    if (!me) {
        return (
            <>
                <AdminLoginPage />
                {message && <p className="admin-floating-message">{message}</p>}
            </>
        )
    }

    return (
        <Routes>
            <Route element={<AdminLayout me={me} onLogout={logout} />}>
                <Route path="/" element={<ManageHomePage />} />
                <Route path="/manage/users" element={<ManageUsersPage />} />
            </Route>

            <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
    )
}

export default AdminRoutes
