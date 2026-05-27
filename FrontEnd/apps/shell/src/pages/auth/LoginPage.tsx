import { redirectToLogin } from "../../features/auth/authApi.ts";

function LoginPage() {
    return (
        <div>
            <h1>Login</h1>

            <button onClick={redirectToLogin}>
                Login with Authorization Server
            </button>
        </div>
    );
}

export default LoginPage;