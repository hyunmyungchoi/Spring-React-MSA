import { bffGet } from "../../common/api/bffClient";

export type UserMeResponse = {
    sub: string;
    userId: number;
    loginId: string;
    email: string;
    roles: string[];
};

export async function getUserMe(): Promise<UserMeResponse> {
    return bffGet<UserMeResponse>("/bff/user/me");
}