import { useState } from "react";
import type { FormEvent } from "react";
import { useUserSignup } from "../../hooks/useUserSignup";
import { USER_ERROR_MESSAGES } from "../../messages/userErrorMessages";
import type { SignupResponse } from "../../types/signup";

type UserSignupFormProps = {
    pending: boolean;
    onPendingChange: (pending: boolean) => void;
    onMessageChange: (message: string) => void;
    onSignupSuccess: (response: SignupResponse) => void;
};

// Renders the member signup form.
function UserSignupForm({ pending, onPendingChange, onMessageChange, onSignupSuccess }: UserSignupFormProps) {
    const { signup } = useUserSignup();
    const [loginId, setLoginId] = useState("");
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [username, setUsername] = useState("");
    const [phoneNumber, setPhoneNumber] = useState("");

    const handleSignup = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        onPendingChange(true);
        onMessageChange("");

        try {
            const response = await signup({
                loginId,
                email,
                password,
                username,
                phoneNumber: phoneNumber || undefined,
            });

            onSignupSuccess(response);
        } catch (error) {
            onMessageChange(error instanceof Error ? error.message : USER_ERROR_MESSAGES.SIGNUP_FAILED);
        } finally {
            onPendingChange(false);
        }
    };

    return (
        <form className="auth-form" onSubmit={handleSignup}>
            <label>
                Login ID
                <input
                    type="text"
                    value={loginId}
                    onChange={(event) => setLoginId(event.target.value)}
                    placeholder="user01"
                    required
                />
            </label>
            <label>
                Email
                <input
                    type="email"
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                    placeholder="user@example.com"
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
                    minLength={4}
                />
            </label>
            <label>
                Name
                <input
                    type="text"
                    value={username}
                    onChange={(event) => setUsername(event.target.value)}
                    placeholder="Member name"
                    required
                />
            </label>
            <label>
                Phone
                <input
                    type="tel"
                    value={phoneNumber}
                    onChange={(event) => setPhoneNumber(event.target.value)}
                    placeholder="01012345678"
                />
            </label>

            <button className="primary-button" type="submit" disabled={pending}>
                Create member
            </button>
        </form>
    );
}

export default UserSignupForm;
