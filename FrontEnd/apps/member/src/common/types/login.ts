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
