import type { UserSessionMe } from "./userSession";

export type PasswordLoginRequest = {
    loginId: string;
    password: string;
};

export type PasswordLoginResponse = {
    authenticated: boolean;
    redirectUrl?: string;
    user?: UserSessionMe;
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
    user?: UserSessionMe;
};
