import { BFF_BASE_URL } from "../../config/shellEnv";

type BffRequestOptions = Omit<RequestInit, "credentials">;

export async function bffRequest<T>(
    path: string,
    options: BffRequestOptions = {}
): Promise<T> {
    const response = await fetch(`${BFF_BASE_URL}${path}`, {
        ...options,
        credentials: "include",
        headers: {
            Accept: "application/json",
            ...(options.body ? { "Content-Type": "application/json" } : {}),
            ...options.headers,
        },
    });

    if (!response.ok) {
        const errorBody = await response.text().catch(() => "");
        throw new Error(resolveErrorMessage(errorBody, response.status));
    }

    if (response.status === 204) {
        return undefined as T;
    }

    const text = await response.text();

    if (!text) {
        return undefined as T;
    }

    return JSON.parse(text) as T;
}

export function bffGet<T>(path: string): Promise<T> {
    return bffRequest<T>(path, {
        method: "GET",
    });
}

export function bffPost<T>(path: string, body?: unknown): Promise<T> {
    return bffRequest<T>(path, {
        method: "POST",
        body: body === undefined ? undefined : JSON.stringify(body),
    });
}

function resolveErrorMessage(errorBody: string, status: number) {
    if (!errorBody) {
        return `BFF request failed. status=${status}`;
    }

    try {
        const parsed = JSON.parse(errorBody) as {
            message?: string;
            detail?: string;
            error?: string;
        };

        return parsed.message ?? parsed.detail ?? parsed.error ?? errorBody;
    } catch {
        return errorBody;
    }
}
