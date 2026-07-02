import axios from "axios";
import type { AxiosError, AxiosRequestConfig } from "axios";
import { resolveMemberApiErrorMessage, unwrapMemberApiResponse } from "./memberApiContract";

const CSRF_COOKIE_NAME = "MEMBER-XSRF-TOKEN";
const CSRF_BOOTSTRAP_PATH = "/bff/auth/me";

export class MemberApiError extends Error {
    status: number;

    constructor(status: number, message: string) {
        super(message);
        this.status = status;
        this.name = "MemberApiError";
    }
}

export const memberApiClient = axios.create({
    withCredentials: true,
    withXSRFToken: true,
    xsrfCookieName: CSRF_COOKIE_NAME,
    xsrfHeaderName: "X-MEMBER-XSRF-TOKEN",
    headers: {
        Accept: "application/json",
    },
});

// Sends a typed JSON request through the member gateway.
export async function memberRequest<T>(config: AxiosRequestConfig): Promise<T> {
    try {
        await ensureCsrfToken(config);
        const response = await memberApiClient.request<T>(config);
        return unwrapMemberApiResponse<T>(response.data);
    } catch (error) {
        throw toMemberApiError(error);
    }
}

async function ensureCsrfToken(config: AxiosRequestConfig) {
    const url = config.url ?? "";

    if (!isUnsafeMethod(config.method) || !isMemberBffPath(url) || url === CSRF_BOOTSTRAP_PATH || readCookie(CSRF_COOKIE_NAME)) {
        return;
    }

    await memberApiClient.get(CSRF_BOOTSTRAP_PATH).catch(() => undefined);
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

function isUnsafeMethod(method?: string) {
    const resolvedMethod = method?.toUpperCase() ?? "GET";
    return !["GET", "HEAD", "OPTIONS", "TRACE"].includes(resolvedMethod);
}

function isMemberBffPath(path: string) {
    return path.startsWith("/bff/");
}

function readCookie(name: string) {
    if (typeof document === "undefined") {
        return null;
    }

    const cookie = document.cookie
        .split("; ")
        .find((entry) => entry.startsWith(`${name}=`));

    if (!cookie) {
        return null;
    }

    return decodeURIComponent(cookie.substring(name.length + 1));
}
