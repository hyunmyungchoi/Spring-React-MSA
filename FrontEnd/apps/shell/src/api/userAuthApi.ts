import { USER_GATEWAY_BASE_URL } from "../config/userEnv";
import { USER_ERROR_MESSAGES } from "../messages/userErrorMessages";
import type { EmailOtpSendResponse, EmailOtpVerifyResponse, PasswordLoginResponse } from "../types/login";
import type { SignupRequest, SignupResponse } from "../types/signup";
import type { UserAuthMeResponse } from "../types/userSession";
import type { UserLogoutResponse } from "../types/userAuth";
import { userFetchJson } from "./userFetch";

// Loads the current user authentication session.
export function fetchUserAuthMe(signal?: AbortSignal): Promise<UserAuthMeResponse> {
    return userFetchJson<UserAuthMeResponse>("/bff/auth/me", { signal });
}

// Requests user logout from the BFF.
export function requestUserLogout(): Promise<UserLogoutResponse> {
    return userFetchJson<UserLogoutResponse>("/bff/auth/logout", {
        method: "POST",
    });
}

// Creates a user account through the BFF.
export function signupUser(request: SignupRequest): Promise<SignupResponse> {
    return userFetchJson<SignupResponse>("/bff/auth/signup", {
        method: "POST",
        body: request,
    });
}

// Starts a password login and redirects to the authorization flow.
export async function loginUserWithPassword(loginId: string, password: string): Promise<PasswordLoginResponse> {
    await prepareUserAuthorizationRequest();

    const response = await userFetchJson<PasswordLoginResponse>("/login/password", {
        method: "POST",
        body: { loginId, password },
    });

    if (!response.redirectUrl) {
        throw new Error(USER_ERROR_MESSAGES.LOGIN_REDIRECT_PATH_NOT_FOUND);
    }

    window.location.href = response.redirectUrl;
    return response;
}

// Sends an email OTP for user login.
export async function sendUserEmailOtp(email: string): Promise<EmailOtpSendResponse> {
    await prepareUserAuthorizationRequest();

    return userFetchJson<EmailOtpSendResponse>("/login/email/send-otp", {
        method: "POST",
        body: { email },
    });
}

// Verifies an email OTP and redirects to the authorization flow.
export async function verifyUserEmailOtp(email: string, otp: string): Promise<EmailOtpVerifyResponse> {
    const response = await userFetchJson<EmailOtpVerifyResponse>("/login/email/verify", {
        method: "POST",
        body: { email, otp },
    });

    if (!response.redirectUrl) {
        throw new Error(USER_ERROR_MESSAGES.LOGIN_REDIRECT_PATH_NOT_FOUND);
    }

    window.location.href = response.redirectUrl;
    return response;
}

// Prepares the server-side authorization request before credential submission.
async function prepareUserAuthorizationRequest() {
    const response = await fetch(`${USER_GATEWAY_BASE_URL}/bff/auth/login`, {
        credentials: "include",
        headers: {
            Accept: "text/html",
        },
    });

    if (!response.ok) {
        throw new Error(USER_ERROR_MESSAGES.LOGIN_SESSION_CREATE_FAILED);
    }
}
