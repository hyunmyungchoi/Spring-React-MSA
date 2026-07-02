import { useState } from 'react'
import type { FormEvent } from 'react'
import { useAdminLogin } from '../../hooks/useAdminLogin'
import { ADMIN_ERROR_MESSAGES } from '../../messages/adminErrorMessages'

type AdminLoginFormProps = {
  defaultLoginId: string
  pending: boolean
  onPendingChange: (pending: boolean) => void
  onMessageChange: (message: string) => void
}

// Renders the admin password login form.
function AdminLoginForm({ defaultLoginId, pending, onPendingChange, onMessageChange }: AdminLoginFormProps) {
  const { loginWithPassword } = useAdminLogin()
  const [passwordLoginId, setPasswordLoginId] = useState(defaultLoginId)
  const [password, setPassword] = useState('password')

  const handlePasswordLogin = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    onPendingChange(true)
    onMessageChange('')

    try {
      await loginWithPassword(passwordLoginId, password)
    } catch (error) {
      onMessageChange(error instanceof Error ? error.message : ADMIN_ERROR_MESSAGES.LOGIN_FAILED)
      onPendingChange(false)
    }
  }

  return (
    <form className="admin-auth-form" onSubmit={handlePasswordLogin}>
      <label>
        ID
        <input
          type="text"
          value={passwordLoginId}
          onChange={(event) => setPasswordLoginId(event.target.value)}
          placeholder="admin"
          required
        />
      </label>
      <label>
        Password
        <input
          type="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          placeholder="password"
          required
        />
      </label>
      <button className="admin-primary-button" type="submit" disabled={pending}>
        Login
      </button>
    </form>
  )
}

export default AdminLoginForm
