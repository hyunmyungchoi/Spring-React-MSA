import { useState } from 'react'
import type { FormEvent } from 'react'
import { useAdminSignup } from '../../hooks/useAdminSignup'
import { ADMIN_ERROR_MESSAGES } from '../../messages/adminErrorMessages'
import type { AdminSignupResponse } from '../../types/adminAuth'

type AdminSignupFormProps = {
  pending: boolean
  onPendingChange: (pending: boolean) => void
  onMessageChange: (message: string) => void
  onSignupSuccess: (response: AdminSignupResponse) => void
}

// Renders the admin signup form.
function AdminSignupForm({ pending, onPendingChange, onMessageChange, onSignupSuccess }: AdminSignupFormProps) {
  const { signup } = useAdminSignup()
  const [loginId, setLoginId] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [username, setUsername] = useState('')
  const [phoneNumber, setPhoneNumber] = useState('')

  // Submits a new admin signup request.
  const handleSignup = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    onPendingChange(true)
    onMessageChange('')

    try {
      const response = await signup({
        loginId,
        email,
        password,
        username,
        phoneNumber,
        whatsappNumber: phoneNumber,
      })

      onSignupSuccess(response)
    } catch (error) {
      onMessageChange(error instanceof Error ? error.message : ADMIN_ERROR_MESSAGES.SIGNUP_FAILED)
    } finally {
      onPendingChange(false)
    }
  }

  return (
    <form className="admin-auth-form" onSubmit={handleSignup}>
      <label>
        아이디
        <input
          type="text"
          value={loginId}
          onChange={(event) => setLoginId(event.target.value)}
          placeholder="admin01"
          required
        />
      </label>
      <label>
        이메일
        <input
          type="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          placeholder="admin@example.com"
          required
        />
      </label>
      <label>
        비밀번호
        <input
          type="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          placeholder="password"
          required
          minLength={4}
        />
      </label>
      <label>
        이름
        <input
          type="text"
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          placeholder="Admin User"
          required
        />
      </label>
      <label>
        휴대폰
        <input
          type="tel"
          value={phoneNumber}
          onChange={(event) => setPhoneNumber(event.target.value)}
          placeholder="01012345679"
        />
      </label>

      <button className="admin-primary-button" type="submit" disabled={pending}>
        관리자 회원가입
      </button>
    </form>
  )
}

export default AdminSignupForm
