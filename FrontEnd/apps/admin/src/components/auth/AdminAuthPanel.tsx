import { useState } from 'react'
import type { AdminSignupResponse } from '../../types/adminAuth'
import AdminLoginForm from './AdminLoginForm'
import AdminSignupForm from './AdminSignupForm'

type AdminAuthMode = 'login' | 'signup'

// Coordinates the admin login and signup forms.
function AdminAuthPanel() {
  const [mode, setMode] = useState<AdminAuthMode>('login')
  const [defaultLoginId, setDefaultLoginId] = useState('admin')
  const [message, setMessage] = useState('')
  const [pending, setPending] = useState(false)

  // Moves successful signup admins back to the login form.
  const handleSignupSuccess = (response: AdminSignupResponse) => {
    setDefaultLoginId(response.loginId)
    setMode('login')
    setMessage('관리자 회원가입이 완료되었습니다. 로그인해주세요.')
  }

  return (
    <section className="admin-auth-shell">
      <div className="admin-auth-copy">
        <span className="admin-pill">Admin Console</span>
        <h1>관리자 로그인</h1>
        <p>ROLE_ADMIN 계정으로 사용자 조회와 관리 기능에 접근합니다.</p>
      </div>

      <div className="admin-auth-card">
        <div className="admin-segmented" aria-label="admin auth mode">
          <button
            type="button"
            className={mode === 'login' ? 'active' : ''}
            onClick={() => setMode('login')}
          >
            로그인
          </button>
          <button
            type="button"
            className={mode === 'signup' ? 'active' : ''}
            onClick={() => setMode('signup')}
          >
            회원가입
          </button>
        </div>

        {mode === 'login' ? (
          <AdminLoginForm
            defaultLoginId={defaultLoginId}
            pending={pending}
            onPendingChange={setPending}
            onMessageChange={setMessage}
          />
        ) : (
          <AdminSignupForm
            pending={pending}
            onPendingChange={setPending}
            onMessageChange={setMessage}
            onSignupSuccess={handleSignupSuccess}
          />
        )}

        {message && <p className="admin-message">{message}</p>}
      </div>
    </section>
  )
}

export default AdminAuthPanel
