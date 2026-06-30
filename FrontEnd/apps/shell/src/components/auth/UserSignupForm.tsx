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

// Renders the user signup form.
function UserSignupForm({ pending, onPendingChange, onMessageChange, onSignupSuccess }: UserSignupFormProps) {
    const { signup } = useUserSignup();
    const [loginId, setLoginId] = useState("");
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [username, setUsername] = useState("");
    const [phoneNumber, setPhoneNumber] = useState("");

    // Submits a new user signup request.
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
                phoneNumber,
                whatsappNumber: phoneNumber,
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
                아이디
                <input
                    type="text"
                    value={loginId}
                    onChange={(event) => setLoginId(event.target.value)}
                    placeholder="user01"
                    required
                />
            </label>
            <label>
                이메일
                <input
                    type="email"
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                    placeholder="user@example.com"
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
    );
}

export default UserSignupForm;
