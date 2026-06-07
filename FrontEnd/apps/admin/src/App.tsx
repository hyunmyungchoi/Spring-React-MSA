import { useState } from 'react'
import './App.css'

type AdminMeResponse = {
  authenticated?: boolean
  sub?: string
  userId?: number
  loginId?: string
  email?: string
  roles?: string[]
  reason?: string
  user?: null
}

type AdminLogoutResponse = {
  logout: string
  authServerLogoutRequired?: boolean
  authServerLogoutUrl?: string
}

const ADMIN_GATEWAY_BASE_URL = 'http://localhost:8090'

function App() {
  const [me, setMe] = useState<AdminMeResponse | null>(null)
  const [message, setMessage] = useState<string>('')

  const login = () => {
    window.location.href = `${ADMIN_GATEWAY_BASE_URL}/admin-bff/auth/login`
  }

  const loadMe = async () => {
    setMessage('')

    const response = await fetch(`${ADMIN_GATEWAY_BASE_URL}/admin-bff/auth/me`, {
      method: 'GET',
      credentials: 'include'
    })

    const data = await response.json()
    setMe(data)
  }

  const logout = async () => {
    setMessage('')

    const response = await fetch(`${ADMIN_GATEWAY_BASE_URL}/admin-bff/auth/logout`, {
      method: 'POST',
      credentials: 'include'
    })

    const data: AdminLogoutResponse = await response.json()

    setMe(null)
    setMessage(JSON.stringify(data, null, 2))

    if (data.authServerLogoutUrl) {
      window.location.href = data.authServerLogoutUrl
    }
  }

  return (
      <main style={{ padding: 40 }}>
        <h1>Spring MSA Admin Frontend</h1>

        <div style={{ display: 'flex', gap: 12, marginBottom: 24 }}>
          <button onClick={login}>Admin Login</button>
          <button onClick={loadMe}>Admin Me</button>
          <button onClick={logout}>Admin Logout</button>
        </div>

        <section>
          <h2>Admin Me</h2>
          <pre>{me ? JSON.stringify(me, null, 2) : 'No data'}</pre>
        </section>

        <section>
          <h2>Message</h2>
          <pre>{message || 'No message'}</pre>
        </section>
      </main>
  )
}

export default App