export type UserLogoutResponse = {
    logout: string;
    authServerLogoutRequired?: boolean;
    authServerLogoutUrl?: string;
};

export type { EmailOtpSendResponse, EmailOtpVerifyResponse, PasswordLoginResponse } from "./login";
export type { SignupRequest, SignupResponse } from "./signup";
