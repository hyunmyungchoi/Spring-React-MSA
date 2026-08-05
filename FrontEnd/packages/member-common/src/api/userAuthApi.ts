import type { PasswordLoginResponse } from "../types/login";
import type { SignupRequest, SignupResponse } from "../types/signup";
import type { UserAuthMeResponse } from "../types/userSession";
import type { UserHeartbeatResponse, UserLogoutResponse } from "../types/userAuth";
import { memberRequest } from "./memberApiClient";

const MEMBER_OAUTH2_LOGIN_PATH = "/bff/oauth2/authorization/member-bff";

// Loads the current user authentication session.
export function fetchUserAuthMe(signal?: AbortSignal): Promise<UserAuthMeResponse> {
    return memberRequest<UserAuthMeResponse>({ 
        url: "/bff/auth/me", 
        signal 
    });
}

// Requests user logout from the BFF.
export function requestUserLogout(): Promise<UserLogoutResponse> {
    return memberRequest<UserLogoutResponse>({
        url: "/bff/auth/logout",
        method: "POST",
    });
}

// Refreshes the member online heartbeat TTL key.
export function sendUserHeartbeat(): Promise<UserHeartbeatResponse> {
    return memberRequest<UserHeartbeatResponse>({
        url: "/bff/auth/heartbeat",
        method: "POST",
    });
}

// Creates a user account through the BFF.
export function signupUser(request: SignupRequest): Promise<SignupResponse> {
    return memberRequest<SignupResponse>({
        url: "/bff/registration/member",
        method: "POST",
        data: request,
    });
}

// Starts a password login and redirects to the authorization flow.
export async function loginUserWithPassword(loginId: string, password: string): Promise<PasswordLoginResponse> {
    const existingSession = await fetchUserAuthMe();

    if (existingSession.authenticated) {
        window.location.href = "/";
        return {
            authenticated: true,
            redirectUrl: "/",
            user: existingSession.user ?? undefined,
        };
    }

    const response = await memberRequest<PasswordLoginResponse>({
        url: "/login/password",
        method: "POST",
        data: { loginId, password },
    });

    window.location.href = response.redirectUrl ?? MEMBER_OAUTH2_LOGIN_PATH;
    return response;
}
