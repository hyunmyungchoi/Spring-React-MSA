import { useState } from "react";
import type { FormEvent } from "react";
import { Navigate } from "react-router-dom";
import { useAppSelector } from "../../app/reduxHooks";
import {
    loginWithPassword,
    sendEmailOtp,
    signup,
    verifyEmailOtp,
} from "../../features/auth/authApi";

type AuthMode = "login" | "signup";
type LoginMethod = "password" | "email";

function LoginPage() {
    const { authenticated, loading } = useAppSelector((state) => state.auth);
    const [mode, setMode] = useState<AuthMode>("login");
    const [loginMethod, setLoginMethod] = useState<LoginMethod>("password");
    const [passwordLoginId, setPasswordLoginId] = useState("user");
    const [password, setPassword] = useState("password");
    const [email, setEmail] = useState("user@test.com");
    const [otp, setOtp] = useState("");
    const [signupLoginId, setSignupLoginId] = useState("");
    const [signupEmail, setSignupEmail] = useState("");
    const [signupPassword, setSignupPassword] = useState("");
    const [username, setUsername] = useState("");
    const [phoneNumber, setPhoneNumber] = useState("");
    const [message, setMessage] = useState("");
    const [pending, setPending] = useState(false);

    if (!loading && authenticated) {
        return <Navigate to="/" replace />;
    }

    const handlePasswordLogin = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setPending(true);
        setMessage("");

        try {
            await loginWithPassword(passwordLoginId, password);
        } catch (error) {
            setMessage(error instanceof Error ? error.message : "아이디 로그인 실패");
            setPending(false);
        }
    };

    const handleSendOtp = async () => {
        setPending(true);
        setMessage("");

        try {
            const response = await sendEmailOtp(email);
            setOtp(response.devOtp ?? "");
            setMessage("인증번호가 전송되었습니다.");
        } catch (error) {
            setMessage(error instanceof Error ? error.message : "이메일 인증 요청 실패");
        } finally {
            setPending(false);
        }
    };

    const handleVerifyOtp = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setPending(true);
        setMessage("");

        try {
            await verifyEmailOtp(email, otp);
        } catch (error) {
            setMessage(error instanceof Error ? error.message : "이메일 로그인 실패");
            setPending(false);
        }
    };

    const handleSignup = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setPending(true);
        setMessage("");

        try {
            const response = await signup({
                loginId: signupLoginId,
                email: signupEmail,
                password: signupPassword,
                username,
                phoneNumber,
                whatsappNumber: phoneNumber,
            });

            setPasswordLoginId(response.loginId);
            setEmail(response.email);
            setMode("login");
            setLoginMethod("password");
            setMessage("회원가입이 완료되었습니다.");
        } catch (error) {
            setMessage(error instanceof Error ? error.message : "회원가입 실패");
        } finally {
            setPending(false);
        }
    };

    return (
        <main className="auth-page user-auth-page">
            <section className="auth-panel">
                <div className="auth-copy">
                    <span className="auth-badge">User Console</span>
                    <h1>일반 유저 로그인</h1>
                    <p>커뮤니티와 스톡 서비스를 이용하려면 먼저 로그인하세요.</p>
                </div>

                <div className="auth-box">
                    <div className="segmented-control" aria-label="auth mode">
                        <button
                            type="button"
                            className={mode === "login" ? "active" : ""}
                            onClick={() => setMode("login")}
                        >
                            로그인
                        </button>
                        <button
                            type="button"
                            className={mode === "signup" ? "active" : ""}
                            onClick={() => setMode("signup")}
                        >
                            회원가입
                        </button>
                    </div>

                    {mode === "login" ? (
                        <>
                            <div className="method-control" aria-label="login method">
                                <button
                                    type="button"
                                    className={loginMethod === "password" ? "active" : ""}
                                    onClick={() => setLoginMethod("password")}
                                >
                                    아이디
                                </button>
                                <button
                                    type="button"
                                    className={loginMethod === "email" ? "active" : ""}
                                    onClick={() => setLoginMethod("email")}
                                >
                                    이메일
                                </button>
                            </div>

                            {loginMethod === "password" ? (
                                <form className="auth-form" onSubmit={handlePasswordLogin}>
                                    <label>
                                        아이디
                                        <input
                                            type="text"
                                            value={passwordLoginId}
                                            onChange={(event) => setPasswordLoginId(event.target.value)}
                                            placeholder="user"
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
                                    <button className="primary-button" type="submit" disabled={pending}>
                                        아이디 로그인
                                    </button>
                                </form>
                            ) : (
                                <form className="auth-form" onSubmit={handleVerifyOtp}>
                                    <label>
                                        이메일
                                        <input
                                            type="email"
                                            value={email}
                                            onChange={(event) => setEmail(event.target.value)}
                                            placeholder="user@test.com"
                                            required
                                        />
                                    </label>

                                    <div className="inline-field">
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

                                    <button className="primary-button" type="submit" disabled={pending}>
                                        이메일 로그인
                                    </button>
                                </form>
                            )}
                        </>
                    ) : (
                        <form className="auth-form" onSubmit={handleSignup}>
                            <label>
                                아이디
                                <input
                                    type="text"
                                    value={signupLoginId}
                                    onChange={(event) => setSignupLoginId(event.target.value)}
                                    placeholder="user01"
                                    required
                                />
                            </label>
                            <label>
                                이메일
                                <input
                                    type="email"
                                    value={signupEmail}
                                    onChange={(event) => setSignupEmail(event.target.value)}
                                    placeholder="user@example.com"
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
                                    placeholder="홍길동"
                                    required
                                />
                            </label>
                            <label>
                                휴대폰
                                <input
                                    type="tel"
                                    value={phoneNumber}
                                    onChange={(event) => setPhoneNumber(event.target.value)}
                                    placeholder="01012345678"
                                />
                            </label>

                            <button className="primary-button" type="submit" disabled={pending}>
                                회원가입
                            </button>
                        </form>
                    )}

                    {message && <p className="auth-message">{message}</p>}
                </div>
            </section>
        </main>
    );
}

export default LoginPage;
