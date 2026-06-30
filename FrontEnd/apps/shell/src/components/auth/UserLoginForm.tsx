import { useState } from "react";
import type { FormEvent } from "react";
import { useUserLogin } from "../../hooks/useUserLogin";
import { USER_ERROR_MESSAGES } from "../../messages/userErrorMessages";

type LoginMethod = "password" | "email";

type UserLoginFormProps = {
    defaultLoginId: string;
    defaultEmail: string;
    pending: boolean;
    onPendingChange: (pending: boolean) => void;
    onMessageChange: (message: string) => void;
};

// Renders user password and email OTP login forms.
function UserLoginForm({ defaultLoginId, defaultEmail, pending, onPendingChange, onMessageChange }: UserLoginFormProps) {
    const { loginWithPassword, sendEmailOtp, verifyEmailOtp } = useUserLogin();
    const [loginMethod, setLoginMethod] = useState<LoginMethod>("password");
    const [passwordLoginId, setPasswordLoginId] = useState(defaultLoginId);
    const [password, setPassword] = useState("password");
    const [email, setEmail] = useState(defaultEmail);
    const [otp, setOtp] = useState("");

    // Submits user password login credentials.
    const handlePasswordLogin = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        onPendingChange(true);
        onMessageChange("");

        try {
            await loginWithPassword(passwordLoginId, password);
        } catch (error) {
            onMessageChange(error instanceof Error ? error.message : USER_ERROR_MESSAGES.LOGIN_FAILED);
            onPendingChange(false);
        }
    };

    // Requests an email OTP for user login.
    const handleSendOtp = async () => {
        onPendingChange(true);
        onMessageChange("");

        try {
            const response = await sendEmailOtp(email);
            setOtp(response.devOtp ?? "");
            onMessageChange("인증번호가 전송되었습니다.");
        } catch (error) {
            onMessageChange(error instanceof Error ? error.message : USER_ERROR_MESSAGES.EMAIL_OTP_SEND_FAILED);
        } finally {
            onPendingChange(false);
        }
    };

    // Submits the email OTP for user login.
    const handleVerifyOtp = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        onPendingChange(true);
        onMessageChange("");

        try {
            await verifyEmailOtp(email, otp);
        } catch (error) {
            onMessageChange(error instanceof Error ? error.message : USER_ERROR_MESSAGES.EMAIL_LOGIN_FAILED);
            onPendingChange(false);
        }
    };

    return (
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
    );
}

export default UserLoginForm;
