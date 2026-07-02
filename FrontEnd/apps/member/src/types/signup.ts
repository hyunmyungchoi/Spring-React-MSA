export type SignupRequest = {
    loginId: string;
    email: string;
    password: string;
    username: string;
    phoneNumber?: string;
};

export type SignupResponse = {
    userId: number;
    loginId: string;
    email: string;
    username: string;
    enabled: boolean;
    roles: string[];
};
