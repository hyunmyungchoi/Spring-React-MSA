import { MemberApiError } from "../api/memberApiClient";

// Converts unknown user errors into display-ready messages.
export function getUserErrorMessage(error: unknown, fallbackMessage: string): string {
    if (error instanceof MemberApiError) {
        return error.message ? `${fallbackMessage}. ${error.message}` : fallbackMessage;
    }

    if (error instanceof Error && error.message) {
        return error.message;
    }

    return fallbackMessage;
}
