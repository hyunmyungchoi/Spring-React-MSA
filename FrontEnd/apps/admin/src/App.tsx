import { useEffect, useState } from 'react'
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

const getInitialMessage = (): string => {
  const params = new URLSearchParams(window.location.search)
  const error = params.get('error')

  return error ? `Admin login failed: ${error}` : ''
}

const fetchAdminMe = async (signal?: AbortSignal): Promise<AdminMeResponse> => {
  const response = await fetch(`${ADMIN_GATEWAY_BASE_URL}/admin-bff/auth/me`, {
    method: 'GET',
    credentials: 'include',
    signal
  })

  return (await response.json()) as AdminMeResponse
}



function App() {
  const [me, setMe] = useState<AdminMeResponse | null>(null)
  const [message, setMessage] = useState<string>(getInitialMessage)
  const [userMe, setUserMe] = useState<unknown>(null)

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

    const response = await fetch(`${ADMIN_GATEWAY_BASE_URL}/admin-bff/auth/logout`, {
      method: 'POST',
      credentials: 'include'
    })

    const data = (await response.json()) as AdminLogoutResponse

    setMe(null)
    setMessage(JSON.stringify(data, null, 2))

    if (data.authServerLogoutUrl) {
      window.location.href = data.authServerLogoutUrl
    }
  }

  const fetchAdminUserMe = async (signal?: AbortSignal): Promise<unknown> => {
    const response = await fetch(`${ADMIN_GATEWAY_BASE_URL}/admin-bff/user/me`, {
      method: 'GET',
      credentials: 'include',
      signal,
    })

    return await response.json()
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

        <div style={{ display: 'flex', gap: 12, marginBottom: 24 }}>
          <button onClick={login}>Admin Login</button>
          <button onClick={loadMe}>Admin Me</button>
          <button onClick={logout}>Admin Logout</button>
          <button onClick={loadUserMe}>Admin User Me</button>
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
          <h2>Message</h2>
          <pre>{message || 'No message'}</pre>
        </section>
      </main>
  )
}

export default App