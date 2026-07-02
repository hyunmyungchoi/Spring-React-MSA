import { useState } from "react";
import type { FormEvent } from "react";
import { useUserLogin } from "../../hooks/useUserLogin";
import { USER_ERROR_MESSAGES } from "../../messages/userErrorMessages";

type UserLoginFormProps = {
    defaultLoginId: string;
    pending: boolean;
    onPendingChange: (pending: boolean) => void;
    onMessageChange: (message: string) => void;
};

// Renders the member password login form.
function UserLoginForm({ defaultLoginId, pending, onPendingChange, onMessageChange }: UserLoginFormProps) {
    const { loginWithPassword } = useUserLogin();
    const [loginId, setLoginId] = useState(defaultLoginId);
    const [password, setPassword] = useState("password");

    const handlePasswordLogin = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        onPendingChange(true);
        onMessageChange("");

        try {
            await loginWithPassword(loginId, password);
        } catch (error) {
            onMessageChange(error instanceof Error ? error.message : USER_ERROR_MESSAGES.LOGIN_FAILED);
            onPendingChange(false);
        }
    };

    return (
        <form className="auth-form" onSubmit={handlePasswordLogin}>
            <label>
                Login ID
                <input
                    type="text"
                    value={loginId}
                    onChange={(event) => setLoginId(event.target.value)}
                    placeholder="user"
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
            <button className="primary-button" type="submit" disabled={pending}>
                Sign in
            </button>
        </form>
    );
}

export default UserLoginForm;
