import { UserFetchError } from "../api/userFetch";

// Converts unknown user errors into display-ready messages.
export function getUserErrorMessage(error: unknown, fallbackMessage: string): string {
    if (error instanceof UserFetchError) {
        return error.message ? `${fallbackMessage}. ${error.message}` : fallbackMessage;
    }

    if (error instanceof Error && error.message) {
        return error.message;
    }

    return fallbackMessage;
}
