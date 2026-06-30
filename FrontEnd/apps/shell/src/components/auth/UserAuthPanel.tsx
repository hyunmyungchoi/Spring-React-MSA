import { useState } from "react";
import type { SignupResponse } from "../../types/signup";
import UserLoginForm from "./UserLoginForm";
import UserSignupForm from "./UserSignupForm";

type AuthMode = "login" | "signup";

// Coordinates the user login and signup forms.
function UserAuthPanel() {
    const [mode, setMode] = useState<AuthMode>("login");
    const [defaultLoginId, setDefaultLoginId] = useState("user");
    const [defaultEmail, setDefaultEmail] = useState("user@test.com");
    const [message, setMessage] = useState("");
    const [pending, setPending] = useState(false);

    // Moves successful signup users back to the login form.
    const handleSignupSuccess = (response: SignupResponse) => {
        setDefaultLoginId(response.loginId);
        setDefaultEmail(response.email);
        setMode("login");
        setMessage("회원가입이 완료되었습니다. 로그인해주세요.");
    };

    return (
        <section className="auth-panel">
            <div className="auth-copy">
                <span className="auth-badge">User Console</span>
                <h1>사용자 로그인</h1>
                <p>커뮤니티와 스톡 서비스를 이용하려면 먼저 로그인해주세요.</p>
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
                    <UserLoginForm
                        defaultLoginId={defaultLoginId}
                        defaultEmail={defaultEmail}
                        pending={pending}
                        onPendingChange={setPending}
                        onMessageChange={setMessage}
                    />
                ) : (
                    <UserSignupForm
                        pending={pending}
                        onPendingChange={setPending}
                        onMessageChange={setMessage}
                        onSignupSuccess={handleSignupSuccess}
                    />
                )}

                {message && <p className="auth-message">{message}</p>}
            </div>
        </section>
    );
}

export default UserAuthPanel;
