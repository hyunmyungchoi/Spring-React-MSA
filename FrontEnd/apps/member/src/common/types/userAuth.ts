export type UserLogoutResponse = {
    logout: string;
    authServerLogoutRequired?: boolean;
    authServerLogoutUrl?: string;
};

export type UserHeartbeatResponse = {
    online: boolean;
    heartbeatAt: string;
    expiresAt: string;
    ttlSeconds: number;
};

export type { PasswordLoginResponse } from "./login";
export type { SignupRequest, SignupResponse } from "./signup";
