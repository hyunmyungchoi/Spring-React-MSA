import { NavLink } from 'react-router-dom'
import { useAdminLogout } from '../../hooks/useAdminLogout'
import { useAdminMe } from '../../hooks/useAdminMe'

// Renders admin navigation and account actions.
function AdminNavbar() {
  const { me, loading } = useAdminMe()
  const logout = useAdminLogout()
  const displayName = me?.name ?? me?.loginId ?? me?.email ?? 'Admin'

  return (
    <>
      <header className="admin-topbar">
        <div>
          <span className="admin-pill">Admin Console</span>
          <h1>관리자 대시보드</h1>
        </div>

        <div className="admin-account">
          <div className="admin-account-summary">
            <strong>{displayName}</strong>
            <span>{me?.email ?? me?.loginId ?? '-'}</span>
          </div>
          <button type="button" onClick={logout} disabled={loading}>
            로그아웃
          </button>
        </div>
      </header>

      <nav className="admin-nav" aria-label="admin navigation">
        <NavLink to="/">홈</NavLink>
        <NavLink to="/manage/users">유저 관리</NavLink>
        <NavLink to="/manage/logs">로그 관리</NavLink>
      </nav>
    </>
  )
}

export default AdminNavbar
