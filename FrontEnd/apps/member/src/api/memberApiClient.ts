import { USER_GATEWAY_BASE_URL } from "../config/userEnv";
import type { UserApiErrorBody } from "../types/userResponse";

type UserFetchOptions = Omit<RequestInit, "body" | "credentials"> & {
    body?: unknown;
};

export class UserFetchError extends Error {
    status: number;

    constructor(status: number, message: string) {
        super(message);
        this.status = status;
        this.name = "UserFetchError";
    }
}

// Sends a JSON request to the user BFF with cookie credentials.
export async function userFetchJson<T>(path: string, options: UserFetchOptions = {}): Promise<T> {
    const response = await fetch(`${USER_GATEWAY_BASE_URL}${path}`, {
        ...options,
        credentials: "include",
        headers: {
            Accept: "application/json",
            ...(options.body === undefined ? {} : { "Content-Type": "application/json" }),
            ...options.headers,
        },
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
    });

    if (!response.ok) {
        const errorBody = await response.text().catch(() => "");
        throw new UserFetchError(response.status, resolveUserErrorMessage(errorBody, response.status));
    }

    if (response.status === 204) {
        return undefined as T;
    }

    const text = await response.text();
    return text ? (JSON.parse(text) as T) : (undefined as T);
}

// Extracts the most useful error text from a failed user BFF response.
function resolveUserErrorMessage(errorBody: string, status: number) {
    if (!errorBody) {
        return `User API request failed: ${status}`;
    }

    try {
        const parsed = JSON.parse(errorBody) as UserApiErrorBody;
        return parsed.message ?? parsed.detail ?? parsed.error ?? errorBody;
    } catch {
        return errorBody;
    }
}
