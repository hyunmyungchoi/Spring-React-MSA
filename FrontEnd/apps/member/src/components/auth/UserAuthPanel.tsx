import { useState } from "react";
import type { SignupResponse } from "../../types/signup";
import UserLoginForm from "./UserLoginForm";
import UserSignupForm from "./UserSignupForm";

type AuthMode = "login" | "signup";

// Coordinates the member login and signup forms.
function UserAuthPanel() {
    const [mode, setMode] = useState<AuthMode>("login");
    const [defaultLoginId, setDefaultLoginId] = useState("user");
    const [message, setMessage] = useState("");
    const [pending, setPending] = useState(false);

    const handleSignupSuccess = (response: SignupResponse) => {
        setDefaultLoginId(response.loginId);
        setMode("login");
        setMessage("Signup complete. Sign in with the new member account.");
    };

    return (
        <section className="auth-panel">
            <div className="auth-copy">
                <span className="auth-badge">Member Console</span>
                <h1>Member sign in</h1>
                <p>Sign in to choose Community or Stock services.</p>
            </div>

            <div className="auth-box">
                <div className="segmented-control" aria-label="auth mode">
                    <button
                        type="button"
                        className={mode === "login" ? "active" : ""}
                        onClick={() => setMode("login")}
                    >
                        Sign in
                    </button>
                    <button
                        type="button"
                        className={mode === "signup" ? "active" : ""}
                        onClick={() => setMode("signup")}
                    >
                        Sign up
                    </button>
                </div>

                {mode === "login" ? (
                    <UserLoginForm
                        defaultLoginId={defaultLoginId}
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
