export type UserSessionMe = {
    sub?: string;
    name?: string;
    userId?: number;
    loginId?: string;
    email?: string;
    roles?: string[];
};

export type UserAuthMeResponse = {
    authenticated: boolean;
    user: UserSessionMe | null;
    reason?: string | null;
};

export type UserServiceMeResponse = {
    sub: string;
    userId: number;
    loginId: string;
    email: string;
    roles: string[];
};

export type UserSessionState = {
    me: UserSessionMe | null;
    isAuthenticated: boolean;
    loading: boolean;
    error: string | null;
};
