import type { UserApiErrorBody } from '../types/userResponse'

export type MemberApiEnvelope<T> = {
    success?: boolean;
    status?: number;
    data?: T;
    message?: string | null;
    detail?: string;
    error?: string;
};

export class MemberApiContractError extends Error {
    status: number;

    constructor(status: number, message: string) {
        super(message);
        this.status = status;
        this.name = "MemberApiContractError";
    }
}

// Normalizes direct JSON bodies and shared BFF envelopes into one typed value.
export function unwrapMemberApiResponse<T>(body: T | MemberApiEnvelope<T>): T {
    if (!isMemberApiEnvelope<T>(body)) {
        return body;
    }

    if (body.success === false) {
        throw new MemberApiContractError(body.status ?? 0, body.message ?? body.error ?? "Member API request failed");
    }

    return body.data as T;
}

export function resolveMemberApiErrorMessage(errorBody: unknown, status: number) {
    if (!errorBody) {
        return `Member API request failed: ${status}`;
    }

    if (typeof errorBody === "string") {
        try {
            return resolveMemberApiErrorMessage(JSON.parse(errorBody) as UserApiErrorBody, status);
        } catch {
            return errorBody;
        }
    }

    if (typeof errorBody === "object") {
        const parsed = errorBody as UserApiErrorBody;
        return parsed.message ?? parsed.detail ?? parsed.error ?? `Member API request failed: ${status}`;
    }

    return `Member API request failed: ${status}`;
}

function isMemberApiEnvelope<T>(body: T | MemberApiEnvelope<T>): body is MemberApiEnvelope<T> {
    return typeof body === "object" && body !== null && ("success" in body || "data" in body || "status" in body);
}
