import { loginUserWithPassword } from "../api/userAuthApi";

// Exposes user login API commands to forms.
export function useUserLogin() {
    return {
        loginWithPassword: loginUserWithPassword,
    };
}
