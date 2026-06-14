import { useEffect, useState } from 'react'
import {
  ADMIN_GATEWAY_BASE_URL,
  fetchAdminMe,
  requestAdminLogout,
  type AdminMeResponse,
} from './api/adminAuthApi'
import {
  fetchAdminUserDetail,
  fetchAdminUserMe,
  fetchAdminUsers,
  type AdminUserResponse,
} from './api/adminUserApi'
import AdminUsersTable from './components/AdminUsersTable'
import './App.css'

const getInitialMessage = (): string => {
  const params = new URLSearchParams(window.location.search)
  const error = params.get('error')

  return error ? `Admin login failed: ${error}` : ''
}

function App() {
  const [me, setMe] = useState<AdminMeResponse | null>(null)
  const [message, setMessage] = useState<string>(getInitialMessage)
  const [userMe, setUserMe] = useState<unknown>(null)

  const [adminUsers, setAdminUsers] = useState<AdminUserResponse[] | null>(null)
  const [adminUserDetail, setAdminUserDetail] = useState<AdminUserResponse | null>(null)
  const [adminUserId, setAdminUserId] = useState<string>('1')

  const login = () => {
    window.location.href = `${ADMIN_GATEWAY_BASE_URL}/admin-bff/auth/login`
  }

  const loadMe = async () => {
    setMessage('')

    const data = await fetchAdminMe()
    setMe(data)
  }

  const logout = async () => {
    setMessage('')

    const data = await requestAdminLogout()

    setMe(null)
    setMessage(JSON.stringify(data, null, 2))

    if (data.authServerLogoutUrl) {
      window.location.href = data.authServerLogoutUrl
    }
  }

  const loadUserMe = async () => {
    setMessage('')

    try {
      const data = await fetchAdminUserMe()
      setUserMe(data)
    } catch {
      setMessage('Failed to load admin user me')
    }
  }

  const loadAdminUsers = async () => {
    setMessage('')

    try {
      const data = await fetchAdminUsers()
      setAdminUsers(data)
    } catch {
      setMessage('Failed to load admin users')
    }
  }

  const loadAdminUserDetail = async () => {
    setMessage('')

    if (!adminUserId.trim()) {
      setMessage('User ID is required')
      return
    }

    try {
      const data = await fetchAdminUserDetail(adminUserId.trim())
      setAdminUserDetail(data)
    } catch {
      setMessage('Failed to load admin user detail')
    }
  }

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const error = params.get('error')

    if (error) {
      window.history.replaceState({}, '', window.location.pathname)
      return
    }

    const controller = new AbortController()
    let ignored = false

    const loadInitialMe = async () => {
      try {
        const data = await fetchAdminMe(controller.signal)

        if (!ignored) {
          setMe(data)
        }
      } catch (error) {
        if (ignored) {
          return
        }

        if (error instanceof DOMException && error.name === 'AbortError') {
          return
        }

        setMessage('Failed to load admin session')
      }
    }

    void loadInitialMe()

    return () => {
      ignored = true
      controller.abort()
    }
  }, [])

  return (
      <main style={{ padding: 40 }}>
        <h1>Spring MSA Admin Frontend</h1>

        <div style={{ display: 'flex', gap: 12, marginBottom: 24, flexWrap: 'wrap' }}>
          <button onClick={login}>Admin Login</button>
          <button onClick={loadMe}>Admin Me</button>
          <button onClick={logout}>Admin Logout</button>
          <button onClick={loadUserMe}>Admin User Me</button>
          <button onClick={loadAdminUsers}>Admin Users</button>

          <input
              value={adminUserId}
              onChange={(event) => setAdminUserId(event.target.value)}
              placeholder="User ID"
              style={{ width: 80 }}
          />
          <button onClick={loadAdminUserDetail}>Admin User Detail</button>
        </div>

        <section>
          <h2>Admin Me</h2>
          <pre>{me ? JSON.stringify(me, null, 2) : 'No data'}</pre>
        </section>

        <section>
          <h2>Admin User Me</h2>
          <pre>{userMe ? JSON.stringify(userMe, null, 2) : 'No data'}</pre>
        </section>

        <section>
          <h2>Admin Users</h2>
          <AdminUsersTable users={adminUsers} />
        </section>

        <section>
          <h2>Admin User Detail</h2>
          <pre>{adminUserDetail ? JSON.stringify(adminUserDetail, null, 2) : 'No data'}</pre>
        </section>

        <section>
          <h2>Message</h2>
          <pre>{message || 'No message'}</pre>
        </section>
      </main>
  )
}

export default App