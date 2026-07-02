import { signupUser } from "../api/userAuthApi";

// Exposes the user signup API command to forms.
export function useUserSignup() {
    return {
        signup: signupUser,
    };
}
