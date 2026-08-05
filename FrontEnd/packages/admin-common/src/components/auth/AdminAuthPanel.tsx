import { useState } from 'react'
import AdminLoginForm from './AdminLoginForm'

// Coordinates the admin login form.
function AdminAuthPanel() {
  const [message, setMessage] = useState('')
  const [pending, setPending] = useState(false)

  return (
    <section className="admin-auth-shell">
      <div className="admin-auth-copy">
        <span className="admin-pill">Admin Console</span>
        <h1>Admin login</h1>
      </div>

      <div className="admin-auth-card">
        <AdminLoginForm
          defaultLoginId=""
          pending={pending}
          onPendingChange={setPending}
          onMessageChange={setMessage}
        />

        {message && <p className="admin-message">{message}</p>}
      </div>
    </section>
  )
}

export default AdminAuthPanel
