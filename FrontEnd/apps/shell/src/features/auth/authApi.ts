import { bffGet, bffPost } from "../../common/api/bffClient";
import { BFF_BASE_URL } from "../../config/shellEnv";

type LogoutResponse = {
    logout: string;
    authServerLogoutRequired?: boolean;
    authServerLogoutUrl?: string;
};

export type SignupRequest = {
    loginId: string;
    email: string;
    password: string;
    username: string;
    phoneNumber?: string;
    whatsappNumber?: string;
};

export type SignupResponse = {
    userId: number;
    loginId: string;
    email: string;
    username: string;
    enabled: boolean;
    roles: string[];
};

export type EmailOtpSendResponse = {
    sent: boolean;
    expiresInSeconds: number;
    devOtp?: string;
};

export type EmailOtpVerifyResponse = {
    verified: boolean;
    authenticated: boolean;
    redirectUrl?: string;
    user?: unknown;
};

export type PasswordLoginResponse = {
    authenticated: boolean;
    redirectUrl?: string;
    user?: unknown;
};

export async function fetchAuthMe() {
    return bffGet<unknown>("/bff/auth/me");
}

export async function logout() {
    const response = await bffPost<LogoutResponse>("/bff/auth/logout");

    if (response.authServerLogoutRequired && response.authServerLogoutUrl) {
        window.location.href = response.authServerLogoutUrl;
        return;
    }

    window.location.href = "/login";
}

export async function signup(request: SignupRequest) {
    return bffPost<SignupResponse>("/bff/auth/signup", request);
}

export async function loginWithPassword(loginId: string, password: string) {
    await prepareAuthorizationRequest();

    const response = await bffPost<PasswordLoginResponse>("/login/password", {
        loginId,
        password,
    });

    if (!response.redirectUrl) {
        throw new Error("로그인 이동 경로를 찾지 못했습니다.");
    }

    window.location.href = response.redirectUrl;

    return response;
}

export async function sendEmailOtp(email: string) {
    await prepareAuthorizationRequest();
    return bffPost<EmailOtpSendResponse>("/login/email/send-otp", { email });
}

export async function verifyEmailOtp(email: string, otp: string) {
    const response = await bffPost<EmailOtpVerifyResponse>("/login/email/verify", {
        email,
        otp,
    });

    if (response.redirectUrl) {
        window.location.href = response.redirectUrl;
    } else {
        throw new Error("로그인 이동 경로를 찾지 못했습니다.");
    }

    return response;
}

async function prepareAuthorizationRequest() {
    const response = await fetch(`${BFF_BASE_URL}/bff/auth/login`, {
        credentials: "include",
        headers: {
            Accept: "text/html",
        },
    });

    if (!response.ok) {
        throw new Error("로그인 세션을 만들지 못했습니다.");
    }
}
