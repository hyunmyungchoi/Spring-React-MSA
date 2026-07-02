export type UserLogoutResponse = {
    logout: string;
    authServerLogoutRequired?: boolean;
    authServerLogoutUrl?: string;
};

export type { PasswordLoginResponse } from "./login";
export type { SignupRequest, SignupResponse } from "./signup";
