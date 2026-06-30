import { useState } from 'react'
import type { FormEvent } from 'react'
import {
    loginAdminWithPassword,
    sendAdminEmailOtp,
    signupAdmin,
    verifyAdminEmailOtp,
} from '../../features/auth/adminAuthApi'

type AdminAuthMode = 'login' | 'signup'
type AdminLoginMethod = 'password' | 'email'

function AdminLoginPage() {
    const [mode, setMode] = useState<AdminAuthMode>('login')
    const [loginMethod, setLoginMethod] = useState<AdminLoginMethod>('password')
    const [passwordLoginId, setPasswordLoginId] = useState('admin')
    const [password, setPassword] = useState('password')
    const [email, setEmail] = useState('admin@test.com')
    const [otp, setOtp] = useState('')
    const [signupLoginId, setSignupLoginId] = useState('')
    const [signupEmail, setSignupEmail] = useState('')
    const [signupPassword, setSignupPassword] = useState('')
    const [username, setUsername] = useState('')
    const [phoneNumber, setPhoneNumber] = useState('')
    const [message, setMessage] = useState('')
    const [pending, setPending] = useState(false)

    const handlePasswordLogin = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault()
        setPending(true)
        setMessage('')

        try {
            await loginAdminWithPassword(passwordLoginId, password)
        } catch (error) {
            setMessage(error instanceof Error ? error.message : '관리자 아이디 로그인 실패')
            setPending(false)
        }
    }

    const handleSendOtp = async () => {
        setPending(true)
        setMessage('')

        try {
            const response = await sendAdminEmailOtp(email)
            setOtp(response.devOtp ?? '')
            setMessage('관리자 이메일 인증번호가 전송되었습니다.')
        } catch (error) {
            setMessage(error instanceof Error ? error.message : '관리자 이메일 인증 요청 실패')
        } finally {
            setPending(false)
        }
    }

    const handleVerifyOtp = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault()
        setPending(true)
        setMessage('')

        try {
            await verifyAdminEmailOtp(email, otp)
        } catch (error) {
            setMessage(error instanceof Error ? error.message : '관리자 이메일 로그인 실패')
            setPending(false)
        }
    }

    const handleSignup = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault()
        setPending(true)
        setMessage('')

        try {
            const response = await signupAdmin({
                loginId: signupLoginId,
                email: signupEmail,
                password: signupPassword,
                username,
                phoneNumber,
                whatsappNumber: phoneNumber,
            })

            setPasswordLoginId(response.loginId)
            setEmail(response.email)
            setMode('login')
            setLoginMethod('password')
            setMessage('관리자 회원가입이 완료되었습니다.')
        } catch (error) {
            setMessage(error instanceof Error ? error.message : '관리자 회원가입 실패')
        } finally {
            setPending(false)
        }
    }

    return (
        <main className="admin-auth-page">
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
                        <>
                            <div className="admin-method-control" aria-label="admin login method">
                                <button
                                    type="button"
                                    className={loginMethod === 'password' ? 'active' : ''}
                                    onClick={() => setLoginMethod('password')}
                                >
                                    아이디
                                </button>
                                <button
                                    type="button"
                                    className={loginMethod === 'email' ? 'active' : ''}
                                    onClick={() => setLoginMethod('email')}
                                >
                                    이메일
                                </button>
                            </div>

                            {loginMethod === 'password' ? (
                                <form className="admin-auth-form" onSubmit={handlePasswordLogin}>
                                    <label>
                                        아이디
                                        <input
                                            type="text"
                                            value={passwordLoginId}
                                            onChange={(event) => setPasswordLoginId(event.target.value)}
                                            placeholder="admin"
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
                                        />
                                    </label>
                                    <button className="admin-primary-button" type="submit" disabled={pending}>
                                        아이디 로그인
                                    </button>
                                </form>
                            ) : (
                                <form className="admin-auth-form" onSubmit={handleVerifyOtp}>
                                    <label>
                                        이메일
                                        <input
                                            type="email"
                                            value={email}
                                            onChange={(event) => setEmail(event.target.value)}
                                            placeholder="admin@test.com"
                                            required
                                        />
                                    </label>

                                    <div className="admin-inline-field">
                                        <label>
                                            인증번호
                                            <input
                                                type="text"
                                                inputMode="numeric"
                                                maxLength={6}
                                                value={otp}
                                                onChange={(event) => setOtp(event.target.value)}
                                                placeholder="000000"
                                                required
                                            />
                                        </label>
                                        <button type="button" onClick={handleSendOtp} disabled={pending}>
                                            전송
                                        </button>
                                    </div>

                                    <button className="admin-primary-button" type="submit" disabled={pending}>
                                        이메일 로그인
                                    </button>
                                </form>
                            )}
                        </>
                    ) : (
                        <form className="admin-auth-form" onSubmit={handleSignup}>
                            <label>
                                아이디
                                <input
                                    type="text"
                                    value={signupLoginId}
                                    onChange={(event) => setSignupLoginId(event.target.value)}
                                    placeholder="admin01"
                                    required
                                />
                            </label>
                            <label>
                                이메일
                                <input
                                    type="email"
                                    value={signupEmail}
                                    onChange={(event) => setSignupEmail(event.target.value)}
                                    placeholder="admin@example.com"
                                    required
                                />
                            </label>
                            <label>
                                비밀번호
                                <input
                                    type="password"
                                    value={signupPassword}
                                    onChange={(event) => setSignupPassword(event.target.value)}
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
                    )}

                    {message && <p className="admin-message">{message}</p>}
                </div>
            </section>
        </main>
    )
}

export default AdminLoginPage
