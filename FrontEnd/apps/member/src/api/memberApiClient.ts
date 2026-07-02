import axios from "axios";
import type { AxiosError, AxiosRequestConfig } from "axios";
import { MEMBER_GATEWAY_BASE_URL } from "../config/userEnv";
import { resolveMemberApiErrorMessage, unwrapMemberApiResponse } from "./memberApiContract";

export class MemberApiError extends Error {
    status: number;

    constructor(status: number, message: string) {
        super(message);
        this.status = status;
        this.name = "MemberApiError";
    }
}

export const memberApiClient = axios.create({
    baseURL: MEMBER_GATEWAY_BASE_URL,
    withCredentials: true,
    xsrfCookieName: "XSRF-TOKEN",
    xsrfHeaderName: "X-XSRF-TOKEN",
    headers: {
        Accept: "application/json",
    },
});

// Sends a typed JSON request through the member gateway.
export async function memberRequest<T>(config: AxiosRequestConfig): Promise<T> {
    try {
        const response = await memberApiClient.request<T>(config);
        return unwrapMemberApiResponse<T>(response.data);
    } catch (error) {
        throw toMemberApiError(error);
    }
}

// Extracts the most useful error text from a failed member BFF response.
function toMemberApiError(error: unknown) {
    if (!axios.isAxiosError(error)) {
        return error;
    }

    const axiosError = error as AxiosError<unknown>;
    const status = axiosError.response?.status ?? 0;
    const message = resolveMemberApiErrorMessage(axiosError.response?.data, status);
    return new MemberApiError(status, message);
}
