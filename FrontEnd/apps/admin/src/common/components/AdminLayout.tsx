import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import type { AdminMeResponse } from '../../features/auth/adminAuthApi'

export type AdminOutletContext = {
    me: AdminMeResponse
}

type AdminLayoutProps = {
    me: AdminMeResponse
    onLogout: () => void
}

function AdminLayout({ me, onLogout }: AdminLayoutProps) {
    const [showProfile, setShowProfile] = useState(false)
    const user = me.user
    const displayName = user?.name ?? user?.loginId ?? user?.email ?? 'Admin'

    return (
        <main className="admin-shell">
            <header className="admin-topbar">
                <div>
                    <span className="admin-pill">Admin Console</span>
                    <h1>관리자 대시보드</h1>
                </div>

                <div className="admin-account">
                    <div className="admin-account-summary">
                        <strong>{displayName}</strong>
                        <span>{user?.email ?? user?.loginId ?? '-'}</span>
                    </div>
                    <button type="button" onClick={() => setShowProfile((current) => !current)}>
                        내정보보기
                    </button>
                    <button type="button" onClick={onLogout}>
                        로그아웃
                    </button>
                </div>
            </header>

            {showProfile && (
                <section className="admin-profile-panel">
                    <dl>
                        <div>
                            <dt>User ID</dt>
                            <dd>{user?.userId ?? '-'}</dd>
                        </div>
                        <div>
                            <dt>Login ID</dt>
                            <dd>{user?.loginId ?? '-'}</dd>
                        </div>
                        <div>
                            <dt>Email</dt>
                            <dd>{user?.email ?? '-'}</dd>
                        </div>
                        <div>
                            <dt>Roles</dt>
                            <dd>{user?.roles?.join(', ') ?? '-'}</dd>
                        </div>
                    </dl>
                </section>
            )}

            <nav className="admin-nav">
                <NavLink to="/">홈</NavLink>
                <NavLink to="/manage/users">Manage</NavLink>
            </nav>

            <Outlet context={{ me } satisfies AdminOutletContext} />
        </main>
    )
}

export default AdminLayout
